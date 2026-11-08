package kr.ac.jbnu.se.tetris.ai;

import java.util.ArrayList;
import java.util.List;
import kr.ac.jbnu.se.tetris.core.ActionResult;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameEngine;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.PieceGenerator;
import kr.ac.jbnu.se.tetris.core.PieceType;
import kr.ac.jbnu.se.tetris.core.SeededPieceGenerator;

/** 중간 이동을 포함한 계획 전체가 실제 엔진에서 합법인지 확인 */
public final class HeuristicStrategyTest {
    public static void main(String[] args) {
        executablePathsAndBounds();
        holdAndInactiveStates();
        deterministicProfiles();
    }

    private static void executablePathsAndBounds() {
        HeuristicStrategy strategy = new HeuristicStrategy();
        long totalNanos = 0;
        int totalCandidates = 0;
        int decisions = 0;
        for (long seed = 1; seed <= 3; seed++) {
            GameEngine engine = new GameEngine(new SeededPieceGenerator(seed));
            long sequence = 0;
            check(engine.dispatch(new GameAction(GameAction.Type.START, "local", sequence++)).isAccepted(), "Start");
            for (int piece = 0; piece < 8 && engine.getState().getStatus() == GameState.Status.RUNNING; piece++) {
                GameState before = engine.getState();
                if (before.isAwaitingSpawn()) {
                    check(engine.dispatch(new GameAction(GameAction.Type.GRAVITY_TICK, "local", sequence++)).isAccepted(),
                            "Post-clear spawn");
                    before = engine.getState();
                }
                AIPlan plan = strategy.plan(before);
                AIPlan repeated = strategy.plan(before);
                check(plan.getSourceVersion() == before.getVersion(), "Snapshot version");
                if (!plan.isTimedOut() && !repeated.isTimedOut()) {
                    check(plan.getActions().equals(repeated.getActions()), "Deterministic completed path");
                }
                check(plan.getCandidateCount() > 0 && plan.getCandidateCount() <= 1800, "Bounded candidates");
                check(!plan.getActions().isEmpty()
                        && plan.getActions().get(plan.getActions().size() - 1) == GameAction.Type.HARD_DROP,
                        "Plan must lock");
                int holds = 0;
                for (GameAction.Type action : plan.getActions()) {
                    if (action == GameAction.Type.HOLD) holds++;
                    ActionResult result = engine.dispatch(new GameAction(action, "local", sequence++));
                    check(result.isAccepted(), "Engine rejected " + action + ": " + result.getReason());
                }
                check(holds <= 1, "At most one hold");
                decisions++;
                totalNanos += plan.getElapsedNanos();
                totalCandidates += plan.getCandidateCount();
            }
        }
        System.out.println("AI sample: " + decisions + " decisions, " + totalCandidates
                + " candidates, " + (totalNanos / 1_000_000) + " ms cumulative planning");
    }

    private static void holdAndInactiveStates() {
        final PieceType[] sequence = { PieceType.Z, PieceType.I, PieceType.O, PieceType.T, PieceType.L };
        GameEngine engine = new GameEngine(new PieceGenerator() {
            private int index;
            public PieceType nextPiece() { return sequence[(index++) % sequence.length]; }
        });
        HeuristicStrategy strategy = new HeuristicStrategy(new HeuristicWeights(
                0, 0, 0, -100, 0, 0, 0));
        check(strategy.plan(engine.getState()).getActions().isEmpty(), "READY has no plan");
        long n = 0;
        check(engine.dispatch(new GameAction(GameAction.Type.START, "local", n++)).isAccepted(), "Start");
        GameState state = engine.getState();
        check(state.canHold() && state.getNextPieces().get(0) == PieceType.I, "Known hold preview");
        AIPlan plan = strategy.plan(state);
        check(!plan.getActions().isEmpty() && plan.getActions().get(0) == GameAction.Type.HOLD,
                "Flat I placement should win over Z by height");
        check(engine.dispatch(new GameAction(GameAction.Type.HOLD, "local", n++)).isAccepted(), "Hold accepted");
        check(!engine.getState().canHold(), "Repeat hold is prohibited");
        AIPlan afterHold = strategy.plan(engine.getState());
        check(!afterHold.getActions().contains(GameAction.Type.HOLD), "No illegal repeat hold");
        for (int i = 1; i < plan.getActions().size(); i++) {
            ActionResult result = engine.dispatch(new GameAction(plan.getActions().get(i), "local", n++));
            check(result.isAccepted(), "HOLD branch path rejected: " + result.getReason());
        }
        check(engine.dispatch(new GameAction(GameAction.Type.PAUSE, "local", n++)).isAccepted(), "Pause");
        check(strategy.plan(engine.getState()).getActions().isEmpty(), "PAUSED has no plan");
    }

    private static void deterministicProfiles() {
        GameEngine engine = new GameEngine(new SeededPieceGenerator(19));
        GameState board = engine.getState();
        BoardEvaluator safe = new BoardEvaluator(HeuristicWeights.SAFE);
        BoardEvaluator quick = new BoardEvaluator(HeuristicWeights.QUICK);
        BoardEvaluator tetris = new BoardEvaluator(HeuristicWeights.TETRIS);
        double safeFourGain = safe.score(board.getBoard(), 4) - safe.score(board.getBoard(), 0);
        double quickOneGain = quick.score(board.getBoard(), 1) - quick.score(board.getBoard(), 0);
        double safeOneGain = safe.score(board.getBoard(), 1) - safe.score(board.getBoard(), 0);
        double tetrisFourGain = tetris.score(board.getBoard(), 4) - tetris.score(board.getBoard(), 0);
        check(quickOneGain > safeOneGain, "QUICK values immediate lines more");
        check(tetrisFourGain > safeFourGain, "TETRIS values four-line clear more");

        List<GameAction.Type> mutable = new ArrayList<GameAction.Type>();
        mutable.add(GameAction.Type.HARD_DROP);
        AIPlan immutable = new AIPlan(0, mutable, 1, 0, 0, false);
        mutable.clear();
        check(immutable.getActions().size() == 1, "Defensive plan copy");
        boolean blocked = false;
        try { immutable.getActions().clear(); } catch (UnsupportedOperationException expected) { blocked = true; }
        check(blocked, "Immutable action list");

        engine.dispatch(new GameAction(GameAction.Type.START, "local", 0));
        AIPlan limited = new HeuristicStrategy(HeuristicWeights.SAFE, 1, 1_000_000_000L).plan(engine.getState());
        check(limited.isTimedOut() && limited.getCandidateCount() == 1,
                "Very small bounds stop search safely");
        long n = 1;
        for (GameAction.Type action : limited.getActions()) {
            check(engine.dispatch(new GameAction(action, "local", n++)).isAccepted(), "Timed-out path is safe");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
