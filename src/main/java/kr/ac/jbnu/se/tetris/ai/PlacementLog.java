package kr.ac.jbnu.se.tetris.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import kr.ac.jbnu.se.tetris.core.BoardState;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameEvent;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.Piece;
import kr.ac.jbnu.se.tetris.core.PieceType;

/** 시작 상황과 실제 선택을 별도로 기록하는 크기 제한 로그 */
public final class PlacementLog {
    // 플레이어 관측용 배치 기록 보관 한도
    public static final int DEFAULT_CAPACITY = 128;
    private final int capacity;
    private final ArrayDeque<Sample> samples = new ArrayDeque<Sample>();
    private GameState start;
    private boolean held;
    private boolean hardDropped;
    private long revision;

    public PlacementLog() { this(DEFAULT_CAPACITY); }

    public PlacementLog(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Capacity must be positive");
        this.capacity = capacity;
    }

    public synchronized void observe(GameState before, List<GameEvent> events) {
        observe(before, null, events);
    }

    public synchronized void observe(GameState before, GameAction.Type action, List<GameEvent> events) {
        if (before == null || events == null) throw new IllegalArgumentException("State and events are required");
        if (start == null && before.getStatus() == GameState.Status.RUNNING
                && before.getActivePiece() != null && !before.isAwaitingSpawn()) {
            start = before;
            held = false;
            hardDropped = false;
        }
        GameEvent placed = null;
        int cleared = 0;
        int combo = -1;
        boolean tSpin = false;
        for (GameEvent event : events) {
            if (event == null || !before.getActorId().equals(event.getActorId())) continue;
            if (event.getType() == GameEvent.Type.PIECE_HELD) held = true;
            if (event.getType() == GameEvent.Type.PIECE_PLACED) {
                if (action == GameAction.Type.HARD_DROP) hardDropped = true;
                placed = event;
            }
            if (event.getType() == GameEvent.Type.LINE_CLEAR) {
                cleared = event.getLineCount();
                // 첫 줄 삭제에는 COMBO 이벤트가 없으므로 LINE_CLEAR의 콤보 값 사용
                combo = event.getCombo();
            }
            if (event.getType() == GameEvent.Type.COMBO) combo = event.getCombo();
            if (event.getType() == GameEvent.Type.T_SPIN) tSpin = true;
        }
        if (placed != null) {
            if (start != null) {
                if (samples.size() == capacity) samples.removeFirst();
                samples.addLast(new Sample(start, placed, held, hardDropped, cleared, combo, tSpin));
                revision++;
            }
            start = null;
            held = false;
            hardDropped = false;
        }
        // 일시정지·게임오버에서 미완성 피스 기록 생성 방지
        if (before.getStatus() == GameState.Status.GAME_OVER) start = null;
    }

    public synchronized List<Sample> snapshot() {
        return Collections.unmodifiableList(new ArrayList<Sample>(samples));
    }

    public synchronized long getRevision() { return revision; }
    public synchronized int size() { return samples.size(); }

    /** 새 전투의 첫 피스 전 미완성 관측만 폐기 */
    public synchronized void beginSession() {
        start = null;
        held = false;
        hardDropped = false;
    }

    /** UI·관측 작업에 전달 가능한 불변 보드와 입력 피스 */
    public static final class Sample {
        private final BoardState board;
        private final Piece currentPiece;
        private final int pieceX, pieceY;
        private final List<PieceType> nextPieces;
        private final PieceType holdPiece;
        private final boolean canHold;
        private final PieceType selectedPiece;
        private final int selectedRotation, selectedX, selectedY;
        private final boolean held, hardDropped;
        private final int linesCleared, combo, startingBoardHeight;
        private final boolean tSpin;

        private Sample(GameState start, GameEvent placed, boolean held, boolean hardDropped,
                       int linesCleared, int combo, boolean tSpin) {
            board = start.getBoard();
            currentPiece = start.getActivePiece();
            pieceX = start.getPieceX();
            pieceY = start.getPieceY();
            nextPieces = Collections.unmodifiableList(new ArrayList<PieceType>(start.getNextPieces()));
            holdPiece = start.getHoldPiece();
            canHold = start.canHold();
            selectedPiece = placed.getPiece().getType();
            selectedRotation = placed.getPiece().getRotation();
            selectedX = placed.getX();
            selectedY = placed.getY();
            this.held = held;
            this.hardDropped = hardDropped;
            this.linesCleared = linesCleared;
            this.combo = combo;
            this.tSpin = tSpin;
            this.startingBoardHeight = PlayerProfile.maximumHeight(board);
        }

        public BoardState getBoard() { return board; }
        public Piece getCurrentPiece() { return currentPiece; }
        public int getPieceX() { return pieceX; }
        public int getPieceY() { return pieceY; }
        public List<PieceType> getNextPieces() { return nextPieces; }
        public PieceType getHoldPiece() { return holdPiece; }
        public boolean canHold() { return canHold; }
        public PieceType getSelectedPiece() { return selectedPiece; }
        public int getSelectedRotation() { return selectedRotation; }
        public int getSelectedX() { return selectedX; }
        public int getSelectedY() { return selectedY; }
        public boolean isHeld() { return held; }
        public boolean isHardDropped() { return hardDropped; }
        public int getLinesCleared() { return linesCleared; }
        public int getCombo() { return combo; }
        public boolean isTetris() { return linesCleared == 4; }
        public boolean isTSpin() { return tSpin; }
        public int getStartingBoardHeight() { return startingBoardHeight; }
    }
}
