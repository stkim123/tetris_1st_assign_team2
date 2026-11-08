package kr.ac.jbnu.se.tetris.app.session;

/** 로컬 요청 ID와 게임 규칙의 최종 수락 여부 */
public final class CommandOutcome {
    private final long requestId;
    private final boolean accepted;
    private final String reasonCode;
    private final String matchId;
    private final Long battleVersion;

    public CommandOutcome(long requestId, boolean accepted, String reasonCode,
                          String matchId, Long battleVersion) {
        if (requestId <= 0 || (accepted && reasonCode != null)
                || (!accepted && (reasonCode == null || reasonCode.trim().isEmpty()))
                || (battleVersion != null && battleVersion.longValue() < 0)) {
            throw new IllegalArgumentException("Invalid command outcome");
        }
        this.requestId = requestId;
        this.accepted = accepted;
        this.reasonCode = reasonCode;
        this.matchId = matchId;
        this.battleVersion = battleVersion;
    }

    public long getRequestId() { return requestId; }
    public boolean isAccepted() { return accepted; }
    public String getReasonCode() { return reasonCode; }
    public String getMatchId() { return matchId; }
    public Long getBattleVersion() { return battleVersion; }
}
