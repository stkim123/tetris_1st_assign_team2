package kr.ac.jbnu.se.tetris.app;

import kr.ac.jbnu.se.tetris.battle.BattleState;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameState;

/** 로컬 실행 구현의 시간 진행 계약 및 외부 MatchSession과의 구분 */
interface PlaySession extends AutoCloseable {
    void submit(GameAction.Type action);
    void tick();
    void pause();
    void resume();
    void pulse(long nowNanos);
    GameState getPlayerState();
    BattleState getBattleState();
    boolean isPaused();
    boolean isFinished();
    boolean isThinking();
    @Override void close();
}
