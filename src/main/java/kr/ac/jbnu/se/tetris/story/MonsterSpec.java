package kr.ac.jbnu.se.tetris.story;

/** 콘텐츠와 AI 프로필 참조를 분리한 불변 몬스터 명세 */
public final class MonsterSpec {
    private final String id;
    private final String name;
    private final MonsterTier tier;
    private final int hp;
    private final String aiProfileId;
    private final StoryDifficulty difficulty;

    public MonsterSpec(String id, String name, MonsterTier tier, int hp, String aiProfileId) {
        this(id, name, tier, hp, aiProfileId, null);
    }

    public MonsterSpec(String id, String name, MonsterTier tier, int hp, String aiProfileId,
            StoryDifficulty difficulty) {
        if (id == null || id.trim().isEmpty() || name == null || name.trim().isEmpty()
                || tier == null || hp < 1 || hp > 10000 || aiProfileId == null
                || !aiProfileId.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid monster specification");
        }
        this.id = id; this.name = name; this.tier = tier; this.hp = hp;
        this.aiProfileId = aiProfileId; this.difficulty = difficulty;
    }
    public String getId() { return id; }
    public String getName() { return name; }
    public MonsterTier getTier() { return tier; }
    public int getHp() { return hp; }
    public String getAiProfileId() { return aiProfileId; }
    public StoryDifficulty getDifficulty() { return difficulty; }
}
