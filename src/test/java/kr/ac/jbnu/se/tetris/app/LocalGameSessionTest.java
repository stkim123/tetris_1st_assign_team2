package kr.ac.jbnu.se.tetris.app;

import kr.ac.jbnu.se.tetris.ai.AIPlan;
import kr.ac.jbnu.se.tetris.ai.HeuristicStrategy;
import kr.ac.jbnu.se.tetris.ai.HeuristicWeights;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.PieceType;

/** 로컬 모드의 실제 엔진 조작과 서로 다른 종료 조건을 검증한다. */
public final class LocalGameSessionTest {
    public static void main(String[] args) {
        rejectsNullMode();
        sameSeedReplaysSameGame();
        infiniteInputLifecycleAndTopOut();
        sprintClearsFortyRealLines();
    }

    private static void rejectsNullMode() {
        try {
            new LocalGameSession(null, 1);
            throw new AssertionError("null local mode must be rejected");
        } catch (IllegalArgumentException expected) { }
    }

    private static void sameSeedReplaysSameGame() {
        LocalGameSession first = new LocalGameSession(LocalGameSession.Mode.INFINITE, 2026);
        LocalGameSession replay = new LocalGameSession(LocalGameSession.Mode.INFINITE, 2026);
        try {
            for (int turn = 0; turn < 30; turn++) {
                checkSameState(first.getPlayerState(), replay.getPlayerState());
                if (first.isFinished()) break;
                if (first.getPlayerState().isAwaitingSpawn()) {
                    first.tick();
                    replay.tick();
                } else {
                    first.submit(GameAction.Type.HARD_DROP);
                    replay.submit(GameAction.Type.HARD_DROP);
                }
            }
            check(first.isFinished() && replay.isFinished(),
                    "equal seven bag seeds replay the same top out");
            checkSameState(first.getPlayerState(), replay.getPlayerState());
        } finally {
            first.close();
            replay.close();
        }
    }

    private static void infiniteInputLifecycleAndTopOut() {
        LocalGameSession session = new LocalGameSession(LocalGameSession.Mode.INFINITE, 42);
        try {
            check(session.getMode() == LocalGameSession.Mode.INFINITE, "mode is retained");
            check(session.getBattleState() == null && !session.isThinking(), "no battle or AI state");
            check(!session.isCompleted() && !session.isFinished(), "infinite starts active");
            check(session.getInstruction().equals("지운 줄: 0"), "cleared lines shown");
            check(session.getPlayerState().getStatus() == GameState.Status.RUNNING,
                    "underlying engine started");

            long initial = session.getPlayerState().getVersion();
            session.submit(GameAction.Type.START);
            session.submit(GameAction.Type.PAUSE);
            session.submit(GameAction.Type.RESUME);
            session.submit(GameAction.Type.GRAVITY_TICK);
            session.submit(GameAction.Type.RECEIVE_GARBAGE);
            session.submit(GameAction.Type.USE_ITEM);
            session.submit(null);
            check(session.getPlayerState().getVersion() == initial,
                    "only local player actions enter the session");
            int y = session.getPlayerState().getPieceY();
            session.tick();
            check(session.getPlayerState().getPieceY() == y - 1,
                    "timer tick moves the actual piece");
            session.submit(GameAction.Type.MOVE_LEFT);
            check(session.getPlayerState().getVersion() > initial + 1,
                    "player input changes the actual engine");

            session.pause();
            long paused = session.getPlayerState().getVersion();
            check(session.isPaused(), "pause reaches engine");
            session.tick();
            session.submit(GameAction.Type.HARD_DROP);
            check(session.getPlayerState().getVersion() == paused,
                    "paused input and gravity do not change the board");
            session.resume();
            check(!session.isPaused(), "resume reaches engine");
            session.submit(GameAction.Type.HARD_DROP);
            check(occupiedCells(session) > 0, "hard drop locked a real piece");

            // 같은 위치에 계속 고정하면 줄을 만들기 전에 실제 보드가 쌓여 TopOut 된다.
            for (int turn = 0; turn < 100 && !session.isFinished(); turn++) {
                if (session.getPlayerState().isAwaitingSpawn()) session.tick();
                if (!session.isFinished()) session.submit(GameAction.Type.HARD_DROP);
            }
            check(session.isFinished() && !session.isCompleted(),
                    "top out ends infinite without marking a sprint clear");
            check(session.getPlayerState().getStatus() == GameState.Status.GAME_OVER,
                    "top out belongs to the real engine");
            long terminal = session.getPlayerState().getVersion();
            session.tick();
            session.resume();
            session.submit(GameAction.Type.HARD_DROP);
            check(session.getPlayerState().getVersion() == terminal,
                    "top out remains terminal");
        } finally {
            session.close();
        }

        LocalGameSession closed = new LocalGameSession(LocalGameSession.Mode.INFINITE, 7);
        closed.close();
        long version = closed.getPlayerState().getVersion();
        closed.submit(GameAction.Type.HARD_DROP);
        closed.tick();
        closed.resume();
        closed.pulse(1);
        closed.close();
        check(closed.getPlayerState().getVersion() == version,
                "closed session ignores input and is idempotent");
    }

