package kr.ac.jbnu.se.tetris.ai;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.ac.jbnu.se.tetris.controller.AIController;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameEngine;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.PieceType;
import kr.ac.jbnu.se.tetris.core.PieceGenerator;

/** 비동기 AI 계획의 중복 요청, 취소, 낡은 상태 폐기를 확인 */
public final class AIControllerTest {
    public static void main(String[] args) throws Exception {
        noBlockingAndStaleDiscard();
        staleFailureSurfaces();
        cancellationAndErrors();
    }

    private static void staleFailureSurfaces() throws Exception {
        GameEngine engine = engine();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        AIController controller = new AIController(new AIStrategy() {
            public AIPlan plan(GameState state) {
                entered.countDown();
                awaitIgnoringInterrupt(release);
                throw new IllegalArgumentException("stale strategy failed");
            }
        });
        try {
            check(controller.request(engine.getState()), "Stale failure request accepted");
            check(entered.await(2, TimeUnit.SECONDS), "Failing worker entered");
            check(engine.dispatch(new GameAction(GameAction.Type.MOVE_LEFT, "local", 1)).isAccepted(),
                    "Version advanced before failure");
            release.countDown();
            waitFinished(controller);
            boolean surfaced = false;
            try { controller.poll(engine.getState()); }
            catch (IllegalStateException expected) {
                surfaced = expected.getCause() instanceof IllegalArgumentException
                        && "stale strategy failed".equals(expected.getCause().getMessage());
            }
            check(surfaced, "Stale worker failure is not hidden");
        } finally { release.countDown(); controller.close(); }
    }

    private static void noBlockingAndStaleDiscard() throws Exception {
        GameEngine engine = engine();
        GameState state = engine.getState();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicBoolean daemon = new AtomicBoolean();
        final Thread caller = Thread.currentThread();
        AIController controller = new AIController(new AIStrategy() {
            public AIPlan plan(GameState snapshot) {
                daemon.set(Thread.currentThread().isDaemon() && Thread.currentThread() != caller);
                entered.countDown();
                awaitIgnoringInterrupt(release);
                return hardDrop(snapshot);
            }
        });
        try {
            long before = System.nanoTime();
            check(controller.request(state), "Request accepted");
            check(System.nanoTime() - before < TimeUnit.MILLISECONDS.toNanos(250), "Request is nonblocking");
            check(entered.await(2, TimeUnit.SECONDS), "Worker started");
            check(daemon.get(), "Planning uses a daemon worker");
            check(controller.isThinking() && controller.poll(state) == null, "Pending result");
            check(!controller.request(state), "No duplicate pending work");
            check(engine.dispatch(new GameAction(GameAction.Type.MOVE_LEFT, "local", 1)).isAccepted(), "Move");
            release.countDown();
            waitFinished(controller);
            check(controller.poll(engine.getState()) == null, "Old version discarded");
            check(controller.request(engine.getState()), "New version can be requested");
            waitFinished(controller);
            AIPlan delivered = controller.poll(engine.getState());
            check(delivered != null && delivered.getSourceVersion() == engine.getState().getVersion(),
                    "Current version delivered");
            check(!controller.request(engine.getState()), "Same delivered version cannot loop");
        } finally { release.countDown(); controller.close(); }
    }

    private static void cancellationAndErrors() throws Exception {
        GameEngine engine = engine();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final int[] calls = { 0 };
        AIController controller = new AIController(new AIStrategy() {
            public AIPlan plan(GameState state) {
                synchronized (calls) { calls[0]++; }
                if (state.getVersion() == 1) {
                    entered.countDown();
                    awaitIgnoringInterrupt(release);
                }
                return hardDrop(state);
            }
        });
        try {
            check(controller.request(engine.getState()), "Initial request");
            check(entered.await(2, TimeUnit.SECONDS), "First worker entered");
            controller.cancelPending();
            check(!controller.isThinking(), "Cancel clears pending marker");
            check(engine.dispatch(new GameAction(GameAction.Type.MOVE_LEFT, "local", 1)).isAccepted(), "Move");
            check(controller.request(engine.getState()), "New work queued behind cancellation");
            release.countDown();
            waitFinished(controller);
            AIPlan plan = controller.poll(engine.getState());
            check(plan != null && plan.getSourceVersion() == engine.getState().getVersion(),
                    "Late canceled completion did not replace new plan");
        } finally { release.countDown(); controller.close(); }

        AIController failing = new AIController(new AIStrategy() {
            public AIPlan plan(GameState state) { throw new IllegalArgumentException("broken strategy"); }
        });
        try {
            check(failing.request(engine.getState()), "Failure request accepted");
            waitFinished(failing);
            boolean surfaced = false;
            try { failing.poll(engine.getState()); }
            catch (IllegalStateException expected) {
                surfaced = expected.getCause() instanceof IllegalArgumentException;
            }
            check(surfaced, "Worker exception propagated");
        } finally { failing.close(); }
    }

    private static GameEngine engine() {
        GameEngine engine = new GameEngine(new PieceGenerator() {
            public PieceType nextPiece() { return PieceType.O; }
        });
        check(engine.dispatch(new GameAction(GameAction.Type.START, "local", 0)).isAccepted(), "Start");
        return engine;
    }

    private static AIPlan hardDrop(GameState state) {
        return new AIPlan(state.getVersion(), Collections.singletonList(GameAction.Type.HARD_DROP),
                1, 0, 0, false);
    }

    private static void waitFinished(AIController controller) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (controller.isThinking() && System.nanoTime() < end) Thread.sleep(1);
        check(!controller.isThinking(), "Worker completed");
    }

    private static void awaitIgnoringInterrupt(CountDownLatch latch) {
        while (latch.getCount() > 0) {
            try { latch.await(); }
            catch (InterruptedException ignored) { /* 중단 신호를 무시하는 이전 전략을 재현 */ }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
