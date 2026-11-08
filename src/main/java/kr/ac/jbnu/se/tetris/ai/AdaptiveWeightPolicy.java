package kr.ac.jbnu.se.tetris.ai;

/** 플레이어의 공개 배치 관측에 반응하는 범위 제한 가중치 정책 */
public final class AdaptiveWeightPolicy implements WeightPolicy {
    // 배치 표본이 적을 때 우연한 한두 번의 행동으로 정책이 흔들리지 않도록 하는 최소 개수
    private static final int MIN_OBSERVATIONS = 6;
    // 이전에 수락된 가중치에서 목표 가중치로 이동하는 비율이며 증가 시 반응 속도 증가
    private static final double RESPONSE = 0.5;

    @Override public PolicyDecision decide(AIContext context, AIProfile profile) {
        if (context == null || profile == null) {
            throw new IllegalArgumentException("Context and profile are required");
        }
        HeuristicWeights base = profile.getBaseWeights();
        PlayerProfile.Snapshot observed = context.getObservation();
        String state = "ADAPTIVE:" + context.getMatchId().length() + ":" + context.getMatchId();
        if (observed.getPlacements() < MIN_OBSERVATIONS) {
            return decision(context, profile, base, state);
        }

        // 공격 관측 증가 시 줄 삭제 보상 강화, 높이·구멍·HP 위험 증가 시 보드 보전 강화
        double pressure = clamp(observed.getRecentAttackRate() * 2.0
                + observed.getRecentTetrisRate() * 2.0
                + observed.getRecentComboRate() * 0.5
                + observed.getRecentHardDropRate() * 0.2);
        double caution = clamp(observed.getRecentAverageHoles() / 10.0
                + observed.getRecentAverageHeight() / 80.0
                + (1.0 - (double) context.getSelfHp() / context.getSelfMaxHp()) * 0.5);
        double limit = profile.getMaxWeightDeltaRatio();
        HeuristicWeights target = new HeuristicWeights(
                base.getLine() * (1.0 + limit * pressure),
                base.getFourLineBonus() * (1.0 + limit * observed.getRecentTetrisRate()),
                base.getAggregateHeight() * (1.0 + limit * caution),
                base.getMaximumHeight() * (1.0 + limit * caution),
                base.getHoles() * (1.0 + limit * caution),
                base.getBumpiness(), base.getWells());

        HeuristicWeights previous = context.getPreviousWeights();
        if (!state.equals(context.getPolicyState()) || !profile.allows(previous)) {
            return decision(context, profile, target, state);
        }
        // 이전 계획이 전부 수락된 경우에만 세션이 previousWeights를 넘겨 주는 경계
        HeuristicWeights smoothed = new HeuristicWeights(
                blend(previous.getLine(), target.getLine()),
                blend(previous.getFourLineBonus(), target.getFourLineBonus()),
                blend(previous.getAggregateHeight(), target.getAggregateHeight()),
                blend(previous.getMaximumHeight(), target.getMaximumHeight()),
                blend(previous.getHoles(), target.getHoles()),
                blend(previous.getBumpiness(), target.getBumpiness()),
                blend(previous.getWells(), target.getWells()));
        return decision(context, profile, smoothed, state);
    }

    private static PolicyDecision decision(AIContext context, AIProfile profile,
                                           HeuristicWeights weights, String state) {
        return new PolicyDecision(profile.getPolicyId(), weights, state,
                context.getDecisionId(), context.getGameState().getVersion());
    }

    private static double blend(double oldValue, double target) {
        return oldValue + RESPONSE * (target - oldValue);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
