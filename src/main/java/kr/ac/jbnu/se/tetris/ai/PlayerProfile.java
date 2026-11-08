package kr.ac.jbnu.se.tetris.ai;

import java.util.List;
import kr.ac.jbnu.se.tetris.core.BoardState;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameEvent;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.PieceType;

/** 실제 플레이 이벤트의 누적 통계·최근 성향 보관 및 엔진 상태 유지 */
public final class PlayerProfile {
    // 최근 성향 이동 평균 계수 증가 시 새 배치에 빠른 반응과 관측 변동성 증가
    private static final double RECENT_ALPHA = 0.15;
    private long placements;
    private long lineClears;
    private long tetrisClears;
    private long combos;
    private long holds;
    private long hardDrops;
    private long attacks;
    private long dangerTicks;
    private long tSpins;
    private double totalHeight;
    private double totalHoles;
    private boolean pendingHold;
    private boolean hasRecentPlacement;
    private double recentTetris, recentCombo, recentHold, recentHardDrop;
    private double recentAttack, recentHeight, recentHoles;

    public synchronized void observe(GameState before, List<GameEvent> events) {
        observe(before, null, events);
    }

    public synchronized void observe(GameState before, GameAction.Type action, List<GameEvent> events) {
        if (before == null || events == null) throw new IllegalArgumentException("State and events are required");
        boolean placed = false;
        boolean acceptedEvent = false;
        boolean tetrisThisPiece = false, comboThisPiece = false, attackThisPiece = false;
        for (GameEvent event : events) {
            if (event == null || !before.getActorId().equals(event.getActorId())) continue;
            if (event.getType() != GameEvent.Type.ACTION_REJECTED) acceptedEvent = true;
            switch (event.getType()) {
                case GAME_STARTED: pendingHold = false; break;
                case PIECE_PLACED: placements++; placed = true; break;
                case LINE_CLEAR:
                    lineClears += event.getLineCount();
                    if (event.getLineCount() == 4) { tetrisClears++; tetrisThisPiece = true; }
                    if (event.getLineCount() >= 2) { attacks++; attackThisPiece = true; }
                    break;
                case COMBO: combos++; comboThisPiece = true; break;
                case PIECE_HELD: holds++; pendingHold = true; break;
                case T_SPIN: tSpins++; break;
                default: break;
            }
        }
        if (placed) {
            if (action == GameAction.Type.HARD_DROP) hardDrops++;
            BoardState board = before.getBoard();
            int height = aggregateHeight(board);
            int holeCount = holes(board);
            totalHeight += height;
            totalHoles += holeCount;
            double alpha = hasRecentPlacement ? RECENT_ALPHA : 1.0;
            recentTetris += alpha * ((tetrisThisPiece ? 1 : 0) - recentTetris);
            recentCombo += alpha * ((comboThisPiece ? 1 : 0) - recentCombo);
            recentHold += alpha * ((pendingHold ? 1 : 0) - recentHold);
            recentHardDrop += alpha * ((action == GameAction.Type.HARD_DROP ? 1 : 0) - recentHardDrop);
            recentAttack += alpha * ((attackThisPiece ? 1 : 0) - recentAttack);
            recentHeight += alpha * (height - recentHeight);
            recentHoles += alpha * (holeCount - recentHoles);
            hasRecentPlacement = true;
            pendingHold = false;
        }
        // 실제 중력 틱 기준의 위험 지속 시간 계산 및 입력 횟수와 분리
        if (action == GameAction.Type.GRAVITY_TICK && acceptedEvent
                && before.getStatus() == GameState.Status.RUNNING
                && maximumHeight(before.getBoard()) >= 16) dangerTicks++;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(placements, lineClears, tetrisClears, combos, holds,
                hardDrops, attacks, dangerTicks, tSpins, totalHeight, totalHoles,
                recentTetris, recentCombo, recentHold, recentHardDrop,
                recentAttack, recentHeight, recentHoles);
    }

