package kr.ac.jbnu.se.tetris.app.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import kr.ac.jbnu.se.tetris.battle.BattleEvent;

/** 화면 갱신 시점의 상태와 확정 이벤트 및 요청 결과 */
public final class SessionUpdate {
    private final SessionSnapshot snapshot;
    private final List<BattleEvent> events;
    private final CommandOutcome outcome;

    public SessionUpdate(SessionSnapshot snapshot, List<BattleEvent> events, CommandOutcome outcome) {
        if (snapshot == null || events == null) throw new IllegalArgumentException("Session update is required");
        for (BattleEvent event : events) if (event == null) throw new IllegalArgumentException("Null battle event");
        this.snapshot = snapshot;
        this.events = Collections.unmodifiableList(new ArrayList<BattleEvent>(events));
        this.outcome = outcome;
    }

    public SessionSnapshot getSnapshot() { return snapshot; }
    public List<BattleEvent> getEvents() { return events; }
    public CommandOutcome getOutcome() { return outcome; }
}
