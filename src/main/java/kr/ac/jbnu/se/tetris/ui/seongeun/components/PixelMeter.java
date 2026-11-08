package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;

/** 얇은 콘크리트 테두리 안을 칸 단위로 채우는 픽셀 게이지. */
public final class PixelMeter extends JComponent {
    private int value, maximum;
    private Color fill;

    public PixelMeter(int maximum, Color fill) {
        this.maximum = Math.max(1, maximum);
        this.fill = fill;
        setPreferredSize(new Dimension(160, 14));
        setMinimumSize(new Dimension(40, 10));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
    }

    public void setValue(int value) {
        int clamped = Math.max(0, Math.min(maximum, value));
        if (clamped != this.value) { this.value = clamped; repaint(); }
    }

    public int getValue() { return value; }

    public void setMaximum(int maximum) { this.maximum = Math.max(1, maximum); repaint(); }

    public void setFill(Color fill) { this.fill = fill; repaint(); }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            double ratio = maximum <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, value / (double) maximum));

            // 바깥 진한 배경
            g.setColor(new Color(0x293334));
            g.fillRoundRect(0, 0, w, h, 18, 18);

            // 안쪽 어두운 트랙
            int inset = 2;
            g.setColor(new Color(0x394244));
            g.fillRoundRect(inset, inset, w - inset * 2, h - inset * 2, 14, 14);

            // 채워진 FEVER 바
            int innerW = w - inset * 2;
            int innerH = h - inset * 2;
            int fillW = (int) Math.round(innerW * ratio);

            if (fillW > 0) {
                g.setColor(fill);
                g.fillRoundRect(inset, inset, fillW, innerH, 14, 14);

                // 위쪽 하이라이트
                g.setColor(new Color(255, 255, 255, 70));
                g.fillRoundRect(inset + 2, inset + 2, Math.max(0, fillW - 4), Math.max(2, innerH / 3), 10, 10);
            }

            // 칸 느낌
            g.setColor(new Color(0x2F3839));
            for (int x = 18; x < innerW; x += 18) {
                g.drawLine(inset + x, inset + 2, inset + x, h - inset - 3);
            }

            // 바깥 테두리
            g.setColor(new Color(0x1A2122));
            g.drawRoundRect(0, 0, w - 1, h - 1, 18, 18);

        } finally {
            g.dispose();
        }
    }

}
