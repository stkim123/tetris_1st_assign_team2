package kr.ac.jbnu.se.tetris.app;

import kr.ac.jbnu.se.tetris.battle.BattleState;
import kr.ac.jbnu.se.tetris.controller.Controller;
import kr.ac.jbnu.se.tetris.controller.PlayerController;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameEngine;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.SevenBagGenerator;

/** 성은 로컬 모드의 두 종료 규칙을 기존 단일 플레이 엔진에 연결한다. */
public final class LocalGameSession implements PlaySession {
    public enum Mode { INFINITE, SPRINT }

    private static final int SPRINT_LINES = 40;
    private final Mode mode;
    private final Controller controller;
    private boolean completed;
    private boolean closed;

    public LocalGameSession(Mode mode, long seed) {
        if (mode == null) throw new IllegalArgumentException("Local mode is required");
        this.mode = mode;
        controller = new PlayerController(new GameEngine("local", new SevenBagGenerator(seed)), "local");
        controller.submit(GameAction.Type.START);
    }

    public Mode getMode() { return mode; }

    /** 스프린트의 40줄 완주만 완료로 표시한다. 게임 오버는 별도 종료 상태다. */
    public synchronized boolean isCompleted() { return completed; }

    public synchronized String getInstruction() {
        int lines = getPlayerState().getLinesCleared();
        if (mode == Mode.INFINITE) return "지운 줄: " + lines;
        return Math.min(lines, SPRINT_LINES) + "/" + SPRINT_LINES + "줄"
                + (completed ? " · 완료" : "");
    }

    @Override public synchronized void submit(GameAction.Type action) {
        if (closed || isFinished() || isPaused() || !isPlayerAction(action)) return;
        controller.submit(action);
        completeSprintIfReady();
    }

    /** 중력 명령은 사용자 입력 경로로 주입하지 않고 타이머에서만 전달한다. */
    @Override public synchronized void tick() {
        if (closed || isFinished() || isPaused()) return;
        controller.submit(GameAction.Type.GRAVITY_TICK);
        completeSprintIfReady();
    }

    private static boolean isPlayerAction(GameAction.Type action) {
        return action == GameAction.Type.MOVE_LEFT || action == GameAction.Type.MOVE_RIGHT
                || action == GameAction.Type.ROTATE_LEFT || action == GameAction.Type.ROTATE_RIGHT
                || action == GameAction.Type.SOFT_DROP || action == GameAction.Type.HARD_DROP
                || action == GameAction.Type.HOLD;
    }

    private void completeSprintIfReady() {
        if (mode == Mode.SPRINT && !completed
                && getPlayerState().getLinesCleared() >= SPRINT_LINES) {
            completed = true;
            if (getPlayerState().getStatus() == GameState.Status.RUNNING) {
                controller.submit(GameAction.Type.PAUSE);
            }
        }
    }

    @Override public synchronized void pause() {
        if (!closed && getPlayerState().getStatus() == GameState.Status.RUNNING) {
            controller.submit(GameAction.Type.PAUSE);
        }
    }

    @Override public synchronized void resume() {
        if (!closed && !isFinished() && isPaused()) controller.submit(GameAction.Type.RESUME);
    }

    /** 상대나 별도 판단 주기가 없는 로컬 모드다. */
    @Override public void pulse(long nowNanos) { }
    @Override public GameState getPlayerState() { return controller.getState(); }
    @Override public BattleState getBattleState() { return null; }
    @Override public boolean isPaused() { return getPlayerState().getStatus() == GameState.Status.PAUSED; }
    @Override public synchronized boolean isFinished() {
        return completed || getPlayerState().getStatus() == GameState.Status.GAME_OVER;
    }
    @Override public boolean isThinking() { return false; }
    @Override public synchronized void close() {
        if (closed) return;
        pause();
        closed = true;
    }
}
