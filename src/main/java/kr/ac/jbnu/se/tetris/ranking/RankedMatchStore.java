package kr.ac.jbnu.se.tetris.ranking;

import java.io.IOException;

/** Blocking persistence operations; schedule off the serialized game loop. */
public interface RankedMatchStore {
    /** Claims the singleton lease, fencing and reconciling an expired owner in one DB transaction. */
    RunLease claimRun(String serverRunId) throws IOException;
    /** Extends only a still-valid lease owned by this run. */
    RunLease renewRun(String serverRunId) throws IOException;
    /** Persists operator intent; a drained service stays drained across restarts. */
    RunLease setAdmission(String serverRunId, boolean enabled) throws IOException;
    MatchRecord beginMatch(String matchId, String serverRunId, String rulesVersion,
            String firstUserId, String secondUserId) throws IOException;
    MatchRecord finishMatch(String matchId, String serverRunId, String winnerUserId,
            String reason) throws IOException;
    MatchRecord voidMatch(String matchId, String serverRunId, String reason) throws IOException;
    MatchRecord getMatch(String matchId) throws IOException;
    /** Reconciles another run only while the caller owns the current lease. */
    int voidStoppedRun(String currentRunId, String stoppedRunId) throws IOException;
}
