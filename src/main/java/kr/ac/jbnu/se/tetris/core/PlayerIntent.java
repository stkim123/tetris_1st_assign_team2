package kr.ac.jbnu.se.tetris.core;

/** 실행자 ID와 순번 없이 사용자 행동만 전달하는 불변 입력 */
public final class PlayerIntent {
    private final GameAction.Type type;
    private final GameAction.ItemUse itemUse;

    public PlayerIntent(GameAction.Type type) { this(type, null); }

    public PlayerIntent(GameAction.Type type, GameAction.ItemUse itemUse) {
        if (type == null || type == GameAction.Type.START || type == GameAction.Type.PAUSE
                || type == GameAction.Type.RESUME || type == GameAction.Type.GRAVITY_TICK
                || type == GameAction.Type.RECEIVE_GARBAGE
                || (type == GameAction.Type.USE_ITEM) != (itemUse != null)) {
            throw new IllegalArgumentException("Invalid player intent or item payload");
        }
        this.type = type;
        this.itemUse = itemUse;
    }

    public GameAction.Type getType() { return type; }
    public GameAction.ItemUse getItemUse() { return itemUse; }
}