    static int aggregateHeight(BoardState board) {
        int sum = 0;
        for (int x = 0; x < board.getWidth(); x++) sum += height(board, x);
        return sum;
    }

    static int maximumHeight(BoardState board) {
        int maximum = 0;
        for (int x = 0; x < board.getWidth(); x++) maximum = Math.max(maximum, height(board, x));
        return maximum;
    }

    static int holes(BoardState board) {
        int holes = 0;
        for (int x = 0; x < board.getWidth(); x++) {
            int top = height(board, x);
            for (int y = 0; y < top; y++) if (board.getCell(x, y) == PieceType.EMPTY) holes++;
        }
        return holes;
    }

    private static int height(BoardState board, int x) {
        for (int y = board.getHeight() - 1; y >= 0; y--) {
            if (board.getCell(x, y) != PieceType.EMPTY) return y + 1;
        }
        return 0;
    }

    /** AI 작업 스레드용 불변 통계 사본 */
    public static final class Snapshot {
        private final long placements, lineClears, tetrisClears, combos, holds;
        private final long hardDrops, attacks, dangerTicks, tSpins;
        private final double totalHeight, totalHoles;
        private final double recentTetris, recentCombo, recentHold, recentHardDrop;
        private final double recentAttack, recentHeight, recentHoles;

        private Snapshot(long placements, long lineClears, long tetrisClears, long combos,
                         long holds, long hardDrops, long attacks, long dangerTicks, long tSpins,
                         double totalHeight, double totalHoles, double recentTetris,
                         double recentCombo, double recentHold, double recentHardDrop,
                         double recentAttack, double recentHeight, double recentHoles) {
            this.placements = placements;
            this.lineClears = lineClears;
            this.tetrisClears = tetrisClears;
            this.combos = combos;
            this.holds = holds;
            this.hardDrops = hardDrops;
            this.attacks = attacks;
            this.dangerTicks = dangerTicks;
            this.tSpins = tSpins;
            this.totalHeight = totalHeight;
            this.totalHoles = totalHoles;
            this.recentTetris = recentTetris;
            this.recentCombo = recentCombo;
            this.recentHold = recentHold;
            this.recentHardDrop = recentHardDrop;
            this.recentAttack = recentAttack;
            this.recentHeight = recentHeight;
            this.recentHoles = recentHoles;
        }

        public long getPlacements() { return placements; }
        public long getLineClears() { return lineClears; }
        public long getTetrisClears() { return tetrisClears; }
        public long getCombos() { return combos; }
        public long getHolds() { return holds; }
        public long getHardDrops() { return hardDrops; }
        public long getAttacks() { return attacks; }
        public long getDangerTicks() { return dangerTicks; }
        public long getTSpins() { return tSpins; }
        public double getAverageHeight() { return placements == 0 ? 0 : totalHeight / placements; }
        public double getAverageHoles() { return placements == 0 ? 0 : totalHoles / placements; }
        public double getTetrisRate() { return placements == 0 ? 0 : (double) tetrisClears / placements; }
        public double getComboRate() { return placements == 0 ? 0 : (double) combos / placements; }
        public double getHoldRate() { return placements == 0 ? 0 : (double) holds / placements; }
        public double getHardDropRate() { return placements == 0 ? 0 : (double) hardDrops / placements; }
        public double getAttackRate() { return placements == 0 ? 0 : (double) attacks / placements; }
        public double getRecentTetrisRate() { return recentTetris; }
        public double getRecentComboRate() { return recentCombo; }
        public double getRecentHoldRate() { return recentHold; }
        public double getRecentHardDropRate() { return recentHardDrop; }
        public double getRecentAttackRate() { return recentAttack; }
        public double getRecentAverageHeight() { return recentHeight; }
        public double getRecentAverageHoles() { return recentHoles; }
    }
}
