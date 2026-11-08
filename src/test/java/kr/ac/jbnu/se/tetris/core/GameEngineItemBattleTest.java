package kr.ac.jbnu.se.tetris.core;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

/** 아이템 원본 식별과 전투 착지 경계를 실제 명령 순서로 검증한다. */
public final class GameEngineItemBattleTest {
    public static void main(String[] args) throws Exception {
        fifthPieceGetsItem();
        oreOnlyMinedWhenItsOwnRowClears();
        holdKeepsItemIdentity();
        rotationKeepsOreCell();
        clearsTwoOriginalPiecesOnceEach();
        managedLockAndGarbageBoundary();
    }

    private static void oreOnlyMinedWhenItsOwnRowClears() {
        Board board = new Board();
        for (int x = 0; x < 10; x++) if (x != 4 && x != 5) board.setCell(x, 0, PieceType.T);
        Piece ore = Piece.withItem(PieceType.O, "heal", 17, 0);
        board.place(ore, 4, 1);
        check(board.snapshot().getItemId(4, 1).equals("heal")
                && board.snapshot().getItemId(4, 0) == null, "Only designated cell is marked");
        check(board.itemsOnCompletedRows(java.util.Collections.<Long>emptySet()).isEmpty(),
                "Clearing other cells from ore mino does not mine it");
        board.removeFullLines();
        for (int x = 0; x < 10; x++) if (x != 4 && x != 5) board.setCell(x, 0, PieceType.T);
        GameEvent.ItemExtraction mined = board.itemsOnCompletedRows(
                java.util.Collections.<Long>emptySet()).get(17L);
        check(mined != null && mined.getX() == 4 && mined.getY() == 0
                && mined.getItemId().equals("heal"), "Ore extraction has pre-compression board coordinate");
        board.clearCollectedItemMarkers(java.util.Collections.singleton(17L));
        check(board.itemsOnCompletedRows(java.util.Collections.<Long>emptySet()).isEmpty(),
                "Mined origin cannot award twice");
    }

    private static void rotationKeepsOreCell() {
        Piece ore = Piece.withItem(PieceType.T, "shield", 31, 3);
        Piece rotated = ore.rotateRight().rotateRight().rotateLeft();
        check(rotated.getOreCellIndex() == 3 && rotated.hasOreAt(3)
                && !rotated.hasOreAt(0), "Rotation preserves original ore cell index");
        Board board = new Board();
        board.place(rotated, 5, 3);
        int marked = 0;
        for (int y = 0; y < 22; y++) for (int x = 0; x < 10; x++)
            if (board.snapshot().getItemId(x, y) != null) marked++;
        check(marked == 1, "Rotated piece still places one ore cell");
    }

    private static void fifthPieceGetsItem() {
        GameEngine engine = engine();
        engine.setBattleManaged(true);
        engine.configureItemSpawns(5, 19L);
        send(engine, GameAction.Type.START, 0);
        long sequence = 1;
        for (int index = 1; index <= 4; index++) {
            check(engine.getState().getActivePiece().getItemId() == null,
                    "Only the fifth original piece receives an item");
            send(engine, GameAction.Type.HARD_DROP, sequence++);
            send(engine, GameAction.Type.GRAVITY_TICK, sequence++);
        }
        check(engine.getState().getActivePiece().getItemId() != null,
                "Fifth original piece has one item");
    }

    private static void holdKeepsItemIdentity() {
        GameEngine engine = engine();
        engine.configureItemSpawns(1, 21L);
        send(engine, GameAction.Type.START, 0);
        Piece first = engine.getState().getActivePiece();
        check(first.getItemId() != null, "Spawned item piece");
        send(engine, GameAction.Type.HOLD, 1);
        check(first.getItemId().equals(engine.getState().getHoldItemId()), "HOLD retains item");
        send(engine, GameAction.Type.HARD_DROP, 2);
        send(engine, GameAction.Type.HOLD, 3);
        Piece restored = engine.getState().getActivePiece();
        check(restored.getIdentity() == first.getIdentity()
                && first.getItemId().equals(restored.getItemId())
                && first.getOreCellIndex() == restored.getOreCellIndex(),
                "Swap retains original identity and ore index");
    }

