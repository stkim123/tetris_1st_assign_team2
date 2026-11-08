package kr.ac.jbnu.se.tetris.network;

/** 송신 요청과 서버의 확정 판정을 연결하는 결과 */
public final class RequestOutcome {
    private final long requestId;
    private final boolean accepted;
    private final String reasonCode;
    private final String roomId;
    private final String matchId;
    private final Long battleVersion;

    public RequestOutcome(long requestId, boolean accepted, String reasonCode,
                          String roomId, String matchId, Long battleVersion) {
        if (requestId <= 0 || (accepted && reasonCode != null)
                || (!accepted && (reasonCode == null || reasonCode.trim().isEmpty()))
                || (battleVersion != null && battleVersion.longValue() < 0)) {
            throw new IllegalArgumentException("Invalid network request outcome");
        }
        this.requestId = requestId;
        this.accepted = accepted;
        this.reasonCode = reasonCode;
        this.roomId = roomId;
        this.matchId = matchId;
        this.battleVersion = battleVersion;
    }

    public long getRequestId() { return requestId; }
    public boolean isAccepted() { return accepted; }
    public String getReasonCode() { return reasonCode; }
    public String getRoomId() { return roomId; }
    public String getMatchId() { return matchId; }
    public Long getBattleVersion() { return battleVersion; }
}
