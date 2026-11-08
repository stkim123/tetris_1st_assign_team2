package kr.ac.jbnu.se.tetris.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/** 현재 화면을 창 없이 여러 크기의 이미지 버퍼에 그려 레이아웃 예외를 잡는다. */
public final class OffscreenUiTest {
    private static final int[][] SIZES = {{1020, 720}, {820, 650}, {960, 820}, {784, 562}};

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel preview = UiPreviewMain.createPanel();
            JTabbedPane tabs = (JTabbedPane) preview.getComponent(0);
            if (tabs.getTabCount() < 12) throw new AssertionError("Current screen fixtures are missing");
            for (int index = 0; index < tabs.getTabCount(); index++) {
                tabs.setSelectedIndex(index);
                for (int[] size : SIZES) paint(preview, size[0], size[1]);
            }
            System.out.println("PASS OffscreenUiTest: " + tabs.getTabCount()
                    + " current screens at " + SIZES.length + " sizes");
        });
    }

    private static void paint(Container root, int width, int height) {
        root.setSize(width, height);
        layout(root);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try { root.printAll(graphics); } finally { graphics.dispose(); }
    }

    private static void layout(Container root) {
        root.doLayout();
        for (Component child : root.getComponents())
            if (child.isVisible() && child instanceof Container) layout((Container) child);
    }
}
