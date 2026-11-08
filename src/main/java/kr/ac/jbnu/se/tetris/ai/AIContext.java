package kr.ac.jbnu.se.tetris.ai;

import kr.ac.jbnu.se.tetris.core.GameState;

/** AI 요청 시점의 불변 보드·관측·정책 상태 사본 */
public final class AIContext {
    private final String matchId;
    private final GameState gameState;
    private final int selfHp;
    private final int selfMaxHp;
    private final PlayerProfile.Snapshot observation;
    private final long decisionId;
    private final HeuristicWeights previousWeights;
    private final String policyState;

    public AIContext(String matchId, GameState gameState, int selfHp,
                     PlayerProfile.Snapshot observation, long decisionId,
                     HeuristicWeights previousWeights, String policyState) {
        this(matchId, gameState, selfHp, Math.max(100, selfHp), observation,
                decisionId, previousWeights, policyState);
    }

    public AIContext(String matchId, GameState gameState, int selfHp, int selfMaxHp,
                     PlayerProfile.Snapshot observation, long decisionId,
                     HeuristicWeights previousWeights, String policyState) {
        if (matchId == null || matchId.trim().isEmpty() || gameState == null
                || selfHp < 0 || selfMaxHp < 1 || selfHp > selfMaxHp
                || decisionId < 0 || observation == null) {
            throw new IllegalArgumentException("Invalid AI context");
        }
        this.matchId = matchId;
        this.gameState = gameState;
        this.selfHp = selfHp;
        this.selfMaxHp = selfMaxHp;
        this.observation = observation;
        this.decisionId = decisionId;
        this.previousWeights = previousWeights;
        this.policyState = policyState;
    }

    public String getMatchId() { return matchId; }
    public String getActorId() { return gameState.getActorId(); }
    public GameState getGameState() { return gameState; }
    public int getSelfHp() { return selfHp; }
    public int getSelfMaxHp() { return selfMaxHp; }
    public PlayerProfile.Snapshot getObservation() { return observation; }
    public long getDecisionId() { return decisionId; }
    public HeuristicWeights getPreviousWeights() { return previousWeights; }
    public String getPolicyState() { return policyState; }
}
