package kr.ac.jbnu.se.tetris.core;

/** 모든 컨트롤러의 공통 명령 및 행위자별 순번을 통한 중복·역순 입력 구분 */
public final class GameAction {
    public enum Type {
        START, MOVE_LEFT, MOVE_RIGHT, ROTATE_LEFT, ROTATE_RIGHT,
        SOFT_DROP, HARD_DROP, GRAVITY_TICK, PAUSE, RESUME, HOLD, USE_ITEM, RECEIVE_GARBAGE
    }

    private final Type type;
    private final String actorId;
    private final long sequence;
    private final ItemUse itemUse;
    private final Garbage garbage;

    public GameAction(Type type, String actorId, long sequence) {
        this(type, actorId, sequence, null, null);
    }

    public GameAction(Type type, String actorId, long sequence, ItemUse itemUse) {
        this(type, actorId, sequence, itemUse, null);
    }

    private GameAction(Type type, String actorId, long sequence, ItemUse itemUse, Garbage garbage) {
        if (itemUse != null && type != Type.USE_ITEM) {
            throw new IllegalArgumentException("Item payload requires USE_ITEM");
        }
        if (garbage != null && type != Type.RECEIVE_GARBAGE) {
            throw new IllegalArgumentException("Garbage payload requires RECEIVE_GARBAGE");
        }
        this.type = type;
        this.actorId = actorId;
        this.sequence = sequence;
        this.itemUse = itemUse;
        this.garbage = garbage;
    }

    public static GameAction garbage(String actorId, long sequence, Garbage payload) {
        if (payload == null) throw new IllegalArgumentException("Garbage payload is required");
        return new GameAction(Type.RECEIVE_GARBAGE, actorId, sequence, null, payload);
    }

    public Type getType() { return type; }
    public String getActorId() { return actorId; }
    public long getSequence() { return sequence; }
    public ItemUse getItemUse() { return itemUse; }
    public Garbage getGarbage() { return garbage; }

    public static final class Garbage {
        private final int lines;
        private final int holeColumn;

        public Garbage(int lines, int holeColumn) {
            if (lines <= 0 || lines > 22 || holeColumn < 0 || holeColumn >= 10) {
                throw new IllegalArgumentException("Garbage needs 1..22 lines and a hole in 0..9");
            }
            this.lines = lines;
            this.holeColumn = holeColumn;
        }

        public int getLines() { return lines; }
        public int getHoleColumn() { return holeColumn; }
    }

    /** 아이템 사용 명령 데이터 및 전투 규칙 계층의 효과 적용 책임 */
    public static final class ItemUse {
        private final String itemId;
        private final String targetActorId;
        private final TargetCell targetCell;

        public ItemUse(String itemId, String targetActorId) {
            this(itemId, targetActorId, null);
        }

        public ItemUse(String itemId, String targetActorId, TargetCell targetCell) {
            if (itemId == null || itemId.trim().isEmpty()
                    || targetActorId == null || targetActorId.trim().isEmpty()) {
                throw new IllegalArgumentException("Item ID and target actor ID are required");
            }
            this.itemId = itemId;
            this.targetActorId = targetActorId;
            this.targetCell = targetCell;
        }

        public String getItemId() { return itemId; }
        public String getTargetActorId() { return targetActorId; }
        public TargetCell getTargetCell() { return targetCell; }
    }

    public static final class TargetCell {
        private final int x;
        private final int y;

        public TargetCell(int x, int y) {
            if (x < 0 || y < 0) throw new IllegalArgumentException("Target cell must be non-negative");
            this.x = x;
            this.y = y;
        }

        public int getX() { return x; }
        public int getY() { return y; }
    }
}
