package kr.ac.jbnu.se.tetris.ai;

/** 높은 평가 점수 우선 원칙의 불변 가중치 */
public final class HeuristicWeights {
    // SAFE는 높은 보드와 구멍을 강하게 피하는 기본 평가 성향
    public static final HeuristicWeights SAFE = new HeuristicWeights(6, 15, -.45, -.8, -7, -.35, -.25);
    // QUICK은 줄 삭제 보상 증가와 보드 위험 벌점 완화에 따른 빠른 공격 성향
    public static final HeuristicWeights QUICK = new HeuristicWeights(12, 18, -.2, -.25, -4, -.2, -.1);
    // TETRIS는 네 줄 동시 삭제 보너스를 크게 둔 축적 성향
    public static final HeuristicWeights TETRIS = new HeuristicWeights(3, 48, -.3, -.4, -6, -.25, -.05);

    // 최종 점수는 각 보드 지표와 해당 가중치의 곱을 합산한 값
    // line과 fourLineBonus의 양수 확대는 줄 삭제와 네 줄 삭제 우선도 증가
    private final double line;
    private final double fourLineBonus;
    // aggregateHeight와 maximumHeight의 음수 절댓값 확대는 높은 보드 회피 강화
    private final double aggregateHeight;
    private final double maximumHeight;
    // holes·bumpiness·wells의 음수 절댓값 확대는 구멍·높이 차·우물 회피 강화
    // 여러 가중치를 함께 바꾸면 성향 간 상쇄가 가능하므로 실제 대전 결과 확인 필요
    private final double holes;
    private final double bumpiness;
    private final double wells;

    public HeuristicWeights(double line, double fourLineBonus, double aggregateHeight,
                            double maximumHeight, double holes, double bumpiness, double wells) {
        double[] values = { line, fourLineBonus, aggregateHeight, maximumHeight, holes, bumpiness, wells };
        for (double value : values) if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Heuristic weights must be finite");
        }
        this.line = line;
        this.fourLineBonus = fourLineBonus;
        this.aggregateHeight = aggregateHeight;
        this.maximumHeight = maximumHeight;
        this.holes = holes;
        this.bumpiness = bumpiness;
        this.wells = wells;
    }

    public double getLine() { return line; }
    public double getFourLineBonus() { return fourLineBonus; }
    public double getAggregateHeight() { return aggregateHeight; }
    public double getMaximumHeight() { return maximumHeight; }
    public double getHoles() { return holes; }
    public double getBumpiness() { return bumpiness; }
    public double getWells() { return wells; }
}
