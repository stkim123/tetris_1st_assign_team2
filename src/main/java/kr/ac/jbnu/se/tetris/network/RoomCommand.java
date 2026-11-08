package kr.ac.jbnu.se.tetris.network;

/** 방 작업별로 필요한 값만 보유하는 불변 요청 */
public final class RoomCommand {
    public enum Type { CREATE_ROOM, JOIN_ROOM, SET_READY, LEAVE_ROOM, REQUEST_REMATCH, GET_ROOM_STATE }

    private final Type type;
    private final String roomId;
    private final Boolean ready;
    private final Integer maxParticipants;

    private RoomCommand(Type type, String roomId, Boolean ready, Integer maxParticipants) {
        this.type = type;
        this.roomId = roomId;
        this.ready = ready;
        this.maxParticipants = maxParticipants;
    }

    public static RoomCommand createRoom(int maxParticipants) {
        if (maxParticipants < 2 || maxParticipants > 4) {
            throw new IllegalArgumentException("Room needs 2 to 4 participant slots");
        }
        return new RoomCommand(Type.CREATE_ROOM, null, null, maxParticipants);
    }

    public static RoomCommand joinRoom(String roomId) {
        if (roomId == null || roomId.trim().isEmpty()) throw new IllegalArgumentException("Room ID is required");
        return new RoomCommand(Type.JOIN_ROOM, roomId, null, null);
    }

    public static RoomCommand setReady(boolean ready) {
        return new RoomCommand(Type.SET_READY, null, ready, null);
    }

    public static RoomCommand leaveRoom() {
        return new RoomCommand(Type.LEAVE_ROOM, null, null, null);
    }

    public static RoomCommand requestRematch() {
        return new RoomCommand(Type.REQUEST_REMATCH, null, null, null);
    }

    public static RoomCommand getRoomState() {
        return new RoomCommand(Type.GET_ROOM_STATE, null, null, null);
    }

    public Type getType() { return type; }
    public String getRoomId() { return roomId; }
    public Boolean getReady() { return ready; }
    public Integer getMaxParticipants() { return maxParticipants; }
}
