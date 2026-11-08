/* 게임버튼 설정을 위한 파일입니다. 예시로 new GameButton("ready") 이라고 만들면
120x35 크기의 버튼에 "ready"라는 글자가 표시되며 버튼이 만들어집니다. */

package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.Dimension; // JButton 사용
// 원본 화면의 버튼 크기를 유지하면서 공통 픽셀 외형을 사용한다.

public class GameButton extends PixelButton {
    public GameButton(String text) {
        super(text);

        setPreferredSize(new Dimension(120, 35)); // 가로:120, 세로:35
        setFocusPainted(false);
    }
}
