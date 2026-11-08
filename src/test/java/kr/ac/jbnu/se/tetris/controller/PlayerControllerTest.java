package kr.ac.jbnu.se.tetris.controller;

import java.util.ArrayList;
import java.util.List;
import kr.ac.jbnu.se.tetris.core.ActionResult;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameActionSink;
import kr.ac.jbnu.se.tetris.core.GameEngine;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.PieceGenerator;
import kr.ac.jbnu.se.tetris.core.PieceType;

/** 사람 입력이 공통 명령과 단조 증가 순번으로 전달되는지 확인 */
public final class PlayerControllerTest {
    public static void main(String[] args) {
        final GameEngine engine = new GameEngine("player", new PieceGenerator() {
            public PieceType nextPiece() { return PieceType.T; }
        });
        final List<GameAction> observed = new ArrayList<GameAction>();
        GameActionSink spy = new GameActionSink() {
            public ActionResult dispatch(GameAction action) {
                observed.add(action);
                return engine.dispatch(action);
            }
            public GameState getState() { return engine.getState(); }
        };
        Controller controller = new PlayerController(spy, "player");
        check(controller.submit(GameAction.Type.START).isAccepted(), "Start");
        check(controller.submit(GameAction.Type.GRAVITY_TICK).isAccepted(), "Timer tick");
        check(controller.submit(GameAction.Type.MOVE_LEFT).isAccepted(), "Key action");
        GameAction.TargetCell cell = new GameAction.TargetCell(2, 3);
        GameAction.ItemUse itemUse = new GameAction.ItemUse("hammer", "player", cell);
        ActionResult itemResult = controller.submit(itemUse);
        check(!itemResult.isAccepted() && itemResult.getReason().contains("not implemented"),
                "Phase 0 item is explicit unsupported action");
        check(controller.submit(GameAction.Type.MOVE_RIGHT).isAccepted(), "Sequence continues after rejection");
        GameAction.Garbage garbage = new GameAction.Garbage(2, 4);
        check(controller.submit(garbage).isAccepted(), "Trusted garbage action");
        check(controller.getState().getPendingGarbageLines() == 2, "Garbage queued through controller");
        check(observed.size() == 6, "Dispatch count");
        for (int i = 0; i < observed.size(); i++) {
            check(observed.get(i).getSequence() == i, "Shared monotonically increasing sequence");
            check("player".equals(observed.get(i).getActorId()), "Actor retained");
        }
        GameAction observedItem = observed.get(3);
        check(observedItem.getType() == GameAction.Type.USE_ITEM
                && observedItem.getItemUse() == itemUse
                && observedItem.getItemUse().getTargetCell().getX() == 2
                && observedItem.getItemUse().getTargetCell().getY() == 3,
                "Item payload remains intact");
        check(observed.get(5).getType() == GameAction.Type.RECEIVE_GARBAGE
                && observed.get(5).getGarbage() == garbage, "Garbage payload remains intact");
        check(controller.getState().getVersion() == 5 && controller.getState().getTick() == 1,
                "Controller reads engine state");
        boolean threw = false;
        try { new GameAction(GameAction.Type.MOVE_LEFT, "player", 6, itemUse); }
        catch (IllegalArgumentException expected) { threw = true; }
        check(threw, "Item payload cannot accompany movement");
        threw = false;
        try { new GameAction.ItemUse(" ", "player"); }
        catch (IllegalArgumentException expected) { threw = true; }
        check(threw, "Item ID validation");
        threw = false;
        try { new GameAction.TargetCell(-1, 0); }
        catch (IllegalArgumentException expected) { threw = true; }
        check(threw, "Target cell validation");
        check(java.lang.reflect.Modifier.isFinal(GameAction.ItemUse.class.getModifiers())
                && java.lang.reflect.Modifier.isFinal(GameAction.TargetCell.class.getModifiers())
                && java.lang.reflect.Modifier.isFinal(GameAction.Garbage.class.getModifiers()),
                "Payload value objects are final");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
