package kr.ac.jbnu.se.tetris.controller;

import java.util.List;
import kr.ac.jbnu.se.tetris.core.ActionResult;
import kr.ac.jbnu.se.tetris.core.BoardState;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameEngine;
import kr.ac.jbnu.se.tetris.core.GameEvent;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.Piece;
import kr.ac.jbnu.se.tetris.core.PieceGenerator;
import kr.ac.jbnu.se.tetris.core.PieceType;
import kr.ac.jbnu.se.tetris.core.PlacementResult;
import kr.ac.jbnu.se.tetris.core.PlacementSimulator;

/** 외부 패키지 소비자 관점에서 0단계 AI 배치 조회 계약을 검증 */
public final class PlacementContractTest {
    public static void main(String[] args) {
        GameEngine engine = new GameEngine(new PieceGenerator() {
            public PieceType nextPiece() { return PieceType.O; }
        });
        ActionResult start = engine.dispatch(new GameAction(GameAction.Type.START, "local", 0));
        GameState before = start.getState();
        BoardState frozen = before.getBoard();
        Piece piece = before.getActivePiece();
        check(PlacementSimulator.spawnX(frozen) == before.getPieceX()
                && PlacementSimulator.spawnY(frozen, piece) == before.getPieceY(),
                "Engine and simulator share legacy spawn geometry");
        check(PlacementSimulator.canPlace(frozen, piece, before.getPieceX(), before.getPieceY()),
                "Public canPlace sees active position");
        PlacementResult simulated = PlacementSimulator.hardDrop(frozen, piece,
                before.getPieceX(), before.getPieceY());
        check(simulated.isValid() && simulated.getReason() == null && simulated.getY() == 1,
                "Detached hard drop lands at floor");
        check(simulated.getPiece() == piece && simulated.getX() == before.getPieceX()
                && simulated.getLinesCleared() == 0, "Placement payload");
        check(engine.getState().getVersion() == before.getVersion()
                && engine.getState().getBoard().getCell(6, 0) == PieceType.EMPTY,
                "Simulation leaves live engine untouched");
        PlacementResult invalid = PlacementSimulator.hardDrop(frozen, piece, -1, before.getPieceY());
        check(!invalid.isValid() && invalid.getReason().contains("Initial placement")
                && invalid.getX() == -1 && invalid.getY() == before.getPieceY(),
                "Invalid start is rejected without teleport");
        check(invalid.getBoard().getCell(6, 0) == PieceType.EMPTY,
                "Invalid result retains unchanged snapshot");
        ActionResult real = engine.dispatch(new GameAction(GameAction.Type.HARD_DROP, "local", 1));
        check(real.isAccepted(), "Live hard drop accepted");
        check(real.getEvents().get(0).getY() == simulated.getY(), "Landing Y parity");
        sameBoard(simulated.getBoard(), real.getState().getBoard());
        check(frozen.getCell(6, 0) == PieceType.EMPTY, "Prior snapshot stays frozen");
        check(!PlacementSimulator.canPlace(real.getState().getBoard(), piece, 6, 1),
                "Collision query sees newly locked cells");
        PlacementResult blocked = PlacementSimulator.hardDrop(real.getState().getBoard(), piece, 6, 1);
        check(!blocked.isValid(), "Blocked initial candidate rejected");

        List<GameEvent> events = start.getEvents();
        boolean threw = false;
        try { events.add(real.getEvents().get(0)); }
        catch (UnsupportedOperationException expected) { threw = true; }
        check(threw, "Action event list is immutable");
    }

    private static void sameBoard(BoardState a, BoardState b) {
        check(a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight(), "Board dimensions");
        for (int y = 0; y < a.getHeight(); y++) for (int x = 0; x < a.getWidth(); x++) {
            check(a.getCell(x, y) == b.getCell(x, y), "Simulated board parity at " + x + "," + y);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
