package kr.ac.jbnu.se.tetris.ai;

/** 정책 선택과 휴리스틱 탐색 한도를 묶는 몬스터별 설정 */
public final class AIProfile {
    private final String profileId;
    private final String policyId;
    private final HeuristicWeights baseWeights;
    // profiles.properties의 delayMillis 조절로 변경하는 행동 간격이며 증가 시 압박 감소
    private final int delayMillis;
    // 탐색 상태 수 상한이며 증가 시 탐색 범위와 CPU 비용 증가 가능성
    private final int maxSearchStates;
    // 탐색 루프의 시간 예산 단위 ms이며 운영체제 수준의 실행 시간 보장과 구분
    private final int budgetMillis;
    // 후속 적응 정책의 기본 가중치 대비 최대 조정 비율이며 FIXED의 자동 난이도 조절 기능과 무관
    private final double maxWeightDeltaRatio;

    public AIProfile(String profileId, String policyId, HeuristicWeights baseWeights,
                     int delayMillis, int maxSearchStates, int budgetMillis,
                     double maxWeightDeltaRatio) {
        if (profileId == null || !profileId.matches("[a-z][a-z0-9_]*")
                || policyId == null || policyId.trim().isEmpty() || baseWeights == null
                || delayMillis < 100 || delayMillis > 10000
                || maxSearchStates < 1 || maxSearchStates > 10000
                || budgetMillis < 1 || budgetMillis > 1000
                || Double.isNaN(maxWeightDeltaRatio) || Double.isInfinite(maxWeightDeltaRatio)
                || maxWeightDeltaRatio < 0 || maxWeightDeltaRatio > 1) {
            throw new IllegalArgumentException("Invalid AI profile");
        }
        this.profileId = profileId;
        this.policyId = policyId;
        this.baseWeights = baseWeights;
        this.delayMillis = delayMillis;
        this.maxSearchStates = maxSearchStates;
        this.budgetMillis = budgetMillis;
        this.maxWeightDeltaRatio = maxWeightDeltaRatio;
    }

    public String getProfileId() { return profileId; }
    public String getPolicyId() { return policyId; }
    public HeuristicWeights getBaseWeights() { return baseWeights; }
    public int getDelayMillis() { return delayMillis; }
    public int getMaxSearchStates() { return maxSearchStates; }
    public int getBudgetMillis() { return budgetMillis; }
    public double getMaxWeightDeltaRatio() { return maxWeightDeltaRatio; }

    /** 기준 가중치 대비 조정 비율과 부호 경계 검증 */
    public boolean allows(HeuristicWeights weights) {
        if (weights == null) return false;
        double[] base = {baseWeights.getLine(), baseWeights.getFourLineBonus(),
                baseWeights.getAggregateHeight(), baseWeights.getMaximumHeight(),
                baseWeights.getHoles(), baseWeights.getBumpiness(), baseWeights.getWells()};
        double[] candidate = {weights.getLine(), weights.getFourLineBonus(),
                weights.getAggregateHeight(), weights.getMaximumHeight(),
                weights.getHoles(), weights.getBumpiness(), weights.getWells()};
        for (int i = 0; i < base.length; i++) {
            if (Math.abs(candidate[i] - base[i]) > Math.abs(base[i]) * maxWeightDeltaRatio + 1e-9) {
                return false;
            }
        }
        return true;
    }
}
