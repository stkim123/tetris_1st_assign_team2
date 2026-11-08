package kr.ac.jbnu.se.tetris.app;

import kr.ac.jbnu.se.tetris.ai.AIPlan;
import kr.ac.jbnu.se.tetris.ai.HeuristicStrategy;
import kr.ac.jbnu.se.tetris.ai.HeuristicWeights;
import kr.ac.jbnu.se.tetris.battle.BattleState;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameState;

/** 실제 비동기 몬스터 worker와 공통 전투 엔진을 창 없이 끝까지 검증 */
public final class MonsterSessionTest {
    private static final long VIRTUAL_TURN_NANOS = 1_000_000_000L;
    private static final long WALL_LIMIT_NANOS = 30_000_000_000L;

    public static void main(String[] args) throws Exception {
        pauseCancelsAndResumes();
        seededDuelFinishes();
        closeCancelsAndIgnoresInput();
    }

    private static void pauseCancelsAndResumes() throws Exception {
        MonsterSession session = new MonsterSession(1);
        try {
            long before = session.getBattleState().getVersion();
            session.pulse(VIRTUAL_TURN_NANOS);
            session.pause();
            check(session.isPaused(), "Pause reaches battle state");
            check(!session.isThinking(), "Pause cancels pending worker plan");
            long paused = session.getBattleState().getVersion();
            check(paused > before, "Pause is a state transition");
            session.pulse(2 * VIRTUAL_TURN_NANOS);
            session.tick();
            session.submit(GameAction.Type.HARD_DROP);
            check(session.getBattleState().getVersion() == paused,
                    "Paused session ignores actions and ticks");
            session.resume();
            check(!session.isPaused(), "Resume reaches battle state");
            long deadline = System.nanoTime() + WALL_LIMIT_NANOS;
            AIPlan plan = awaitMonsterPlan(session, null, 3 * VIRTUAL_TURN_NANOS, deadline);
            check(plan != null && plan.getCandidateCount() > 0
                    && plan.getActions().contains(GameAction.Type.HARD_DROP),
                    "Resumed asynchronous worker makes a legal placement");
            check(session.getOpponentState().getVersion() > 1,
                    "Monster actions reached shared engine");
        } finally {
            session.close();
        }
    }

    private static void seededDuelFinishes() throws Exception {
        MonsterSession session = new MonsterSession(1);
        HeuristicStrategy player = new HeuristicStrategy(HeuristicWeights.TETRIS);
        long deadline = System.nanoTime() + WALL_LIMIT_NANOS;
        long virtualNow = VIRTUAL_TURN_NANOS;
        int monsterPlacements = 0;
        int playerPlacements = 0;
        int monsterCandidates = 0;
        long monsterPlanningNanos = 0;
        boolean damaged = false;
        boolean garbage = false;
        try {
            for (int turn = 0; turn < 300 && !session.isFinished(); turn++) {
                spawnAfterClear(session);
                if (session.isFinished()) break;

                GameState opponent = session.getOpponentState();
                if (opponent.getActivePiece() != null) {
                    AIPlan previous = session.getLastPlan();
                    AIPlan plan = awaitMonsterPlan(session, previous, virtualNow, deadline);
                    check(plan != null && plan.getCandidateCount() > 0
                            && plan.getActions().contains(GameAction.Type.HARD_DROP),
                            "Monster produces an applied placement");
                    monsterPlacements++;
                    monsterCandidates += plan.getCandidateCount();
                    monsterPlanningNanos += plan.getElapsedNanos();
                }
                virtualNow += VIRTUAL_TURN_NANOS;
                observeBattle(session.getBattleState());
                damaged |= hasDamage(session.getBattleState());
                garbage |= hasGarbage(session.getBattleState());
                if (session.isFinished()) break;

                spawnAfterClear(session);
                if (session.isFinished()) break;
                GameState human = session.getPlayerState();
                if (human.getActivePiece() != null) {
                    AIPlan plan = player.plan(human);
                    check(plan.getCandidateCount() > 0
                            && plan.getActions().contains(GameAction.Type.HARD_DROP),
                            "Player strategy found a legal placement");
                    for (GameAction.Type action : plan.getActions()) {
                        long before = session.getPlayerState().getVersion();
                        session.submit(action);
                        if (session.isFinished()) break;
                        check(session.getPlayerState().getVersion() > before,
                                "Player action reached shared engine: " + action);
                    }
                    playerPlacements++;
                }
                damaged |= hasDamage(session.getBattleState());
                garbage |= hasGarbage(session.getBattleState());
                if (System.nanoTime() >= deadline) break;
            }
            BattleState result = session.getBattleState();
            System.out.println("seed=1 playerPlacements=" + playerPlacements
                    + " monsterPlacements=" + monsterPlacements
                    + " monsterCandidates=" + monsterCandidates
                    + " monsterPlanningMs=" + monsterPlanningNanos / 1_000_000
                    + " playerHp=" + result.getParticipant(MonsterSession.PLAYER_ID).getHp()
                    + " monsterHp=" + result.getParticipant(MonsterSession.MONSTER_ID).getHp()
                    + " result=" + result.getReason() + " winner=" + result.getWinnerId());
            check(playerPlacements > 1 && monsterPlacements > 1,
                    "Both participants made multiple real placements");
            check(damaged, "A line clear caused HP damage");
            check(garbage, "A line clear queued garbage for an opponent");
            check(result.getStatus() == BattleState.Status.FINISHED
                    && result.getReason() != null, "Seeded duel reached a terminal match");
        } finally {
            session.close();
        }
    }

    private static void closeCancelsAndIgnoresInput() {
        MonsterSession session = new MonsterSession(2);
        session.pulse(VIRTUAL_TURN_NANOS);
        session.close();
        check(!session.isThinking(), "Close cancels worker");
        long version = session.getBattleState().getVersion();
        session.pulse(2 * VIRTUAL_TURN_NANOS);
        session.tick();
        session.submit(GameAction.Type.HARD_DROP);
        session.resume();
        session.close();
        check(session.getBattleState().getVersion() == version,
                "Closed session ignores later calls and closes twice");
    }

    private static AIPlan awaitMonsterPlan(MonsterSession session, AIPlan previous,
                                           long virtualNow, long deadline) throws Exception {
        do {
            session.pulse(virtualNow);
            if (session.getLastPlan() != previous) return session.getLastPlan();
            if (session.isFinished()) return null;
            Thread.sleep(2);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Monster worker did not complete before bounded duel deadline");
    }

    private static void spawnAfterClear(MonsterSession session) {
        if (!session.isFinished() && (session.getPlayerState().isAwaitingSpawn()
                || session.getOpponentState().isAwaitingSpawn())) session.tick();
    }

    private static boolean hasDamage(BattleState state) {
        return state.getParticipant(MonsterSession.PLAYER_ID).getHp() < 100
                || state.getParticipant(MonsterSession.MONSTER_ID).getHp() < 100;
    }

    private static boolean hasGarbage(BattleState state) {
        return state.getParticipant(MonsterSession.PLAYER_ID).getPendingGarbageLines() > 0
                || state.getParticipant(MonsterSession.MONSTER_ID).getPendingGarbageLines() > 0;
    }

    private static void observeBattle(BattleState state) {
        check(state.getParticipants().size() == 2, "Both participants remain addressable");
        check(state.getParticipant(MonsterSession.PLAYER_ID).getHp() >= 0
                && state.getParticipant(MonsterSession.MONSTER_ID).getHp() >= 0,
                "Battle HP remains non-negative");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
