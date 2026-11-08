package kr.ac.jbnu.se.tetris.app;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import kr.ac.jbnu.se.tetris.ai.AIContext;
import kr.ac.jbnu.se.tetris.ai.DifficultyProfile;
import kr.ac.jbnu.se.tetris.ai.HeuristicWeights;
import kr.ac.jbnu.se.tetris.ai.PolicyDecision;
import kr.ac.jbnu.se.tetris.character.CharacterSpec;
import kr.ac.jbnu.se.tetris.ai.AIPlan;
import kr.ac.jbnu.se.tetris.ai.AIStrategy;
import kr.ac.jbnu.se.tetris.ai.HeuristicStrategy;
import kr.ac.jbnu.se.tetris.ai.PlayerProfile;
import kr.ac.jbnu.se.tetris.ai.PlacementLog;
import kr.ac.jbnu.se.tetris.battle.BattleEvent;
import kr.ac.jbnu.se.tetris.battle.BattleManager;
import kr.ac.jbnu.se.tetris.battle.BattleResult;
import kr.ac.jbnu.se.tetris.battle.BattleState;
import kr.ac.jbnu.se.tetris.battle.ParticipantSpec;
import kr.ac.jbnu.se.tetris.battle.ParticipantState;
import kr.ac.jbnu.se.tetris.controller.AIController;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.GameEvent;

/** AI 계산은 worker에서, 완성된 계획은 호출자(앱 EDT)의 같은 전투 큐에서 실행 */
public final class MonsterSession implements PlaySession {
    public static final String PLAYER_ID = "local";
    public static final String MONSTER_ID = "monster";
    // 정상 진행 중 계획 적용 완료 후 다음 판단 요청까지의 대기 간격(ns), 증가 시 상대 행동 빈도 감소
    // 몬스터 행동 간격 조절 위치는 ai/profiles.properties의 delayMillis
    private final long actionDelayNanos;
    private final BattleManager battle;
    private final AIController ai;
    private final PlayerProfile profile;
    private final PlacementLog placementLog;
    private final String matchId;
    private final String localParticipantId;
    private final String opponentParticipantId;
    private final LongSupplier clock;
    private long nextDecisionNanos;
    private long remainingDelayNanos;
    private long nextDecisionId;
    private HeuristicWeights previousWeights;
    private String policyState;
    private BattleResult lastBattleResult;
    private AIPlan lastPlan;
    private boolean closed;

    public MonsterSession(long seed) {
        this(seed, "일반 몬스터", 100, 700, new HeuristicStrategy(), new PlayerProfile(), new PlacementLog());
    }

    /** 스토리 전투에서 캐릭터와 난이도 설정을 한 번에 주입한다. */
    public MonsterSession(long seed, String name, DifficultyProfile difficulty,
                          CharacterSpec playerCharacter) {
        this(seed, name, difficulty, playerCharacter, UUID.randomUUID().toString(),
                PLAYER_ID, MONSTER_ID);
    }

    /** 스토리 진행 서비스의 run ID를 그대로 전투 ID에 연결한다. */
    public MonsterSession(long seed, String name, DifficultyProfile difficulty,
                          CharacterSpec playerCharacter, String matchId,
                          String localId, String opponentId) {
        this(seed, name, requireDifficulty(difficulty).getMonsterHp(),
                difficulty.getMonsterDelayMillis(), MonsterStrategies.create(difficulty),
                new PlayerProfile(), new PlacementLog(), matchId,
                localId, opponentId, System::nanoTime,
                playerCharacter == null ? CharacterSpec.DEFAULT : playerCharacter, difficulty);
    }

    private static DifficultyProfile requireDifficulty(DifficultyProfile difficulty) {
        if (difficulty == null) throw new IllegalArgumentException("Difficulty required");
        return difficulty;
    }

    /** 난이도 데이터와 전략을 주입하며 사람/몬스터의 전투 규칙은 같은 엔진을 사용 */
    public MonsterSession(long seed, String name, int hp, int delayMillis, AIStrategy strategy,
                          PlayerProfile profile, PlacementLog placementLog) {
        this(seed, name, hp, delayMillis, strategy, profile, placementLog, UUID.randomUUID().toString(),
                PLAYER_ID, MONSTER_ID, System::nanoTime);
    }

