package kr.ac.jbnu.se.tetris.story;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** UTF-8 설정 파일 검증 및 스테이지 목록 변환 */
public final class StageCatalog {
    private final List<Stage> stages;

    private StageCatalog(List<Stage> stages) {
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("At least one stage is required");
        }
        Set<String> stageIds = new HashSet<String>();
        Set<String> monsterIds = new HashSet<String>();
        for (Stage stage : stages) {
            if (stage == null || !stageIds.add(stage.getId())) {
                throw new IllegalArgumentException("Stage IDs must be unique");
            }
            for (MonsterSpec monster : stage.getEncounters()) {
                if (!monsterIds.add(monster.getId())) {
                    throw new IllegalArgumentException("Monster IDs must be unique");
                }
            }
        }
        this.stages = Collections.unmodifiableList(new ArrayList<Stage>(stages));
    }

    /** 배포 패키지에 포함된 기본 캠페인 읽기 */
    public static StageCatalog loadDefault() {
        InputStream stream = StageCatalog.class.getResourceAsStream("/story/stages.properties");
        if (stream == null) throw new IllegalStateException("Missing story/stages.properties");
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            StageCatalog catalog = load(reader);
            int encounters = 0;
            for (Stage stage : catalog.getStages()) {
                for (MonsterSpec monster : stage.getEncounters()) {
                    if (monster.getDifficulty() == null) {
                        throw new IllegalStateException("Missing difficulty for " + monster.getId());
                    }
                    encounters++;
                }
            }
            if (encounters != 9) throw new IllegalStateException("Default campaign must have nine encounters");
            return catalog;
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read story/stages.properties", error);
        }
    }

    /** 호출자의 Reader 소유 및 잘못된 키·누락 값 즉시 거부 */
    public static StageCatalog load(Reader reader) throws IOException {
        if (reader == null) throw new IllegalArgumentException("Reader is required");
        Properties config = new StrictProperties();
        config.load(reader);
        Set<String> knownKeys = new HashSet<String>();
        int count = integer(config, knownKeys, "stage.count", 1, 100);
        List<Stage> stages = new ArrayList<Stage>(count);
        for (int index = 1; index <= count; index++) {
            String stageKey = "stage." + index;
            String stageId = required(config, knownKeys, stageKey + ".id");
            String stageName = required(config, knownKeys, stageKey + ".name");
            List<MonsterSpec> encounters = new ArrayList<MonsterSpec>(3);
            if (config.containsKey(stageKey + ".encounter.count")) {
                int encounterCount = integer(config, knownKeys,
                        stageKey + ".encounter.count", 1, 9);
                for (int number = 1; number <= encounterCount; number++) {
                    String encounterKey = stageKey + ".encounter." + number;
                    MonsterTier tier;
                    try {
                        tier = MonsterTier.valueOf(required(config, knownKeys,
                                encounterKey + ".tier").toUpperCase(java.util.Locale.ROOT));
                    } catch (IllegalArgumentException invalid) {
                        throw new IllegalArgumentException("Invalid monster pattern: " + encounterKey,
                                invalid);
                    }
                    encounters.add(new MonsterSpec(
                            required(config, knownKeys, encounterKey + ".id"),
                            required(config, knownKeys, encounterKey + ".name"), tier,
                            integer(config, knownKeys, encounterKey + ".hp", 1, 10000),
                            required(config, knownKeys, encounterKey + ".aiProfile"),
                            difficulty(config, knownKeys, encounterKey)));
                }
            } else {
                // 이전 3전투 설정 파일도 읽되, 배포 기본 캠페인은 1레벨 1전투다.
                for (MonsterTier tier : MonsterTier.values()) {
                    String encounterKey = stageKey + "." +
                            tier.name().toLowerCase(java.util.Locale.ROOT);
                    encounters.add(new MonsterSpec(
                            required(config, knownKeys, encounterKey + ".id"),
                            required(config, knownKeys, encounterKey + ".name"), tier,
                            integer(config, knownKeys, encounterKey + ".hp", 1, 10000),
                            required(config, knownKeys, encounterKey + ".aiProfile")));
                }
            }
            stages.add(new Stage(stageId, stageName, encounters));
        }
        for (String key : config.stringPropertyNames()) {
            if (!knownKeys.contains(key)) throw new IllegalArgumentException("Unknown story property: " + key);
        }
        return new StageCatalog(stages);
    }

    private static String required(Properties config, Set<String> knownKeys, String key) {
        knownKeys.add(key);
        String value = config.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing story property: " + key);
        }
        return value.trim();
    }

    private static int integer(Properties config, Set<String> knownKeys, String key, int min, int max) {
        String value = required(config, knownKeys, key);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed >= min && parsed <= max) return parsed;
        } catch (NumberFormatException ignored) {
            // 숫자 형식 오류와 범위 오류의 동일한 설정 오류 처리
        }
        throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
    }

    private static StoryDifficulty difficulty(Properties config, Set<String> knownKeys, String prefix) {
        String[] suffixes = {"playerGravityMillis", "monsterDelayMillis", "maxSearchStates",
                "budgetMillis", "attackMultiplier", "extraGarbageLines", "monsterItemLevel"};
        boolean present = false;
        for (String suffix : suffixes) present |= config.containsKey(prefix + "." + suffix);
        // 구형 스토리 진행 테스트의 콘텐츠 전용 설정은 허용한다. 수치가 하나라도 있으면 전체 필수.
        if (!present) return null;
        return new StoryDifficulty(
                integer(config, knownKeys, prefix + ".playerGravityMillis", 100, 2000),
                integer(config, knownKeys, prefix + ".monsterDelayMillis", 100, 10000),
                integer(config, knownKeys, prefix + ".maxSearchStates", 1, 10000),
                integer(config, knownKeys, prefix + ".budgetMillis", 1, 1000),
                decimal(config, knownKeys, prefix + ".attackMultiplier", 1.0, 4.0),
                integer(config, knownKeys, prefix + ".extraGarbageLines", 0, 4),
                integer(config, knownKeys, prefix + ".monsterItemLevel", 0, 5));
    }

    private static double decimal(Properties config, Set<String> knownKeys, String key,
            double min, double max) {
        String value = required(config, knownKeys, key);
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isFinite(parsed) && parsed >= min && parsed <= max) return parsed;
        } catch (NumberFormatException ignored) {
            // 숫자 형식 오류와 범위 오류의 동일한 설정 오류 처리
        }
        throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
    }

    public List<Stage> getStages() { return stages; }

    public Stage getStage(String id) {
        if (id == null) return null;
        for (Stage stage : stages) if (stage.getId().equals(id)) return stage;
        return null;
    }

    /** Properties의 중복 키 덮어쓰기를 설정 단계에서 차단 */
    private static final class StrictProperties extends Properties {
        private static final long serialVersionUID = 1L;

        @Override public synchronized Object put(Object key, Object value) {
            if (containsKey(key)) throw new IllegalArgumentException("Duplicate story property: " + key);
            return super.put(key, value);
        }
    }
}
