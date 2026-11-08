package kr.ac.jbnu.se.tetris.battle;

import kr.ac.jbnu.se.tetris.core.GameEvent;

/** LINE_CLEAR 이벤트 한 건에서 공격 한 건을 산출하며 수치는 battle/balance.properties에서 조회 */
public final class DamageManager {
    private final BattleBalance balance;

    /** battle/balance.properties의 기본 수치로 생성 */
    public DamageManager() { this(BattleBalance.loadDefault()); }

    /** 테스트나 난이도별 수치 주입을 위한 지정 수치 생성 */
    public DamageManager(BattleBalance balance) {
        if (balance == null) throw new IllegalArgumentException("Battle balance is required");
        this.balance = balance;
    }

    /** 버프와 방어가 없는 기본 공격 계산 */
    public Attack forLineClear(GameEvent event) { return forLineClear(event, 0.0, 0.0); }

    /** 공격 버프 합과 방어 합을 반영한 공격 계산, 합연산은 호출하는 쪽에서 미리 더해 전달 */
    public Attack forLineClear(GameEvent event, double attackBuff, double defense) {
        if (event == null || event.getType() != GameEvent.Type.LINE_CLEAR) {
            throw new IllegalArgumentException("LINE_CLEAR event required");
        }
        return calculate(event.getLineCount(), event.getCombo(), event.isTSpin(), event.isPerfectClear(),
                attackBuff, defense);
    }

    Attack calculate(int lines, int combo, boolean tSpin) {
        return calculate(lines, combo, tSpin, false, 0.0, 0.0);
    }

    /** 줄 수·콤보·T-Spin·퍼펙트 클리어 여부와 버프에서 공격 한 건을 계산하는 순수 계산 */
    Attack calculate(int lines, int combo, boolean tSpin, boolean perfectClear,
                     double attackBuff, double defense) {
        if (lines < 1 || lines > 4) {
            throw new IllegalArgumentException("A placement may clear 1 to 4 lines");
        }
        if (!isFiniteNonNegative(attackBuff) || !isFiniteNonNegative(defense)) {
            throw new IllegalArgumentException("Buff and defense must be finite and non-negative");
        }
        // 퍼펙트 클리어는 기본 묶음 대체이므로 콤보와 버프·방어를 모두 무시한 고정값
        if (perfectClear) {
            return new Attack(balance.getPerfectClearDamage(), balance.getPerfectClearGarbage());
        }
        // 피해 = round((기본피해[줄 수] * (T-Spin 배율 또는 1) + 콤보 보너스) * (1 + 공격 버프 합 - 방어 합)), 최소 0
        int baseDamage = balance.damageForLines(lines) * (tSpin ? balance.getTSpinDamageMultiplier() : 1)
                + balance.comboDamage(combo);
        double factor = 1.0 + (attackBuff - defense);
        // 큰 유한 버프의 곱셈·정수 변환 오버플로 방지 및 HP 계산에 전달할 피해 범위 보장
        long roundedDamage = Math.round(baseDamage * factor);
        int damage = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, roundedDamage));
        // 가비지 = 기본가비지[줄 수] + 콤보 보너스 + (T-Spin 보너스), 버프와 방어는 적용하지 않음
        int garbage = balance.garbageForLines(lines) + balance.comboGarbage(combo)
                + (tSpin ? balance.getTSpinGarbageBonus() : 0);
        return new Attack(damage, garbage);
    }

    private static boolean isFiniteNonNegative(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0.0;
    }

    /** 상대 HP에서 차감할 피해와 상대에게 보낼 가비지 줄 수를 담은 불변 결과 */
    public static final class Attack {
        private final int damage;
        private final int garbageLines;

        Attack(int damage, int garbageLines) {
            this.damage = damage;
            this.garbageLines = garbageLines;
        }

        public int getDamage() { return damage; }
        public int getGarbageLines() { return garbageLines; }
    }
}
