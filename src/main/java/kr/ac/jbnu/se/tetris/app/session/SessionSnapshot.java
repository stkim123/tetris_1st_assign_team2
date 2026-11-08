package kr.ac.jbnu.se.tetris.app.session;

import kr.ac.jbnu.se.tetris.battle.BattleSnapshots;
import kr.ac.jbnu.se.tetris.battle.BattleState;

/** 대전 모드와 참가자 표시 상태의 불변 사본 */
public final class SessionSnapshot {
    private final String sessionId;
    private final SessionMode mode;
    private final SessionPhase phase;
    private final String localParticipantId;
    private final String matchId;
    private final BattleState battleState;
    private final SessionCapabilities capabilities;

    public SessionSnapshot(String sessionId, SessionMode mode, SessionPhase phase,
                           String localParticipantId, String matchId, BattleState battleState,
                           SessionCapabilities capabilities) {
        if (blank(sessionId) || mode == null || phase == null || capabilities == null
                || (matchId != null && blank(matchId))
                || (localParticipantId != null && blank(localParticipantId))) {
            throw new IllegalArgumentException("Invalid session snapshot");
        }
        if (phase == SessionPhase.RUNNING || phase == SessionPhase.PAUSED
                || phase == SessionPhase.FINISHED) {
            if (battleState == null || blank(matchId) || blank(localParticipantId)
                    || battleState.getParticipant(localParticipantId) == null) {
                throw new IllegalArgumentException("Active session needs a local battle participant");
            }
            BattleState.Status expected = phase == SessionPhase.RUNNING ? BattleState.Status.RUNNING
                    : phase == SessionPhase.PAUSED ? BattleState.Status.PAUSED : BattleState.Status.FINISHED;
            if (battleState.getStatus() != expected) {
                throw new IllegalArgumentException("Session phase and battle status disagree");
            }
        } else if (battleState != null) {
            throw new IllegalArgumentException("Inactive session cannot carry a battle state");
        }
        this.sessionId = sessionId;
        this.mode = mode;
        this.phase = phase;
        this.localParticipantId = localParticipantId;
        this.matchId = matchId;
        this.battleState = battleState == null ? null : BattleSnapshots.copyOf(battleState);
        this.capabilities = capabilities;
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    public String getSessionId() { return sessionId; }
    public SessionMode getMode() { return mode; }
    public SessionPhase getPhase() { return phase; }
    public String getLocalParticipantId() { return localParticipantId; }
    public String getMatchId() { return matchId; }
    public BattleState getBattleState() { return battleState; }
    public SessionCapabilities getCapabilities() { return capabilities; }
}
