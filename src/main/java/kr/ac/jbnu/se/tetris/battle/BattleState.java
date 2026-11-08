package kr.ac.jbnu.se.tetris.battle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 생성 시 전달한 참가자 순서를 유지하는 불변 대전 스냅샷 */
public final class BattleState {
    public enum Status { READY, RUNNING, PAUSED, FINISHED }

    private final Status status;
    private final long version;
    private final Map<String, ParticipantState> participants;
    private final String winnerId;
    private final String reason;
    private final long elapsedMillis;

    BattleState(Status status, long version, Map<String, ParticipantState> participants,
                String winnerId, String reason) {
        this(status, version, participants, winnerId, reason, 0);
    }

    BattleState(Status status, long version, Map<String, ParticipantState> participants,
                String winnerId, String reason, long elapsedMillis) {
        this.status = status;
        this.version = version;
        this.participants = Collections.unmodifiableMap(
                new LinkedHashMap<String, ParticipantState>(participants));
        this.winnerId = winnerId;
        this.reason = reason;
        this.elapsedMillis = elapsedMillis;
    }

    public Status getStatus() { return status; }
    public long getVersion() { return version; }
    public Map<String, ParticipantState> getParticipants() { return participants; }
    public ParticipantState getParticipant(String id) { return participants.get(id); }
    public String getWinnerId() { return winnerId; }
    public String getReason() { return reason; }
    public long getElapsedMillis() { return elapsedMillis; }
}