    private static void clearsTwoOriginalPiecesOnceEach() throws Exception {
        GameEngine engine = engine();
        engine.configureItemSpawns(1, 3L);
        send(engine, GameAction.Type.START, 0);
        Board board = boardOf(engine);
        for (int y = 0; y < 2; y++) for (int x = 0; x < 10; x++) {
            if (x != 4 && x != 5 && x != 6 && x != 7) board.setCell(x, y, PieceType.T);
        }
        Piece first = engine.getState().getActivePiece();
        send(engine, GameAction.Type.HARD_DROP, 1);
        int oreCells = 0;
        for (int y = 0; y < 22; y++) for (int x = 0; x < 10; x++)
            if (engine.getState().getBoard().getItemId(x, y) != null) oreCells++;
        check(oreCells == 1, "Placed item metadata belongs to one cell");
        Piece second = engine.getState().getActivePiece();
        send(engine, GameAction.Type.MOVE_LEFT, 2);
        send(engine, GameAction.Type.MOVE_LEFT, 3);
        ActionResult clear = send(engine, GameAction.Type.HARD_DROP, 4);
        GameEvent event = event(clear.getEvents(), GameEvent.Type.LINE_CLEAR);
        check(event != null && event.getCollectedItems().size() == 2,
                "Both source pieces collect once when rows clear");
        check(event.getCollectedItems().contains(first.getItemId())
                && event.getCollectedItems().contains(second.getItemId()), "Correct item IDs collected");
        check(event.getItemExtractions().size() == 2,
                "LINE_CLEAR identifies each mined cell before line compression");
        boolean immutable = false;
        try { event.getCollectedItems().clear(); }
        catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "Collected item list is immutable");
    }

    private static void managedLockAndGarbageBoundary() {
        GameEngine engine = engine();
        engine.setBattleManaged(true);
        send(engine, GameAction.Type.START, 0);
        ActionResult locked = send(engine, GameAction.Type.HARD_DROP, 1);
        check(locked.getState().isAwaitingSpawn() && locked.getState().getActivePiece() == null,
                "Managed lock waits for battle effects even without line clear");
        check(!send(engine, GameAction.Type.MOVE_LEFT, 2).isAccepted(), "Cannot move during boundary");
        ActionResult raised = engine.applyGarbage(Arrays.asList(new GameAction.Garbage(2, 3)));
        check(raised.isAccepted() && event(raised.getEvents(), GameEvent.Type.GARBAGE_RECEIVED) != null,
                "Managed garbage applied at boundary");
        check(raised.getState().getBoard().getCell(3, 0) == PieceType.EMPTY
                && raised.getState().getBoard().getCell(2, 0) == PieceType.GARBAGE,
                "Garbage geometry");
        ActionResult cleaned = engine.clearBottomGarbageLine();
        check(cleaned.isAccepted() && event(cleaned.getEvents(), GameEvent.Type.GARBAGE_CLEANED) != null,
                "Cleaner removes bottom garbage row");
        check(send(engine, GameAction.Type.GRAVITY_TICK, 3).getState().getActivePiece() != null,
                "Battle resumes spawning on explicit gravity tick");
    }

    private static GameEngine engine() {
        return new GameEngine(new PieceGenerator() {
            public PieceType nextPiece() { return PieceType.O; }
        });
    }

    private static Board boardOf(GameEngine engine) throws Exception {
        Field field = GameEngine.class.getDeclaredField("board");
        field.setAccessible(true);
        return (Board) field.get(engine);
    }

    private static ActionResult send(GameEngine engine, GameAction.Type type, long sequence) {
        return engine.dispatch(new GameAction(type, "local", sequence));
    }

    private static GameEvent event(List<GameEvent> events, GameEvent.Type type) {
        for (GameEvent event : events) if (event.getType() == type) return event;
        return null;
    }

    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
}
