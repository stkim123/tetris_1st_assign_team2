package kr.ac.jbnu.se.tetris.battle;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/** 클래스패스 battle/balance.properties의 로딩·검증과 전투 수치의 불변 조회 경계 */
public final class BattleBalance {
    private static final String RESOURCE = "battle/balance.properties";
    // 줄 수·콤보별 표의 항목 수, 줄 표는 0~4줄, 콤보 표는 0~3콤보와 4콤보 이상
    private static final int TABLE_ENTRIES = 5;
    // int 오버플로와 비현실적인 값을 막는 일반 수치 상한
    private static final int MAX_VALUE = 1000;
    private static final int MAX_MULTIPLIER = 10;
    // GameAction.Garbage 계약의 가비지 한 건 최대 줄 수
    private static final int MAX_GARBAGE_PER_ATTACK = 22;
    // 목록에 없는 키는 오타로 간주해 거절
    private static final Set<String> KEYS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "damage.lines", "garbage.lines", "damage.combo", "garbage.combo",
            "damage.perfect", "garbage.perfect",
            "damage.tspin.multiplier", "garbage.tspin.bonus")));

    // 지운 줄 수와 콤보를 인덱스로 하는 표, 길이 5
    private final int[] damageByLines;
    private final int[] garbageByLines;
    private final int[] comboDamage;
    private final int[] comboGarbage;
    private final int perfectClearDamage;
    private final int perfectClearGarbage;
    private final int tSpinDamageMultiplier;
    private final int tSpinGarbageBonus;

    /** 검증을 마친 값으로만 생성하는 비공개 생성자, 배열은 복사해 보관 */
    private BattleBalance(int[] damageByLines, int[] garbageByLines, int[] comboDamage, int[] comboGarbage,
                          int perfectClearDamage, int perfectClearGarbage,
                          int tSpinDamageMultiplier, int tSpinGarbageBonus) {
        this.damageByLines = damageByLines.clone();
        this.garbageByLines = garbageByLines.clone();
        this.comboDamage = comboDamage.clone();
        this.comboGarbage = comboGarbage.clone();
        this.perfectClearDamage = perfectClearDamage;
        this.perfectClearGarbage = perfectClearGarbage;
        this.tSpinDamageMultiplier = tSpinDamageMultiplier;
        this.tSpinGarbageBonus = tSpinGarbageBonus;
    }

    /** 실행 파일에 포함된 기본 설정 로딩과 누락 시 설정 오류 거절 */
    public static BattleBalance loadDefault() {
        InputStream input = BattleBalance.class.getClassLoader().getResourceAsStream(RESOURCE);
        if (input == null) throw new IllegalStateException("Missing " + RESOURCE);
        return load(input);
    }

    /** 제공한 스트림의 소유권 이전과 설정 오류의 명시적 거절 */
    public static BattleBalance load(InputStream input) {
        if (input == null) throw new IllegalArgumentException("Battle balance input is required");
        Properties properties = new Properties();
        // UTF-8로 읽어야 한글 주석이 깨지지 않음
        try (InputStream stream = input;
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read battle balance", failure);
        }
        // 키 이름 오타가 기본값으로 조용히 넘어가지 않도록 알 수 없는 키 먼저 거절
        for (String key : properties.stringPropertyNames()) {
            if (!KEYS.contains(key)) throw new IllegalArgumentException("Unknown battle balance key: " + key);
        }
        int[] damage = table(properties, "damage.lines", true);
        int[] garbage = table(properties, "garbage.lines", true);
        int[] comboDamage = table(properties, "damage.combo", false);
        int[] comboGarbage = table(properties, "garbage.combo", false);
        int perfectDamage = integer(properties, "damage.perfect", 0, MAX_VALUE);
        int perfectGarbage = integer(properties, "garbage.perfect", 0, MAX_GARBAGE_PER_ATTACK);
        // 배율 0은 T-Spin 피해를 없애므로 최소 1
        int multiplier = integer(properties, "damage.tspin.multiplier", 1, MAX_MULTIPLIER);
        int tSpinBonus = integer(properties, "garbage.tspin.bonus", 0, MAX_VALUE);
        // 가장 큰 가비지 공격이 22줄을 넘으면 전투 중 GameAction.Garbage 생성이 실패하므로 로딩 시점에 거절
        int largestBase = 0;
        for (int value : garbage) largestBase = Math.max(largestBase, value);
        int largestCombo = 0;
        for (int value : comboGarbage) largestCombo = Math.max(largestCombo, value);
        if (largestBase + largestCombo + tSpinBonus > MAX_GARBAGE_PER_ATTACK) {
            throw new IllegalArgumentException("Largest garbage attack exceeds " + MAX_GARBAGE_PER_ATTACK
                    + " lines: check garbage.lines, garbage.combo, garbage.tspin.bonus");
        }
        return new BattleBalance(damage, garbage, comboDamage, comboGarbage, perfectDamage, perfectGarbage,
                multiplier, tSpinBonus);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing battle balance property: " + key);
        }
        return value.trim();
    }

    /** 정수 형식과 minimum~maximum 범위(양 끝 포함)의 확인 */
    private static int integer(Properties properties, String key, int minimum, int maximum) {
        int value;
        try {
            value = Integer.parseInt(required(properties, key));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid integer battle balance property: " + key, invalid);
        }
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Battle balance property out of range "
                    + minimum + ".." + maximum + ": " + key);
        }
        return value;
    }

    /** 쉼표로 구분한 5개 항목 표의 변환, 줄 수 표는 첫 항목(0줄)이 반드시 0 */
    private static int[] table(Properties properties, String key, boolean firstMustBeZero) {
        // limit -1로 끝이 비어 있는 "0,4,8,12," 같은 값도 빈 항목으로 세어 거절
        String[] parts = required(properties, key).split(",", -1);
        if (parts.length != TABLE_ENTRIES) {
            throw new IllegalArgumentException(key + " needs exactly " + TABLE_ENTRIES + " entries");
        }
        int[] result = new int[TABLE_ENTRIES];
        for (int i = 0; i < TABLE_ENTRIES; i++) {
            // 항목 하나를 임시 Properties에 넣어 integer()의 형식·범위 검증과 오류 메시지를 재사용
            Properties single = new Properties();
            single.setProperty(key, parts[i]);
            result[i] = integer(single, key, 0, MAX_VALUE);
        }
        if (firstMustBeZero && result[0] != 0) {
            throw new IllegalArgumentException(key + " first entry (no lines) must be 0");
        }
        return result;
    }

    /** 한 번에 지운 줄 수(1~4)별 기본 피해 */
    public int damageForLines(int lines) { return damageByLines[checkLines(lines)]; }

    /** 한 번에 지운 줄 수(1~4)별 기본 가비지 줄 수 */
    public int garbageForLines(int lines) { return garbageByLines[checkLines(lines)]; }

    /** 콤보별 추가 피해, 콤보 없음(음수)은 0이고 4콤보 이상은 마지막 항목 */
    public int comboDamage(int combo) { return combo < 0 ? 0 : comboDamage[Math.min(combo, TABLE_ENTRIES - 1)]; }

    /** 콤보별 추가 가비지 줄 수, 콤보 없음(음수)은 0이고 4콤보 이상은 마지막 항목 */
    public int comboGarbage(int combo) { return combo < 0 ? 0 : comboGarbage[Math.min(combo, TABLE_ENTRIES - 1)]; }

    public int getPerfectClearDamage() { return perfectClearDamage; }
    public int getPerfectClearGarbage() { return perfectClearGarbage; }
    public int getTSpinDamageMultiplier() { return tSpinDamageMultiplier; }
    public int getTSpinGarbageBonus() { return tSpinGarbageBonus; }

    private static int checkLines(int lines) {
        if (lines < 1 || lines > 4) throw new IllegalArgumentException("A placement may clear 1 to 4 lines");
        return lines;
    }
}