    private static void sprintClearsFortyRealLines() {
        LocalGameSession session = new LocalGameSession(LocalGameSession.Mode.SPRINT, 1);
        HeuristicStrategy player = new HeuristicStrategy(HeuristicWeights.SAFE);
        try {
            check(session.getInstruction().equals("0/40줄"), "sprint progress begins at zero");
            for (int placements = 0; placements < 500 && !session.isFinished(); placements++) {
                if (session.getPlayerState().isAwaitingSpawn()) session.tick();
                if (session.isFinished()) break;
                int linesBefore = session.getPlayerState().getLinesCleared();
                AIPlan plan = player.plan(session.getPlayerState());
                check(plan.getCandidateCount() > 0
                                && plan.getActions().contains(GameAction.Type.HARD_DROP),
                        "strategy found a legal placement on the real board");
                for (GameAction.Type action : plan.getActions()) {
                    session.submit(action);
                    if (session.isFinished()) break;
                }
                int lines = session.getPlayerState().getLinesCleared();
                check(lines >= linesBefore, "cleared line counter never decreases");
                if (lines < 40) check(!session.isCompleted(), "sprint has not finished early");
            }
            check(session.isCompleted() && session.isFinished(),
                    "forty line clears finish sprint on the real engine");
            check(session.getPlayerState().getLinesCleared() >= 40,
                    "engine counter reached sprint target");
            check(session.isPaused() && session.getInstruction().contains("완료"),
                    "completion freezes the board and updates the instruction");
            long terminal = session.getPlayerState().getVersion();
            session.submit(GameAction.Type.HARD_DROP);
            session.tick();
            session.resume();
            check(session.getPlayerState().getVersion() == terminal,
                    "sprint completion blocks all further board changes");
        } finally {
            session.close();
        }
    }

    private static int occupiedCells(LocalGameSession session) {
        int count = 0;
        for (int y = 0; y < session.getPlayerState().getBoard().getHeight(); y++) {
            for (int x = 0; x < session.getPlayerState().getBoard().getWidth(); x++) {
                if (session.getPlayerState().getBoard().getCell(x, y) != PieceType.EMPTY) count++;
            }
        }
        return count;
    }

    private static void checkSameState(GameState first, GameState replay) {
        check(first.getStatus() == replay.getStatus()
                        && first.getVersion() == replay.getVersion()
                        && first.getLinesCleared() == replay.getLinesCleared()
                        && first.isAwaitingSpawn() == replay.isAwaitingSpawn()
                        && first.getNextPieces().equals(replay.getNextPieces())
                        && first.getHoldPiece() == replay.getHoldPiece(),
                "equal seeds keep matching engine state");
        check(first.getActivePiece() == null ? replay.getActivePiece() == null
                        : replay.getActivePiece() != null
                        && first.getActivePiece().getType() == replay.getActivePiece().getType()
                        && first.getActivePiece().getRotation() == replay.getActivePiece().getRotation()
                        && first.getPieceX() == replay.getPieceX()
                        && first.getPieceY() == replay.getPieceY(),
                "equal seeds keep matching active piece");
        for (int y = 0; y < first.getBoard().getHeight(); y++) {
            for (int x = 0; x < first.getBoard().getWidth(); x++) {
                check(first.getBoard().getCell(x, y) == replay.getBoard().getCell(x, y),
                        "equal seeds keep matching board cells");
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
