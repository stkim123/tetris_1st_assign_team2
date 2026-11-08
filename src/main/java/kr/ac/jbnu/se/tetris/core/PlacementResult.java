package kr.ac.jbnu.se.tetris.core;

/** 실제 보드와 분리된 기하학적 착지 결과 및 입력 경로의 도달 가능성 보장 제외 */
public final class PlacementResult {
    private final boolean valid;
    private final String reason;
    private final BoardState board;
    private final Piece piece;
    private final int x;
    private final int y;
    private final int linesCleared;

    PlacementResult(boolean valid, String reason, BoardState board, Piece piece,
                    int x, int y, int linesCleared) {
        if (board == null || piece == null || (valid && reason != null)
                || (!valid && (reason == null || reason.isEmpty())) || linesCleared < 0) {
            throw new IllegalArgumentException("Invalid placement result");
        }
        this.valid = valid;
        this.reason = reason;
        this.board = board;
        this.piece = piece;
        this.x = x;
        this.y = y;
        this.linesCleared = linesCleared;
    }

    public boolean isValid() { return valid; }
    public String getReason() { return reason; }
    public BoardState getBoard() { return board; }
    public Piece getPiece() { return piece; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getLinesCleared() { return linesCleared; }
}
