package kr.ac.jbnu.se.tetris.battle;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.PieceGenerator;
import kr.ac.jbnu.se.tetris.core.PieceType;

/** HP, 가비지, 이벤트 순서, 종료 조건 등 공통 전투 규칙을 확인 */
public final class BattleManagerTest {
    public static void main(String[] args) {
        lifecycleAndPause();
        lineClearDamageAndGarbage();
        damageRules();
        hpLossAndTopOutAreDistinct();
        multipleParticipantsAndFailures();
        failedTickFreezesPartialAdvance();
    }

    private static void lifecycleAndPause() {
        BattleManager battle = squares(100, 100);
        BattleState ready = battle.getState();
        check(ready.getStatus() == BattleState.Status.READY && ready.getVersion() == 0, "ready");
        check(!battle.submit("player", GameAction.Type.HARD_DROP).isAccepted(), "cannot play before start");
        BattleResult started = battle.start();
        check(started.isAccepted() && started.getState().getStatus() == BattleState.Status.RUNNING,
                "start");
        check(count(started, BattleEvent.Type.MATCH_STARTED) == 1, "one match start");
        check(started.getState().getParticipant("player").getGameState().getActivePiece() != null,
                "player engine started");
        check(started.getState().getParticipant("monster").getGameState().getActivePiece() != null,
                "monster engine started");
        check(!battle.submit("missing", GameAction.Type.HARD_DROP).isAccepted(), "invalid actor");
        check(!battle.submit("player", GameAction.Type.RECEIVE_GARBAGE).isAccepted(), "trusted garbage");
        check(!battle.submit("player", GameAction.Type.PAUSE).isAccepted(), "manager pause only");
        check(battle.pause().isAccepted(), "pause");
        check(battle.getState().getParticipant("player").getGameState().getStatus()
                == GameState.Status.PAUSED, "player paused");
        check(battle.getState().getParticipant("monster").getGameState().getStatus()
                == GameState.Status.PAUSED, "monster paused");
        check(!battle.tick().isAccepted(), "paused tick rejected");
        check(battle.resume().isAccepted(), "resume");
        BattleState old = battle.getState();
        check(battle.tick().isAccepted(), "tick");
        check(old.getVersion() < battle.getState().getVersion(), "snapshot version");
        check(old.getParticipant("player").getGameState().getTick() == 0,
                "old snapshot immutable");
    }

    private static void lineClearDamageAndGarbage() {
        BattleManager battle = squares(100, 100);
        check(battle.start().isAccepted(), "start");
        BattleResult clear = fillTwoRows(battle, "player");
        check(count(clear, BattleEvent.Type.DAMAGE) == 1, "one damage for one clear");
        check(count(clear, BattleEvent.Type.HP_CHANGED) == 1, "one HP transition");
        check(count(clear, BattleEvent.Type.GARBAGE_SENT) == 1, "one garbage attack");
        // O 블록 다섯 개로 빈 보드의 두 줄을 지우므로 퍼펙트 클리어, 기본 묶음 대체 피해 40과 가비지 8
        check(battle.getState().getParticipant("monster").getHp() == 60, "perfect clear damage 40");
        check(battle.getState().getParticipant("monster")
                .getPendingGarbageLines() == 8, "perfect clear garbage queued");
        BattleResult applied = battle.submit("monster", GameAction.Type.HARD_DROP);
        check(applied.isAccepted(), "target locks piece");
        check(count(applied, BattleEvent.Type.GARBAGE_RECEIVED) == 0, "two-second wait prevents immediate rise");
        check(battle.getState().getParticipant("monster")
                .getPendingGarbageLines() == 8, "garbage remains in tank before delay");
        check(!battle.getState().getParticipant("monster").isEliminated(), "still alive");
        assertIncreasingIds(clear);
    }

    private static void hpLossAndTopOutAreDistinct() {
        BattleManager hpBattle = squares(100, 8);
        hpBattle.start();
        BattleResult killed = fillTwoRows(hpBattle, "player");
        check(killed.getState().getStatus() == BattleState.Status.FINISHED, "HP match finished");
        check("player".equals(killed.getState().getWinnerId()), "HP winner");
        check("HP_DEPLETED".equals(killed.getState().getReason()), "HP reason");
        check(killed.getState().getParticipant("monster").getHp() == 0, "HP zero");
        check(count(killed, BattleEvent.Type.GARBAGE_SENT) == 0, "no garbage after lethal damage");
        check(!hpBattle.tick().isAccepted(), "finished tick rejected");
        check(!hpBattle.submit("player", GameAction.Type.HARD_DROP).isAccepted(), "finished input rejected");

        BattleManager topOut = squares(100, 100);
        topOut.start();
        BattleResult last = null;
        for (int i = 0; i < 11; i++) {
            last = topOut.submit("player", GameAction.Type.HARD_DROP);
            check(last.isAccepted(), "stacking square " + i);
        }
        check(last.getState().getStatus() == BattleState.Status.FINISHED, "top out match finished");
        check("monster".equals(last.getState().getWinnerId()), "top out winner");
        check("TOP_OUT".equals(last.getState().getReason()), "top out reason");
        check(last.getState().getParticipant("player").getHp() == 100, "top out keeps HP");
    }

