package kr.ac.jbnu.se.tetris.battle;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.ac.jbnu.se.tetris.character.CharacterCatalog;
import kr.ac.jbnu.se.tetris.character.CharacterSpec;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.PieceGenerator;
import kr.ac.jbnu.se.tetris.core.PieceType;

/** HP 피해 공식의 버프 합·방어·반올림·퍼펙트 클리어 처리와 캐릭터 버프의 전투 연결을 확인 */
public final class DamageManagerTest {
    public static void main(String[] args) {
        buffsAreSummedNotMultiplied();
        defenseRoundingAndFloor();
        largeBuffDamageSaturates();
        garbageIgnoresBuffs();
        perfectClearIgnoresEverything();
        attackerCharacterBuffReachesBattle();
        overwhelmingBuffEndsBattleWithoutFailure();
        participantSpecKeepsCharacter();
    }

    /** 공격 버프는 곱하지 않고 합산, 규칙 문서의 최대 조합 예시(공격형+피해 증가+Fever) 확인 */
    private static void buffsAreSummedNotMultiplied() {
        DamageManager manager = new DamageManager();
        // 4줄 20 + 4콤보 12 = 32, 버프 합 0.5 + 0.5 + 0.2 = 1.2이므로 32 * 2.2 = 70.4 -> 70
        check(manager.calculate(4, 4, false, false, 1.2, 0.0).getDamage() == 70, "summed buffs 2.2x");
        // 곱셈이었다면 32 * 1.5 * 1.5 * 1.2 = 86.4가 되어 달라야 함
        check(manager.calculate(1, 0, false, false, 0.5, 0.0).getDamage() == 6, "single with attacker buff 4 * 1.5");
        check(manager.calculate(1, 0, false, false, 0.0, 0.0).getDamage() == 4, "no buff keeps base");
    }

    /** 방어는 합산에서 빼고 결과는 반올림, 최소 0 */
    private static void defenseRoundingAndFloor() {
        DamageManager manager = new DamageManager();
        // 8 * (1 - 0.3) = 5.6 -> 6 (반올림)
        check(manager.calculate(2, 0, false, false, 0.0, 0.3).getDamage() == 6, "defense rounds half up");
        // 4 * 1.2 = 4.8 -> 5
        check(manager.calculate(1, 0, false, false, 0.2, 0.0).getDamage() == 5, "fever buff rounds up");
        // 공격 0.5와 방어 0.3은 합산: 8 * 1.2 = 9.6 -> 10
        check(manager.calculate(2, 0, false, false, 0.5, 0.3).getDamage() == 10, "buff minus defense");
        // 방어가 커서 배율이 음수가 되어도 피해는 0 아래로 내려가지 않음
        check(manager.calculate(4, 4, false, false, 0.0, 3.0).getDamage() == 0, "damage floors at zero");
    }

    /** int 경계와 double 곱셈 한도를 넘는 유한 버프의 피해 포화 및 HP 계산 호환성 확인 */
    private static void largeBuffDamageSaturates() {
        DamageManager manager = new DamageManager();
        double boundaryBuff = Integer.MAX_VALUE / 4.0 - 1.0;
        check(manager.calculate(1, 0, false, false, boundaryBuff - 0.25, 0.0).getDamage()
                == Integer.MAX_VALUE - 1, "damage below int limit stays exact");
        check(manager.calculate(1, 0, false, false, boundaryBuff, 0.0).getDamage()
                == Integer.MAX_VALUE, "damage at int limit stays exact");
        for (double buff : new double[] {536870911.0, Double.MAX_VALUE}) {
            int damage = manager.calculate(1, 0, false, false, buff, 0.0).getDamage();
            check(damage == Integer.MAX_VALUE, "overflowing damage saturates at int limit");
            check(new HPManager().applyDamage(100, 100, damage) == 0, "saturated damage depletes HP");
        }
        check(manager.calculate(1, 0, false, false, 0.0, Double.MAX_VALUE).getDamage()
                == 0, "large defense still floors damage at zero");
        for (double buff : new double[] {Math.scalb(1.0, 53), Double.MAX_VALUE}) {
            check(manager.calculate(1, 0, false, false, buff, buff).getDamage()
                    == 4, "equal large buff and defense preserve base damage");
        }
    }

