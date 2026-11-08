package kr.ac.jbnu.se.tetris.ai;

import kr.ac.jbnu.se.tetris.core.GameState;

/** 정책이 선정한 가중치를 공통 탐색과 평가에 연결하는 전략 */
public final class PolicyDrivenStrategy implements AIStrategy {
    private final AIProfile profile;
    private final WeightPolicy policy;

    public PolicyDrivenStrategy(AIProfile profile, WeightPolicy policy) {
        if (profile == null || policy == null) throw new IllegalArgumentException("Profile and policy are required");
        this.profile = profile;
        this.policy = policy;
    }

    public AIPlan plan(GameState state) {
        if (state == null) throw new IllegalArgumentException("Game state is required");
        return plan(new AIContext("legacy", state, 100, new PlayerProfile().snapshot(),
                state.getVersion(), null, null));
    }

    public AIPlan plan(AIContext context) {
        if (context == null) throw new IllegalArgumentException("AI context is required");
        PolicyDecision decision;
        try {
            decision = policy.decide(context, profile);
        } catch (RuntimeException failure) {
            // 정책 런타임 오류만 검증된 기본 가중치로 복구, 탐색 오류는 별도 전파
            decision = fallback(context, "POLICY_EXCEPTION");
        }
        String invalid = invalidReason(context, decision);
        if (invalid != null) decision = fallback(context, invalid);
        AIPlan heuristic = new HeuristicStrategy(decision.getWeights(),
                profile.getMaxSearchStates(), profile.getBudgetMillis() * 1_000_000L)
                .plan(context.getGameState());
        return new AIPlan(heuristic.getSourceVersion(), heuristic.getActions(),
                heuristic.getCandidateCount(), heuristic.getElapsedNanos(),
                heuristic.getScore(), heuristic.isTimedOut(), decision);
    }

    public AIProfile getProfile() { return profile; }

    private String invalidReason(AIContext context, PolicyDecision decision) {
        if (decision == null) return "NO_POLICY_DECISION";
        if (!profile.getPolicyId().equals(decision.getPolicyId())) return "POLICY_ID_MISMATCH";
        if (decision.getDecisionId() != context.getDecisionId()) return "DECISION_ID_MISMATCH";
        if (decision.getSourceVersion() != context.getGameState().getVersion()) {
            return "SOURCE_VERSION_MISMATCH";
        }
        if (!profile.allows(decision.getWeights())) return "WEIGHTS_OUT_OF_RANGE";
        return null;
    }

    private PolicyDecision fallback(AIContext context, String reason) {
        return new PolicyDecision(profile.getPolicyId(), profile.getBaseWeights(),
                context.getPolicyState(), context.getDecisionId(),
                context.getGameState().getVersion(), reason);
    }
}
