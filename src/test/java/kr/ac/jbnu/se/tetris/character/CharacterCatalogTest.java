package kr.ac.jbnu.se.tetris.character;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** characters.properties의 네 캐릭터 값, 수치 변경 반영, 잘못된 설정의 거절을 확인 */
public final class CharacterCatalogTest {
    private static final String VALID = "attack.damageBuff=0.5\n"
            + "defense.hpBuff=0.5\n"
            + "utility.extraSlots=1\n";

    public static void main(String[] args) {
        defaultFileMatchesRuleDocument();
        changedValuesReachCharacters();
        invalidSettingsAreRejected();
        characterSpecValidation();
    }

    /** 기본형 HP 100·슬롯 3칸 기준으로 공격형 +50% 피해, 방어형 HP 150, 유틸형 슬롯 4칸 */
    private static void defaultFileMatchesRuleDocument() {
        CharacterCatalog catalog = CharacterCatalog.loadDefault();
        CharacterSpec basic = catalog.basic();
        check(basic == CharacterSpec.DEFAULT && basic.getMaxHp() == 100 && basic.getItemSlots() == 3
                && basic.getDamageBuff() == 0.0, "basic has no bonus");
        CharacterSpec attacker = catalog.attacker();
        check(attacker.getDamageBuff() == 0.5 && attacker.getMaxHp() == 100 && attacker.getItemSlots() == 3,
                "attacker only has damage buff");
        CharacterSpec defender = catalog.defender();
        check(defender.getMaxHp() == 150 && defender.getDamageBuff() == 0.0 && defender.getItemSlots() == 3,
                "defender only has HP 150");
        CharacterSpec utility = catalog.utility();
        check(utility.getItemSlots() == 4 && utility.getMaxHp() == 100 && utility.getDamageBuff() == 0.0,
                "utility only has four slots");
        List<CharacterSpec> all = catalog.all();
        check(all.size() == 4 && all.get(0) == basic && all.get(3) == utility, "four characters in order");
        check(!attacker.getId().equals(defender.getId()) && !defender.getId().equals(utility.getId())
                && !utility.getId().equals(basic.getId()), "distinct ids");
    }

    private static void changedValuesReachCharacters() {
        CharacterCatalog tuned = load("attack.damageBuff=0.25\ndefense.hpBuff=1.0\nutility.extraSlots=2\n");
        check(tuned.attacker().getDamageBuff() == 0.25, "tuned attacker buff");
        check(tuned.defender().getMaxHp() == 200, "tuned defender HP");
        check(tuned.utility().getItemSlots() == 5, "tuned utility slots");
    }

    private static void invalidSettingsAreRejected() {
        check(load(VALID) != null, "valid fixture loads");
        reject(VALID.replace("attack.damageBuff=0.5", "attack.damageBuff=-0.1"), "negative buff");
        reject(VALID.replace("attack.damageBuff=0.5", "attack.damageBuff=abc"), "non-numeric buff");
        reject(VALID.replace("attack.damageBuff=0.5", "attack.damageBuff=NaN"), "NaN buff");
        reject(VALID.replace("attack.damageBuff=0.5", "attack.damageBuff=Infinity"), "infinite buff");
        reject(VALID.replace("defense.hpBuff=0.5", "defense.hpBuff=6"), "HP buff above range");
        reject(VALID.replace("utility.extraSlots=1", "utility.extraSlots=-1"), "negative slots");
        reject(VALID.replace("utility.extraSlots=1", "utility.extraSlots=1.5"), "fractional slots");
        reject(VALID + "utility.typo=1\n", "unknown key");
        reject(VALID.replace("defense.hpBuff=0.5\n", ""), "missing key");
        reject("", "empty file");
        try {
            CharacterCatalog.load(null);
            throw new IllegalStateException("null input accepted");
        } catch (IllegalArgumentException expected) {
            // 입력 스트림 누락의 명시적 거절
        }
    }

    private static void characterSpecValidation() {
        CharacterSpec legacy = new CharacterSpec("x", "엑스", 80);
        check(legacy.getMaxHp() == 80 && legacy.getDamageBuff() == 0.0
                && legacy.getItemSlots() == CharacterSpec.DEFAULT_ITEM_SLOTS, "three-argument constructor keeps defaults");
        rejectSpec("x", "엑스", 0, 0.0, 3, "zero HP");
        rejectSpec("x", "엑스", 100, -0.5, 3, "negative buff");
        rejectSpec("x", "엑스", 100, Double.NaN, 3, "NaN buff");
        rejectSpec("x", "엑스", 100, 0.0, 0, "no slots");
        rejectSpec(" ", "엑스", 100, 0.0, 3, "blank id");
    }

    private static void rejectSpec(String id, String name, int hp, double buff, int slots, String message) {
        try {
            new CharacterSpec(id, name, hp, buff, slots);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new IllegalStateException("Invalid character accepted: " + message);
    }

    private static CharacterCatalog load(String text) {
        return CharacterCatalog.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static void reject(String text, String message) {
        try {
            load(text);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new IllegalStateException("Invalid characters accepted: " + message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
