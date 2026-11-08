package kr.ac.jbnu.se.tetris.support;

import java.util.LinkedHashMap;
import java.util.Map;
import kr.ac.jbnu.se.tetris.app.session.OnlineMatchSession;
import kr.ac.jbnu.se.tetris.app.session.SessionListener;
import kr.ac.jbnu.se.tetris.app.session.SessionUpdate;
import kr.ac.jbnu.se.tetris.network.NetworkUpdate;
import kr.ac.jbnu.se.tetris.network.RoomState;

/** UI 담당자의 서버 없는 온라인 화면 재생 예제 */
public final class OnlinePreviewScenario {
    private final FakeNetworkClient fake;
    private final OnlineMatchSession session;

    private OnlinePreviewScenario(FakeNetworkClient fake, OnlineMatchSession session) {
        this.fake = fake;
        this.session = session;
    }

    public static OnlinePreviewScenario running() {
        FakeNetworkClient fake = new FakeNetworkClient();
        OnlineMatchSession session = new OnlineMatchSession("preview-session", fake);
        Map<String, Boolean> ready = new LinkedHashMap<String, Boolean>();
        ready.put("student-a", true);
        ready.put("student-b", true);
        fake.emit(NetworkUpdate.connected());
        fake.emit(NetworkUpdate.roomState(new RoomState("preview-room", 1,
                RoomState.Phase.IN_MATCH, "student-a", ready)));
        fake.emit(NetworkUpdate.matchStarted("preview-room", 2, "preview-match", "student-a"));
        fake.emit(NetworkUpdate.snapshot("preview-room", "preview-match",
                SampleSnapshots.running("student-a", "student-b")));
        fake.drain();
        return new OnlinePreviewScenario(fake, session);
    }

    public FakeNetworkClient getFake() { return fake; }
    public OnlineMatchSession getSession() { return session; }

    public static void main(String[] args) {
        OnlinePreviewScenario preview = running();
        preview.getSession().subscribe(new SessionListener() {
            @Override public void onUpdate(SessionUpdate update) {
                System.out.println(update.getSnapshot().getPhase() + " "
                        + update.getSnapshot().getMatchId());
            }
        });
        preview.getFake().emit(NetworkUpdate.snapshot("preview-room", "preview-match",
                SampleSnapshots.finished("student-a", "student-b", "student-a")));
        preview.getFake().drain();
        preview.getSession().close();
    }
}