    private static void multipleParticipantsAndFailures() {
        Map<String, PieceGenerator> generators = new LinkedHashMap<String, PieceGenerator>();
        for (String id : Arrays.asList("a", "b", "c")) generators.put(id, constant(PieceType.O));
        BattleManager three = new BattleManager(Arrays.asList(
                new ParticipantSpec("a", "A", 100), new ParticipantSpec("b", "B", 8),
                new ParticipantSpec("c", "C", 100)), 7, generators);
        three.start();
        fillTwoRows(three, "a");
        check(three.getState().getParticipant("b").isEliminated(), "next target eliminated");
        check(three.getState().getStatus() == BattleState.Status.RUNNING, "third participant survives");
        check(three.getState().getParticipant("c").getHp() == 100, "third not hit yet");
        check(three.tick().isAccepted(), "spawn after first clear");
        BattleResult secondClear = fillTwoRows(three, "a");
        check(count(secondClear, BattleEvent.Type.DAMAGE) == 1, "next clear attacks once");
        // 두 번째도 빈 보드에서 지운 퍼펙트 클리어라 피해 40
        check(three.getState().getParticipant("c").getHp() == 60,
                "next living target receives perfect clear damage");

        Map<String, PieceGenerator> failing = new LinkedHashMap<String, PieceGenerator>();
        failing.put("player", constant(PieceType.O));
        failing.put("monster", constant(null));
        BattleManager failedStart = new BattleManager(Arrays.asList(
                new ParticipantSpec("player", "Player", 100),
                new ParticipantSpec("monster", "Monster", 100)), 1, failing);
        BattleResult rejected = failedStart.start();
        check(!rejected.isAccepted(), "generator failure rejected");
        check(rejected.getState().getStatus() == BattleState.Status.FINISHED,
                "partial start frozen");
        check(rejected.getReason().startsWith("START_FAILED"), "start error surfaced");
    }

    private static void failedTickFreezesPartialAdvance() {
        Map<String, PieceGenerator> generators = new LinkedHashMap<String, PieceGenerator>();
        generators.put("player", constant(PieceType.O));
        generators.put("monster", new PieceGenerator() {
            private int calls;
            public PieceType nextPiece() { return ++calls <= 4 ? PieceType.O : null; }
        });
        BattleManager battle = new BattleManager(Arrays.asList(
                new ParticipantSpec("player", "Player", 100),
                new ParticipantSpec("monster", "Monster", 100)), 2, generators);
        check(battle.start().isAccepted(), "start with four prepared pieces");
        BattleResult failed = null;
        for (int i = 0; i < 30; i++) {
            failed = battle.tick();
            if (!failed.isAccepted()) break;
        }
        check(!failed.isAccepted(), "bad generator rejected on tick");
        check(failed.getState().getStatus() == BattleState.Status.FINISHED,
                "partial tick cannot continue match");
        check(failed.getReason().startsWith("TICK_FAILED")
                || failed.getReason().startsWith("SPAWN_FAILED"), "tick failure surfaced");
        check(!battle.submit("player", GameAction.Type.HARD_DROP).isAccepted(),
                "partial tick match frozen");
    }

    private static void damageRules() {
        DamageManager manager = new DamageManager();
        check(manager.calculate(1, 0, false).getDamage() == 4, "single damage");
        check(manager.calculate(2, 0, false).getGarbageLines() == 1, "double garbage");
        // T-Spin 보너스는 꺼져 있으므로 T-Spin이어도 3줄 12 + 2콤보 4 = 16, 가비지 2 + 1 = 3
        check(manager.calculate(3, 2, true).getDamage() == 16, "T-spin bonus disabled plus combo damage");
        check(manager.calculate(3, 2, true).getGarbageLines() == 3, "T-spin bonus disabled plus combo garbage");
        // 4콤보 이상은 마지막 항목 +12를 그대로 사용
        check(manager.calculate(4, 20, false).getDamage() == 32, "combo bonus uses last entry from four combos");
        check(new HPManager().applyDamage(4, 100, 30) == 0, "HP floors at zero");
        check(new HPManager().applyHealing(99, 100, 30) == 100, "HP caps at max");
    }

    private static BattleResult fillTwoRows(BattleManager battle, String actor) {
        BattleResult last = null;
        for (int x : new int[] {0, 2, 4, 6, 8}) {
            while (battle.getState().getParticipant(actor).getGameState().getPieceX() > x) {
                check(battle.submit(actor, GameAction.Type.MOVE_LEFT).isAccepted(), "move left");
            }
            while (battle.getState().getParticipant(actor).getGameState().getPieceX() < x) {
                check(battle.submit(actor, GameAction.Type.MOVE_RIGHT).isAccepted(), "move right");
            }
            last = battle.submit(actor, GameAction.Type.HARD_DROP);
            check(last.isAccepted(), "drop");
        }
        return last;
    }

    private static BattleManager squares(int playerHp, int monsterHp) {
        Map<String, PieceGenerator> generators = new LinkedHashMap<String, PieceGenerator>();
        generators.put("player", constant(PieceType.O));
        generators.put("monster", constant(PieceType.O));
        return new BattleManager(Arrays.asList(new ParticipantSpec("player", "Player", playerHp),
                new ParticipantSpec("monster", "Monster", monsterHp)), 42, generators);
    }

    private static PieceGenerator constant(final PieceType type) {
        return new PieceGenerator() { public PieceType nextPiece() { return type; } };
    }

    private static int count(BattleResult result, BattleEvent.Type type) {
        int count = 0;
        for (BattleEvent event : result.getEvents()) if (event.getType() == type) count++;
        return count;
    }

    private static void assertIncreasingIds(BattleResult result) {
        long last = 0;
        for (BattleEvent event : result.getEvents()) {
            check(event.getEventId() > last, "event order");
            last = event.getEventId();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
