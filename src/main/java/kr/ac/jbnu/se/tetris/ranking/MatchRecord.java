package kr.ac.jbnu.se.tetris.ranking;

public final class MatchRecord {
    public enum Status { RUNNING, FINALIZED, VOID }

    private final String matchId;
    private final String serverRunId;
    private final String firstUserId;
    private final String secondUserId;
    private final Status status;
    private final String winnerUserId;
    private final String reason;
    private final Integer firstRatingBefore;
    private final Integer firstRatingAfter;
    private final Integer secondRatingBefore;
    private final Integer secondRatingAfter;

    public MatchRecord(String matchId, String serverRunId, String firstUserId, String secondUserId,
            Status status, String winnerUserId, String reason, Integer firstRatingBefore,
            Integer firstRatingAfter, Integer secondRatingBefore, Integer secondRatingAfter) {
        this.matchId = matchId;
        this.serverRunId = serverRunId;
        this.firstUserId = firstUserId;
        this.secondUserId = secondUserId;
        this.status = status;
        this.winnerUserId = winnerUserId;
        this.reason = reason;
        this.firstRatingBefore = firstRatingBefore;
        this.firstRatingAfter = firstRatingAfter;
        this.secondRatingBefore = secondRatingBefore;
        this.secondRatingAfter = secondRatingAfter;
    }

    public String getMatchId() { return matchId; }
    public String getServerRunId() { return serverRunId; }
    public String getFirstUserId() { return firstUserId; }
    public String getSecondUserId() { return secondUserId; }
    public Status getStatus() { return status; }
    public String getWinnerUserId() { return winnerUserId; }
    public String getReason() { return reason; }
    public Integer getFirstRatingBefore() { return firstRatingBefore; }
    public Integer getFirstRatingAfter() { return firstRatingAfter; }
    public Integer getSecondRatingBefore() { return secondRatingBefore; }
    public Integer getSecondRatingAfter() { return secondRatingAfter; }
}
