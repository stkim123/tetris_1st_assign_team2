package kr.ac.jbnu.se.tetris.character;

/** 공통 전투에 주입할 캐릭터 설정, 최대 HP·공격 버프·아이템 슬롯 수를 담은 불변 값 */
public final class CharacterSpec {
    /** 기본형 캐릭터, 능력치 보너스 없는 HP 100과 아이템 슬롯 3칸 */
    public static final CharacterSpec DEFAULT = new CharacterSpec("student", "대학생", 100);
    public static final int DEFAULT_ITEM_SLOTS = 3;
    private final String id;
    private final String name;
    private final int maxHp;
    private final double damageBuff;
    private final int itemSlots;

    /** 버프 없는 기본 슬롯 캐릭터, 기존 호출부와의 호환용 */
    public CharacterSpec(String id, String name, int maxHp) {
        this(id, name, maxHp, 0.0, DEFAULT_ITEM_SLOTS);
    }

    /** damageBuff는 공격 버프 합에 더해지는 값(0.5면 +50%), itemSlots는 아이템 슬롯 칸 수 */
    public CharacterSpec(String id, String name, int maxHp, double damageBuff, int itemSlots) {
        if (id == null || id.trim().isEmpty() || name == null || name.trim().isEmpty() || maxHp <= 0) {
            throw new IllegalArgumentException("Character requires identity and positive HP");
        }
        if (Double.isNaN(damageBuff) || Double.isInfinite(damageBuff) || damageBuff < 0.0) {
            throw new IllegalArgumentException("Damage buff must be finite and non-negative");
        }
        if (itemSlots < 1) throw new IllegalArgumentException("Character needs at least one item slot");
        this.id = id; this.name = name; this.maxHp = maxHp;
        this.damageBuff = damageBuff; this.itemSlots = itemSlots;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getMaxHp() { return maxHp; }
    public double getDamageBuff() { return damageBuff; }
    public int getItemSlots() { return itemSlots; }
}
