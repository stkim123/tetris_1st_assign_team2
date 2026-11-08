package kr.ac.jbnu.se.tetris.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import kr.ac.jbnu.se.tetris.battle.BattleEvent;
import kr.ac.jbnu.se.tetris.battle.BattleSnapshots;
import kr.ac.jbnu.se.tetris.battle.BattleState;

/** 요청 결과와 방, 경기 상태를 구분한 네트워크 수신 메시지 */
public final class NetworkUpdate {
    public enum Type {
        CONNECTED, CONNECTION_FAILED, ROOM_STATE, MATCH_STARTED,
        SNAPSHOT, EVENTS, REQUEST_OUTCOME, RANKED_SAVE_STATUS, ERROR, CLOSED
    }

    private final Type type;
    private final RoomState roomState;
    private final String roomId;
    private final long roomVersion;
    private final String matchId;
    private final String localParticipantId;
    private final BattleState battleState;
    private final List<BattleEvent> events;
    private final RequestOutcome requestOutcome;
    private final String reasonCode;

    private NetworkUpdate(Type type, RoomState roomState, String roomId, long roomVersion,
                          String matchId, String localParticipantId, BattleState battleState,
                          List<BattleEvent> events, RequestOutcome requestOutcome, String reasonCode) {
        this.type = type;
        this.roomState = roomState;
        this.roomId = roomId;
        this.roomVersion = roomVersion;
        this.matchId = matchId;
        this.localParticipantId = localParticipantId;
        this.battleState = battleState == null ? null : BattleSnapshots.copyOf(battleState);
        this.events = Collections.unmodifiableList(new ArrayList<BattleEvent>(events));
        this.requestOutcome = requestOutcome;
        this.reasonCode = reasonCode;
    }

    public static NetworkUpdate connected() {
        return of(Type.CONNECTED, null, null, -1, null, null, null, null, null);
    }

    public static NetworkUpdate connectionFailed(String reasonCode) {
        return of(Type.CONNECTION_FAILED, null, null, -1, null, null, null, null,
                required(reasonCode, "Connection failure reason"));
    }

    public static NetworkUpdate roomState(RoomState state) {
        if (state == null) throw new IllegalArgumentException("Room state is required");
        return new NetworkUpdate(Type.ROOM_STATE, state, state.getRoomId(), state.getRoomVersion(),
                null, state.getLocalParticipantId(), null,
                Collections.<BattleEvent>emptyList(), null, null);
    }

    public static NetworkUpdate matchStarted(String roomId, long roomVersion,
                                             String matchId, String localParticipantId) {
        if (roomVersion < 0) throw new IllegalArgumentException("Room version is required");
        return of(Type.MATCH_STARTED, null, required(roomId, "Room ID"), roomVersion,
                required(matchId, "Match ID"), required(localParticipantId, "Local participant ID"),
                null, null, null);
    }

    public static NetworkUpdate snapshot(String roomId, String matchId, BattleState state) {
        if (state == null) throw new IllegalArgumentException("Battle state is required");
        return of(Type.SNAPSHOT, null, required(roomId, "Room ID"), -1,
                required(matchId, "Match ID"), null, state, null, null);
    }

    public static NetworkUpdate events(String roomId, String matchId, List<BattleEvent> events) {
        if (events == null) throw new IllegalArgumentException("Battle events are required");
        for (BattleEvent event : events) if (event == null) throw new IllegalArgumentException("Null battle event");
        return new NetworkUpdate(Type.EVENTS, null, required(roomId, "Room ID"), -1,
                required(matchId, "Match ID"), null, null, events, null, null);
    }

    public static NetworkUpdate requestOutcome(RequestOutcome outcome) {
        if (outcome == null) throw new IllegalArgumentException("Request outcome is required");
        return new NetworkUpdate(Type.REQUEST_OUTCOME, null, outcome.getRoomId(), -1,
                outcome.getMatchId(), null, null, Collections.<BattleEvent>emptyList(), outcome, null);
    }

    public static NetworkUpdate error(String reasonCode) {
        return of(Type.ERROR, null, null, -1, null, null, null, null,
                required(reasonCode, "Error reason"));
    }

    /** Ranked result persistence state: SAVE_PENDING, SAVED, SAVE_FAILED or VOIDED. */
    public static NetworkUpdate rankedSaveStatus(String roomId, String matchId, String statusCode) {
        return of(Type.RANKED_SAVE_STATUS, null, required(roomId, "Room ID"), -1,
                required(matchId, "Match ID"), null, null, null,
                required(statusCode, "Ranked save status"));
    }

    public static NetworkUpdate closed(String reasonCode) {
        return of(Type.CLOSED, null, null, -1, null, null, null, null,
                required(reasonCode, "Close reason"));
    }

    private static NetworkUpdate of(Type type, RoomState state, String roomId, long roomVersion,
                                    String matchId, String localId, BattleState battle,
                                    RequestOutcome outcome, String reason) {
        return new NetworkUpdate(type, state, roomId, roomVersion, matchId, localId, battle,
                Collections.<BattleEvent>emptyList(), outcome, reason);
    }

    private static String required(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    public Type getType() { return type; }
    public RoomState getRoomState() { return roomState; }
    public String getRoomId() { return roomId; }
    public long getRoomVersion() { return roomVersion; }
    public String getMatchId() { return matchId; }
    public String getLocalParticipantId() { return localParticipantId; }
    public BattleState getBattleState() { return battleState; }
    public List<BattleEvent> getEvents() { return events; }
    public RequestOutcome getRequestOutcome() { return requestOutcome; }
    public String getReasonCode() { return reasonCode; }
}
