package kr.ac.jbnu.se.tetris.ui;

import javax.swing.JPanel;

/** 게임 규칙과 분리된 화면 전환 수명주기 */
public interface Screen {
    String getId();
    JPanel getPanel();
    default void onEnter() { }
    default void onExit() { }
}
