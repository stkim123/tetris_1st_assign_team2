package kr.ac.jbnu.se.tetris.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 실제 엔진 상태의 표시용 사본과 입력 방어 복사 검증 */
public final class SnapshotContractTest {
    public static void main(String[] args) {
        boardAndGameDefensiveCopies();
        realEngineStates();
        invalidSnapshots();
    }

    private static void boardAndGameDefensiveCopies() {
        PieceType[] cells = emptyCells();
        cells[0] = PieceType.Z;
        BoardState board = CoreSnapshots.board(10, 22, cells);
        cells[0] = PieceType.EMPTY;
        check(board.getCell(0, 0) == PieceType.Z, "Caller cell array cannot change board");
        List<PieceType> next = new ArrayList<PieceType>(Arrays.asList(PieceType.I, PieceType.O));
        GameState display = CoreSnapshots.game("p-1", 3, 9, GameState.Status.READY,
                board, null, 0, 0, 0, false, PieceType.EMPTY, false,
                next, -1, -1, 0);
        next.clear();
        check(display.getNextPieces().size() == 2, "Caller preview list cannot change game");
        rejectUnsupported(new Runnable() {
            public void run() { display.getNextPieces().clear(); }
        });
        GameState copy = CoreSnapshots.copyOf(display);
        check(copy != display && copy.getBoard() != display.getBoard()
                && copy.getBoard().getCell(0, 0) == PieceType.Z
                && copy.getNextPieces().equals(display.getNextPieces()), "Independent display copy");
    }

    private static void realEngineStates() {
        GameEngine engine = new GameEngine("player-17", new PieceGenerator() {
            public PieceType nextPiece() { return PieceType.O; }
        });
        long[] sequence = {0};
        compare(engine.getState(), CoreSnapshots.copyOf(engine.getState()));
        send(engine, GameAction.Type.START, sequence);
        compare(engine.getState(), CoreSnapshots.copyOf(engine.getState()));
        send(engine, GameAction.Type.PAUSE, sequence);
        compare(engine.getState(), CoreSnapshots.copyOf(engine.getState()));
        send(engine, GameAction.Type.RESUME, sequence);

        // 다섯 O 피스로 아래 두 줄 완성 후 다음 피스 대기 상태 생성
        for (int targetX = 0; targetX <= 8; targetX += 2) {
            while (engine.getState().getPieceX() > targetX) {
                send(engine, GameAction.Type.MOVE_LEFT, sequence);
            }
            while (engine.getState().getPieceX() < targetX) {
                send(engine, GameAction.Type.MOVE_RIGHT, sequence);
            }
            send(engine, GameAction.Type.HARD_DROP, sequence);
        }
        GameState waiting = engine.getState();
        check(waiting.getStatus() == GameState.Status.RUNNING && waiting.isAwaitingSpawn()
                && waiting.getActivePiece() == null && waiting.getGhostY() == -1,
                "Real delayed spawn state");
        compare(waiting, CoreSnapshots.copyOf(waiting));
        send(engine, GameAction.Type.GRAVITY_TICK, sequence);
        compare(engine.getState(), CoreSnapshots.copyOf(engine.getState()));

        // 같은 위치의 반복 착지로 실제 탑아웃 상태 생성
        int placements = 0;
        while (engine.getState().getStatus() == GameState.Status.RUNNING && placements++ < 30) {
            if (engine.getState().isAwaitingSpawn()) send(engine, GameAction.Type.GRAVITY_TICK, sequence);
            else send(engine, GameAction.Type.HARD_DROP, sequence);
        }
        check(engine.getState().getStatus() == GameState.Status.GAME_OVER, "Real top out");
        compare(engine.getState(), CoreSnapshots.copyOf(engine.getState()));
    }

    private static void invalidSnapshots() {
        reject(new Runnable() { public void run() {
            CoreSnapshots.board(9, 22, emptyCells());
        }});
        reject(new Runnable() { public void run() {
            CoreSnapshots.board(10, 22, new PieceType[219]);
        }});
        reject(new Runnable() { public void run() {
            PieceType[] cells = emptyCells(); cells[3] = null;
            CoreSnapshots.board(10, 22, cells);
        }});
        final BoardState board = CoreSnapshots.board(10, 22, emptyCells());
        reject(new Runnable() { public void run() {
            CoreSnapshots.game(" ", 0, 0, GameState.Status.READY, board,
                    null, 0, 0, 0, false, PieceType.EMPTY, false,
                    new ArrayList<PieceType>(), -1, -1, 0);
        }});
        reject(new Runnable() { public void run() {
            CoreSnapshots.game("p", 0, 0, GameState.Status.RUNNING, board,
                    new Piece(PieceType.O), 4, 19, 0, false, PieceType.EMPTY, true,
                    Arrays.asList(PieceType.I), -1, -1, 0);
        }});
        reject(new Runnable() { public void run() {
            CoreSnapshots.game("p", 0, 0, GameState.Status.RUNNING, board,
                    null, 0, 0, 0, false, PieceType.EMPTY, false,
                    Arrays.asList(PieceType.I), -1, -1, 0);
        }});
        reject(new Runnable() { public void run() {
            CoreSnapshots.game("p", 0, 0, GameState.Status.READY, board,
                    null, 0, 0, 0, false, PieceType.EMPTY, false,
                    Arrays.asList(PieceType.GARBAGE), -1, -1, 0);
        }});
    }

    private static PieceType[] emptyCells() {
        PieceType[] cells = new PieceType[220];
        Arrays.fill(cells, PieceType.EMPTY);
        return cells;
    }

    private static void send(GameEngine engine, GameAction.Type type, long[] sequence) {
        ActionResult result = engine.dispatch(new GameAction(type, "player-17", sequence[0]++));
        check(result.isAccepted(), "Engine accepted " + type + ": " + result.getReason());
    }

    private static void compare(GameState source, GameState copy) {
        check(source != copy && source.getBoard() != copy.getBoard(), "Distinct state and board");
        check(source.getActorId().equals(copy.getActorId())
                && source.getVersion() == copy.getVersion() && source.getTick() == copy.getTick()
                && source.getStatus() == copy.getStatus() && source.getActivePiece() == copy.getActivePiece()
                && source.getPieceX() == copy.getPieceX() && source.getPieceY() == copy.getPieceY()
                && source.getLinesCleared() == copy.getLinesCleared()
                && source.isAwaitingSpawn() == copy.isAwaitingSpawn()
                && source.getHoldPiece() == copy.getHoldPiece() && source.canHold() == copy.canHold()
                && source.getNextPieces().equals(copy.getNextPieces())
                && source.getGhostY() == copy.getGhostY() && source.getCombo() == copy.getCombo()
                && source.getPendingGarbageLines() == copy.getPendingGarbageLines(), "Game fields retained");
        for (int y = 0; y < 22; y++) for (int x = 0; x < 10; x++) {
            check(source.getBoard().getCell(x, y) == copy.getBoard().getCell(x, y), "Board cells retained");
        }
    }

    private static void reject(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Invalid snapshot rejected");
    }

    private static void rejectUnsupported(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (UnsupportedOperationException expected) { rejected = true; }
        check(rejected, "Snapshot collection immutable");
    }

    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
}
