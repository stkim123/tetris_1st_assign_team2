package kr.ac.jbnu.se.tetris.battle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import kr.ac.jbnu.se.tetris.core.GameState;

/** 전투 스냅샷에 포함되는 참가자 한 명의 불변 상태 */
public final class ParticipantState {
    private final String id;
    private final String name;
    private final int hp;
    private final int maxHp;
    private final GameState gameState;
    private final boolean eliminated;
    private final String characterId;
    private final int itemSlots;
    private final List<String> items;
    private final List<Integer> itemCharges;
    private final int fever;
    private final long feverRemainingMillis;
    private final long timeWarpRemainingMillis;
    private final int pendingGarbageLines;
    private final long garbageWaitRemainingMillis;
    private final int gravityMillis;
    private final int maxCombo;
    private final int totalDamage;

    ParticipantState(String id, String name, int hp, int maxHp, GameState gameState,
                     boolean eliminated) {
        this(id, name, hp, maxHp, gameState, eliminated, "student", 3,
                Collections.<String>emptyList(), Collections.<Integer>emptyList(),
                0, 0, 0, 0, 0, 500, 0, 0);
    }

    ParticipantState(String id, String name, int hp, int maxHp, GameState gameState,
            boolean eliminated, String characterId, int itemSlots, List<String> items,
            int fever, long feverRemainingMillis, long timeWarpRemainingMillis,
            int pendingGarbageLines, long garbageWaitRemainingMillis, int gravityMillis,
            int maxCombo, int totalDamage) {
        this(id, name, hp, maxHp, gameState, eliminated, characterId, itemSlots, items,
                Collections.nCopies(items.size(), 1), fever, feverRemainingMillis,
                timeWarpRemainingMillis, pendingGarbageLines, garbageWaitRemainingMillis,
                gravityMillis, maxCombo, totalDamage);
    }

    ParticipantState(String id, String name, int hp, int maxHp, GameState gameState,
            boolean eliminated, String characterId, int itemSlots, List<String> items,
            List<Integer> itemCharges, int fever, long feverRemainingMillis,
            long timeWarpRemainingMillis, int pendingGarbageLines,
            long garbageWaitRemainingMillis, int gravityMillis, int maxCombo, int totalDamage) {
        this.id = id;
        this.name = name;
        this.hp = hp;
        this.maxHp = maxHp;
        this.gameState = gameState;
        this.eliminated = eliminated;
        this.characterId = characterId;
        this.itemSlots = itemSlots;
        this.items = Collections.unmodifiableList(new ArrayList<String>(items));
        this.itemCharges = Collections.unmodifiableList(new ArrayList<Integer>(itemCharges));
        this.fever = fever;
        this.feverRemainingMillis = feverRemainingMillis;
        this.timeWarpRemainingMillis = timeWarpRemainingMillis;
        this.pendingGarbageLines = pendingGarbageLines;
        this.garbageWaitRemainingMillis = garbageWaitRemainingMillis;
        this.gravityMillis = gravityMillis;
        this.maxCombo = maxCombo;
        this.totalDamage = totalDamage;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public GameState getGameState() { return gameState; }
    public boolean isEliminated() { return eliminated; }
    public String getCharacterId() { return characterId; }
    public int getItemSlots() { return itemSlots; }
    public List<String> getItems() { return items; }
    /** getItems()와 인덱스를 공유하는 슬롯별 남은 사용 횟수. */
    public List<Integer> getItemCharges() { return itemCharges; }
    public int getFever() { return fever; }
    public boolean isFeverActive() { return feverRemainingMillis > 0; }
    public long getFeverRemainingMillis() { return feverRemainingMillis; }
    public long getTimeWarpRemainingMillis() { return timeWarpRemainingMillis; }
    public int getPendingGarbageLines() { return pendingGarbageLines; }
    public long getGarbageWaitRemainingMillis() { return garbageWaitRemainingMillis; }
    public int getGravityMillis() { return gravityMillis; }
    public int getMaxCombo() { return maxCombo; }
    public int getTotalDamage() { return totalDamage; }
}
