package kr.ac.jbnu.se.tetris.ai;

/** 몬스터 패턴과 독립적인 난이도 축을 한 전투에 전달하는 불변 설정. */
public final class DifficultyProfile {
    public enum SpecialPattern { FIXED, ADAPTIVE, BOSS_PHASE }
    public static final class AiStrength {
        private final int maxSearchStates;
        private final int budgetMillis;
        public AiStrength(int maxSearchStates, int budgetMillis) {
            if (maxSearchStates < 1 || maxSearchStates > 10000 || budgetMillis < 1 || budgetMillis > 1000)
                throw new IllegalArgumentException("Invalid AI strength");
            this.maxSearchStates = maxSearchStates;
            this.budgetMillis = budgetMillis;
        }
        public int getMaxSearchStates() { return maxSearchStates; }
        public int getBudgetMillis() { return budgetMillis; }
    }
    public static final class AttackStrength {
        private final double damageBuff;
        private final int extraGarbageLines;
        public AttackStrength(double damageBuff, int extraGarbageLines) {
            if (!Double.isFinite(damageBuff) || damageBuff < 0 || damageBuff > 3
                    || extraGarbageLines < 0 || extraGarbageLines > 4)
                throw new IllegalArgumentException("Invalid attack strength");
            this.damageBuff = damageBuff;
            this.extraGarbageLines = extraGarbageLines;
        }
        public double getDamageBuff() { return damageBuff; }
        public int getExtraGarbageLines() { return extraGarbageLines; }
    }

    private final int level;
    private final int monsterHp;
    private final int playerGravityMillis;
    private final int monsterDelayMillis;
    private final AiStrength aiStrength;
    private final AttackStrength attackStrength;
    private final SpecialPattern specialPattern;
    private final String aiProfileId;
    private final int monsterItemLevel;

    public DifficultyProfile(int level, int monsterHp, int playerGravityMillis,
            int monsterDelayMillis, AiStrength aiStrength, AttackStrength attackStrength,
            SpecialPattern specialPattern, String aiProfileId) {
        this(level, monsterHp, playerGravityMillis, monsterDelayMillis, aiStrength,
                attackStrength, specialPattern, aiProfileId, 5);
    }

    public DifficultyProfile(int level, int monsterHp, int playerGravityMillis,
            int monsterDelayMillis, AiStrength aiStrength, AttackStrength attackStrength,
            SpecialPattern specialPattern, String aiProfileId, int monsterItemLevel) {
        if (level < 1 || level > 9 || monsterHp < 1 || monsterHp > 10000
                || playerGravityMillis < 100 || playerGravityMillis > 2000
                || monsterDelayMillis < 100 || monsterDelayMillis > 10000
                || aiStrength == null || attackStrength == null || specialPattern == null
                || aiProfileId == null || aiProfileId.trim().isEmpty()
                || monsterItemLevel < 0 || monsterItemLevel > 5)
            throw new IllegalArgumentException("Invalid difficulty profile");
        this.level = level; this.monsterHp = monsterHp;
        this.playerGravityMillis = playerGravityMillis;
        this.monsterDelayMillis = monsterDelayMillis;
        this.aiStrength = aiStrength; this.attackStrength = attackStrength;
        this.specialPattern = specialPattern; this.aiProfileId = aiProfileId;
        this.monsterItemLevel = monsterItemLevel;
    }
    public int getLevel() { return level; }
    public int getMonsterHp() { return monsterHp; }
    public int getPlayerGravityMillis() { return playerGravityMillis; }
    public int getMonsterDelayMillis() { return monsterDelayMillis; }
    public AiStrength getAiStrength() { return aiStrength; }
    public AttackStrength getAttackStrength() { return attackStrength; }
    public SpecialPattern getSpecialPattern() { return specialPattern; }
    public String getAiProfileId() { return aiProfileId; }
    public int getMonsterItemLevel() { return monsterItemLevel; }
    public DifficultyProfile withEncounter(int hp, SpecialPattern pattern, String profileId) {
        return new DifficultyProfile(level, hp, playerGravityMillis, monsterDelayMillis,
                aiStrength, attackStrength, pattern, profileId, monsterItemLevel);
    }
    public AIProfile toAiProfile() {
        AIProfile base = AIProfileCatalog.loadDefault().get(aiProfileId);
        return new AIProfile(base.getProfileId(), specialPattern.name(), base.getBaseWeights(),
                monsterDelayMillis, aiStrength.getMaxSearchStates(), aiStrength.getBudgetMillis(),
                base.getMaxWeightDeltaRatio());
    }
}
