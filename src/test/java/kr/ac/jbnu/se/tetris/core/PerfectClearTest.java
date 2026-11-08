package kr.ac.jbnu.se.tetris.core;

import java.util.List;

/** 퍼펙트 클리어 판정, 이벤트 표시, 콤보 초기화, 가비지 순서를 확인 */
public final class PerfectClearTest {
    public static void main(String[] args) {
        emptyBoardDoubleIsPerfect();
        leftoverBlockIsNotPerfect();
        comboRestartsAfterPerfectClear();
        perfectIsJudgedBeforeGarbageLands();
        onlyLineClearEventsCanBePerfect();
    }

    /** 빈 보드에 O 다섯 개로 두 줄을 지우면 퍼펙트 클리어, 콤보 이벤트 없이 콤보 없음 상태로 돌아감 */
    private static void emptyBoardDoubleIsPerfect() {
        GameEngine engine = new GameEngine(constant(PieceType.O));
        send(engine, GameAction.Type.START, 0);
        ActionResult last = fillTwoRows(engine, 1).result;
        GameEvent clear = event(last.getEvents(), GameEvent.Type.LINE_CLEAR);
        check(clear != null && clear.getLineCount() == 2 && clear.isPerfectClear(), "perfect clear flagged");
        check(clear.getCombo() == 0, "event still carries the first clear as combo 0");
        check(event(last.getEvents(), GameEvent.Type.COMBO) == null, "no combo event for perfect clear");
        check(engine.getState().getCombo() == -1, "combo state reset to none");
        check(engine.getState().getBoard().getCell(0, 0) == PieceType.EMPTY, "board is empty");
    }

    /** 먼저 쌓은 블록 하나가 남으면 퍼펙트 클리어가 아니고 콤보는 이어짐 */
    private static void leftoverBlockIsNotPerfect() {
        GameEngine engine = new GameEngine(constant(PieceType.O));
        send(engine, GameAction.Type.START, 0);
        // 0열에 O를 두 번 쌓은 뒤 나머지 열을 채우면 아래 두 줄만 지워지고 위의 O 하나가 남음
        Placement placement = null;
        long sequence = 1;
        for (int x : new int[] {0, 0, 2, 4, 6, 8}) {
            placement = place(engine, x, sequence);
            sequence = placement.nextSequence;
        }
        GameEvent clear = event(placement.result.getEvents(), GameEvent.Type.LINE_CLEAR);
        check(clear != null && clear.getLineCount() == 2 && !clear.isPerfectClear(), "leftover is not perfect");
        check(engine.getState().getCombo() == 0, "combo stays after normal clear");
        check(engine.getState().getBoard().getCell(0, 0) == PieceType.O, "leftover block fell to the bottom");
    }

    /** 퍼펙트 클리어 뒤 다음 줄 제거는 다시 0콤보에서 시작 */
    private static void comboRestartsAfterPerfectClear() {
        GameEngine engine = new GameEngine(constant(PieceType.O));
        send(engine, GameAction.Type.START, 0);
        Placement first = fillTwoRows(engine, 1);
        check(first.result.getEvents().size() > 0 && engine.getState().isAwaitingSpawn(),
                "next piece waits after the clear");
        ActionResult spawn = send(engine, GameAction.Type.GRAVITY_TICK, first.nextSequence);
        check(spawn.isAccepted() && engine.getState().getActivePiece() != null, "next piece spawns on tick");
        ActionResult second = fillTwoRows(engine, first.nextSequence + 1).result;
        GameEvent clear = event(second.getEvents(), GameEvent.Type.LINE_CLEAR);
        check(clear != null && clear.getCombo() == 0 && clear.isPerfectClear(),
                "next clear after perfect clear starts again at combo 0");
    }

    /** 가비지는 줄 제거 직후 올라오지만 퍼펙트 클리어는 가비지 삽입 전 보드로 판정 */
    private static void perfectIsJudgedBeforeGarbageLands() {
        GameEngine engine = new GameEngine(constant(PieceType.O));
        send(engine, GameAction.Type.START, 0);
        // 가비지는 다음 착지 때 올라오므로 마지막 블록을 놓기 직전에 대기열에 넣어야 같은 착지에서 함께 처리됨
        long sequence = 1;
        for (int x : new int[] {0, 2, 4, 6}) sequence = place(engine, x, sequence).nextSequence;
        check(engine.dispatch(GameAction.garbage("local", sequence++, new GameAction.Garbage(1, 3))).isAccepted(),
                "garbage queued before the last piece");
        ActionResult last = place(engine, 8, sequence).result;
        GameEvent clear = event(last.getEvents(), GameEvent.Type.LINE_CLEAR);
        check(clear != null && clear.isPerfectClear(), "perfect before garbage lands");
        check(event(last.getEvents(), GameEvent.Type.GARBAGE_RECEIVED) != null, "garbage still lands at lock");
        check(engine.getState().getBoard().getCell(2, 0) == PieceType.GARBAGE, "garbage row now on board");
    }

    private static void onlyLineClearEventsCanBePerfect() {
        try {
            new GameEvent(GameEvent.Type.GAME_STARTED, 1, 0, 0, "local", null, 0, 0, 0, null, -1, false, true);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("non LINE_CLEAR perfect event accepted");
    }

    /** O 블록 다섯 개를 0, 2, 4, 6, 8열에 차례로 놓아 열 개의 칸 두 줄을 채움, 마지막 착지 결과 반환 */
    private static Placement fillTwoRows(GameEngine engine, long firstSequence) {
        long sequence = firstSequence;
        Placement last = null;
        for (int x : new int[] {0, 2, 4, 6, 8}) {
            last = place(engine, x, sequence);
            sequence = last.nextSequence;
        }
        return last;
    }

    private static Placement place(GameEngine engine, int column, long sequence) {
        check(engine.getState().getActivePiece() != null, "an active piece is needed to place");
        for (int moves = 0; engine.getState().getPieceX() != column; moves++) {
            check(moves < 12, "piece did not reach column " + column);
            GameAction.Type direction = engine.getState().getPieceX() > column
                    ? GameAction.Type.MOVE_LEFT : GameAction.Type.MOVE_RIGHT;
            check(send(engine, direction, sequence++).isAccepted(), "move accepted");
        }
        ActionResult result = send(engine, GameAction.Type.HARD_DROP, sequence++);
        check(result.isAccepted(), "drop accepted");
        return new Placement(result, sequence);
    }

    private static final class Placement {
        private final ActionResult result;
        private final long nextSequence;

        private Placement(ActionResult result, long nextSequence) {
            this.result = result;
            this.nextSequence = nextSequence;
        }
    }

    private static GameEvent event(List<GameEvent> events, GameEvent.Type type) {
        for (GameEvent event : events) if (event.getType() == type) return event;
        return null;
    }

    private static PieceGenerator constant(final PieceType piece) {
        return new PieceGenerator() { public PieceType nextPiece() { return piece; } };
    }

    private static ActionResult send(GameEngine engine, GameAction.Type type, long sequence) {
        return engine.dispatch(new GameAction(type, "local", sequence));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
