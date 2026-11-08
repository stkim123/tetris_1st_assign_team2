package kr.ac.jbnu.se.tetris.battle;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/** balance.properties의 기본값 보존, 수치 변경 반영, 잘못된 설정의 거절을 확인 */
public final class BattleBalanceTest {
    // balance.properties 기본값과 같은 정상 설정, 거절 시험은 이 문자열의 한 항목만 바꿔 사용
    private static final String VALID = "damage.lines=0,4,8,12,20\n"
            + "garbage.lines=0,0,1,2,4\n"
            + "damage.combo=0,0,4,8,12\n"
            + "garbage.combo=0,0,1,2,3\n"
            + "damage.perfect=40\n"
            + "garbage.perfect=8\n"
            + "damage.tspin.multiplier=1\n"
            + "garbage.tspin.bonus=0\n";

    public static void main(String[] args) {
        defaultFileKeepsRuleDocument();
        changedValuesReachDamageRules();
        invalidSettingsAreRejected();
    }

    /** 실제 balance.properties(클래스패스)가 규칙 문서의 표와 같은 값을 갖는지 확인 */
    private static void defaultFileKeepsRuleDocument() {
        BattleBalance balance = BattleBalance.loadDefault();
        check(balance.damageForLines(1) == 4 && balance.damageForLines(4) == 20, "default damage table");
        check(balance.garbageForLines(2) == 1 && balance.garbageForLines(4) == 4, "default garbage table");
        // 콤보 표: 0~1콤보 0, 2콤보 +4/+1, 3콤보 +8/+2, 4콤보 이상 +12/+3
        check(balance.comboDamage(0) == 0 && balance.comboDamage(1) == 0, "no combo damage before two combos");
        check(balance.comboDamage(2) == 4 && balance.comboDamage(3) == 8 && balance.comboDamage(4) == 12,
                "combo damage table");
        check(balance.comboGarbage(2) == 1 && balance.comboGarbage(3) == 2 && balance.comboGarbage(4) == 3,
                "combo garbage table");
        check(balance.comboDamage(20) == 12 && balance.comboGarbage(20) == 3, "four combos and above share last entry");
        check(balance.comboDamage(-1) == 0 && balance.comboGarbage(-1) == 0, "no combo state adds nothing");
        check(balance.getPerfectClearDamage() == 40 && balance.getPerfectClearGarbage() == 8, "perfect clear values");
        // T-Spin 보너스는 규칙에서 제외되어 꺼진 상태
        check(balance.getTSpinDamageMultiplier() == 1 && balance.getTSpinGarbageBonus() == 0, "T-spin bonus disabled");
    }

    /** 파일의 수치를 바꾸면 계산 결과에 그대로 반영되는지 확인, 수치가 코드에 박혀 있지 않다는 증거 */
    private static void changedValuesReachDamageRules() {
        String tuned = VALID.replace("damage.lines=0,4,8,12,20", "damage.lines=0,5,10,15,30")
                .replace("damage.combo=0,0,4,8,12", "damage.combo=0,1,2,3,4")
                .replace("damage.tspin.multiplier=1", "damage.tspin.multiplier=3")
                .replace("garbage.combo=0,0,1,2,3", "garbage.combo=0,0,0,0,5")
                .replace("damage.perfect=40", "damage.perfect=99");
        DamageManager manager = new DamageManager(load(tuned));
        check(manager.calculate(1, 0, false).getDamage() == 5, "tuned single damage");
        check(manager.calculate(1, 1, false).getDamage() == 5 + 1, "tuned combo table");
        check(manager.calculate(1, 9, false).getDamage() == 5 + 4, "tuned combo last entry");
        check(manager.calculate(2, 0, true).getDamage() == 30, "tuned T-spin multiplier");
        check(manager.calculate(2, 6, false).getGarbageLines() == 1 + 5, "tuned combo garbage");
        check(manager.calculate(1, 0, false, true, 0.0, 0.0).getDamage() == 99, "tuned perfect clear damage");
    }

    /** 잘못된 설정을 읽는 시점에 거절하는지 확인, 정상 설정에서 한 항목만 틀리게 바꿔 시험 */
    private static void invalidSettingsAreRejected() {
        // 비교 기준인 정상 설정이 읽혀야 아래 거절 시험이 의미를 가짐
        check(load(VALID) != null, "valid fixture loads");
        reject(VALID.replace("damage.lines=0,4,8,12,20", "damage.lines=0,4,8,12"), "wrong line entry count");
        reject(VALID.replace("damage.lines=0,4,8,12,20", "damage.lines=1,4,8,12,20"), "first line entry must be zero");
        reject(VALID.replace("damage.lines=0,4,8,12,20", "damage.lines=0,4,-8,12,20"), "negative value");
        reject(VALID.replace("damage.combo=0,0,4,8,12", "damage.combo=0,0,4,8"), "wrong combo entry count");
        reject(VALID.replace("damage.combo=0,0,4,8,12", "damage.combo=0,0,4,8,abc"), "non-numeric combo");
        reject(VALID.replace("garbage.combo=0,0,1,2,3", "garbage.combo=0,0,1,2,-1"), "negative combo garbage");
        reject(VALID.replace("damage.perfect=40", "damage.perfect=-1"), "negative perfect damage");
        reject(VALID.replace("garbage.perfect=8", "garbage.perfect=23"), "perfect garbage above 22 lines");
        reject(VALID.replace("damage.tspin.multiplier=1", "damage.tspin.multiplier=0"), "multiplier below one");
        // 가비지 최댓값 4 + 콤보 3 + T-Spin 20 = 27이 22줄을 넘음
        reject(VALID.replace("garbage.tspin.bonus=0", "garbage.tspin.bonus=20"), "garbage above 22 lines");
        reject(VALID + "damage.typo=1\n", "unknown key");
        reject(VALID + "damage.combo.cap=5\n", "removed old combo key");
        reject(VALID.replace("damage.perfect=40\n", ""), "missing key");
        reject("", "empty file");
        try {
            BattleBalance.load(null);
            throw new IllegalStateException("null input accepted");
        } catch (IllegalArgumentException expected) {
            // 입력 스트림 누락의 명시적 거절
        }
    }

    private static BattleBalance load(String text) {
        return BattleBalance.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    /** 예외 없이 읽히면 잘못된 설정을 받아들인 것이므로 message와 함께 실패 처리 */
    private static void reject(String text, String message) {
        try {
            load(text);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new IllegalStateException("Invalid balance accepted: " + message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
