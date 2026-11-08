package kr.ac.jbnu.se.tetris.core;

import java.util.List;

/** 명령 처리, 상태 전이, 이벤트와 생성기 실패 처리의 회귀를 확인 */
public final class GameEngineTest {
    public static void main(String[] args) {
        lifecycleAndSequences();
        movementAndLocking();
        clearsDelaySpawn();
        topOutOnce();
        seededReplay();
        generatorFailuresAreAtomic();
    }

    private static void lifecycleAndSequences() {
        GameEngine engine = new GameEngine("alpha", constant(PieceType.I));
        GameState ready = engine.getState();
        check(ready.getStatus() == GameState.Status.READY && ready.getVersion() == 0, "Ready");
        ActionResult invalidActor = engine.dispatch(new GameAction(GameAction.Type.START, "other", 0));
        reject(invalidActor, 0, 0, "Wrong actor");
        ActionResult start = engine.dispatch(new GameAction(GameAction.Type.START, "alpha", 0));
        accepted(start, 1, 0, GameEvent.Type.GAME_STARTED, GameEvent.Type.PIECE_SPAWNED);
        check(start.getState().getPieceX() == 6 && start.getState().getPieceY() == 20, "Legacy spawn");
        check(start.getEvents().get(0).getEventId() == 2, "Rejected action has event ID");
        reject(engine.dispatch(new GameAction(GameAction.Type.START, "alpha", 1)), 1, 0, "START requires READY");
        reject(engine.dispatch(new GameAction(GameAction.Type.USE_ITEM, "alpha", 2)), 1, 0, "not implemented");
        reject(engine.dispatch(new GameAction(GameAction.Type.RECEIVE_GARBAGE, "alpha", 3)), 1, 0,
                "requires a payload");
        reject(engine.dispatch(new GameAction(GameAction.Type.MOVE_LEFT, "alpha", 3)), 1, 0, "Sequence");
        accepted(engine.dispatch(new GameAction(GameAction.Type.PAUSE, "alpha", 4)), 2, 0, GameEvent.Type.PAUSED);
        reject(engine.dispatch(new GameAction(GameAction.Type.GRAVITY_TICK, "alpha", 5)), 2, 0, "RUNNING");
        accepted(engine.dispatch(new GameAction(GameAction.Type.RESUME, "alpha", 6)), 3, 0, GameEvent.Type.RESUMED);
        accepted(engine.dispatch(new GameAction(GameAction.Type.GRAVITY_TICK, "alpha", 7)), 4, 1, GameEvent.Type.PIECE_MOVED);
        check(ready.getBoard().getCell(0, 0) == PieceType.EMPTY, "Old snapshot stays frozen");
    }

    private static void movementAndLocking() {
        GameEngine engine = new GameEngine(constant(PieceType.I));
        send(engine, GameAction.Type.START, 0);
        for (int i = 1; i <= 6; i++) accepted(send(engine, GameAction.Type.MOVE_LEFT, i), i + 1, 0,
                GameEvent.Type.PIECE_MOVED);
        reject(send(engine, GameAction.Type.MOVE_LEFT, 7), 7, 0, "collision");
        reject(send(engine, GameAction.Type.ROTATE_RIGHT, 8), 7, 0, "collision");
        ActionResult soft = send(engine, GameAction.Type.SOFT_DROP, 9);
        accepted(soft, 8, 0, GameEvent.Type.PIECE_MOVED);
        check(soft.getState().getPieceY() == 19, "Soft drop moves one row");
        ActionResult hard = send(engine, GameAction.Type.HARD_DROP, 10);
        accepted(hard, 9, 0, GameEvent.Type.PIECE_PLACED, GameEvent.Type.PIECE_SPAWNED);
        GameEvent placed = hard.getEvents().get(0);
        check(placed.getPiece().getType() == PieceType.I && placed.getX() == 0 && placed.getY() == 2,
                "Placement payload");
        check(hard.getState().getBoard().getCell(0, 0) == PieceType.I, "Hard drop reaches floor");
        // 아래로 움직일 수 없는 소프트 드롭도 입력으로 수락되며 현재 블록을 고정
        GameEngine square = new GameEngine(constant(PieceType.O));
        send(square, GameAction.Type.START, 0);
        for (int i = 1; i <= 20; i++) send(square, GameAction.Type.SOFT_DROP, i);
        ActionResult lock = send(square, GameAction.Type.SOFT_DROP, 21);
        accepted(lock, 22, 0, GameEvent.Type.PIECE_PLACED, GameEvent.Type.PIECE_SPAWNED);
    }

