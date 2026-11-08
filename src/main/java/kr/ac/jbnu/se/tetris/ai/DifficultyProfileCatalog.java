package kr.ac.jbnu.se.tetris.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import kr.ac.jbnu.se.tetris.story.MonsterSpec;
import kr.ac.jbnu.se.tetris.story.MonsterTier;
import kr.ac.jbnu.se.tetris.story.Stage;
import kr.ac.jbnu.se.tetris.story.StageCatalog;
import kr.ac.jbnu.se.tetris.story.StoryDifficulty;

/** 아홉 스토리 전투의 모든 레벨 수치는 story/stages.properties에서 읽는다. */
public final class DifficultyProfileCatalog {
    private static final List<MonsterSpec> ENCOUNTERS = encounters(StageCatalog.loadDefault());

    private DifficultyProfileCatalog() { }

    /** 과거 숫자 단계 조회 API. 실제 AI는 maxSearchStates/budgetMillis를 사용한다. */
    @Deprecated
    public static int getAiTier(int level) {
        int states = encounterAt(level).getDifficulty().getMaxSearchStates();
        return states <= 150 ? 1 : states <= 400 ? 2 : 3;
    }

    public static MonsterTier getPattern(int level) { return encounterAt(level).getTier(); }
    public static double getAttackMultiplier(int level) {
        return encounterAt(level).getDifficulty().getAttackMultiplier();
    }

    public static DifficultyProfile level(int level) {
        MonsterSpec encounter = encounterAt(level);
        StoryDifficulty tuning = encounter.getDifficulty();
        return new DifficultyProfile(level, encounter.getHp(), tuning.getPlayerGravityMillis(),
                tuning.getMonsterDelayMillis(),
                new DifficultyProfile.AiStrength(tuning.getMaxSearchStates(), tuning.getBudgetMillis()),
                new DifficultyProfile.AttackStrength(tuning.getAttackMultiplier() - 1.0,
                        tuning.getExtraGarbageLines()),
                special(encounter.getTier()), encounter.getAiProfileId(),
                tuning.getMonsterItemLevel());
    }

    public static DifficultyProfile forEncounter(MonsterSpec monster) {
        if (monster == null) throw new IllegalArgumentException("Monster required");
        for (int index = 0; index < ENCOUNTERS.size(); index++) {
            if (ENCOUNTERS.get(index).getId().equals(monster.getId())) return level(index + 1);
        }
        throw new IllegalArgumentException("Unknown story monster: " + monster.getId());
    }

    private static MonsterSpec encounterAt(int level) {
        if (level < 1 || level > ENCOUNTERS.size()) {
            throw new IllegalArgumentException("Level must be 1..9");
        }
        return ENCOUNTERS.get(level - 1);
    }

    private static List<MonsterSpec> encounters(StageCatalog catalog) {
        List<MonsterSpec> monsters = new ArrayList<MonsterSpec>(9);
        for (Stage stage : catalog.getStages()) monsters.addAll(stage.getEncounters());
        return Collections.unmodifiableList(monsters);
    }

    private static DifficultyProfile.SpecialPattern special(MonsterTier tier) {
        return tier == MonsterTier.NORMAL ? DifficultyProfile.SpecialPattern.FIXED
                : tier == MonsterTier.ELITE ? DifficultyProfile.SpecialPattern.ADAPTIVE
                : DifficultyProfile.SpecialPattern.BOSS_PHASE;
    }
}
