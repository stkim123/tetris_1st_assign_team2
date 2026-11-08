package kr.ac.jbnu.se.tetris.battle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import kr.ac.jbnu.se.tetris.core.GameAction;

/** 공격 묶음의 순서와 구멍, 최초 수신 시각을 보존하는 가비지 대기열. */
public final class GarbageTank {
    public static final long DELAY_MILLIS = 2000;
    public static final int PLACEMENT_CAP = 4;
    private final Queue<Chunk> chunks = new ArrayDeque<Chunk>();
    private final Random random;
    private int pendingLines;
    private int previousHole = -1;
    private long readyAt = -1;

    public GarbageTank(long seed) { random = new Random(seed); }

    public void enqueue(int lines, long elapsedMillis) {
        if (lines <= 0) return;
        if (pendingLines > Integer.MAX_VALUE - lines)
            throw new IllegalArgumentException("Garbage tank overflow");
        int hole;
        if (previousHole < 0) hole = random.nextInt(10);
        else if (random.nextInt(5) == 0) {
            int different = random.nextInt(9);
            hole = different >= previousHole ? different + 1 : different;
        } else hole = previousHole;
        previousHole = hole;
        if (pendingLines == 0) readyAt = elapsedMillis + DELAY_MILLIS;
        chunks.add(new Chunk(lines, hole));
        pendingLines += lines;
    }

    /** 오래된 묶음부터 상쇄하고 남은 공격량을 반환한다. */
    public int cancel(int outgoing) {
        if (outgoing < 0) throw new IllegalArgumentException("Negative attack");
        while (outgoing > 0 && !chunks.isEmpty()) {
            Chunk first = chunks.peek();
            int removed = Math.min(outgoing, first.lines);
            first.lines -= removed; pendingLines -= removed; outgoing -= removed;
            if (first.lines == 0) chunks.remove();
        }
        if (pendingLines == 0) readyAt = -1;
        return outgoing;
    }

    public boolean removeOldestLine() {
        if (pendingLines == 0) return false;
        cancel(1);
        return true;
    }

    public List<GameAction.Garbage> drainForPlacement(long elapsedMillis) {
        List<GameAction.Garbage> result = new ArrayList<GameAction.Garbage>();
        if (pendingLines == 0 || elapsedMillis < readyAt) return result;
        int allowance = PLACEMENT_CAP;
        while (allowance > 0 && !chunks.isEmpty()) {
            Chunk first = chunks.peek();
            int count = Math.min(allowance, first.lines);
            result.add(new GameAction.Garbage(count, first.hole));
            first.lines -= count; pendingLines -= count; allowance -= count;
            if (first.lines == 0) chunks.remove();
        }
        if (pendingLines == 0) readyAt = -1;
        return result;
    }

    public int getPendingLines() { return pendingLines; }
    public int getPreviousHole() { return previousHole; }
    public long getWaitRemainingMillis(long elapsedMillis) {
        return pendingLines == 0 ? 0 : Math.max(0, readyAt - elapsedMillis);
    }

    private static final class Chunk {
        private int lines;
        private final int hole;
        private Chunk(int lines, int hole) { this.lines = lines; this.hole = hole; }
    }
}
