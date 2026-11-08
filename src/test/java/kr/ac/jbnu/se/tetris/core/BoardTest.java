package kr.ac.jbnu.se.tetris.core;

/** 보드 충돌과 줄 제거의 경계 조건을 확인 */
public final class BoardTest {
    public static void main(String[] args) {
        geometryAndRotations();
        collisionAndSnapshot();
        lineCompression();
    }

    private static void geometryAndRotations() {
        int[][][] expected = {
            {{0,-1},{0,0},{-1,0},{-1,1}},
            {{0,-1},{0,0},{1,0},{1,1}},
            {{0,-1},{0,0},{0,1},{0,2}},
            {{-1,0},{0,0},{1,0},{0,1}},
            {{0,0},{1,0},{0,1},{1,1}},
            {{-1,-1},{0,-1},{0,0},{0,1}},
            {{1,-1},{0,-1},{0,0},{0,1}}
        };
        PieceType[] types = {PieceType.Z, PieceType.S, PieceType.I, PieceType.T,
                PieceType.O, PieceType.L, PieceType.J};
        for (int t = 0; t < types.length; t++) {
            Piece initial = new Piece(types[t]);
            for (int i = 0; i < 4; i++) {
                check(initial.x(i) == expected[t][i][0] && initial.y(i) == expected[t][i][1],
                        "Original geometry " + types[t]);
            }
            Piece current = initial;
            for (int r = 0; r < 4; r++) current = current.rotateRight();
            for (int i = 0; i < 4; i++) {
                check(current.x(i) == initial.x(i) && current.y(i) == initial.y(i),
                        "Four rotations restore " + types[t]);
            }
            check(initial.rotateRight().rotateLeft().x(0) == initial.x(0), "Inverse rotation");
            if (types[t] == PieceType.O) check(initial.rotateRight() == initial, "O keeps orientation");
        }
    }

    private static void collisionAndSnapshot() {
        Board board = new Board();
        Piece square = new Piece(PieceType.O);
        check(board.canPlace(square, 0, 1), "Square fits at left bottom");
        check(!board.canPlace(square, -1, 1), "Left wall");
        check(!board.canPlace(square, 9, 1), "Right wall");
        check(!board.canPlace(square, 0, 0), "Floor");
        check(!board.canPlace(square, 0, 22), "Ceiling");
        BoardState before = board.snapshot();
        board.place(square, 0, 1);
        check(!board.canPlace(square, 0, 1), "Locked cell collision");
        check(before.getCell(0, 0) == PieceType.EMPTY, "Snapshot does not follow mutation");
        check(board.snapshot().getCell(0, 0) == PieceType.O, "Locked piece is visible");
        boolean threw = false;
        try { before.getCell(-1, 0); } catch (IndexOutOfBoundsException expected) { threw = true; }
        check(threw, "Bounds check");
    }

    private static void lineCompression() {
        Board board = new Board();
        for (int x = 0; x < 10; x++) board.setCell(x, 0, PieceType.Z);
        board.setCell(4, 21, PieceType.T);
        check(board.removeFullLines() == 1, "One line removed");
        check(board.getCell(4, 20) == PieceType.T, "Top marker moved down");
        check(board.getCell(4, 21) == PieceType.EMPTY, "Original top row cleared");

        Board multi = new Board();
        for (int x = 0; x < 10; x++) {
            multi.setCell(x, 0, PieceType.Z);
            multi.setCell(x, 2, PieceType.S);
        }
        multi.setCell(3, 1, PieceType.I);
        multi.setCell(7, 3, PieceType.J);
        check(multi.removeFullLines() == 2, "Two lines removed");
        check(multi.getCell(3, 0) == PieceType.I, "Intervening row compressed");
        check(multi.getCell(7, 1) == PieceType.J, "Upper row compressed");
        check(multi.getCell(7, 21) == PieceType.EMPTY, "Unused top cleared");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
