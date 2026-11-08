package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.*;
import javax.swing.*;

/**
 * 창의 글래스 패널로 쓰는 화면 전환 효과. 화면이 바뀐 직후 전체를 덮은 픽셀 블록이
 * 대각선 방향으로 사라지며 새 화면을 드러낸다. 마우스 입력은 아래 화면으로 통과시킨다.
 */
public final class ScreenTransition extends JComponent {
    private static final int DURATION = 420;
    private final Timer timer = new Timer(16, event -> tick());
    private long startedAt;
    private Color accent = UniversityPixelTheme.GOLD;

    public ScreenTransition() {
        setOpaque(false);
        setVisible(false);
        timer.setCoalesce(true);
    }

    public void play(Color accent) {
        this.accent = accent == null ? UniversityPixelTheme.GOLD : accent;
        startedAt = System.currentTimeMillis();
        setVisible(true);
        timer.start();
        repaint();
    }

    public void stop() {
        timer.stop();
        setVisible(false);
    }

    @Override public boolean contains(int x, int y) {
        return false;
    }

    private void tick() {
        if (System.currentTimeMillis() - startedAt > DURATION) {
            timer.stop();
            setVisible(false);
        }
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        long age = System.currentTimeMillis() - startedAt;
        if (age > DURATION) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            int w = getWidth(), h = getHeight();
            int block = Math.max(24, Math.min(48, w / 22));
            int columns = w / block + 1, rows = h / block + 1;
            float t = age / (float) DURATION;
            for (int row = 0; row < rows; row++) for (int column = 0; column < columns; column++) {
                // 왼쪽 위에서 오른쪽 아래로 퍼지는 순서, 칸마다 약간 엇갈린다.
                float order = (column + row) / (float) (columns + rows) * 0.7f
                        + ((column * 7 + row * 13) % 5) * 0.04f;
                float local = (t - order) / 0.3f;
                if (local >= 1f) continue;
                int inset = local <= 0 ? 0 : Math.round(block / 2f * local);
                int x = column * block, y = row * block;
                g.setColor(UniversityPixelTheme.BLACK);
                g.fillRect(x + inset, y + inset, block - inset * 2, block - inset * 2);
                if (local > 0 && local < 0.7f) {
                    g.setColor(accent);
                    g.drawRect(x + inset, y + inset, block - inset * 2 - 1, block - inset * 2 - 1);
                }
            }
        } finally { g.dispose(); }
    }
}
