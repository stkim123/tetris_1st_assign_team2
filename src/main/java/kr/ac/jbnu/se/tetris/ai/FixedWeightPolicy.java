package kr.ac.jbnu.se.tetris.ai;

/** 관측과 무관하게 프로필 가중치를 사용하는 기본 정책 */
public final class FixedWeightPolicy implements WeightPolicy {
    public PolicyDecision decide(AIContext context, AIProfile profile) {
        if (context == null || profile == null) throw new IllegalArgumentException("Context and profile are required");
        return new PolicyDecision(profile.getPolicyId(), profile.getBaseWeights(),
                context.getPolicyState(), context.getDecisionId(),
                context.getGameState().getVersion());
    }
}