    private static void clearsDelaySpawn() {
        GameEngine engine = new GameEngine(constant(PieceType.O));
        int sequence = 0;
        send(engine, GameAction.Type.START, sequence++);
        int[] columns = {0, 2, 4, 6, 8};
        for (int target : columns) {
            while (engine.getState().getPieceX() > target) send(engine, GameAction.Type.MOVE_LEFT, sequence++);
            while (engine.getState().getPieceX() < target) send(engine, GameAction.Type.MOVE_RIGHT, sequence++);
            ActionResult placement = send(engine, GameAction.Type.HARD_DROP, sequence++);
            if (target == 8) {
                types(placement.getEvents(), GameEvent.Type.PIECE_PLACED, GameEvent.Type.LINE_CLEAR);
                check(placement.getEvents().get(1).getLineCount() == 2, "Two-line event payload");
                check(placement.getState().getLinesCleared() == 2, "Cumulative lines");
                check(placement.getState().isAwaitingSpawn() && placement.getState().getActivePiece() == null,
                        "Delayed spawn state");
            }
        }
        long version = engine.getState().getVersion();
        reject(send(engine, GameAction.Type.MOVE_LEFT, sequence++), version, 0, "Waiting");
        accepted(send(engine, GameAction.Type.PAUSE, sequence++), version + 1, 0, GameEvent.Type.PAUSED);
        accepted(send(engine, GameAction.Type.RESUME, sequence++), version + 2, 0, GameEvent.Type.RESUMED);
        ActionResult spawn = send(engine, GameAction.Type.GRAVITY_TICK, sequence);
        accepted(spawn, version + 3, 1, GameEvent.Type.PIECE_SPAWNED);
        check(!spawn.getState().isAwaitingSpawn(), "Spawn wait ends");
    }

    private static void topOutOnce() {
        GameEngine engine = new GameEngine(constant(PieceType.O));
        send(engine, GameAction.Type.START, 0);
        ActionResult last = null;
        for (int i = 1; i <= 11; i++) last = send(engine, GameAction.Type.HARD_DROP, i);
        types(last.getEvents(), GameEvent.Type.PIECE_PLACED, GameEvent.Type.TOP_OUT, GameEvent.Type.GAME_OVER);
        check(last.getState().getStatus() == GameState.Status.GAME_OVER, "Top out status");
        check("TOP_OUT".equals(last.getEvents().get(2).getReason()), "Game over reason");
        reject(send(engine, GameAction.Type.HARD_DROP, 12), last.getState().getVersion(), 0, "RUNNING");
    }

    private static void seededReplay() {
        GameEngine a = new GameEngine(new SeededPieceGenerator(42));
        GameEngine b = new GameEngine(new SeededPieceGenerator(42));
        for (int sequence = 0; sequence < 25; sequence++) {
            GameAction.Type action = sequence == 0 ? GameAction.Type.START : GameAction.Type.HARD_DROP;
            ActionResult ar = send(a, action, sequence);
            ActionResult br = send(b, action, sequence);
            check(ar.isAccepted() == br.isAccepted(), "Same seed acceptance");
            GameState as = ar.getState(), bs = br.getState();
            check(as.getStatus() == bs.getStatus() && as.getVersion() == bs.getVersion(), "Same seed state");
            Piece ap = as.getActivePiece(), bp = bs.getActivePiece();
            check((ap == null && bp == null) || (ap != null && bp != null && ap.getType() == bp.getType()),
                    "Same seed piece stream");
            for (int y = 0; y < 22; y++) for (int x = 0; x < 10; x++) {
                check(as.getBoard().getCell(x, y) == bs.getBoard().getCell(x, y), "Same seed board");
            }
        }
    }

