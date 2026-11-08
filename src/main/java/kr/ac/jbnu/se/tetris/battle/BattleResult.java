package kr.ac.jbnu.se.tetris.battle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 전투 관리자 명령 한 건의 원자적 결과와 확정 스냅샷 */
public final class BattleResult {
    private final boolean accepted;
    private final String reason;
    private final BattleState state;
    private final List<BattleEvent> events;

    BattleResult(boolean accepted, String reason, BattleState state, List<BattleEvent> events) {
        this.accepted = accepted;
        this.reason = reason;
        this.state = state;
        this.events = Collections.unmodifiableList(new ArrayList<BattleEvent>(events));
    }

    public boolean isAccepted() { return accepted; }
    public String getReason() { return reason; }
    public BattleState getState() { return state; }
    public List<BattleEvent> getEvents() { return events; }
}
