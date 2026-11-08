package kr.ac.jbnu.se.tetris.controller;

import kr.ac.jbnu.se.tetris.core.ActionResult;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameActionSink;
import kr.ac.jbnu.se.tetris.core.GameState;

/** 세션의 입력 순번을 단독 소유하며 키보드와 자동 낙하 타이머가 이를 공유 */
public final class PlayerController implements Controller {
    private final GameActionSink sink;
    private final String actorId;
    private long sequence;

    public PlayerController(GameActionSink sink, String actorId) {
        if (sink == null || actorId == null || actorId.trim().isEmpty()) {
            throw new IllegalArgumentException("Controller needs a sink and actor ID");
        }
        this.sink = sink;
        this.actorId = actorId;
    }

    public synchronized ActionResult submit(GameAction.Type type) {
        return sink.dispatch(new GameAction(type, actorId, sequence++));
    }

    public synchronized ActionResult submit(GameAction.ItemUse itemUse) {
        if (itemUse == null) throw new IllegalArgumentException("Item payload is required");
        return sink.dispatch(new GameAction(GameAction.Type.USE_ITEM, actorId, sequence++, itemUse));
    }

    public synchronized ActionResult submit(GameAction.Garbage garbage) {
        if (garbage == null) throw new IllegalArgumentException("Garbage payload is required");
        return sink.dispatch(GameAction.garbage(actorId, sequence++, garbage));
    }

    public GameState getState() { return sink.getState(); }
}
