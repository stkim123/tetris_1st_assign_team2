package kr.ac.jbnu.se.tetris.ui.seongeun.model;

public class RoomData {
    private final String roomName;
    private final int currentPlayers;
    private final int maxPlayers;
    private final String status;

    public RoomData(String roomName, int currentPlayers, int maxPlayers, String status) {
        this.roomName = roomName;
        this.currentPlayers = currentPlayers;
        this.maxPlayers = maxPlayers;
        this.status = status;
    }

    public String getRoomName() {
        return roomName;
    }

    public int getCurrentPlayers() {
        return currentPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public String getStatus() {
        return status;
    }

    public boolean isJoinable() {
        return currentPlayers < maxPlayers && status.equals("Waiting");
    }
}
