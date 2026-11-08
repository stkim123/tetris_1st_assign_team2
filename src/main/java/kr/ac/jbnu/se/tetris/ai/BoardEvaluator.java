package kr.ac.jbnu.se.tetris.ai;

import kr.ac.jbnu.se.tetris.core.BoardState;
import kr.ac.jbnu.se.tetris.core.PieceType;

/** 시뮬레이션에서 피스 고정 후 보드 평가 */
public final class BoardEvaluator {
    private final HeuristicWeights weights;

    public BoardEvaluator(HeuristicWeights weights) {
        if (weights == null) throw new IllegalArgumentException("Weights are required");
        this.weights = weights;
    }

    public double score(BoardState board, int clearedLines) {
        if (board == null || clearedLines < 0) throw new IllegalArgumentException("Invalid board result");
        int[] heights = new int[board.getWidth()];
        int aggregate = 0;
        int maximum = 0;
        int holes = 0;
        int bumpiness = 0;
        int wells = 0;
        for (int x = 0; x < board.getWidth(); x++) {
            boolean filledAbove = false;
            for (int y = board.getHeight() - 1; y >= 0; y--) {
                if (board.getCell(x, y) != PieceType.EMPTY) {
                    if (!filledAbove) heights[x] = y + 1;
                    filledAbove = true;
                } else if (filledAbove) {
                    holes++;
                }
            }
            aggregate += heights[x];
            maximum = Math.max(maximum, heights[x]);
            if (x > 0) bumpiness += Math.abs(heights[x] - heights[x - 1]);
        }
        for (int x = 0; x < heights.length; x++) {
            int left = x == 0 ? board.getHeight() : heights[x - 1];
            int right = x == heights.length - 1 ? board.getHeight() : heights[x + 1];
            wells += Math.max(0, Math.min(left, right) - heights[x]);
        }
        return clearedLines * weights.getLine()
                + (clearedLines == 4 ? weights.getFourLineBonus() : 0)
                + aggregate * weights.getAggregateHeight()
                + maximum * weights.getMaximumHeight()
                + holes * weights.getHoles()
                + bumpiness * weights.getBumpiness()
                + wells * weights.getWells();
    }
}
