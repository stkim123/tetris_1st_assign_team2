package kr.ac.jbnu.se.tetris.core;

/** 입력 출처와 무관하게 같은 명령 처리 경로와 상태 조회를 제공 */
public interface GameActionSink {
    ActionResult dispatch(GameAction action);
    GameState getState();
}
