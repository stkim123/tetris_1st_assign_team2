package kr.ac.jbnu.se.tetris.battle;

import java.util.Arrays;
import java.util.List;
import kr.ac.jbnu.se.tetris.character.CharacterSpec;

/** 서버가 쓰는 advance(경과 ms) 경로: PvP 낙하 간격과 중력 칸 수 카운터 검증 */
public final class BattleAdvanceStepTest {
    public static void main(String[] args) {
        List<ParticipantSpec> specs = Arrays.asList(
                new ParticipantSpec("a", "A", CharacterSpec.DEFAULT),
                new ParticipantSpec("b", "B", CharacterSpec.DEFAULT));

        // PvP 시작 낙하 간격은 500ms: 1초 동안 참가자 2명이 각각 2칸씩 = 4칸.
        BattleManager pvp = BattleManager.pvp(specs, 7);
        check(pvp.start().isAccepted(), "PvP battle starts");
        long before = pvp.getGravityStepCount();
        pvp.advance(1000);
        check(pvp.getGravityStepCount() - before == 4, "PvP: 500ms interval -> 4 steps/1s, got "
                + (pvp.getGravityStepCount() - before));

        // 틱을 잘게 나눠도(100ms x 10) 누적 결과는 같아야 한다(실제 경과 시간 누적 방식).
        BattleManager sliced = BattleManager.pvp(specs, 7);
        sliced.start();
        long slicedBefore = sliced.getGravityStepCount();
        for (int i = 0; i < 10; i++) sliced.advance(100);
        check(sliced.getGravityStepCount() - slicedBefore == 4, "Sliced advance accumulates identically");

        // 60초 이후 가속 간격 자체는 RpgBattleRulesTest(pvpGravityAt)가 검증한다.

        // 기존 생성자(비 PvP)는 400ms 고정 간격이라 같은 1초에 2.5칸/명 -> 5칸. PvP 모드와 구분된다.
        BattleManager legacy = new BattleManager(specs, 7);
        legacy.start();
        long legacyBefore = legacy.getGravityStepCount();
        legacy.advance(1000);
        check(legacy.getGravityStepCount() - legacyBefore == 4 || legacy.getGravityStepCount() - legacyBefore == 5,
                "Non-PvP baseline interval is 400ms");
        System.out.println("BattleAdvanceStepTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