    /** 가비지 줄 수는 버프와 방어의 영향을 받지 않음 */
    private static void garbageIgnoresBuffs() {
        DamageManager manager = new DamageManager();
        int plain = manager.calculate(3, 2, false, false, 0.0, 0.0).getGarbageLines();
        check(plain == 3, "three lines 2 + two combos 1");
        check(manager.calculate(3, 2, false, false, 2.2, 0.0).getGarbageLines() == plain, "buff keeps garbage");
        check(manager.calculate(3, 2, false, false, 0.0, 0.3).getGarbageLines() == plain, "defense keeps garbage");
    }

    /** 퍼펙트 클리어는 콤보·버프·방어·T-Spin과 줄 수를 모두 무시한 고정값 */
    private static void perfectClearIgnoresEverything() {
        DamageManager manager = new DamageManager();
        for (int lines = 1; lines <= 4; lines++) {
            DamageManager.Attack attack = manager.calculate(lines, 9, true, true, 2.2, 0.3);
            check(attack.getDamage() == 40 && attack.getGarbageLines() == 8, "perfect clear constants: " + lines);
        }
    }

    /** 공격형 캐릭터의 버프가 실제 전투 피해에 반영되고 가비지는 그대로인지 확인 */
    private static void attackerCharacterBuffReachesBattle() {
        CharacterCatalog catalog = CharacterCatalog.loadDefault();
        ParticipantState basicTarget = doubleClearTarget(catalog.basic());
        ParticipantState attackerTarget = doubleClearTarget(catalog.attacker());
        check(basicTarget.getHp() == 100 - 8, "basic double clear damage 8");
        check(attackerTarget.getHp() == 100 - 12, "attacker double clear damage 8 * 1.5 = 12");
        check(basicTarget.getPendingGarbageLines() == 1, "basic double sends one garbage line");
        check(attackerTarget.getPendingGarbageLines() == 2, "attacker passive adds a normal garbage line");
    }

    /** 큰 유한 캐릭터 버프로 공격해도 실제 전투 경로에서 예외 없이 HP 소진 처리 확인 */
    private static void overwhelmingBuffEndsBattleWithoutFailure() {
        CharacterSpec attacker = new CharacterSpec("overflow", "큰 버프", 100, 536870911.0, 3);
        ParticipantState target = doubleClearTarget(attacker);
        check(target.getHp() == 0 && target.isEliminated(), "large character buff eliminates target");
    }

    /** 퍼펙트가 아닌 더블 클리어의 대상 상태 조회, 먼저 쌓은 블록 하나가 남아 보드가 비지 않음 */
    private static ParticipantState doubleClearTarget(CharacterSpec attacker) {
        Map<String, PieceGenerator> generators = new LinkedHashMap<String, PieceGenerator>();
        generators.put("a", constant(PieceType.O));
        generators.put("b", constant(PieceType.O));
        BattleManager battle = new BattleManager(Arrays.asList(new ParticipantSpec("a", "A", attacker),
                new ParticipantSpec("b", "B", CharacterSpec.DEFAULT)), 7, generators);
        check(battle.start().isAccepted(), "start");
        for (int x : new int[] {0, 0, 2, 4, 6, 8}) {
            while (battle.getState().getParticipant("a").getGameState().getPieceX() > x) {
                check(battle.submit("a", GameAction.Type.MOVE_LEFT).isAccepted(), "move left");
            }
            while (battle.getState().getParticipant("a").getGameState().getPieceX() < x) {
                check(battle.submit("a", GameAction.Type.MOVE_RIGHT).isAccepted(), "move right");
            }
            check(battle.submit("a", GameAction.Type.HARD_DROP).isAccepted(), "drop");
        }
        return battle.getState().getParticipant("b");
    }

    private static void participantSpecKeepsCharacter() {
        CharacterSpec defender = CharacterCatalog.loadDefault().defender();
        ParticipantSpec spec = new ParticipantSpec("d", "D", defender);
        check(spec.getCharacter() == defender && spec.getMaxHp() == 150, "spec keeps character and HP 150");
        ParticipantSpec plain = new ParticipantSpec("p", "P", 65);
        check(plain.getMaxHp() == 65 && plain.getCharacter().getDamageBuff() == 0.0, "plain HP spec has no buff");
    }

    private static PieceGenerator constant(final PieceType type) {
        return new PieceGenerator() { public PieceType nextPiece() { return type; } };
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
