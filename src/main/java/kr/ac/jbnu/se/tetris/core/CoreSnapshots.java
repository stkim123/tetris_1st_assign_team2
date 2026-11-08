package kr.ac.jbnu.se.tetris.core;

import java.util.List;

/** Wire 변환과 개발 fixture를 위한 검증된 표시 전용 사본 생성 */
public final class CoreSnapshots {
    private CoreSnapshots() { }

    public static BoardState board(int width, int height, PieceType[] cells) {
        return board(width, height, cells, new long[cells == null ? 0 : cells.length],
                new String[cells == null ? 0 : cells.length]);
    }

    public static BoardState board(int width, int height, PieceType[] cells,
                                   long[] origins, String[] itemIds) {
        if (width != 10 || height != 22 || cells == null || cells.length != 220) {
            throw new IllegalArgumentException("A snapshot requires a 10x22 board");
        }
        for (PieceType cell : cells) if (cell == null) throw new IllegalArgumentException("Null board cell");
        if (origins == null || itemIds == null || origins.length != 220 || itemIds.length != 220) {
            throw new IllegalArgumentException("Invalid board metadata");
        }
        return new BoardState(width, height, cells, origins, itemIds);
    }

    public static BoardState board(int width, int height, PieceType[] cells, String[] itemIds) {
        return board(width, height, cells, new long[cells == null ? 0 : cells.length], itemIds);
    }

    public static GameState game(String actorId, long version, long tick, GameState.Status status,
            BoardState board, Piece activePiece, int pieceX, int pieceY, int linesCleared,
            boolean awaitingSpawn, PieceType holdPiece, boolean canHold, List<PieceType> nextPieces,
            int ghostY, int combo, int pendingGarbageLines) {
        return game(actorId, version, tick, status, board, activePiece, pieceX, pieceY,
                linesCleared, awaitingSpawn, holdPiece, canHold, nextPieces,
                ghostY, combo, pendingGarbageLines, null);
    }

    public static GameState game(String actorId, long version, long tick, GameState.Status status,
            BoardState board, Piece activePiece, int pieceX, int pieceY, int linesCleared,
            boolean awaitingSpawn, PieceType holdPiece, boolean canHold, List<PieceType> nextPieces,
            int ghostY, int combo, int pendingGarbageLines, String holdItemId) {
        return game(actorId, version, tick, status, board, activePiece, pieceX, pieceY,
                linesCleared, awaitingSpawn, holdPiece, canHold, nextPieces, ghostY, combo,
                pendingGarbageLines, holdItemId, holdItemId == null ? -1 : 0);
    }

    public static GameState game(String actorId, long version, long tick, GameState.Status status,
            BoardState board, Piece activePiece, int pieceX, int pieceY, int linesCleared,
            boolean awaitingSpawn, PieceType holdPiece, boolean canHold, List<PieceType> nextPieces,
            int ghostY, int combo, int pendingGarbageLines, String holdItemId, int holdOreCellIndex) {
        if (actorId == null || actorId.trim().isEmpty() || actorId.length() > 128
                || version < 0 || tick < 0 || status == null || board == null
                || board.getWidth() != 10 || board.getHeight() != 22 || linesCleared < 0
                || holdPiece == null || holdPiece == PieceType.GARBAGE || nextPieces == null
                || nextPieces.size() > 3 || ghostY < -1 || ghostY >= 22 || combo < -1
                || pendingGarbageLines < 0 || (holdItemId == null && holdOreCellIndex != -1)
                || (holdItemId != null && (holdOreCellIndex < 0 || holdOreCellIndex > 3))) {
            throw new IllegalArgumentException("Invalid game snapshot");
        }
        for (PieceType next : nextPieces) {
            if (next == null || next == PieceType.EMPTY || next == PieceType.GARBAGE) {
                throw new IllegalArgumentException("Invalid next piece");
            }
        }
        boolean active = status == GameState.Status.RUNNING || status == GameState.Status.PAUSED;
        if (active && (awaitingSpawn ? activePiece != null : activePiece == null)) {
            throw new IllegalArgumentException("Active piece and spawn state disagree");
        }
        if (status == GameState.Status.READY && activePiece != null) {
            throw new IllegalArgumentException("READY cannot contain an active piece");
        }
        if (activePiece != null) {
            if (pieceX < -4 || pieceX > 13 || pieceY < -4 || pieceY > 25) {
                throw new IllegalArgumentException("Piece anchor outside snapshot bounds");
            }
            if (active && !PlacementSimulator.canPlace(board, activePiece, pieceX, pieceY)) {
                throw new IllegalArgumentException("Active piece overlaps the board");
            }
            if (active && ghostY != PlacementSimulator.hardDrop(board, activePiece, pieceX, pieceY).getY()) {
                throw new IllegalArgumentException("Ghost position disagrees with board");
            }
        }
        return new GameState(actorId, version, tick, status, board, activePiece, pieceX, pieceY,
                linesCleared, awaitingSpawn, holdPiece, canHold, nextPieces, ghostY, combo,
                pendingGarbageLines, holdItemId, holdOreCellIndex);
    }

    /** 외부 사본의 불변식 확인 및 컬렉션 방어 복사 */
    public static GameState copyOf(GameState state) {
        if (state == null) throw new IllegalArgumentException("Game state is required");
        PieceType[] cells = new PieceType[220];
        long[] origins = new long[220];
        String[] itemIds = new String[220];
        if (state.getBoard().getWidth() != 10 || state.getBoard().getHeight() != 22) {
            throw new IllegalArgumentException("Unexpected board size");
        }
        for (int y = 0; y < 22; y++) for (int x = 0; x < 10; x++) {
            int index = y * 10 + x;
            cells[index] = state.getBoard().getCell(x, y);
            origins[index] = state.getBoard().getOriginId(x, y);
            itemIds[index] = state.getBoard().getItemId(x, y);
        }
        return game(state.getActorId(), state.getVersion(), state.getTick(), state.getStatus(),
                board(10, 22, cells, origins, itemIds), state.getActivePiece(), state.getPieceX(), state.getPieceY(),
                state.getLinesCleared(), state.isAwaitingSpawn(), state.getHoldPiece(), state.canHold(),
                state.getNextPieces(), state.getGhostY(), state.getCombo(), state.getPendingGarbageLines(),
                state.getHoldItemId(), state.getHoldOreCellIndex());
    }
}
