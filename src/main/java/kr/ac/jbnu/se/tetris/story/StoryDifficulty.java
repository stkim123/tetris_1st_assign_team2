package kr.ac.jbnu.se.tetris.story;

/** 한 스토리 전투의 난이도 수치. 스토리 콘텐츠는 AI 구현에 의존하지 않는다. */
public final class StoryDifficulty {
    private final int playerGravityMillis;
    private final int monsterDelayMillis;
    private final int maxSearchStates;
    private final int budgetMillis;
    private final double attackMultiplier;
    private final int extraGarbageLines;
    private final int monsterItemLevel;

    public StoryDifficulty(int playerGravityMillis, int monsterDelayMillis,
            int maxSearchStates, int budgetMillis, double attackMultiplier,
            int extraGarbageLines, int monsterItemLevel) {
        if (playerGravityMillis < 100 || playerGravityMillis > 2000
                || monsterDelayMillis < 100 || monsterDelayMillis > 10000
                || maxSearchStates < 1 || maxSearchStates > 10000
                || budgetMillis < 1 || budgetMillis > 1000
                || !Double.isFinite(attackMultiplier) || attackMultiplier < 1.0
                || attackMultiplier > 4.0 || extraGarbageLines < 0
                || extraGarbageLines > 4 || monsterItemLevel < 0 || monsterItemLevel > 5) {
            throw new IllegalArgumentException("Invalid story difficulty");
        }
        this.playerGravityMillis = playerGravityMillis;
        this.monsterDelayMillis = monsterDelayMillis;
        this.maxSearchStates = maxSearchStates;
        this.budgetMillis = budgetMillis;
        this.attackMultiplier = attackMultiplier;
        this.extraGarbageLines = extraGarbageLines;
        this.monsterItemLevel = monsterItemLevel;
    }

    public int getPlayerGravityMillis() { return playerGravityMillis; }
    public int getMonsterDelayMillis() { return monsterDelayMillis; }
    public int getMaxSearchStates() { return maxSearchStates; }
    public int getBudgetMillis() { return budgetMillis; }
    public double getAttackMultiplier() { return attackMultiplier; }
    public int getExtraGarbageLines() { return extraGarbageLines; }
    public int getMonsterItemLevel() { return monsterItemLevel; }
}
