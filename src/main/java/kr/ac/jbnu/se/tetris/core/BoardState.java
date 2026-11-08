package kr.ac.jbnu.se.tetris.core;

/** 호출자와 이후 엔진 동작으로부터 분리된 불변 보드 스냅샷 */
public final class BoardState {
    private final int width;
    private final int height;
    private final PieceType[] cells;
    private final long[] origins;
    private final String[] itemIds;

    BoardState(int width, int height, PieceType[] cells) {
        this(width, height, cells, new long[cells.length], new String[cells.length]);
    }

    BoardState(int width, int height, PieceType[] cells, long[] origins, String[] itemIds) {
        if (origins == null || itemIds == null || origins.length != cells.length
                || itemIds.length != cells.length) throw new IllegalArgumentException("Invalid cell metadata");
        this.width = width;
        this.height = height;
        this.cells = cells.clone();
        this.origins = origins.clone();
        this.itemIds = itemIds.clone();
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public PieceType getCell(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("Cell outside board: " + x + "," + y);
        }
        return cells[y * width + x];
    }

    public long getOriginId(int x, int y) {
        getCell(x, y);
        return origins[y * width + x];
    }

    public String getItemId(int x, int y) {
        getCell(x, y);
        return itemIds[y * width + x];
    }
}
