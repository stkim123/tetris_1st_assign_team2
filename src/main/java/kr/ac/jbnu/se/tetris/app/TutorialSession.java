package kr.ac.jbnu.se.tetris.app;

import kr.ac.jbnu.se.tetris.battle.BattleState;
import kr.ac.jbnu.se.tetris.controller.Controller;
import kr.ac.jbnu.se.tetris.controller.PlayerController;
import kr.ac.jbnu.se.tetris.core.ActionResult;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameEngine;
import kr.ac.jbnu.se.tetris.core.GameEvent;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.SeededPieceGenerator;

/** 기존 엔진의 수락된 조작을 관찰해 학습 목표만 관리하는 튜토리얼 세션 */
public final class TutorialSession implements PlaySession {
    private static final String[] GOALS = {
        "1/5 · ← 또는 → 키로 블록을 이동하세요.",
        "2/5 · ↑ 또는 ↓ 키로 블록을 회전하세요.",
        "3/5 · D 키로 블록을 한 칸 내리세요.",
        "4/5 · C 키로 블록을 HOLD에 보관하세요.",
        "5/5 · Space 키로 Ghost 위치에 블록을 놓으세요."
    };
    private final Controller controller;
    private int step;
    private boolean closed;

    public TutorialSession(long seed) {
        controller = new PlayerController(new GameEngine("local", new SeededPieceGenerator(seed)), "local");
        controller.submit(GameAction.Type.START);
    }

    @Override public void submit(GameAction.Type action) {
        if (closed || isFinished()) return;
        ActionResult result = controller.submit(action);
        if (!result.isAccepted()) return;
        // 키를 누른 사실이 아니라 실제 이동/회전/보관/고정 이벤트로 달성을 판단
        for (GameEvent event : result.getEvents()) {
            boolean achieved = step == 0 && event.getType() == GameEvent.Type.PIECE_MOVED
                    && (action == GameAction.Type.MOVE_LEFT || action == GameAction.Type.MOVE_RIGHT)
                || step == 1 && event.getType() == GameEvent.Type.PIECE_ROTATED
                || step == 2 && event.getType() == GameEvent.Type.PIECE_MOVED && action == GameAction.Type.SOFT_DROP
                || step == 3 && event.getType() == GameEvent.Type.PIECE_HELD
                || step == 4 && event.getType() == GameEvent.Type.PIECE_PLACED && action == GameAction.Type.HARD_DROP;
            if (achieved) { step++; break; }
        }
        if (isCompleted() && getPlayerState().getStatus() == GameState.Status.RUNNING) {
            controller.submit(GameAction.Type.PAUSE);
        }
    }

    public int getStep() { return step; }
    public boolean isCompleted() { return step == GOALS.length; }
    public String getInstruction() { return isCompleted() ? "기본 조작 완료 · 스토리 대전에 도전하세요." : GOALS[step]; }
    @Override public void tick() { submit(GameAction.Type.GRAVITY_TICK); }
    @Override public void pause() {
        if (!closed && getPlayerState().getStatus() == GameState.Status.RUNNING) controller.submit(GameAction.Type.PAUSE);
    }
    @Override public void resume() {
        if (!closed && !isFinished() && isPaused()) controller.submit(GameAction.Type.RESUME);
    }
    /** 상대 AI가 없는 튜토리얼의 별도 판단 주기 미사용 */
    @Override public void pulse(long nowNanos) { }
    @Override public GameState getPlayerState() { return controller.getState(); }
    @Override public BattleState getBattleState() { return null; }
    @Override public boolean isPaused() { return getPlayerState().getStatus() == GameState.Status.PAUSED; }
    @Override public boolean isFinished() { return isCompleted() || getPlayerState().getStatus() == GameState.Status.GAME_OVER; }
    @Override public boolean isThinking() { return false; }
    @Override public void close() { pause(); closed = true; }
}
