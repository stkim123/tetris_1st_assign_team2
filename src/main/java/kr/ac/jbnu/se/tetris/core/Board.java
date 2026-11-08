package kr.ac.jbnu.se.tetris.core;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 정착한 블록의 가변 셀 보관 및 GameEngine 전용 변경 권한 */
public final class Board {
    static final int WIDTH = 10;
    static final int HEIGHT = 22;
    private final PieceType[] cells = new PieceType[WIDTH * HEIGHT];
    private final long[] origins = new long[WIDTH * HEIGHT];
    private final String[] itemIds = new String[WIDTH * HEIGHT];

    Board() { Arrays.fill(cells, PieceType.EMPTY); }

    Board(BoardState snapshot) {
        if (snapshot == null || snapshot.getWidth() != WIDTH || snapshot.getHeight() != HEIGHT) {
            throw new IllegalArgumentException("Expected a 10 x 22 board snapshot");
        }
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                PieceType cell = snapshot.getCell(x, y);
                if (cell == null) throw new IllegalArgumentException("Snapshot contains a null cell");
                setCell(x, y, cell);
                int index = y * WIDTH + x;
                origins[index] = snapshot.getOriginId(x, y);
                itemIds[index] = snapshot.getItemId(x, y);
            }
        }
    }

    boolean canPlace(Piece piece, int originX, int originY) {
        for (int i = 0; i < 4; i++) {
            int x = originX + piece.x(i);
            int y = originY - piece.y(i);
            if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || getCell(x, y) != PieceType.EMPTY) return false;
        }
        return true;
    }

    PieceType getCell(int x, int y) { return cells[y * WIDTH + x]; }

    /** 가비지를 포함한 모든 칸이 비어 있는 상태의 확인, 퍼펙트 클리어 판정용 */
    boolean isEmpty() {
        for (PieceType cell : cells) {
            if (cell != PieceType.EMPTY) return false;
        }
        return true;
    }

    void setCell(int x, int y, PieceType type) {
        int index = y * WIDTH + x;
        cells[index] = type;
        origins[index] = 0;
        itemIds[index] = null;
    }

    private void copyCell(int dx, int dy, int sx, int sy) {
        int destination = dy * WIDTH + dx;
        int source = sy * WIDTH + sx;
        cells[destination] = cells[source];
        origins[destination] = origins[source];
        itemIds[destination] = itemIds[source];
    }

    void place(Piece piece, int originX, int originY) {
        if (!canPlace(piece, originX, originY)) throw new IllegalStateException("Invalid piece placement");
        for (int i = 0; i < 4; i++) {
            int x = originX + piece.x(i), y = originY - piece.y(i);
            int index = y * WIDTH + x;
            cells[index] = piece.getType();
            origins[index] = piece.getIdentity();
            itemIds[index] = piece.hasOreAt(i) ? piece.getItemId() : null;
        }
    }

    int landingY(Piece piece, int originX, int startY) {
        if (!canPlace(piece, originX, startY)) {
            throw new IllegalArgumentException("Initial piece placement is invalid");
        }
        int landing = startY;
        while (canPlace(piece, originX, landing - 1)) landing--;
        return landing;
    }

    void copyFrom(Board source) {
        System.arraycopy(source.cells, 0, cells, 0, cells.length);
        System.arraycopy(source.origins, 0, origins, 0, origins.length);
        System.arraycopy(source.itemIds, 0, itemIds, 0, itemIds.length);
    }

    /** 완성 행 안의 실제 광석 칸만 추출한다. 좌표는 압축 전 보드 좌표다. */
    Map<Long, GameEvent.ItemExtraction> itemsOnCompletedRows(Set<Long> previouslyCollected) {
        Map<Long, GameEvent.ItemExtraction> found = new LinkedHashMap<Long, GameEvent.ItemExtraction>();
        for (int y = 0; y < HEIGHT; y++) {
            boolean full = true;
            for (int x = 0; x < WIDTH; x++) {
                if (getCell(x, y) == PieceType.EMPTY) { full = false; break; }
            }
            if (!full) continue;
            for (int x = 0; x < WIDTH; x++) {
                int index = y * WIDTH + x;
                if (origins[index] > 0 && itemIds[index] != null
                        && !previouslyCollected.contains(origins[index])) {
                    found.put(origins[index], new GameEvent.ItemExtraction(
                            itemIds[index], x, y, origins[index]));
                }
            }
        }
        return found;
    }

    void clearCollectedItemMarkers(Set<Long> collected) {
        for (int i = 0; i < origins.length; i++) {
            if (collected.contains(origins[i])) itemIds[i] = null;
        }
    }

    int removeFullLines() {
        // 아래쪽부터 채워 쓰는 방식의 완성 줄 일괄 압축 및 최상단 행 비우기
        int destination = 0;
        for (int source = 0; source < HEIGHT; source++) {
            boolean full = true;
            for (int x = 0; x < WIDTH; x++) {
                if (getCell(x, source) == PieceType.EMPTY) { full = false; break; }
            }
            if (!full) {
                for (int x = 0; x < WIDTH; x++) copyCell(x, destination, x, source);
                destination++;
            }
        }
        int removed = HEIGHT - destination;
        for (int y = destination; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) setCell(x, y, PieceType.EMPTY);
        }
        return removed;
    }

    /** 기존 정착 블록의 상향 이동 및 구멍 하나를 가진 바닥 가비지 행 삽입 */
    boolean addGarbageLine(int holeColumn) {
        if (holeColumn < 0 || holeColumn >= WIDTH) throw new IllegalArgumentException("Invalid hole column");
        boolean overflow = false;
        for (int x = 0; x < WIDTH; x++) overflow |= getCell(x, HEIGHT - 1) != PieceType.EMPTY;
        for (int y = HEIGHT - 1; y > 0; y--) {
            for (int x = 0; x < WIDTH; x++) copyCell(x, y, x, y - 1);
        }
        for (int x = 0; x < WIDTH; x++) setCell(x, 0, x == holeColumn ? PieceType.EMPTY : PieceType.GARBAGE);
        return overflow;
    }

    /** 가장 아래에 가비지가 있는 행을 제거하고 위쪽 행을 한 줄 내린다. */
    boolean clearBottomGarbageLine() {
        int row = -1;
        for (int y = 0; y < HEIGHT && row < 0; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (getCell(x, y) == PieceType.GARBAGE) { row = y; break; }
            }
        }
        if (row < 0) return false;
        for (int y = row; y < HEIGHT - 1; y++) {
            for (int x = 0; x < WIDTH; x++) copyCell(x, y, x, y + 1);
        }
        for (int x = 0; x < WIDTH; x++) setCell(x, HEIGHT - 1, PieceType.EMPTY);
        return true;
    }

    BoardState snapshot() { return new BoardState(WIDTH, HEIGHT, cells, origins, itemIds); }
}
