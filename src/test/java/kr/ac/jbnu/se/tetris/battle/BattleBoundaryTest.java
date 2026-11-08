package kr.ac.jbnu.se.tetris.battle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ac.jbnu.se.tetris.character.CharacterSpec;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameState;

/** 전투 표시 사본·아이템 경계·인원별 포기 규칙 검증 */
public final class BattleBoundaryTest {
    public static void main(String[] args) {
        snapshotsAndCharacterHp();
        itemRejectionPreservesBattle();
        forfeitAcrossRoomSizes();
    }

    private static void snapshotsAndCharacterHp() {
        check(CharacterSpec.DEFAULT.getMaxHp() == 100
                && "student".equals(CharacterSpec.DEFAULT.getId()), "Default character baseline");
        CharacterSpec custom = new CharacterSpec("scholar", "연구자", 135);
        BattleManager battle = new BattleManager(Arrays.asList(
                new ParticipantSpec("a", "A", CharacterSpec.DEFAULT),
                new ParticipantSpec("b", "B", custom)), 19);
        BattleState ready = battle.getState();
        check(ready.getParticipant("a").getHp() == 100
                && ready.getParticipant("b").getMaxHp() == 135, "Character HP enters battle");
        compare(ready, BattleSnapshots.copyOf(ready));
        Map<String, ParticipantState> mutable = new LinkedHashMap<String, ParticipantState>(ready.getParticipants());
        BattleState display = BattleSnapshots.battle(ready.getStatus(), ready.getVersion(),
                mutable, ready.getWinnerId(), ready.getReason());
        mutable.clear();
        check(display.getParticipants().size() == 2, "Caller map cannot change battle");
        rejectUnsupported(new Runnable() { public void run() { display.getParticipants().clear(); } });

        ParticipantState one = ready.getParticipant("a");
        reject(new Runnable() { public void run() {
            BattleSnapshots.participant("wrong", one.getName(), one.getHp(), one.getMaxHp(),
                    one.getGameState(), false);
        }});
        reject(new Runnable() { public void run() {
            BattleSnapshots.participant("a", one.getName(), 0, one.getMaxHp(),
                    one.getGameState(), false);
        }});
        Map<String, ParticipantState> wrong = new LinkedHashMap<String, ParticipantState>();
        wrong.put("wrong", one);
        wrong.put("b", ready.getParticipant("b"));
        reject(new Runnable() { public void run() {
            BattleSnapshots.battle(BattleState.Status.READY, 0, wrong, null, null);
        }});
        reject(new Runnable() { public void run() {
            BattleSnapshots.battle(BattleState.Status.READY, 0, ready.getParticipants(), "a", null);
        }});
        reject(new Runnable() { public void run() {
            BattleSnapshots.battle(BattleState.Status.FINISHED, 1, ready.getParticipants(), "missing", "FORFEIT");
        }});

        check(battle.start().isAccepted(), "Start");
        compare(battle.getState(), BattleSnapshots.copyOf(battle.getState()));
        check(battle.pause().isAccepted(), "Pause");
        compare(battle.getState(), BattleSnapshots.copyOf(battle.getState()));
        check(battle.forfeit("a").isAccepted(), "Forfeit while paused");
        check(battle.getState().getStatus() == BattleState.Status.FINISHED, "Finished status");
        compare(battle.getState(), BattleSnapshots.copyOf(battle.getState()));
    }

    private static void itemRejectionPreservesBattle() {
        BattleManager battle = new BattleManager(specs(2), 23);
        check(battle.start().isAccepted(), "Start");
        BattleState before = battle.getState();
        rejectWithoutChange(battle, before, "ITEM_NOT_OWNED",
                battle.submitItem("p0", new GameAction.ItemUse("damage", "p1",
                        new GameAction.TargetCell(9, 21))));
        rejectWithoutChange(battle, before, "INVALID_TARGET",
                battle.submitItem("p0", new GameAction.ItemUse("damage", "missing")));
        rejectWithoutChange(battle, before, "INVALID_TARGET_CELL",
                battle.submitItem("p0", new GameAction.ItemUse("damage", "p1",
                        new GameAction.TargetCell(10, 21))));
        rejectWithoutChange(battle, before, "INVALID_PAYLOAD", battle.submitItem("p0", null));
        rejectWithoutChange(battle, before, "UNKNOWN_ACTOR",
                battle.submitItem("missing", new GameAction.ItemUse("damage", "p1")));
        rejectWithoutChange(battle, before, "INVALID_PAYLOAD",
                battle.submit("p0", GameAction.Type.USE_ITEM));
        reject(new Runnable() { public void run() {
            new GameAction.ItemUse(" ", "p1");
        }});
        reject(new Runnable() { public void run() {
            new GameAction.TargetCell(-1, 0);
        }});
    }

