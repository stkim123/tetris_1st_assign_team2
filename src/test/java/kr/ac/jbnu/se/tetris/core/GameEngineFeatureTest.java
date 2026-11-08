package kr.ac.jbnu.se.tetris.core;

import java.lang.reflect.Field;
import java.util.List;

/** 컨트롤러가 사용하는 동일한 명령 경로로 1단계 코어 기능을 검증 */
public final class GameEngineFeatureTest {
    public static void main(String[] args) throws Exception {
        holdAndNext();
        failedPrefetchIsAtomic();
        ghostAndCombo();
        garbageBoundaryAndOverflow();
        tSpinCornerRuleAndDropReset();
    }

    private static void holdAndNext() {
        GameEngine engine = new GameEngine(sequence(PieceType.T, PieceType.I, PieceType.O,
                PieceType.Z, PieceType.L, PieceType.J, PieceType.S));
        check(engine.getState().getNextPieces().isEmpty(), "READY has no public preview");
        GameState started = send(engine, GameAction.Type.START, 0).getState();
        check(started.getActivePiece().getType() == PieceType.T, "First draw");
        check(started.getNextPieces().equals(java.util.Arrays.asList(PieceType.I, PieceType.O,
                PieceType.Z)), "Three ordered previews");
        check(started.getHoldPiece() == PieceType.EMPTY && started.canHold(), "Hold initially available");
        boolean immutable = false;
        try { started.getNextPieces().clear(); }
        catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "Preview list is immutable");
        ActionResult hold = send(engine, GameAction.Type.HOLD, 1);
        check(hold.isAccepted() && hold.getState().getActivePiece().getType() == PieceType.I,
                "Empty hold draws NEXT");
        check(hold.getState().getHoldPiece() == PieceType.T && !hold.getState().canHold(),
                "Hold is once per lock");
        check(hold.getState().getNextPieces().equals(java.util.Arrays.asList(
                PieceType.O, PieceType.Z, PieceType.L)), "Preview refilled");
        check(!send(engine, GameAction.Type.HOLD, 2).isAccepted(), "Second hold rejected");
        check(send(engine, GameAction.Type.HARD_DROP, 3).isAccepted(), "Lock after hold");
        check(engine.getState().canHold(), "Next lock resets hold use");
        ActionResult swapped = send(engine, GameAction.Type.HOLD, 4);
        check(swapped.isAccepted() && swapped.getState().getActivePiece().getType() == PieceType.T,
                "Occupied hold swaps");
        check(swapped.getState().getHoldPiece() == PieceType.O, "Swap stores outgoing piece");
        check(swapped.getState().getNextPieces().equals(java.util.Arrays.asList(
                PieceType.Z, PieceType.L, PieceType.J)), "Swap does not consume NEXT");
        check(started.getNextPieces().get(0) == PieceType.I, "Old snapshot remains frozen");
        boolean rejected = false;
        try { new Piece(PieceType.GARBAGE); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Garbage is not a spawnable tetromino");
    }

    private static void failedPrefetchIsAtomic() {
        GameEngine engine = new GameEngine(new PieceGenerator() {
            private int calls;
            public PieceType nextPiece() {
                calls++;
                if (calls == 3 || calls == 6) throw new IllegalStateException("temporary");
                PieceType[] stream = {PieceType.T, PieceType.I, PieceType.O, PieceType.Z,
                        PieceType.L, PieceType.J, PieceType.S};
                return stream[Math.min(calls - 1, stream.length - 1)];
            }
        });
        GameState ready = engine.getState();
        check(!send(engine, GameAction.Type.START, 0).isAccepted(), "Failed initial prefetch");
        samePublishedState(ready, engine.getState());
        GameState started = send(engine, GameAction.Type.START, 1).getState();
        check(started.getActivePiece().getType() == PieceType.T, "Successful draws retained");
        check(started.getNextPieces().equals(java.util.Arrays.asList(PieceType.I, PieceType.Z,
                PieceType.L)), "Preview tracks accepted draw sequence");
        GameState before = engine.getState();
        check(!send(engine, GameAction.Type.HOLD, 2).isAccepted(), "Failed refill rejects HOLD");
        samePublishedState(before, engine.getState());
        check(send(engine, GameAction.Type.HOLD, 3).isAccepted(), "Retry succeeds");
        check(engine.getState().getActivePiece().getType() == PieceType.I, "NEXT head is retained");
    }

