package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 튜토리얼의 크림색 뒤로 가기 버튼을 화면 간에 공유한다.
 * 표시는 아이콘 + Swing 버튼 텍스트이고, 이벤트와 accessible name은 그대로 제공한다.
 */
public final class CampusBackButton extends PixelButton {
    private static final Color BROWN = new Color(0x51321D);
    private static final Color INK = new Color(0x13254D);
    private static final BufferedImage ARROW = CampusArt.image("/ui/campus-rpg/tutorial/icon_back.png");

    public CampusBackButton() { this("돌아가기"); }

    public CampusBackButton(String label) {
        super(label);
        setPreferredSize(new Dimension(190, 56));
        setFont(UniversityPixelTheme.font(18, Font.BOLD));
        setToolTipText("이전 화면으로 돌아갑니다.");
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            int w = getWidth(), h = getHeight();
            if (w < 26 || h < 24) return;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            boolean pressed = getModel().isPressed() && isEnabled();
            boolean rollover = getModel().isRollover() && isEnabled();
            int down = pressed ? 2 : 0;
            int arc = Math.min(18, h / 3);
            g.setColor(new Color(57, 34, 22, 80));
            g.fillRoundRect(1, 3, w - 2, h - 4, arc, arc);
            g.setColor(BROWN);
            g.fillRoundRect(0, down, w - 2, h - 4, arc, arc);
            Color top = pressed ? new Color(0xF1DFCB) : rollover ? new Color(0xFFFCF2) : new Color(0xFFF9F0);
            Color bottom = pressed ? new Color(0xE9D8C3) : new Color(0xF9EFE1);
            g.setPaint(new GradientPaint(0, down + 3, top, 0, h - 7 + down, bottom));
            g.fillRoundRect(3, down + 3, w - 8, h - 10, Math.max(9, arc - 3), Math.max(9, arc - 3));
            g.setColor(new Color(255, 255, 255, 130));
            g.setStroke(new BasicStroke(1.1f));
            g.drawRoundRect(5, down + 5, w - 12, h - 14, Math.max(7, arc - 5), Math.max(7, arc - 5));
            int icon = Math.min(h - 18, Math.max(25, Math.round(h * 0.59f)));
            int iconX = Math.max(10, Math.round(h * 0.22f));
            int iconY = (h - icon) / 2 + down;
            if (ARROW != null) CampusArt.fit(g, ARROW, iconX, iconY, icon, icon);
            else {
                g.setColor(BROWN);
                g.setFont(UniversityPixelTheme.font(26, Font.BOLD));
                g.drawString("←", iconX, h / 2 + 9 + down);
            }
            String value = getText() == null ? "" : getText();
            Font textFont = getFont() != null ? getFont() : UniversityPixelTheme.font(18, Font.BOLD);
            g.setFont(textFont);
            FontMetrics fm = g.getFontMetrics();
            int start = iconX + icon + 8;
            int maxWidth = Math.max(1, w - start - 9);
            while (value.length() > 1 && fm.stringWidth(value) > maxWidth)
                value = value.substring(0, value.length() - 2) + "…";
            int x = start + Math.max(0, (maxWidth - fm.stringWidth(value)) / 2);
            int y = (h - fm.getHeight()) / 2 + fm.getAscent() + down - 1;
            g.setColor(isEnabled() ? INK : new Color(0x99918A));
            g.drawString(value, x, y);
            if (isFocusOwner()) {
                g.setColor(new Color(0x2B79BC));
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(4, down + 4, w - 10, h - 12, arc - 3, arc - 3);
            }
        } finally { g.dispose(); }
    }
}
