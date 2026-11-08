package kr.ac.jbnu.se.tetris.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kr.ac.jbnu.se.tetris.core.BoardState;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.Piece;
import kr.ac.jbnu.se.tetris.core.PieceType;
import kr.ac.jbnu.se.tetris.core.PlacementResult;
import kr.ac.jbnu.se.tetris.core.PlacementSimulator;

/** 실제 엔진이 허용하는 입력을 통한 착지 후보 탐색 */
final class PlacementSearch {
    private static final GameAction.Type[] MOVES = {
        GameAction.Type.MOVE_LEFT, GameAction.Type.MOVE_RIGHT,
        GameAction.Type.ROTATE_LEFT, GameAction.Type.ROTATE_RIGHT, GameAction.Type.SOFT_DROP
    };

    static final class Candidate {
        final Piece piece;
        final int x;
        final int y;
        final boolean held;
        final PlacementResult result;
        final List<GameAction.Type> actions;

        Candidate(Node node, PlacementResult result) {
            this.piece = node.piece;
            this.x = node.x;
            this.y = result.getY();
            this.held = node.held;
            this.result = result;
            this.actions = append(node.path, GameAction.Type.HARD_DROP);
        }
    }

    static final class Result {
        final List<Candidate> candidates;
        final boolean timedOut;

        Result(List<Candidate> candidates, boolean timedOut) {
            this.candidates = candidates;
            this.timedOut = timedOut;
        }
    }

    private static final class Node {
        final Piece piece;
        final int x;
        final int y;
        final boolean held;
        final List<GameAction.Type> path;

        Node(Piece piece, int x, int y, boolean held, List<GameAction.Type> path) {
            this.piece = piece;
            this.x = x;
            this.y = y;
            this.held = held;
            this.path = path;
        }

        String key() { return (held ? "H" : "A") + piece.getRotation() + ":" + x + ":" + y; }
    }

    private PlacementSearch() { }

    static Result search(GameState state, int maxStates, long budgetNanos, long started) {
        if (state.getStatus() != GameState.Status.RUNNING || state.getActivePiece() == null
                || state.isAwaitingSpawn()) {
            return new Result(Collections.<Candidate>emptyList(), false);
        }
        return search(state.getBoard(), state.getActivePiece(), state.getPieceX(), state.getPieceY(),
                state.canHold(), state.getHoldPiece(), state.getNextPieces(), maxStates,
                budgetNanos, started);
    }

    private static Result search(BoardState board, Piece activePiece, int pieceX, int pieceY,
                                 boolean canHold, PieceType holdPiece, List<PieceType> nextPieces,
                                 int maxStates, long budgetNanos, long started) {
        Node initial = new Node(activePiece, pieceX, pieceY,
                false, Collections.<GameAction.Type>emptyList());
        if (!PlacementSimulator.canPlace(board, initial.piece, initial.x, initial.y)) {
            return new Result(Collections.<Candidate>emptyList(), false);
        }
        ArrayDeque<Node> queue = new ArrayDeque<Node>();
        Set<String> visited = new HashSet<String>();
        Set<String> landings = new HashSet<String>();
        List<Candidate> candidates = new ArrayList<Candidate>();
        queue.add(initial);
        visited.add(initial.key());
        Node held = heldRoot(board, canHold, holdPiece, nextPieces);
        if (held != null && PlacementSimulator.canPlace(board, held.piece, held.x, held.y)
                && visited.add(held.key())) queue.add(held);
        int expanded = 0;
        boolean timedOut = false;
        while (!queue.isEmpty()) {
            // 상태 수와 계산 시간 중 먼저 도달한 상한에서 탐색 중단
            if (expanded >= maxStates || (expanded > 0
                    && (System.nanoTime() - started >= budgetNanos
                        || Thread.currentThread().isInterrupted()))) {
                timedOut = true;
                break;
            }
            Node current = queue.removeFirst();
            expanded++;
            // 같은 X·회전에도 시작 높이에 따라 달라지는 틈새 착지 가능성
            PlacementResult result = PlacementSimulator.hardDrop(board, current.piece,
                    current.x, current.y);
            if (result.isValid()) {
                String key = (current.held ? "H" : "A") + current.piece.getType()
                        + ":" + current.piece.getRotation() + ":" + current.x + ":" + result.getY();
                if (landings.add(key)) candidates.add(new Candidate(current, result));
            }
            for (GameAction.Type move : MOVES) {
                if (Thread.currentThread().isInterrupted()
                        || System.nanoTime() - started >= budgetNanos) {
                    timedOut = true;
                    break;
                }
                Piece piece = current.piece;
                int x = current.x, y = current.y;
                switch (move) {
                    case MOVE_LEFT: x--; break;
                    case MOVE_RIGHT: x++; break;
                    case ROTATE_LEFT: piece = piece.rotateLeft(); break;
                    case ROTATE_RIGHT: piece = piece.rotateRight(); break;
                    case SOFT_DROP: y--; break;
                    default: throw new IllegalStateException("Unexpected search action");
                }
                if (!PlacementSimulator.canPlace(board, piece, x, y)) continue;
                Node next = new Node(piece, x, y, current.held, append(current.path, move));
                if (visited.add(next.key())) queue.addLast(next);
            }
            if (timedOut) break;
        }
        return new Result(candidates, timedOut);
    }

    private static Node heldRoot(BoardState board, boolean canHold, PieceType holdPiece,
                                 List<PieceType> nextPieces) {
        if (!canHold) return null;
        PieceType type = holdPiece;
        if (type == null || type == PieceType.EMPTY) {
            if (nextPieces.isEmpty()) return null;
            type = nextPieces.get(0);
        }
        if (type == null || type == PieceType.EMPTY || type == PieceType.GARBAGE) return null;
        Piece piece = new Piece(type);
        return new Node(piece, PlacementSimulator.spawnX(board),
                PlacementSimulator.spawnY(board, piece), true,
                Collections.singletonList(GameAction.Type.HOLD));
    }

    private static List<GameAction.Type> append(List<GameAction.Type> path, GameAction.Type action) {
        List<GameAction.Type> copy = new ArrayList<GameAction.Type>(path.size() + 1);
        copy.addAll(path);
        copy.add(action);
        return copy;
    }
}
