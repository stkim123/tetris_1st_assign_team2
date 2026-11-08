package kr.ac.jbnu.se.tetris.ai;

/** 자기 HP에 따른 준비·공격·생존 국면을 가진 범위 제한 보스 정책 */
public final class BossWeightPolicy implements WeightPolicy {
    private static final int PREPARE = 0;
    private static final int PRESSURE = 1;
    private static final int SURVIVE = 2;
    private static final int MIN_OBSERVATIONS = 6;

    @Override public PolicyDecision decide(AIContext context, AIProfile profile) {
        if (context == null || profile == null) {
            throw new IllegalArgumentException("Context and profile are required");
        }
        int phase = hpPhase(context.getSelfHp(), context.getSelfMaxHp());
        // 같은 경기의 이전 국면은 유지하며 HP 회복과 경계 왕복에 의한 역전환 방지
        for (int previous = phase + 1; previous <= SURVIVE; previous++) {
            if (state(context, previous).equals(context.getPolicyState())) phase = previous;
        }
        PlayerProfile.Snapshot observed = context.getObservation();
        double attack = observed.getPlacements() < MIN_OBSERVATIONS ? 0.0
                : clamp(observed.getRecentAttackRate() * 2.0
                + observed.getRecentTetrisRate() * 2.0);
        HeuristicWeights base = profile.getBaseWeights();
        double limit = profile.getMaxWeightDeltaRatio();
        // 준비 국면은 네 줄 보상 중심, 압박 국면은 줄 삭제, 생존 국면은 보드 위험 회피 강화
        double line = phase == PREPARE ? 0.2 * attack
                : phase == PRESSURE ? 0.6 + 0.2 * attack : 0.8 + 0.2 * attack;
        double fourLine = phase == PREPARE ? 0.0 : phase == PRESSURE ? -0.25 : -0.6;
        double safety = phase == PREPARE ? 0.0 : phase == PRESSURE ? 0.3 : 1.0;
        double shape = phase == SURVIVE ? 0.5 : 0.0;
        HeuristicWeights weights = new HeuristicWeights(
                base.getLine() * (1.0 + limit * line),
                base.getFourLineBonus() * (1.0 + limit * fourLine),
                base.getAggregateHeight() * (1.0 + limit * safety),
                base.getMaximumHeight() * (1.0 + limit * safety),
                base.getHoles() * (1.0 + limit * safety),
                base.getBumpiness() * (1.0 + limit * shape),
                base.getWells() * (1.0 + limit * shape));
        return new PolicyDecision(profile.getPolicyId(), weights, state(context, phase),
                context.getDecisionId(), context.getGameState().getVersion());
    }

    private static int hpPhase(int hp, int maximum) {
        if ((long) hp * 3 <= maximum) return SURVIVE;
        if ((long) hp * 3 <= (long) maximum * 2) return PRESSURE;
        return PREPARE;
    }

    private static String state(AIContext context, int phase) {
        String match = context.getMatchId();
        return "BOSS:" + match.length() + ":" + match + ":" + phase;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