    private static void generatorFailuresAreAtomic() {
        final PieceType[] invalidFirst = {null, PieceType.EMPTY};
        for (PieceType invalid : invalidFirst) {
            GameEngine engine = new GameEngine(sequence(invalid, PieceType.O));
            GameState before = engine.getState();
            reject(send(engine, GameAction.Type.START, 0), 0, 0, "Piece generator returned");
            sameState(before, engine.getState());
            reject(send(engine, GameAction.Type.START, 0), 0, 0, "Sequence");
            accepted(send(engine, GameAction.Type.START, 1), 1, 0,
                    GameEvent.Type.GAME_STARTED, GameEvent.Type.PIECE_SPAWNED);
        }
        GameEngine throwing = new GameEngine(new PieceGenerator() {
            public PieceType nextPiece() { throw new IllegalStateException("broken source"); }
        });
        GameState ready = throwing.getState();
        reject(send(throwing, GameAction.Type.START, 0), 0, 0, "broken source");
        sameState(ready, throwing.getState());

        // START의 NEXT 세 개 공개에 따른 첫 고정 후 보충용 다섯 번째 추첨 결과 사용
        GameEngine hard = new GameEngine(sequence(PieceType.O, PieceType.O, PieceType.O,
                PieceType.O, null, PieceType.O));
        send(hard, GameAction.Type.START, 0);
        GameState beforeHard = hard.getState();
        reject(send(hard, GameAction.Type.HARD_DROP, 1), beforeHard.getVersion(), 0,
                "Piece generator returned");
        sameState(beforeHard, hard.getState());
        accepted(send(hard, GameAction.Type.HARD_DROP, 2), 2, 0,
                GameEvent.Type.PIECE_PLACED, GameEvent.Type.PIECE_SPAWNED);

        GameEngine soft = new GameEngine(sequence(PieceType.O, PieceType.O, PieceType.O,
                PieceType.O, PieceType.EMPTY, PieceType.O));
        send(soft, GameAction.Type.START, 0);
        for (int i = 1; i <= 20; i++) send(soft, GameAction.Type.SOFT_DROP, i);
        GameState beforeSoftLock = soft.getState();
        reject(send(soft, GameAction.Type.SOFT_DROP, 21), beforeSoftLock.getVersion(), 0,
                "Piece generator returned");
        sameState(beforeSoftLock, soft.getState());
        accepted(send(soft, GameAction.Type.SOFT_DROP, 22), beforeSoftLock.getVersion() + 1, 0,
                GameEvent.Type.PIECE_PLACED, GameEvent.Type.PIECE_SPAWNED);

        GameEngine gravity = new GameEngine(sequence(PieceType.O, PieceType.O, PieceType.O,
                PieceType.O, null, PieceType.O));
        send(gravity, GameAction.Type.START, 0);
        for (int i = 1; i <= 20; i++) send(gravity, GameAction.Type.GRAVITY_TICK, i);
        GameState beforeGravityLock = gravity.getState();
        reject(send(gravity, GameAction.Type.GRAVITY_TICK, 21), beforeGravityLock.getVersion(),
                beforeGravityLock.getTick(), "Piece generator returned");
        sameState(beforeGravityLock, gravity.getState());
        accepted(send(gravity, GameAction.Type.GRAVITY_TICK, 22), beforeGravityLock.getVersion() + 1,
                beforeGravityLock.getTick() + 1,
                GameEvent.Type.PIECE_PLACED, GameEvent.Type.PIECE_SPAWNED);

        GameEngine delayed = new GameEngine(new PieceGenerator() {
            private int calls;
            public PieceType nextPiece() {
                calls++;
                if (calls == 9) throw new IllegalStateException("delayed source failed");
                return PieceType.O;
            }
        });
        int sequence = 0;
        send(delayed, GameAction.Type.START, sequence++);
        int[] columns = {0, 2, 4, 6, 8};
        for (int target : columns) {
            while (delayed.getState().getPieceX() > target) send(delayed, GameAction.Type.MOVE_LEFT, sequence++);
            while (delayed.getState().getPieceX() < target) send(delayed, GameAction.Type.MOVE_RIGHT, sequence++);
            send(delayed, GameAction.Type.HARD_DROP, sequence++);
        }
        GameState waiting = delayed.getState();
        check(waiting.isAwaitingSpawn(), "Expected delayed spawn");
        reject(send(delayed, GameAction.Type.GRAVITY_TICK, sequence++), waiting.getVersion(),
                waiting.getTick(), "delayed source failed");
        sameState(waiting, delayed.getState());
        accepted(send(delayed, GameAction.Type.GRAVITY_TICK, sequence), waiting.getVersion() + 1,
                waiting.getTick() + 1, GameEvent.Type.PIECE_SPAWNED);
    }

