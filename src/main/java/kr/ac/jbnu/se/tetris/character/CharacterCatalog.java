package kr.ac.jbnu.se.tetris.character;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** character/characters.properties에서 기본형·공격형·방어형·유틸형 네 캐릭터를 만드는 로딩·검증 경계 */
public final class CharacterCatalog {
    private static final String RESOURCE = "character/characters.properties";
    private static final double MAX_BUFF = 5.0;
    private static final int MAX_EXTRA_SLOTS = 4;
    // 목록에 없는 키는 오타로 간주해 거절
    private static final Set<String> KEYS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "attack.damageBuff", "defense.hpBuff", "utility.extraSlots")));

    private final CharacterSpec basic;
    private final CharacterSpec attacker;
    private final CharacterSpec defender;
    private final CharacterSpec utility;

    private CharacterCatalog(double damageBuff, double hpBuff, int extraSlots) {
        CharacterSpec base = CharacterSpec.DEFAULT;
        this.basic = base;
        this.attacker = new CharacterSpec("attacker", "공격형", base.getMaxHp(), damageBuff, base.getItemSlots());
        this.defender = new CharacterSpec("defender", "방어형",
                (int) Math.round(base.getMaxHp() * (1.0 + hpBuff)), 0.0, base.getItemSlots());
        this.utility = new CharacterSpec("utility", "유틸형", base.getMaxHp(), 0.0,
                base.getItemSlots() + extraSlots);
    }

    /** 실행 파일에 포함된 기본 능력치 로딩과 누락 시 설정 오류 거절 */
    public static CharacterCatalog loadDefault() {
        InputStream input = CharacterCatalog.class.getClassLoader().getResourceAsStream(RESOURCE);
        if (input == null) throw new IllegalStateException("Missing " + RESOURCE);
        return load(input);
    }

    /** 제공한 스트림의 소유권 이전과 설정 오류의 명시적 거절 */
    public static CharacterCatalog load(InputStream input) {
        if (input == null) throw new IllegalArgumentException("Character input is required");
        Properties properties = new Properties();
        try (InputStream stream = input;
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read characters", failure);
        }
        for (String key : properties.stringPropertyNames()) {
            if (!KEYS.contains(key)) throw new IllegalArgumentException("Unknown character key: " + key);
        }
        return new CatalogBuilder(properties).build();
    }

    /** 기본형, 능력치 보너스 없음 */
    public CharacterSpec basic() { return basic; }
    /** 공격형, HP 피해 공격 버프 보유 */
    public CharacterSpec attacker() { return attacker; }
    /** 방어형, 최대 HP 증가 */
    public CharacterSpec defender() { return defender; }
    /** 유틸형, 아이템 슬롯 확장 */
    public CharacterSpec utility() { return utility; }

    /** 기본형, 공격형, 방어형, 유틸형 순서의 네 캐릭터 */
    public List<CharacterSpec> all() {
        return Collections.unmodifiableList(Arrays.asList(basic, attacker, defender, utility));
    }

    /** 속성 문자열의 형식·범위 검증 후 카탈로그를 만드는 보조 객체 */
    private static final class CatalogBuilder {
        private final Properties properties;

        CatalogBuilder(Properties properties) { this.properties = properties; }

        CharacterCatalog build() {
            double damageBuff = decimal("attack.damageBuff", 0.0, MAX_BUFF);
            double hpBuff = decimal("defense.hpBuff", 0.0, MAX_BUFF);
            int extraSlots = integer("utility.extraSlots", 0, MAX_EXTRA_SLOTS);
            return new CharacterCatalog(damageBuff, hpBuff, extraSlots);
        }

        private String required(String key) {
            String value = properties.getProperty(key);
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing character property: " + key);
            }
            return value.trim();
        }

        private double decimal(String key, double minimum, double maximum) {
            double value;
            try {
                value = Double.parseDouble(required(key));
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Invalid number character property: " + key, invalid);
            }
            if (Double.isNaN(value) || value < minimum || value > maximum) {
                throw new IllegalArgumentException("Character property out of range "
                        + minimum + ".." + maximum + ": " + key);
            }
            return value;
        }

        private int integer(String key, int minimum, int maximum) {
            int value;
            try {
                value = Integer.parseInt(required(key));
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Invalid integer character property: " + key, invalid);
            }
            if (value < minimum || value > maximum) {
                throw new IllegalArgumentException("Character property out of range "
                        + minimum + ".." + maximum + ": " + key);
            }
            return value;
        }
    }
}
