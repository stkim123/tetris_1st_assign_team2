package kr.ac.jbnu.se.tetris.network;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 방 버전과 참가자 준비 상태의 불변 사본 */
public final class RoomState {
    public enum Phase { WAITING, IN_MATCH, CLOSED }

    private final String roomId;
    private final long roomVersion;
    private final Phase phase;
    private final String localParticipantId;
    private final Map<String, Boolean> readyByParticipantId;

    public RoomState(String roomId, long roomVersion, Phase phase,
                     String localParticipantId, Map<String, Boolean> readyByParticipantId) {
        if (blank(roomId) || roomVersion < 0 || phase == null || blank(localParticipantId)
                || readyByParticipantId == null || readyByParticipantId.isEmpty()
                || readyByParticipantId.size() > 4
                || !readyByParticipantId.containsKey(localParticipantId)) {
            throw new IllegalArgumentException("Invalid room state");
        }
        for (Map.Entry<String, Boolean> entry : readyByParticipantId.entrySet()) {
            if (blank(entry.getKey()) || entry.getValue() == null) {
                throw new IllegalArgumentException("Invalid room participant");
            }
        }
        this.roomId = roomId;
        this.roomVersion = roomVersion;
        this.phase = phase;
        this.localParticipantId = localParticipantId;
        this.readyByParticipantId = Collections.unmodifiableMap(
                new LinkedHashMap<String, Boolean>(readyByParticipantId));
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    public String getRoomId() { return roomId; }
    public long getRoomVersion() { return roomVersion; }
    public Phase getPhase() { return phase; }
    public String getLocalParticipantId() { return localParticipantId; }
    public Map<String, Boolean> getReadyByParticipantId() { return readyByParticipantId; }
}
