package kr.ac.jbnu.se.tetris.ui;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import kr.ac.jbnu.se.tetris.app.session.MatchSession;
import kr.ac.jbnu.se.tetris.app.session.SessionSnapshot;
import kr.ac.jbnu.se.tetris.app.session.SessionPhase;
import kr.ac.jbnu.se.tetris.battle.BattleEvent;
import kr.ac.jbnu.se.tetris.app.session.SessionUpdate;
import kr.ac.jbnu.se.tetris.app.session.Subscription;

/** 세션 콜백의 EDT 전달 및 구독 해제 이후 예약 콜백 폐기 */
public final class SessionUiBinding {
    private SessionUiBinding() { }
    public static Subscription bind(MatchSession session, Consumer<SessionUpdate> consumer) {
        ScreenRouter.requireEdt();
        AtomicBoolean active = new AtomicBoolean(true);
        Subscription source = session.subscribe(update -> {
            Runnable delivery = () -> {
                if (!active.get()) return;
                // EDT 대기 중 종료·경기 전환 이후 이전 화면으로 복귀하는 상황 방지
                SessionSnapshot current = session.getSnapshot();
                List<BattleEvent> events = update.getEvents();
                if (!Objects.equals(current.getMatchId(), update.getSnapshot().getMatchId())
                        || current.getPhase() == SessionPhase.CLOSED
                        || current.getPhase() == SessionPhase.FAILED) {
                    events = Collections.emptyList();
                }
                consumer.accept(new SessionUpdate(current, events, update.getOutcome()));
            };
            if (SwingUtilities.isEventDispatchThread() && update.getOutcome() == null) delivery.run();
            else SwingUtilities.invokeLater(delivery);
        });
        return () -> { active.set(false); source.close(); };
    }
}