    private static void forfeitAcrossRoomSizes() {
        for (int count = 2; count <= 4; count++) {
            BattleManager battle = new BattleManager(specs(count), 29);
            check(battle.start().isAccepted() && battle.pause().isAccepted(), "Paused room start");
            for (int index = 0; index < count - 1; index++) {
                BattleState before = battle.getState();
                String leaving = "p" + index;
                BattleResult result = battle.forfeit(leaving);
                check(result.isAccepted() && result.getState().getParticipant(leaving).isEliminated(),
                        "Departing participant eliminated");
                check(result.getState().getVersion() > before.getVersion(), "Accepted exit advances version");
                if (index < count - 2) {
                    check(result.getState().getStatus() == BattleState.Status.PAUSED
                            && result.getState().getWinnerId() == null, "Other participants remain paused");
                } else {
                    check(result.getState().getStatus() == BattleState.Status.FINISHED
                            && ("p" + (count - 1)).equals(result.getState().getWinnerId())
                            && "FORFEIT".equals(result.getState().getReason()),
                            "Last participant wins after forfeit");
                    compare(result.getState(), BattleSnapshots.copyOf(result.getState()));
                }
            }
            BattleState finished = battle.getState();
            check(!battle.forfeit("p0").isAccepted()
                    && battle.getState().getVersion() == finished.getVersion(),
                    "Finished result cannot be changed by another exit");
        }
    }

    private static List<ParticipantSpec> specs(int count) {
        List<ParticipantSpec> result = new ArrayList<ParticipantSpec>();
        for (int index = 0; index < count; index++) {
            result.add(new ParticipantSpec("p" + index, "P" + index, CharacterSpec.DEFAULT));
        }
        return result;
    }

    private static void rejectWithoutChange(BattleManager battle, BattleState before,
                                             String reason, BattleResult result) {
        check(!result.isAccepted() && reason.equals(result.getReason()), "Expected rejection: " + reason);
        compare(before, battle.getState());
    }

    private static void compare(BattleState expected, BattleState actual) {
        check(expected.getVersion() == actual.getVersion()
                && expected.getStatus() == actual.getStatus()
                && same(expected.getWinnerId(), actual.getWinnerId())
                && same(expected.getReason(), actual.getReason())
                && expected.getParticipants().keySet().equals(actual.getParticipants().keySet()),
                "Battle identity, status and participants retained");
        for (String id : expected.getParticipants().keySet()) {
            ParticipantState left = expected.getParticipant(id);
            ParticipantState right = actual.getParticipant(id);
            check(left.getHp() == right.getHp() && left.getMaxHp() == right.getMaxHp()
                    && left.isEliminated() == right.isEliminated()
                    && left.getName().equals(right.getName()), "Participant HP and identity retained");
            GameState a = left.getGameState(), b = right.getGameState();
            check(a.getVersion() == b.getVersion() && a.getStatus() == b.getStatus()
                    && a.getActorId().equals(b.getActorId()) && a.getGhostY() == b.getGhostY()
                    && a.isAwaitingSpawn() == b.isAwaitingSpawn(), "Core state retained");
            for (int y = 0; y < a.getBoard().getHeight(); y++) {
                for (int x = 0; x < a.getBoard().getWidth(); x++) {
                    check(a.getBoard().getCell(x, y) == b.getBoard().getCell(x, y), "Board retained");
                }
            }
        }
    }

    private static boolean same(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static void reject(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Invalid boundary rejected");
    }

    private static void rejectUnsupported(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (UnsupportedOperationException expected) { rejected = true; }
        check(rejected, "Battle collection immutable");
    }

    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
}
