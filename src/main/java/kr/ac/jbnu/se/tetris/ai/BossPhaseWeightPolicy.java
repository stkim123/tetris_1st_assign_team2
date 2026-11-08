package kr.ac.jbnu.se.tetris.ai;

/** 보스 HP에 따라 준비, 압박, 생존 성향을 바꾸되 동일 탐색 엔진을 사용. */
public final class BossPhaseWeightPolicy implements WeightPolicy {
    @Override public PolicyDecision decide(AIContext context, AIProfile profile) {
        if (context == null || profile == null) throw new IllegalArgumentException("Context and profile required");
        long hp100 = (long) context.getSelfHp() * 100;
        long maxHp = context.getSelfMaxHp();
        String phase = hp100 >= 67 * maxHp ? "PREPARE"
                : hp100 > 33 * maxHp ? "PRESSURE" : "SURVIVE";
        HeuristicWeights b = profile.getBaseWeights();
        double limit = Math.min(.2, profile.getMaxWeightDeltaRatio());
        HeuristicWeights weights = b;
        if ("PRESSURE".equals(phase)) {
            weights = new HeuristicWeights(b.getLine() * (1 + limit), b.getFourLineBonus() * (1 + limit),
                    b.getAggregateHeight() * (1 - limit * .75), b.getMaximumHeight() * (1 - limit * .75),
                    b.getHoles(), b.getBumpiness(), b.getWells());
        } else if ("SURVIVE".equals(phase)) {
            weights = new HeuristicWeights(b.getLine() * (1 - limit * .75),
                    b.getFourLineBonus() * (1 - limit * .75),
                    b.getAggregateHeight() * (1 + limit), b.getMaximumHeight() * (1 + limit),
                    b.getHoles() * (1 + limit), b.getBumpiness() * (1 + limit),
                    b.getWells() * (1 + limit));
        }
        return new PolicyDecision(profile.getPolicyId(), weights, phase,
                context.getDecisionId(), context.getGameState().getVersion());
    }
}
