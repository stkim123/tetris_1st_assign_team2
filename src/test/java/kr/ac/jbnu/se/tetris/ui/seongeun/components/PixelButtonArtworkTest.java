package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

/** Regression: results use text buttons; challenge still uses its supplied artwork. */
public final class PixelButtonArtworkTest {
    private PixelButtonArtworkTest() { }

    public static void main(String[] args) {
        textButton();
        challengeArtwork();
        backButton();
        System.out.println("PASS PixelButtonArtworkTest");
    }

    private static void textButton() {
        PixelButton button = new PixelButton("로비로");
        button.setSize(215, 56);
        BufferedImage canvas = render(button);
        assert (canvas.getRGB(14, 28) >>> 24) != 0
                : "로비로 버튼은 PNG가 아닌 전체 너비의 텍스트 버튼이어야 합니다.";
        clickable(button);
    }

    private static void challengeArtwork() {
        assert CampusArt.image("/ui/campus-rpg/reference-icons/challenge_button.png") != null;
        PixelButton button = new PixelButton("도전하기");
        button.setSize(185, 56);
        BufferedImage canvas = render(button);
        assert (canvas.getRGB(14, 28) >>> 24) == 0 : "도전하기 PNG의 투명 영역은 유지합니다.";
        assert (canvas.getRGB(93, 28) >>> 24) != 0;
        clickable(button);
    }

    private static void backButton() {
        CampusBackButton button = new CampusBackButton("돌아가기");
        button.setSize(190, 56);
        BufferedImage canvas = render(button);
        assert (canvas.getRGB(14, 28) >>> 24) != 0;
        assert "돌아가기".equals(button.getText());
        clickable(button);
    }

    private static BufferedImage render(PixelButton button) {
        BufferedImage canvas = new BufferedImage(button.getWidth(), button.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = canvas.createGraphics();
        try { button.paint(graphics); }
        finally { graphics.dispose(); }
        return canvas;
    }

    private static void clickable(PixelButton button) {
        AtomicInteger clicks = new AtomicInteger();
        button.addActionListener(event -> clicks.incrementAndGet());
        button.doClick();
        assert clicks.get() == 1 : "버튼 클릭 이벤트가 유지되어야 합니다.";
    }
}
