package kr.ac.jbnu.se.tetris.ai;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import kr.ac.jbnu.se.tetris.core.ActionResult;
import kr.ac.jbnu.se.tetris.core.Board;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameEngine;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.PieceGenerator;
import kr.ac.jbnu.se.tetris.core.PieceType;

/** 첫 줄 삭제와 연속 삭제의 콤보 관측 검증 */
public final class PlacementLogTest {
    public static void main(String[] args) throws Exception {
        GameEngine engine = new GameEngine(new PieceGenerator() {
            public PieceType nextPiece() { return PieceType.O; }
        });
        PlacementLog log = new PlacementLog();
        check(engine.dispatch(new GameAction(GameAction.Type.START, "local", 0)).isAccepted(), "Start");
        GameState first = engine.getState();
        int holeX = first.getPieceX();
        Field boardField = GameEngine.class.getDeclaredField("board");
        boardField.setAccessible(true);
        Board board = (Board) boardField.get(engine);
        Method setCell = Board.class.getDeclaredMethod("setCell", int.class, int.class, PieceType.class);
        setCell.setAccessible(true);
        for (int y = 0; y < 4; y++) for (int x = 0; x < first.getBoard().getWidth(); x++) {
            if (x != holeX && x != holeX + 1) setCell.invoke(board, x, y, PieceType.Z);
        }
        long sequence = 1;
        for (int expectedCombo = 0; expectedCombo < 2; expectedCombo++) {
            GameState before = engine.getState();
            ActionResult drop = engine.dispatch(new GameAction(GameAction.Type.HARD_DROP,
                    "local", sequence++));
            check(drop.isAccepted(), "Drop");
            log.observe(before, GameAction.Type.HARD_DROP, drop.getEvents());
            check(log.snapshot().get(expectedCombo).getLinesCleared() == 2,
                    "Line clear observed");
            check(log.snapshot().get(expectedCombo).getCombo() == expectedCombo,
                    "First clear and consecutive combo metadata");
            if (expectedCombo == 0) {
                before = engine.getState();
                ActionResult spawn = engine.dispatch(new GameAction(GameAction.Type.GRAVITY_TICK,
                        "local", sequence++));
                check(spawn.isAccepted(), "Delayed spawn");
                log.observe(before, GameAction.Type.GRAVITY_TICK, spawn.getEvents());
            }
        }
    }

    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
}