    private static void ghostAndCombo() throws Exception {
        GameEngine engine = new GameEngine(constant(PieceType.O));
        send(engine, GameAction.Type.START, 0);
        GameState first = engine.getState();
        int ghost = first.getGhostY();
        check(ghost == PlacementSimulator.hardDrop(first.getBoard(), first.getActivePiece(),
                first.getPieceX(), first.getPieceY()).getY(), "Ghost matches simulator");
        Board board = boardOf(engine);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 10; x++) {
            if (x != 6 && x != 7) board.setCell(x, y, PieceType.Z);
        }
        // 두 번째 줄 제거에서 보드가 완전히 비면 퍼펙트 클리어로 콤보가 초기화되므로 한 칸을 남겨 콤보 이어짐을 확인
        board.setCell(0, 4, PieceType.Z);
        int sequence = 1;
        for (int round = 0; round < 2; round++) {
            ActionResult drop = send(engine, GameAction.Type.HARD_DROP, sequence++);
            GameEvent clear = event(drop.getEvents(), GameEvent.Type.LINE_CLEAR);
            check(clear != null && clear.getLineCount() == 2 && clear.getCombo() == round
                    && !clear.isTSpin(), "Line event carries combo and T-spin metadata");
            check(engine.getState().getCombo() == round, "Combo state");
            check((event(drop.getEvents(), GameEvent.Type.COMBO) != null) == (round > 0),
                    "Combo event after consecutive clear");
            check(engine.getState().getGhostY() == -1, "No ghost during delayed spawn");
            send(engine, GameAction.Type.GRAVITY_TICK, sequence++);
        }
        send(engine, GameAction.Type.HARD_DROP, sequence);
        check(engine.getState().getCombo() == -1, "No-clear lock resets combo");
    }

    private static void garbageBoundaryAndOverflow() {
        GameEngine engine = new GameEngine(constant(PieceType.O));
        send(engine, GameAction.Type.START, 0);
        GameState before = engine.getState();
        ActionResult queued = engine.dispatch(GameAction.garbage("local", 1, new GameAction.Garbage(1, 3)));
        check(queued.isAccepted() && event(queued.getEvents(), GameEvent.Type.GARBAGE_QUEUED).getLineCount() == 1,
                "Garbage accepted into pending queue");
        check(engine.getState().getPendingGarbageLines() == 1
                && engine.getState().getBoard().getCell(0, 0) == PieceType.EMPTY,
                "Queued attack leaves active board alone");
        ActionResult lock = send(engine, GameAction.Type.HARD_DROP, 2);
        check(event(lock.getEvents(), GameEvent.Type.GARBAGE_RECEIVED).getLineCount() == 1,
                "Garbage applied at next lock");
        check(lock.getState().getPendingGarbageLines() == 0, "Queue drained");
        check(lock.getState().getBoard().getCell(3, 0) == PieceType.EMPTY
                && lock.getState().getBoard().getCell(2, 0) == PieceType.GARBAGE
                && lock.getState().getBoard().getCell(6, 1) == PieceType.O,
                "Row hole and upward shift");
        check(before.getBoard().getCell(2, 0) == PieceType.EMPTY, "Old board remains frozen");

        GameEngine overflow = new GameEngine(constant(PieceType.O));
        send(overflow, GameAction.Type.START, 0);
        overflow.dispatch(GameAction.garbage("local", 1, new GameAction.Garbage(22, 3)));
        ActionResult result = send(overflow, GameAction.Type.HARD_DROP, 2);
        check(result.isAccepted() && result.getState().getStatus() == GameState.Status.GAME_OVER,
                "Garbage overflow top-out");
        check(event(result.getEvents(), GameEvent.Type.TOP_OUT) != null
                && event(result.getEvents(), GameEvent.Type.GAME_OVER) != null,
                "Top-out reported once");
        check("GARBAGE_TOP_OUT".equals(event(result.getEvents(), GameEvent.Type.GAME_OVER).getReason()),
                "Garbage overflow reason is distinct");
        check(!send(overflow, GameAction.Type.HARD_DROP, 3).isAccepted(), "Terminal state rejects input");

        GameEngine bounded = new GameEngine(constant(PieceType.O));
        send(bounded, GameAction.Type.START, 0);
        for (int index = 1; index <= 10; index++) {
            check(bounded.dispatch(GameAction.garbage("local", index,
                    new GameAction.Garbage(22, 3))).isAccepted(), "Pending queue within limit");
        }
        GameState atLimit = bounded.getState();
        check(!bounded.dispatch(GameAction.garbage("local", 11,
                new GameAction.Garbage(1, 3))).isAccepted(), "Pending queue is bounded");
        samePublishedState(atLimit, bounded.getState());
    }

    private static void tSpinCornerRuleAndDropReset() throws Exception {
        GameEngine spin = tFixture();
        check(send(spin, GameAction.Type.ROTATE_RIGHT, 1).isAccepted(), "Fixture rotation accepted");
        ActionResult atRest = send(spin, GameAction.Type.HARD_DROP, 2);
        check(event(atRest.getEvents(), GameEvent.Type.T_SPIN) != null, "Three-corner T-spin");
        GameEngine dropped = tFixture();
        Board board = boardOf(dropped);
        board.setCell(5, 0, PieceType.EMPTY);
        check(send(dropped, GameAction.Type.ROTATE_RIGHT, 1).isAccepted(), "Second rotation accepted");
        check(dropped.getState().getGhostY() == 1, "One-cell landing");
        ActionResult afterDrop = send(dropped, GameAction.Type.HARD_DROP, 2);
        check(event(afterDrop.getEvents(), GameEvent.Type.T_SPIN) == null,
                "Nonzero hard drop clears rotation qualifier");
    }

    private static GameEngine tFixture() throws Exception {
        GameEngine engine = new GameEngine(constant(PieceType.T));
        send(engine, GameAction.Type.START, 0);
        Board board = boardOf(engine);
        board.setCell(6, 1, PieceType.O);
        board.setCell(4, 3, PieceType.O);
        board.setCell(6, 3, PieceType.O);
        board.setCell(5, 0, PieceType.O);
        field(GameEngine.class, "pieceX").setInt(engine, 5);
        field(GameEngine.class, "pieceY").setInt(engine, 2);
        return engine;
    }

    private static Board boardOf(GameEngine engine) throws Exception {
        return (Board) field(GameEngine.class, "board").get(engine);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static GameEvent event(List<GameEvent> events, GameEvent.Type type) {
        for (GameEvent event : events) if (event.getType() == type) return event;
        return null;
    }

    private static void samePublishedState(GameState before, GameState after) {
        check(before.getVersion() == after.getVersion()
                && before.getStatus() == after.getStatus()
                && before.getActivePiece() == after.getActivePiece()
                && before.getNextPieces().equals(after.getNextPieces())
                && before.getHoldPiece() == after.getHoldPiece()
                && before.getGhostY() == after.getGhostY()
                && before.getPendingGarbageLines() == after.getPendingGarbageLines(),
                "Rejected action is state neutral");
        for (int y = 0; y < before.getBoard().getHeight(); y++) {
            for (int x = 0; x < before.getBoard().getWidth(); x++) {
                check(before.getBoard().getCell(x, y) == after.getBoard().getCell(x, y),
                        "Failed prefetch keeps board");
            }
        }
    }

    private static PieceGenerator constant(final PieceType piece) {
        return new PieceGenerator() { public PieceType nextPiece() { return piece; } };
    }

    private static PieceGenerator sequence(final PieceType... pieces) {
        return new PieceGenerator() {
            private int index;
            public PieceType nextPiece() { return pieces[Math.min(index++, pieces.length - 1)]; }
        };
    }

    private static ActionResult send(GameEngine engine, GameAction.Type type, long sequence) {
        return engine.dispatch(new GameAction(type, "local", sequence));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
