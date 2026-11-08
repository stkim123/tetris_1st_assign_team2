package kr.ac.jbnu.se.tetris.ai;

import java.util.Collections;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameState;

/** 보드 높이·구멍·라인 삭제 가중치 기반 합법 착지 후보 평가 */
public final class HeuristicStrategy implements AIStrategy {
    // 직접 생성한 전략의 기본 탐색 상태 상한이며 스토리 전투에서는 AI 프로필 값으로 대체
    static final int DEFAULT_MAX_STATES = 1800;
    // 직접 생성한 전략의 기본 착지 후보 탐색 시간 100ms이며 스토리 전투에서는 AI 프로필 값으로 대체
    static final long DEFAULT_BUDGET_NANOS = 100_000_000L;
    // 동점 처리 허용 오차로 배치 선택 안정성에만 관여하며 난이도 조절값과 분리
    private static final double TIE_EPSILON = 1e-9;
    private final BoardEvaluator evaluator;
    private final int maxStates;
    private final long budgetNanos;

    public HeuristicStrategy() { this(HeuristicWeights.SAFE); }

    public HeuristicStrategy(HeuristicWeights weights) {
        this(weights, DEFAULT_MAX_STATES, DEFAULT_BUDGET_NANOS);
    }

    public HeuristicStrategy(HeuristicWeights weights, int maxStates, long budgetNanos) {
        if (maxStates < 1 || budgetNanos < 1) {
            throw new IllegalArgumentException("Search bounds must be positive");
        }
        evaluator = new BoardEvaluator(weights);
        this.maxStates = maxStates;
        this.budgetNanos = budgetNanos;
    }

    public AIPlan plan(GameState state) {
        if (state == null) throw new IllegalArgumentException("Game state is required");
        long started = System.nanoTime();
        PlacementSearch.Result search = PlacementSearch.search(state, maxStates, budgetNanos, started);
        PlacementSearch.Candidate best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (PlacementSearch.Candidate candidate : search.candidates) {
            double score = evaluator.score(candidate.result.getBoard(), candidate.result.getLinesCleared());
            if (candidate.held) score -= 0.5;
            if (score > bestScore + TIE_EPSILON ||
                    (Math.abs(score - bestScore) <= TIE_EPSILON
                    && (best == null || candidate.actions.size() < best.actions.size()))) {
                best = candidate;
                bestScore = score;
            }
        }
        return new AIPlan(state.getVersion(), best == null
                ? Collections.<GameAction.Type>emptyList() : best.actions,
                search.candidates.size(), System.nanoTime() - started,
                best == null ? 0 : bestScore, search.timedOut);
    }
}
