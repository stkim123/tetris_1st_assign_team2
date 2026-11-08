package kr.ac.jbnu.se.tetris.item;

/** 아이템 정의 계약 및 실제 효과·소비 규칙의 전투 담당 확장 경계 */
public final class ItemSpec {
    public enum Target { SELF, OPPONENT }
    public enum Category { H, L, G }
    private final String id;
    private final String effectId;
    private final Target target;
    public ItemSpec(String id, String effectId, Target target) {
        if (id == null || id.trim().isEmpty() || effectId == null || effectId.trim().isEmpty() || target == null) {
            throw new IllegalArgumentException("Item requires identity, effect, and target");
        }
        this.id = id; this.effectId = effectId; this.target = target;
    }
    public String getId() { return id; }
    public String getEffectId() { return effectId; }
    public Target getTarget() { return target; }

    /** 기획 9.3: H=HP, L=라인, G=기타. */
    public static Category categoryOf(String id) {
        if ("damage_boost".equals(id) || "heal".equals(id) || "shield".equals(id)) return Category.H;
        if ("garbage_bomb".equals(id) || "line_cleaner".equals(id)) return Category.L;
        if ("fever_charge".equals(id) || "time_warp".equals(id)
                || "nullify".equals(id)) return Category.G;
        throw new IllegalArgumentException("Unknown item: " + id);
    }

    /** 획득 시 한 슬롯에 들어가는 사용 횟수. */
    public static int initialChargesOf(String id) {
        categoryOf(id);
        return "garbage_bomb".equals(id) || "heal".equals(id)
                || "line_cleaner".equals(id) ? 2 : 1;
    }
}
