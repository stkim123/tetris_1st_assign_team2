package kr.ac.jbnu.se.tetris.controller;

import kr.ac.jbnu.se.tetris.core.ActionResult;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameState;

/** 사람·AI·네트워크 입력을 공통 GameAction 경로로 전달하는 계약 */
public interface Controller {
    ActionResult submit(GameAction.Type type);
    ActionResult submit(GameAction.ItemUse itemUse);
    ActionResult submit(GameAction.Garbage garbage);
    GameState getState();
}
