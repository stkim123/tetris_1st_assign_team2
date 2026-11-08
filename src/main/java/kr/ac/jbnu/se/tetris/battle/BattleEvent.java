package kr.ac.jbnu.se.tetris.battle;

import kr.ac.jbnu.se.tetris.core.GameEvent;

/** 전투 계층의 확정 사실과 원인 코어 이벤트를 담는 불변 기록 */
public final class BattleEvent {
    public enum Type {
        MATCH_STARTED, CORE_EVENT, DAMAGE, HP_CHANGED, GARBAGE_SENT,
        GARBAGE_RECEIVED, MATCH_FINISHED, PAUSED, RESUMED, ACTION_REJECTED,
        ITEM_ACQUIRED, ITEM_USED, ITEM_REMOVED
    }

    private final Type type;
    private final long eventId;
    private final String actorId;
    private final String targetId;
    private final int amount;
    private final String reason;
    private final GameEvent coreEvent;
    private final int itemSourceX;
    private final int itemSourceY;
    private final int itemSlotIndex;

    BattleEvent(Type type, long eventId, String actorId, String targetId, int amount,
                String reason, GameEvent coreEvent) {
        this(type, eventId, actorId, targetId, amount, reason, coreEvent, -1, -1, -1);
    }

    BattleEvent(Type type, long eventId, String actorId, String targetId, int amount,
                String reason, GameEvent coreEvent, int itemSourceX, int itemSourceY, int itemSlotIndex) {
        this.type = type;
        this.eventId = eventId;
        this.actorId = actorId;
        this.targetId = targetId;
        this.amount = amount;
        this.reason = reason;
        this.coreEvent = coreEvent;
        this.itemSourceX = itemSourceX;
        this.itemSourceY = itemSourceY;
        this.itemSlotIndex = itemSlotIndex;
    }

    /** 검증된 네트워크 이벤트의 복원 경계. */
    public static BattleEvent fromWire(Type type, long eventId, String actorId, String targetId,
            int amount, String reason, GameEvent coreEvent, int itemSourceX, int itemSourceY,
            int itemSlotIndex) {
        if (type == null || eventId <= 0 || amount < 0 || itemSourceX < -1 || itemSourceX > 9
                || itemSourceY < -1 || itemSourceY > 21 || itemSlotIndex < -1 || itemSlotIndex > 3
                || ((itemSourceX == -1) != (itemSourceY == -1)))
            throw new IllegalArgumentException("Invalid battle wire event");
        return new BattleEvent(type, eventId, actorId, targetId, amount, reason, coreEvent,
                itemSourceX, itemSourceY, itemSlotIndex);
    }

    public Type getType() { return type; }
    public long getEventId() { return eventId; }
    public String getActorId() { return actorId; }
    public String getTargetId() { return targetId; }
    public int getAmount() { return amount; }
    public String getReason() { return reason; }
    public GameEvent getCoreEvent() { return coreEvent; }
    public int getItemSourceX() { return itemSourceX; }
    public int getItemSourceY() { return itemSourceY; }
    public int getItemSlotIndex() { return itemSlotIndex; }
}
