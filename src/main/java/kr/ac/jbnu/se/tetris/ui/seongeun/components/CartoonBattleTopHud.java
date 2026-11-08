package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Small, bright battle header that retains the existing labels and abandon action. */
public final class CartoonBattleTopHud extends JPanel {
    private static final Color INK = new Color(0x51372F);
    private static final Color OUTLINE = new Color(0x49302A);
    private static final Color CREAM = new Color(0xFFF4DF);
    private static final Color GOLD = new Color(0xFFE3A5);
    private static final Color SHADOW = new Color(0, 0, 0, 55);

    private final JLabel level;
    private final JLabel chapter;
    private final JLabel clock;
    private final PixelButton quit;
    private int headingWidth;
    private int timerX;
    private int quitX;
    private int timerWidth;
    private int quitWidth;
    private int barY;
    private int barHeight;

    public CartoonBattleTopHud(JLabel level, JLabel chapter, JLabel clock, PixelButton quit) {
        super(null);
        this.level = level;
        this.chapter = chapter;
        this.clock = clock;
        this.quit = quit;
        setOpaque(false);
        level.setOpaque(false);
        level.setHorizontalAlignment(SwingConstants.CENTER);
        chapter.setForeground(UniversityPixelTheme.TEXT);
        clock.setForeground(UniversityPixelTheme.TEXT);
        clock.setHorizontalAlignment(SwingConstants.CENTER);
        add(level);
        add(chapter);
        add(clock);
        add(quit);
    }

    public static JLabel levelChip(String text, Color accent) {
        JLabel badge = new JLabel(text) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    int w = getWidth(), h = getHeight();
                    if (w >= 9 && h >= 9) {
                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        Color tone = getBackground();
                        if (UniversityPixelTheme.CORAL.equals(tone)) tone = new Color(0xED7E73);
                        else if (UniversityPixelTheme.MINT.equals(tone)) tone = new Color(0x61BFB3);
                        else tone = new Color(0xDFA44F);
                        g.setColor(OUTLINE);
                        g.fillRoundRect(0, 0, w, h, 18, 18);
                        g.setColor(tone);
                        g.fillRoundRect(3, 3, w - 6, h - 6, 15, 15);
                        g.setColor(new Color(255, 255, 255, 100));
                        g.drawRoundRect(5, 4, w - 11, h - 10, 11, 11);
                    }
                } finally { g.dispose(); }
                super.paintComponent(graphics);
            }
            @Override protected void paintBorder(Graphics graphics) {
                // UniversityPixelTheme.setChip may add a rectangular border; the badge is rounded.
            }
        };
        badge.setOpaque(false);
        badge.setHorizontalAlignment(SwingConstants.CENTER);
        UniversityPixelTheme.setChip(badge, text, accent);
        badge.setForeground(UniversityPixelTheme.TEXT_ON_DARK);
        return badge;
    }

    public static PixelButton quitButton(String text) {
        PixelButton button = new PixelButton(text) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    int w = getWidth(), h = getHeight();
                    if (w < 12 || h < 12) return;
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    boolean danger = UniversityPixelTheme.CORAL.equals(getBackground());
                    boolean pressed = getModel().isPressed();
                    boolean hover = getModel().isRollover();
                    Color surface = danger ? new Color(0xF48B80) : new Color(0xF4A17D);
                    if (hover) surface = danger ? new Color(0xFF9B90) : new Color(0xFFB18D);
                    if (pressed) surface = danger ? new Color(0xD87470) : new Color(0xDF8D69);
                    g.setColor(SHADOW);
                    g.fillRoundRect(1, 5, w - 2, h - 5, 18, 18);
                    g.setColor(OUTLINE);
                    g.fillRoundRect(0, 0, w, h - 3, 18, 18);
                    g.setColor(surface);
                    g.fillRoundRect(3, 3 + (pressed ? 2 : 0), w - 6, h - 9, 14, 14);
                    if (!pressed) {
                        g.setColor(new Color(255, 255, 255, 115));
                        g.drawRoundRect(6, 5, w - 13, h - 13, 10, 10);
                    }
                    g.setFont(UniversityPixelTheme.font(13, Font.BOLD));
                    FontMetrics fm = g.getFontMetrics();
                    String caption = getText() == null ? "" : getText();
                    int tx = (w - fm.stringWidth(caption)) / 2;
                    int ty = (h - fm.getHeight()) / 2 + fm.getAscent() - 1 + (pressed ? 2 : 0);
                    g.setColor(UniversityPixelTheme.TEXT);
                    g.drawString(caption, tx, ty);
                } finally { g.dispose(); }
            }
        };
        button.primary();
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setRolloverEnabled(true);
        button.setFocusable(false);
        return button;
    }

    @Override public void doLayout() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        barY = 1;
        barHeight = Math.max(24, h - 4);
        int gap = w < 690 ? 6 : 10;
        quitWidth = w < 640 ? 125 : 146;
        timerWidth = w < 640 ? 91 : 107;
        quitX = Math.max(0, w - quitWidth);
        timerX = Math.max(0, quitX - gap - timerWidth);
        int maxHeading = Math.max(125, timerX - gap);
        headingWidth = Math.min(395, maxHeading);
        if (w < 490) headingWidth = Math.max(95, maxHeading);

        int badgeWidth = Math.min(71, Math.max(48, headingWidth / 4));
        int badgeHeight = Math.max(18, barHeight - 13);
        level.setBounds(9, barY + (barHeight - badgeHeight) / 2, badgeWidth, badgeHeight);
        chapter.setBounds(18 + badgeWidth, barY + 5,
                Math.max(20, headingWidth - badgeWidth - 29), Math.max(20, barHeight - 10));
        clock.setBounds(timerX + 31, barY + 4, Math.max(35, timerWidth - 39), barHeight - 9);
        quit.setBounds(quitX, barY, quitWidth, barHeight);
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (getWidth() < 8 || barHeight < 8) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawPill(g, 0, barY, headingWidth, barHeight, CREAM);
            drawPill(g, timerX, barY, timerWidth, barHeight, GOLD);
            // Small clock icon; the displayed time still comes from the original clock JLabel.
            int cx = timerX + 17, cy = barY + barHeight / 2;
            g.setColor(INK);
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawOval(cx - 8, cy - 8, 16, 16);
            g.drawLine(cx, cy, cx, cy - 5);
            g.drawLine(cx, cy, cx + 4, cy + 2);
            g.drawLine(cx - 3, cy - 11, cx + 3, cy - 11);
            g.setColor(new Color(255, 255, 255, 165));
            g.fillOval(timerX + timerWidth - 10, barY + 9, 3, 3);
        } finally { g.dispose(); }
    }

    private static void drawPill(Graphics2D g, int x, int y, int w, int h, Color fill) {
        if (w < 12 || h < 12) return;
        g.setColor(SHADOW);
        g.fillRoundRect(x + 1, y + 4, w - 2, h - 4, 20, 20);
        g.setColor(OUTLINE);
        g.fillRoundRect(x, y, w, h - 3, 20, 20);
        g.setColor(fill);
        g.fillRoundRect(x + 3, y + 3, w - 6, h - 9, 15, 15);
        g.setColor(new Color(255, 255, 255, 145));
        g.setStroke(new BasicStroke(1.3f));
        g.drawRoundRect(x + 5, y + 5, w - 11, h - 13, 12, 12);
    }
}
