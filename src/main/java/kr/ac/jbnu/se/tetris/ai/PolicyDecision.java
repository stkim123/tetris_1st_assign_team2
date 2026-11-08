package kr.ac.jbnu.se.tetris.ai;

/** 계획 전체가 수락된 뒤에만 확정할 정책 결과 */
public final class PolicyDecision {
    private final String policyId;
    private final HeuristicWeights weights;
    private final String nextPolicyState;
    private final long decisionId;
    private final long sourceVersion;
    private final String fallbackReason;

    public PolicyDecision(String policyId, HeuristicWeights weights, String nextPolicyState,
                          long decisionId, long sourceVersion) {
        this(policyId, weights, nextPolicyState, decisionId, sourceVersion, null);
    }

    public PolicyDecision(String policyId, HeuristicWeights weights, String nextPolicyState,
                          long decisionId, long sourceVersion, String fallbackReason) {
        if (policyId == null || policyId.trim().isEmpty() || weights == null
                || decisionId < 0 || sourceVersion < 0
                || (fallbackReason != null && fallbackReason.trim().isEmpty())) {
            throw new IllegalArgumentException("Invalid policy decision");
        }
        this.policyId = policyId;
        this.weights = weights;
        this.nextPolicyState = nextPolicyState;
        this.decisionId = decisionId;
        this.sourceVersion = sourceVersion;
        this.fallbackReason = fallbackReason;
    }

    public String getPolicyId() { return policyId; }
    public HeuristicWeights getWeights() { return weights; }
    public String getNextPolicyState() { return nextPolicyState; }
    public long getDecisionId() { return decisionId; }
    public long getSourceVersion() { return sourceVersion; }
    public boolean isFallback() { return fallbackReason != null; }
    public String getFallbackReason() { return fallbackReason; }
}
