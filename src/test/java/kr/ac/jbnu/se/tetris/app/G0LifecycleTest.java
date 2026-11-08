package kr.ac.jbnu.se.tetris.app;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingUtilities;
import kr.ac.jbnu.se.tetris.ai.AIPlan;
import kr.ac.jbnu.se.tetris.ai.HeuristicStrategy;
import kr.ac.jbnu.se.tetris.ai.PlacementLog;
import kr.ac.jbnu.se.tetris.ai.PlayerProfile;
import kr.ac.jbnu.se.tetris.app.session.*;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.PlayerIntent;
import kr.ac.jbnu.se.tetris.ui.SessionUiBinding;
import kr.ac.jbnu.se.tetris.battle.BattleSnapshots;
import kr.ac.jbnu.se.tetris.battle.BattleState;
import kr.ac.jbnu.se.tetris.network.NetworkUpdate;
import kr.ac.jbnu.se.tetris.support.OnlinePreviewScenario;

/** 가짜 시계의 재개 대기 시간 및 요청 반환·콜백·종료 순서 검증 */
public final class G0LifecycleTest {
    public static void main(String[] args) throws Exception {
        preservesRemainingDelay();
        callbackOrderAndClose();
        queuedStateCannotReopenClosedView();
    }
    private static void preservesRemainingDelay() throws Exception {
        AtomicLong now = new AtomicLong(1_000_000_000L);
        MonsterSession session = new MonsterSession(1, "테스트 몬스터", 100, 1700,
                new HeuristicStrategy(), new PlayerProfile(), new PlacementLog(), "clock-match",
                "student-x", "opponent-y", now::get);
        try {
            long deadline = System.nanoTime() + 5_000_000_000L;
            do { session.pulse(now.get()); Thread.sleep(1); }
            while (session.getLastPlan() == null && System.nanoTime() < deadline);
            check(session.getLastPlan() != null, "AI initial plan completed");
            AIPlan first = session.getLastPlan();
            now.addAndGet(300_000_000L); session.pause();
            check(session.getRemainingDecisionDelayNanos() == 1_400_000_000L, "remaining delay captured");
            now.addAndGet(60_000_000_000L); session.resume();
            check(session.getRemainingDecisionDelayNanos() == 1_400_000_000L, "pause time excluded");
            now.addAndGet(1_399_999_999L); session.pulse(now.get());
            check(!session.isThinking() && session.getLastPlan() == first, "no early AI request after resume");
            now.incrementAndGet(); session.pulse(now.get());
            check(session.isThinking(), "AI request after remaining delay");
        } finally { session.close(); }
    }
    private static void callbackOrderAndClose() throws Exception {
        LocalMatchSession[] session = new LocalMatchSession[1];
        Subscription[] binding = new Subscription[1];
        List<CommandOutcome> outcomes = new ArrayList<CommandOutcome>();
        long[] submitted = new long[1];
        SwingUtilities.invokeAndWait(() -> {
            session[0] = new LocalMatchSession(new MonsterSession(37));
            binding[0] = SessionUiBinding.bind(session[0], update -> {
                if (update.getOutcome() != null) {
                    check(submitted[0] == update.getOutcome().getRequestId(), "request ID available before result");
                    check(SwingUtilities.isEventDispatchThread(), "callback on EDT");
                    outcomes.add(update.getOutcome());
                }
            });
            submitted[0] = session[0].submit(new PlayerIntent(GameAction.Type.MOVE_LEFT));
            check(outcomes.isEmpty(), "outcome deferred until request returns");
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            check(outcomes.size() == 1 && outcomes.get(0).isAccepted(), "exactly one result");
            session[0].submit(new PlayerIntent(GameAction.Type.MOVE_RIGHT));
            binding[0].close(); session[0].close(); session[0].close();
            check(session[0].getSnapshot().getPhase() == SessionPhase.CLOSED, "closed snapshot");
            check(!session[0].isGravityRunning() && !session[0].isAiTimerRunning(), "no timers after close");
        });
        SwingUtilities.invokeAndWait(() -> { });
        check(outcomes.size() == 1, "queued callback suppressed after unsubscribe");
    }
    private static void queuedStateCannotReopenClosedView() throws Exception {
        List<SessionPhase> phases = new ArrayList<SessionPhase>();
        Subscription[] binding = new Subscription[1];
        SwingUtilities.invokeAndWait(() -> {
            OnlinePreviewScenario preview = OnlinePreviewScenario.running();
            OnlineMatchSession session = preview.getSession();
            binding[0] = SessionUiBinding.bind(session,
                    update -> phases.add(update.getSnapshot().getPhase()));
            BattleState state = session.getSnapshot().getBattleState();
            BattleState newer = BattleSnapshots.battle(state.getStatus(), state.getVersion() + 1,
                    state.getParticipants(), state.getWinnerId(), state.getReason());
            Thread receiver = new Thread(() -> {
                preview.getFake().emit(NetworkUpdate.snapshot("preview-room", "preview-match", newer));
                preview.getFake().drain();
            });
            receiver.start();
            try { receiver.join(3000); }
            catch (InterruptedException error) { throw new AssertionError(error); }
            check(!receiver.isAlive(), "receiver completed while EDT delivery queued");
            session.close();
            check(phases.get(phases.size() - 1) == SessionPhase.CLOSED, "immediate closed view");
        });
        SwingUtilities.invokeAndWait(() -> {
            boolean closed = false;
            for (SessionPhase phase : phases) {
                if (closed) check(phase == SessionPhase.CLOSED, "queued state cannot reopen closed view");
                if (phase == SessionPhase.CLOSED) closed = true;
            }
            check(closed, "closed event delivered");
            binding[0].close();
        });
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
