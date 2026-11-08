package kr.ac.jbnu.se.tetris.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 하나의 원자적 명령 처리로 확정된 상태와 발생 순서대로 정렬된 이벤트를 포함 */
public final class ActionResult {
    private final boolean accepted;
    private final String reason;
    private final GameState state;
    private final List<GameEvent> events;

    ActionResult(boolean accepted, String reason, GameState state, List<GameEvent> events) {
        this.accepted = accepted;
        this.reason = reason;
        this.state = state;
        this.events = Collections.unmodifiableList(new ArrayList<GameEvent>(events));
    }

    public boolean isAccepted() { return accepted; }
    public String getReason() { return reason; }
    public GameState getState() { return state; }
    public List<GameEvent> getEvents() { return events; }
}
