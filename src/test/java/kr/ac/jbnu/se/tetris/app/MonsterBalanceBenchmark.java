package kr.ac.jbnu.se.tetris.app;

import java.util.Locale;
import kr.ac.jbnu.se.tetris.ai.AIPlan;
import kr.ac.jbnu.se.tetris.ai.AIProfile;
import kr.ac.jbnu.se.tetris.ai.AIProfileCatalog;
import kr.ac.jbnu.se.tetris.ai.DifficultyProfile;
import kr.ac.jbnu.se.tetris.ai.DifficultyProfileCatalog;
import kr.ac.jbnu.se.tetris.ai.HeuristicStrategy;
import kr.ac.jbnu.se.tetris.ai.HeuristicWeights;
import kr.ac.jbnu.se.tetris.battle.BattleState;
import kr.ac.jbnu.se.tetris.character.CharacterSpec;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.story.MonsterSpec;
import kr.ac.jbnu.se.tetris.story.MonsterTier;
import kr.ac.jbnu.se.tetris.story.Stage;
import kr.ac.jbnu.se.tetris.story.StageCatalog;

/** 실제 전투 엔진의 몬스터 밸런스 비교용 수동 도구 및 자동 Test 묶음에서 제외 */
public final class MonsterBalanceBenchmark {
    private static final int[] STAGE_INDEXES = {0, 1, 2};
    private static final long[] SEEDS = {1L, 7L};
    private static final int VIRTUAL_LIMIT_MILLIS = 120_000;
    private static final int QUANTUM_MILLIS = 100;
    private static final int HUMAN_CADENCE_MILLIS = 1_400;
    private static final long WALL_LIMIT_NANOS = 10_000_000_000L;

    private MonsterBalanceBenchmark() { }

    public static void main(String[] args) throws Exception {
        boolean smoke = args.length == 1 && ("--smoke".equals(args[0])
                || "--compare-smoke".equals(args[0]));
        boolean compare = args.length == 1 && ("--compare".equals(args[0])
                || "--compare-smoke".equals(args[0]));
        if (args.length > 1 || (args.length == 1 && !smoke && !compare)) {
            throw new IllegalArgumentException("Usage: MonsterBalanceBenchmark "
                    + "[--smoke|--compare|--compare-smoke]");
        }
        StageCatalog catalog = StageCatalog.loadDefault();
        AIProfileCatalog profiles = AIProfileCatalog.loadDefault();
        int[][][] summary = new int[2][MonsterTier.values().length][3];
        System.out.println("Synthetic benchmark: player = Heuristic SAFE surrogate, "
                + "one placement opportunity every 1400ms; not real human-play validation.");
        System.out.println("Rules: actual MonsterSession/BattleManager, each level's gravity/delay/profile, "
                + "virtual limit 120s, wall limit 10s per duel. "
                + "Comparison pairs share level rules, HP and seed; wall-clock budgets may vary.");
        System.out.println("| Stage | Lv | Tier | AI profile | Mode | Seed | Outcome | Virtual s | "
                + "HP player/monster | Placements player/monster | AI decisions | "
                + "Candidates total | Timeout/fallback | AI mean/max ms |");
        System.out.println("|---|---:|---|---|---|---:|---|---:|---:|---:|---:|---:|---:|---:|");

        // 세 과정 × 세 전투 × 두 시드의 총 18회 대전
        for (int stageIndex : STAGE_INDEXES) {
            Stage stage = catalog.getStages().get(stageIndex);
            for (MonsterSpec spec : stage.getEncounters()) {
                MonsterTier tier = spec.getTier();
                if (compare && tier == MonsterTier.NORMAL) continue;
                DifficultyProfile difficulty = DifficultyProfileCatalog.forEncounter(spec);
                AIProfile aiProfile = profiles.get(spec.getAiProfileId());
                for (long seed : SEEDS) {
                    if (smoke && (stageIndex != STAGE_INDEXES[0]
                            || tier != (compare ? MonsterTier.ELITE : MonsterTier.NORMAL)
                            || seed != SEEDS[0])) continue;
                    for (int mode = compare ? 0 : 1; mode <= 1; mode++) {
                        boolean fixedBaseline = compare && mode == 0;
                        Result result = run(spec, difficulty, seed, fixedBaseline);
                        int[] counts = summary[mode][tier.ordinal()];
                        if (result.outcome == Outcome.PLAYER) counts[0]++;
                        else if (result.outcome == Outcome.MONSTER) counts[1]++;
                        else counts[2]++;
                        System.out.println("| " + stage.getId() + " | " + difficulty.getLevel()
                                + " | " + tier + " | " + aiProfile.getProfileId() + " | "
                                + (fixedBaseline ? "FIXED baseline" : difficulty.getSpecialPattern())
                                + " | " + seed + " | " + result.outcome + " | "
                                + oneDecimal(result.virtualMillis / 1000.0) + " | "
                                + result.playerHp + "/" + result.monsterHp + " | "
                                + result.playerPlacements + "/" + result.monsterPlacements + " | "
                                + result.aiDecisions + " | " + result.aiCandidates + " | "
                                + result.aiTimeouts + "/" + result.aiFallbacks + " | "
                                + oneDecimal(result.aiDecisions == 0 ? 0
                                        : result.aiTotalNanos / 1_000_000.0 / result.aiDecisions)
                                + "/" + oneDecimal(result.aiMaxNanos / 1_000_000.0) + " |");
                    }
                }
            }
        }
        System.out.println("\n| Mode | Tier | Player wins | Monster wins | Unresolved |\n"
                + "|---|---|---:|---:|---:|");
        for (int mode = compare ? 0 : 1; mode <= 1; mode++) {
            for (MonsterTier tier : MonsterTier.values()) {
                if (compare && tier == MonsterTier.NORMAL) continue;
                if (smoke && tier != (compare ? MonsterTier.ELITE : MonsterTier.NORMAL)) continue;
                int[] counts = summary[mode][tier.ordinal()];
                System.out.println("| " + (mode == 0 ? "FIXED baseline" : "configured") + " | "
                        + tier + " | " + counts[0]
                        + " | " + counts[1] + " | " + counts[2] + " |");
            }
        }
    }