    private static PieceGenerator sequence(final PieceType... types) {
        return new PieceGenerator() {
            private int index;
            public PieceType nextPiece() { return types[Math.min(index++, types.length - 1)]; }
        };
    }

    private static void sameState(GameState a, GameState b) {
        check(a.getVersion() == b.getVersion() && a.getTick() == b.getTick()
                && a.getStatus() == b.getStatus()
                && a.getActivePiece() == b.getActivePiece()
                && a.getPieceX() == b.getPieceX() && a.getPieceY() == b.getPieceY()
                && a.getLinesCleared() == b.getLinesCleared()
                && a.isAwaitingSpawn() == b.isAwaitingSpawn()
                && a.getHoldPiece() == b.getHoldPiece()
                && a.canHold() == b.canHold()
                && a.getNextPieces().equals(b.getNextPieces())
                && a.getGhostY() == b.getGhostY()
                && a.getCombo() == b.getCombo()
                && a.getPendingGarbageLines() == b.getPendingGarbageLines(),
                "Engine state unchanged");
        for (int y = 0; y < 22; y++) for (int x = 0; x < 10; x++) {
            check(a.getBoard().getCell(x, y) == b.getBoard().getCell(x, y),
                    "Board unchanged at " + x + "," + y);
        }
    }

    private static PieceGenerator constant(final PieceType type) {
        return new PieceGenerator() { public PieceType nextPiece() { return type; } };
    }

    private static ActionResult send(GameEngine engine, GameAction.Type type, long sequence) {
        return engine.dispatch(new GameAction(type, engine.getState().getActorId(), sequence));
    }

    private static void accepted(ActionResult result, long version, long tick, GameEvent.Type... expected) {
        check(result.isAccepted(), "Expected accepted: " + result.getReason());
        check(result.getState().getVersion() == version && result.getState().getTick() == tick,
                "Accepted version/tick");
        types(result.getEvents(), expected);
        long previous = 0;
        for (GameEvent event : result.getEvents()) {
            check(event.getStateVersion() == version && event.getTick() == tick, "Event version/tick");
            check(event.getEventId() > previous, "Event IDs increase");
            previous = event.getEventId();
        }
    }

    private static void reject(ActionResult result, long version, long tick, String reason) {
        check(!result.isAccepted() && result.getReason().contains(reason), "Expected rejection: " + reason);
        check(result.getState().getVersion() == version && result.getState().getTick() == tick,
                "Rejection is state neutral");
        types(result.getEvents(), GameEvent.Type.ACTION_REJECTED);
        check(reason.equals("Wrong actor") || result.getEvents().get(0).getReason().contains(reason),
                "Rejection reason payload");
    }

    private static void types(List<GameEvent> events, GameEvent.Type... expected) {
        check(events.size() == expected.length, "Event count " + events.size());
        for (int i = 0; i < expected.length; i++) check(events.get(i).getType() == expected[i],
                "Event order at " + i);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
