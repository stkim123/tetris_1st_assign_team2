package kr.ac.jbnu.se.tetris.story;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kr.ac.jbnu.se.tetris.ai.DifficultyProfileCatalog;

/** 3개 과정과 9레벨 순서, 임의 패턴, 설정 오류를 검증한다. */
public final class StageCatalogTest {
    public static void main(String[] args) throws Exception {
        defaultCampaignIsCompleteAndImmutable();
        malformedConfigurationIsRejected();
    }

    private static void defaultCampaignIsCompleteAndImmutable() {
        StageCatalog catalog = StageCatalog.loadDefault();
        List<Stage> stages = catalog.getStages();
        check(stages.size() == 3, "대학교·졸업·취업 세 과정");
        check("university".equals(stages.get(0).getId())
                && "graduation".equals(stages.get(1).getId())
                && "employment".equals(stages.get(2).getId()), "과정 순서");
        MonsterTier[] expected = {MonsterTier.NORMAL, MonsterTier.ELITE, MonsterTier.BOSS,
            MonsterTier.NORMAL, MonsterTier.ELITE, MonsterTier.BOSS,
            MonsterTier.ELITE, MonsterTier.ELITE, MonsterTier.BOSS};
        Set<String> ids = new HashSet<String>();
        int level = 0, previousHp = 0;
        for (Stage stage : stages) {
            check(stage.getEncounters().size() == 3, "과정마다 세 전투");
            for (MonsterSpec monster : stage.getEncounters()) {
                level++;
                check(monster.getTier() == expected[level - 1], "Lv " + level + " 패턴");
                check(monster.getId().equals("level_" + level + "_monster")
                        && ids.add(monster.getId()), "Lv " + level + " 고유 ID");
                check(monster.getHp() > previousHp, "레벨별 체력 증가");
                check(DifficultyProfileCatalog.forEncounter(monster).getLevel() == level,
                        "전투 순서가 해당 난이도 레벨에 대응");
                previousHp = monster.getHp();
            }
        }
        check(level == 9, "전투 총 9회");
        check(catalog.getStage("employment") == stages.get(2), "ID 조회");
        check(catalog.getStage("missing") == null, "없는 ID는 null");
        check(stages.get(0).getEncounters().get(0).getDifficulty().getPlayerGravityMillis() == 450
                && stages.get(0).getEncounters().get(0).getDifficulty().getExtraGarbageLines() == 0
                && stages.get(2).getEncounters().get(2).getDifficulty().getMonsterItemLevel() == 5,
                "단일 스토리 설정 파일에서 첫·마지막 전투 난이도 로드");
        expectUnsupported(() -> stages.clear());
        expectUnsupported(() -> stages.get(0).getEncounters().clear());
    }

    private static void malformedConfigurationIsRejected() throws Exception {
        String valid = defaultSettings();
        expectInvalid(() -> load(valid.replace("stage.count=3", "stage.count=0")));
        expectInvalid(() -> load(valid.replace("stage.1.encounter.1.hp=30",
                "stage.1.encounter.1.hp=-1")));
        expectInvalid(() -> load(valid.replace("stage.1.encounter.1.tier=NORMAL",
                "stage.1.encounter.1.tier=UNKNOWN")));
        expectInvalid(() -> load(valid.replace("stage.1.encounter.2.id=level_2_monster",
                "stage.1.encounter.2.id=level_1_monster")));
        expectInvalid(() -> load(valid + "\nstage.1.encounter.1.typo=x\n"));
        expectInvalid(() -> load(valid + "\nstage.count=3\n"));
        expectInvalid(() -> load(valid.replace("stage.3.encounter.3.aiProfile=boss_default", "")));
        expectInvalid(() -> load(valid.replace("stage.1.encounter.1.maxSearchStates=150", "")));
        expectInvalid(() -> load(valid.replace("stage.1.encounter.1.playerGravityMillis=450",
                "stage.1.encounter.1.playerGravityMillis=99")));
        expectInvalid(() -> load(valid.replace("stage.1.encounter.1.monsterDelayMillis=3500",
                "stage.1.encounter.1.monsterDelayMillis=10001")));
        expectInvalid(() -> load(valid.replace("stage.1.encounter.1.budgetMillis=12",
                "stage.1.encounter.1.budgetMillis=0")));
        expectInvalid(() -> load(valid.replace("stage.1.encounter.1.attackMultiplier=1.00",
                "stage.1.encounter.1.attackMultiplier=NaN")));
        expectInvalid(() -> load(valid.replace("stage.1.encounter.1.extraGarbageLines=0", "")));
        expectInvalid(() -> load(valid.replace("stage.1.encounter.1.extraGarbageLines=0",
                "stage.1.encounter.1.extraGarbageLines=5")));
        expectInvalid(() -> load(valid.replace("stage.1.encounter.1.monsterItemLevel=0",
                "stage.1.encounter.1.monsterItemLevel=6")));
        expectInvalid(() -> load(valid + "\nstage.1.encounter.1.maxSearchStates=150\n"));
    }

    private static StageCatalog load(String contents) {
        try { return StageCatalog.load(new StringReader(contents)); }
        catch (IOException error) { throw new AssertionError(error); }
    }
    private static String defaultSettings() throws IOException {
        InputStream stream = StageCatalogTest.class.getResourceAsStream("/story/stages.properties");
        if (stream == null) throw new AssertionError("기본 설정 파일이 없다");
        try (InputStream input = stream; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return new String(bytes.toByteArray(), "UTF-8");
        }
    }
    private static void expectInvalid(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("설정 오류를 거부해야 한다");
    }
    private static void expectUnsupported(Runnable action) {
        try { action.run(); }
        catch (UnsupportedOperationException expected) { return; }
        throw new AssertionError("불변 목록을 수정할 수 없어야 한다");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