    private static Result run(MonsterSpec spec, DifficultyProfile difficulty, long seed,
                              boolean fixedBaseline) throws InterruptedException {
        DifficultyProfile selected = fixedBaseline
                ? difficulty.withEncounter(spec.getHp(), DifficultyProfile.SpecialPattern.FIXED,
                        spec.getAiProfileId()) : difficulty;
        MonsterSession session = new MonsterSession(seed, spec.getName(), selected,
                CharacterSpec.DEFAULT);
        HeuristicStrategy surrogate = new HeuristicStrategy(HeuristicWeights.SAFE,
                700, 40_000_000L);
        Result result = new Result();
        long wallDeadline = System.nanoTime() + WALL_LIMIT_NANOS;
        int nextHumanMillis = 0;
        try {
            for (int millis = 0; millis <= VIRTUAL_LIMIT_MILLIS;
                    millis += QUANTUM_MILLIS) {
                result.virtualMillis = millis;
                if (System.nanoTime() >= wallDeadline) {
                    result.outcome = Outcome.WALL_LIMIT;
                    break;
                }
                AIPlan previous = session.getLastPlan();
                long monsterVersion = session.getOpponentState().getVersion();
                long nowNanos = millis * 1_000_000L;
                session.pulse(nowNanos);

                // 작업자 종료까지 가상 시간을 멈춰 인위적인 계획 노후화 방지
                while (session.isThinking() && !session.isFinished()
                        && System.nanoTime() < wallDeadline) Thread.sleep(1);
                if (System.nanoTime() >= wallDeadline) {
                    result.outcome = Outcome.WALL_LIMIT;
                    break;
                }
                session.pulse(nowNanos);
                AIPlan ready = session.getLastPlan();
                if (ready != null && ready != previous) {
                    result.aiDecisions++;
                    result.aiCandidates += ready.getCandidateCount();
                    if (ready.isTimedOut()) result.aiTimeouts++;
                    if (ready.getPolicyDecision() != null
                            && ready.getPolicyDecision().isFallback()) result.aiFallbacks++;
                    result.aiTotalNanos += ready.getElapsedNanos();
                    result.aiMaxNanos = Math.max(result.aiMaxNanos, ready.getElapsedNanos());
                    if (ready.getActions().contains(GameAction.Type.HARD_DROP)
                            && session.getOpponentState().getVersion() > monsterVersion) {
                        result.monsterPlacements++;
                    }
                }
                if (session.isFinished()) break;

                // 실제 PvE 레벨별 중력 간격을 공유 전투 엔진에 적용
                if (millis > 0) session.advance(QUANTUM_MILLIS);
                if (session.isFinished()) break;

                if (millis >= nextHumanMillis) {
                    GameState state = session.getPlayerState();
                    if (state.getStatus() == GameState.Status.RUNNING
                            && state.getActivePiece() != null && !state.isAwaitingSpawn()) {
                        AIPlan plan = surrogate.plan(state);
                        for (GameAction.Type action : plan.getActions()) {
                            long beforeVersion = session.getPlayerState().getVersion();
                            session.submit(action);
                            if (action == GameAction.Type.HARD_DROP
                                    && session.getPlayerState().getVersion() > beforeVersion) {
                                result.playerPlacements++;
                            }
                            if (session.isFinished()) break;
                        }
                        nextHumanMillis = millis + HUMAN_CADENCE_MILLIS;
                    }
                }
                if (session.isFinished()) break;
            }
            BattleState battle = session.getBattleState();
            result.playerHp = battle.getParticipant(MonsterSession.PLAYER_ID).getHp();
            result.monsterHp = battle.getParticipant(MonsterSession.MONSTER_ID).getHp();
            if (battle.getStatus() == BattleState.Status.FINISHED) {
                String winner = battle.getWinnerId();
                result.outcome = MonsterSession.PLAYER_ID.equals(winner) ? Outcome.PLAYER
                        : MonsterSession.MONSTER_ID.equals(winner) ? Outcome.MONSTER : Outcome.DRAW;
            }
            return result;
        } finally {
            session.close();
        }
    }

    private static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private enum Outcome { PLAYER, MONSTER, DRAW, VIRTUAL_LIMIT, WALL_LIMIT }

    private static final class Result {
        private Outcome outcome = Outcome.VIRTUAL_LIMIT;
        private int virtualMillis;
        private int playerHp;
        private int monsterHp;
        private int playerPlacements;
        private int monsterPlacements;
        private int aiDecisions;
        private int aiCandidates;
        private int aiTimeouts;
        private int aiFallbacks;
        private long aiTotalNanos;
        private long aiMaxNanos;
    }
}
