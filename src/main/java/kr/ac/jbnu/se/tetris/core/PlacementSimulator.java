package kr.ac.jbnu.se.tetris.core;

/** 엔진·실제 보드 변경 없는 AI 후보 배치 평가용 순수 보드 기하 계산 */
public final class PlacementSimulator {
    private PlacementSimulator() { }

    public static int spawnX(BoardState snapshot) {
        if (snapshot == null || snapshot.getWidth() != Board.WIDTH || snapshot.getHeight() != Board.HEIGHT) {
            throw new IllegalArgumentException("Expected a 10 x 22 board snapshot");
        }
        return snapshot.getWidth() / 2 + 1;
    }

    public static int spawnY(BoardState snapshot, Piece piece) {
        if (piece == null) throw new IllegalArgumentException("Piece is null");
        if (snapshot == null || snapshot.getWidth() != Board.WIDTH || snapshot.getHeight() != Board.HEIGHT) {
            throw new IllegalArgumentException("Expected a 10 x 22 board snapshot");
        }
        return snapshot.getHeight() - 1 + piece.minY();
    }

    public static boolean canPlace(BoardState snapshot, Piece piece, int x, int y) {
        if (piece == null) throw new IllegalArgumentException("Piece is null");
        return new Board(snapshot).canPlace(piece, x, y);
    }

    /** 주어진 유효 위치부터 충돌을 통과하지 않는 착지 계산 */
    public static PlacementResult hardDrop(BoardState snapshot, Piece piece, int x, int startY) {
        if (piece == null) throw new IllegalArgumentException("Piece is null");
        Board preview = new Board(snapshot);
        if (!preview.canPlace(piece, x, startY)) {
            return new PlacementResult(false, "Initial placement collides or is outside the board",
                    snapshot, piece, x, startY, 0);
        }
        int landingY = preview.landingY(piece, x, startY);
        preview.place(piece, x, landingY);
        int lines = preview.removeFullLines();
        return new PlacementResult(true, null, preview.snapshot(), piece, x, landingY, lines);
    }
}
