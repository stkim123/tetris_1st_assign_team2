package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

/** Swing 화면 전체에서 공통으로 사용하는 절제된 픽셀 버튼. */
public class PixelButton extends JButton {
    private Color accent = UniversityPixelTheme.GOLD;

    public PixelButton(String text) {
        super(text);
        setFont(UniversityPixelTheme.font(13, Font.BOLD));
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public void setAccent(Color accent) {
        if (accent == null) throw new IllegalArgumentException("accent");
        this.accent = accent;
        super.setBackground(accent);
        repaint();
    }

    /** 화면의 주된 다음 행동. 한 화면에 하나만 두는 것을 기본으로 한다. */
    public PixelButton primary() { setAccent(UniversityPixelTheme.GOLD); return this; }

    /** 돌아가기·취소처럼 흐름을 바꾸지 않는 보조 행동. */
    public PixelButton secondary() { setAccent(UniversityPixelTheme.PANEL_LIGHT); return this; }

    /** 대전 시작·준비처럼 긍정적인 확정 행동. */
    public PixelButton positive() { setAccent(UniversityPixelTheme.MINT); return this; }

    /** 대전 포기·방 나가기처럼 진행 중인 상태를 끊는 행동. */
    public PixelButton danger() { setAccent(UniversityPixelTheme.CORAL); return this; }

    @Override public void setBackground(Color background) {
        super.setBackground(background);
        if (background != null && accent != null) accent = background;
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (w < 12 || h < 12) return;
            boolean enabled = isEnabled();
            boolean pressed = enabled && getModel().isPressed();
            boolean hover = enabled && getModel().isRollover();
            int offset = pressed ? 2 : 0;
            String value = getText() == null ? "" : getText();
            String asset = "도전하기".equals(value) ? "challenge_button" : null;
            BufferedImage image = asset == null ? null
                    : CampusArt.image("/ui/campus-rpg/reference-icons/" + asset + ".png");
            // 버튼 이미지에 테두리와 글자가 모두 있으므로 기본 배경·테두리를 또 그리지 않는다.
            // JButton의 텍스트·리스너는 그대로 유지해 테스트와 실제 클릭이 가능하다.
            if (image != null && enabled && w > 100 && h > 32) {
                CampusArt.fit(g, image, offset, offset, w - 3, h - 4);
                if (isFocusOwner()) {
                    g.setColor(new Color(0x14568E));
                    g.setStroke(new BasicStroke(2f));
                    g.drawRoundRect(5 + offset, 5 + offset, w - 14, h - 16, 16, 16);
                }
                return;
            }
            Color base = !enabled ? new Color(0xD1C9B9) : hover ? lighten(accent) : accent;
            int arc = Math.min(23, Math.max(12, h / 2));
            g.setColor(new Color(44, 27, 18, 85));
            g.fillRoundRect(4, 5, w - 7, h - 7, arc, arc);
            g.setColor(new Color(0x382018));
            g.fillRoundRect(1 + offset, 1 + offset, w - 6, h - 7, arc, arc);
            g.setPaint(new GradientPaint(0,offset + 3, lighten(base),0,h - 7 + offset,base));
            g.fillRoundRect(4 + offset, 4 + offset, w - 12, h - 14, arc - 3, arc - 3);
            g.setColor(new Color(255,255,255,160));
            g.setStroke(new BasicStroke(1.8f));
            g.drawRoundRect(7 + offset, 7 + offset, w - 18, h - 21, arc - 5, arc - 5);
            g.setFont(getFont());
                FontMetrics m = g.getFontMetrics();
                int avail = Math.max(0, w - 20);
                while (value.length() > 1 && m.stringWidth(value) > avail) value = value.substring(0, value.length()-2) + "…";
                Color fg = CampusArt.INK;
                if (enabled && (accent.equals(UniversityPixelTheme.MINT) || accent.equals(UniversityPixelTheme.CORAL))) fg = Color.WHITE;
                g.setColor(new Color(255,255,255,130));
                int tx = (w - m.stringWidth(value)) / 2 + offset;
                int ty = (h - m.getHeight()) / 2 + m.getAscent() + offset;
                if (fg.equals(Color.WHITE)) g.drawString(value,tx+1,ty+2);
                g.setColor(enabled ? fg : new Color(0x817870));
                g.drawString(value,tx,ty);
            if (isFocusOwner()) {
                g.setColor(new Color(0x14568E));
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(5 + offset, 5 + offset, w - 14, h - 16, arc, arc);
            }
        } finally { g.dispose(); }
    }

    private static Color lighten(Color color) {
        return new Color(Math.min(255, color.getRed() + 28), Math.min(255, color.getGreen() + 28),
                Math.min(255, color.getBlue() + 28));
    }
}
