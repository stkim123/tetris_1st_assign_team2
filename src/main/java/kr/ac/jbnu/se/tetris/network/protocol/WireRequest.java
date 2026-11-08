package kr.ac.jbnu.se.tetris.network.protocol;

import kr.ac.jbnu.se.tetris.core.PlayerIntent;
import kr.ac.jbnu.se.tetris.network.RoomCommand;

/** 요청 ID와 방, 입력, 상태 재요청의 불변 통신 값 */
public final class WireRequest {
    public enum Kind { ROOM, INTENT, SNAPSHOT, AUTH_REFRESH }

    private final long requestId;
    private final Kind kind;
    private final RoomCommand roomCommand;
    private final PlayerIntent intent;
    private final String matchId;
    private final String accessToken;

    private WireRequest(long requestId, Kind kind, RoomCommand roomCommand,
                        PlayerIntent intent, String matchId, String accessToken) {
        if (requestId <= 0 || kind == null) throw new IllegalArgumentException("Positive request ID is required");
        this.requestId = requestId;
        this.kind = kind;
        this.roomCommand = roomCommand;
        this.intent = intent;
        this.matchId = matchId;
        this.accessToken = accessToken;
    }

    public static WireRequest room(long requestId, RoomCommand command) {
        if (command == null) throw new IllegalArgumentException("Room command is required");
        return new WireRequest(requestId, Kind.ROOM, command, null, null, null);
    }

    public static WireRequest intent(long requestId, String matchId, PlayerIntent intent) {
        if (blank(matchId) || intent == null) {
            throw new IllegalArgumentException("Match ID and player intent are required");
        }
        return new WireRequest(requestId, Kind.INTENT, null, intent, matchId, null);
    }

    public static WireRequest snapshot(long requestId, String matchId) {
        if (blank(matchId)) throw new IllegalArgumentException("Match ID is required");
        return new WireRequest(requestId, Kind.SNAPSHOT, null, null, matchId, null);
    }

    public static WireRequest authRefresh(long requestId, String accessToken) {
        if (blank(accessToken) || accessToken.length() > 8192) {
            throw new IllegalArgumentException("Access token required");
        }
        return new WireRequest(requestId, Kind.AUTH_REFRESH, null, null, null, accessToken);
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    public long getRequestId() { return requestId; }
    public Kind getKind() { return kind; }
    public RoomCommand getRoomCommand() { return roomCommand; }
    public PlayerIntent getIntent() { return intent; }
    public String getMatchId() { return matchId; }
    public String getAccessToken() { return accessToken; }
}
