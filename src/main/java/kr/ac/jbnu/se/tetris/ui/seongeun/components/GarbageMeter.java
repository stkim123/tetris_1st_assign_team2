package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.*;
import javax.swing.*;

/**
 * 보드 왼쪽에 붙는 세로 가비지 경고 막대. 들어올 줄 수만큼 아래부터 붉은 칸을 쌓고,
 * 곧 올라올 때는 깜박여서 보드를 보면서도 위험을 알아챌 수 있게 한다.
 */
public final class GarbageMeter extends JComponent {
    private static final int ROWS = 22;
    private int lines;
    private long remainingMillis;
    private final Timer blink = new Timer(120, event -> repaint());

    public GarbageMeter() {
        setPreferredSize(new Dimension(12, 300));
        setToolTipText("들어올 가비지 줄");
    }

    public void setPending(int lines, long remainingMillis) {
        this.lines = Math.max(0, lines);
        this.remainingMillis = remainingMillis;
        if (this.lines > 0 && isShowing()) blink.start(); else blink.stop();
        setToolTipText(this.lines == 0 ? "들어올 가비지 없음"
                : "가비지 " + this.lines + "줄 · " + String.format("%.1f", remainingMillis / 1000.0) + "초 뒤");
        repaint();
    }

    @Override public void removeNotify() { blink.stop(); super.removeNotify(); }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            int w = getWidth(), h = getHeight();
            g.setColor(UniversityPixelTheme.BLACK);
            g.fillRect(0, 0, w, h);
            int cell = Math.max(2, (h - 4) / ROWS);
            int top = h - 2 - cell * ROWS;
            g.setColor(new Color(0x1A1440));
            g.fillRect(2, top, w - 4, cell * ROWS);
            boolean urgent = lines > 0 && remainingMillis < 900;
            boolean on = !urgent || (System.currentTimeMillis() / 120) % 2 == 0;
            int shown = Math.min(ROWS, lines);
            for (int i = 0; i < shown; i++) {
                int y = h - 2 - (i + 1) * cell;
                g.setColor(on ? (urgent ? new Color(255, 236, 120) : UniversityPixelTheme.CORAL)
                        : new Color(120, 30, 50));
                g.fillRect(2, y + 1, w - 4, cell - 1);
                g.setColor(new Color(255, 255, 255, 60));
                g.fillRect(2, y + 1, w - 4, Math.max(1, cell / 4));
            }
            if (lines > ROWS) {
                g.setColor(UniversityPixelTheme.TEXT);
                g.fillRect(2, top, w - 4, 3);
            }
        } finally { g.dispose(); }
    }
}
