package kr.ac.jbnu.se.tetris.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** UI와 AI가 공유하는 참가자 한 명의 불변 엔진 스냅샷 */
public final class GameState {
    public enum Status { READY, RUNNING, PAUSED, GAME_OVER }

    private final String actorId;
    private final long version;
    private final long tick;
    private final Status status;
    private final BoardState board;
    private final Piece activePiece;
    private final int pieceX;
    private final int pieceY;
    private final int linesCleared;
    private final boolean awaitingSpawn;
    private final PieceType holdPiece;
    private final String holdItemId;
    private final int holdOreCellIndex;
    private final boolean canHold;
    private final List<PieceType> nextPieces;
    private final int ghostY;
    private final int combo;
    private final int pendingGarbageLines;

    GameState(String actorId, long version, long tick, Status status, BoardState board,
              Piece activePiece, int pieceX, int pieceY, int linesCleared, boolean awaitingSpawn) {
        this(actorId, version, tick, status, board, activePiece, pieceX, pieceY,
                linesCleared, awaitingSpawn, PieceType.EMPTY, false,
                Collections.<PieceType>emptyList(), -1, -1, 0);
    }

    GameState(String actorId, long version, long tick, Status status, BoardState board,
              Piece activePiece, int pieceX, int pieceY, int linesCleared, boolean awaitingSpawn,
              PieceType holdPiece, boolean canHold, List<PieceType> nextPieces,
              int ghostY, int combo, int pendingGarbageLines) {
        this(actorId, version, tick, status, board, activePiece, pieceX, pieceY,
                linesCleared, awaitingSpawn, holdPiece, canHold, nextPieces, ghostY,
                combo, pendingGarbageLines, null);
    }

    GameState(String actorId, long version, long tick, Status status, BoardState board,
              Piece activePiece, int pieceX, int pieceY, int linesCleared, boolean awaitingSpawn,
              PieceType holdPiece, boolean canHold, List<PieceType> nextPieces,
              int ghostY, int combo, int pendingGarbageLines, String holdItemId) {
        this(actorId, version, tick, status, board, activePiece, pieceX, pieceY,
                linesCleared, awaitingSpawn, holdPiece, canHold, nextPieces, ghostY, combo,
                pendingGarbageLines, holdItemId, holdItemId == null ? -1 : 0);
    }

    GameState(String actorId, long version, long tick, Status status, BoardState board,
              Piece activePiece, int pieceX, int pieceY, int linesCleared, boolean awaitingSpawn,
              PieceType holdPiece, boolean canHold, List<PieceType> nextPieces,
              int ghostY, int combo, int pendingGarbageLines, String holdItemId, int holdOreCellIndex) {
        this.actorId = actorId;
        this.version = version;
        this.tick = tick;
        this.status = status;
        this.board = board;
        this.activePiece = activePiece;
        this.pieceX = pieceX;
        this.pieceY = pieceY;
        this.linesCleared = linesCleared;
        this.awaitingSpawn = awaitingSpawn;
        this.holdPiece = holdPiece;
        this.holdItemId = holdItemId;
        this.holdOreCellIndex = holdOreCellIndex;
        this.canHold = canHold;
        this.nextPieces = Collections.unmodifiableList(new ArrayList<PieceType>(nextPieces));
        this.ghostY = ghostY;
        this.combo = combo;
        this.pendingGarbageLines = pendingGarbageLines;
    }

    public String getActorId() { return actorId; }
    public long getVersion() { return version; }
    public long getTick() { return tick; }
    public Status getStatus() { return status; }
    public BoardState getBoard() { return board; }
    public Piece getActivePiece() { return activePiece; }
    public int getPieceX() { return pieceX; }
    public int getPieceY() { return pieceY; }
    public int getLinesCleared() { return linesCleared; }
    public boolean isAwaitingSpawn() { return awaitingSpawn; }
    public PieceType getHoldPiece() { return holdPiece; }
    public String getHoldItemId() { return holdItemId; }
    public int getHoldOreCellIndex() { return holdOreCellIndex; }
    public boolean canHold() { return canHold; }
    public List<PieceType> getNextPieces() { return nextPieces; }
    public int getGhostY() { return ghostY; }
    public int getCombo() { return combo; }
    public int getPendingGarbageLines() { return pendingGarbageLines; }
}