    public MonsterSession(long seed, String name, int hp, int delayMillis, AIStrategy strategy,
            PlayerProfile profile, PlacementLog placementLog, String matchId, String localId,
            String opponentId, LongSupplier clock) {
        this(seed, name, hp, delayMillis, strategy, profile, placementLog, matchId,
                localId, opponentId, clock, CharacterSpec.DEFAULT, null);
    }

    private MonsterSession(long seed, String name, int hp, int delayMillis, AIStrategy strategy,
            PlayerProfile profile, PlacementLog placementLog, String matchId, String localId,
            String opponentId, LongSupplier clock, CharacterSpec playerCharacter,
            DifficultyProfile difficulty) {
        if (delayMillis <= 0 || profile == null || placementLog == null || strategy == null) {
            throw new IllegalArgumentException("Invalid monster session settings");
        }
        this.actionDelayNanos = delayMillis * 1_000_000L;
        this.profile = profile;
        this.placementLog = placementLog;
        if (matchId == null || matchId.trim().isEmpty() || clock == null) {
            throw new IllegalArgumentException("Match identity and clock required");
        }
        this.matchId = matchId; this.localParticipantId = localId;
        this.opponentParticipantId = opponentId; this.clock = clock;
        placementLog.beginSession();
        List<ParticipantSpec> specs = Arrays.asList(new ParticipantSpec(localId, "PLAYER", playerCharacter),
                new ParticipantSpec(opponentId, name, hp));
        battle = difficulty == null ? new BattleManager(specs, seed) : BattleManager.pve(specs, seed,
                difficulty.getPlayerGravityMillis(), difficulty.getMonsterDelayMillis(),
                difficulty.getAttackStrength().getDamageBuff(),
                difficulty.getAttackStrength().getExtraGarbageLines(),
                difficulty.getMonsterItemLevel());
        GameState before = getPlayerState();
        BattleResult start = battle.start();
        lastBattleResult = start;
        if (!start.isAccepted()) throw new IllegalStateException("Battle could not start: " + start.getReason());
        observe(before, GameAction.Type.START, start);
        ai = new AIController(strategy);
    }
    @Override public void submit(GameAction.Type action) {
        if (!closed) {
            GameState before = getPlayerState();
            observe(before, action, battle.submit(localParticipantId, action));
        }
        if (isFinished()) ai.cancelPending();
    }
    @Override public void tick() {
        if (!closed) {
            GameState before = getPlayerState();
            observe(before, GameAction.Type.GRAVITY_TICK, battle.tick());
        }
        if (isFinished()) ai.cancelPending();
    }
    public void advance(long elapsedMillis) {
        if (!closed) {
            GameState before = getPlayerState();
            observe(before, GameAction.Type.GRAVITY_TICK, battle.advance(elapsedMillis));
        }
        if (isFinished()) ai.cancelPending();
    }
    private void observe(GameState before, GameAction.Type action, BattleResult result) {
        lastBattleResult = result;
        // 피해 이벤트에도 같은 원본이 붙으므로 CORE_EVENT만 골라 중복 없이 기록
        List<GameEvent> events = new ArrayList<GameEvent>();
        for (BattleEvent event : result.getEvents()) {
            if (event.getType() == BattleEvent.Type.CORE_EVENT && localParticipantId.equals(event.getActorId())) {
                events.add(event.getCoreEvent());
            }
        }
        profile.observe(before, action, events);
        placementLog.observe(before, action, events);
    }
    @Override public void pause() {
        ai.cancelPending();
        if (!closed && !isFinished() && !isPaused()) {
            remainingDelayNanos = Math.max(0, nextDecisionNanos - clock.getAsLong());
            lastBattleResult = battle.pause();
        }
    }
    @Override public void resume() {
        if (!closed && isPaused()) {
            lastBattleResult = battle.resume();
            nextDecisionNanos = remainingDelayNanos == 0 ? 0 : clock.getAsLong() + remainingDelayNanos;
        }
    }
    @Override public void pulse(long nowNanos) {
        if (closed || isPaused() || isFinished()) return;
        GameState opponent = getOpponentState();
        AIPlan ready = ai.poll(opponent);
        if (ready != null) {
            lastPlan = ready;
            int accepted = 0;
            for (GameAction.Type action : ready.getActions()) {
                BattleResult result = battle.submit(opponentParticipantId, action);
                lastBattleResult = result;
                if (!result.isAccepted()) break;
                accepted++;
                if (isFinished()) break;
            }
            PolicyDecision decision = ready.getPolicyDecision();
            if (decision != null && accepted > 0 && accepted == ready.getActions().size()) {
                previousWeights = decision.getWeights(); policyState = decision.getNextPolicyState();
            }
            if (!isFinished() && accepted == ready.getActions().size()) useOneMonsterItem();
            nextDecisionNanos = nowNanos + actionDelayNanos;
        }
        opponent = getOpponentState();
        if (!isFinished() && nowNanos >= nextDecisionNanos && !ai.isThinking()
                && opponent.getActivePiece() != null && !opponent.isAwaitingSpawn()) {
            ai.request(new AIContext(matchId, opponent, battle.getState().getParticipant(opponentParticipantId).getHp(),
                    battle.getState().getParticipant(opponentParticipantId).getMaxHp(), profile.snapshot(),
                    nextDecisionId++, previousWeights, policyState));
        }
    }
    /** AI 배치가 확정된 뒤 사용 가능한 수동 아이템 한 개만 사용한다. */
    private void useOneMonsterItem() {
        BattleState state = battle.getState();
        ParticipantState self = state.getParticipant(opponentParticipantId);
        ParticipantState player = state.getParticipant(localParticipantId);
        for (String item : self.getItems()) {
            String target = opponentParticipantId;
            if ("heal".equals(item) && self.getHp() == self.getMaxHp()) continue;
            if ("fever_charge".equals(item) && self.isFeverActive()) continue;
            if ("time_warp".equals(item) && self.getTimeWarpRemainingMillis() > 0) continue;
            if ("nullify".equals(item) && player.getItems().isEmpty()) continue;
            if ("garbage_bomb".equals(item) || "nullify".equals(item)) target = localParticipantId;
            if ("damage_boost".equals(item) || "shield".equals(item)) continue;
            BattleResult used = battle.submitItem(opponentParticipantId,
                    new GameAction.ItemUse(item, target));
            if (used.isAccepted()) { lastBattleResult = used; break; }
        }
    }
    public BattleResult submitItem(GameAction.ItemUse item) {
        if (closed) throw new IllegalStateException("CLOSED");
        lastBattleResult = battle.submitItem(localParticipantId, item);
        return lastBattleResult;
    }
    public BattleResult leave() {
        if (closed) throw new IllegalStateException("CLOSED");
        lastBattleResult = battle.forfeit(localParticipantId); ai.cancelPending();
        return lastBattleResult;
    }
    public String getLocalParticipantId() { return localParticipantId; }
    public String getOpponentParticipantId() { return opponentParticipantId; }
    public String getMatchId() { return matchId; }
    public BattleResult getLastBattleResult() { return lastBattleResult; }
    public HeuristicWeights getPreviousWeights() { return previousWeights; }
    public String getPolicyState() { return policyState; }
    public long getRemainingDecisionDelayNanos() { return isPaused() ? remainingDelayNanos
            : Math.max(0, nextDecisionNanos - clock.getAsLong()); }
    public GameState getOpponentState() { return battle.getState().getParticipant(opponentParticipantId).getGameState(); }
    public AIPlan getLastPlan() { return lastPlan; }
    @Override public GameState getPlayerState() { return battle.getState().getParticipant(localParticipantId).getGameState(); }
    @Override public BattleState getBattleState() { return battle.getState(); }
    @Override public boolean isPaused() { return battle.getState().getStatus() == BattleState.Status.PAUSED; }
    @Override public boolean isFinished() { return battle.getState().getStatus() == BattleState.Status.FINISHED; }
    @Override public boolean isThinking() { return ai.isThinking(); }
    @Override public void close() {
        if (closed) return;
        pause(); closed = true; ai.close();
    }
}
