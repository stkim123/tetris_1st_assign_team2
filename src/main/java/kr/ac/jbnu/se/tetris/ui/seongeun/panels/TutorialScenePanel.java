package kr.ac.jbnu.se.tetris.ui.seongeun.panels;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import javax.swing.JButton;
import javax.swing.BorderFactory;
import javax.swing.SwingConstants;
import javax.swing.JLabel;
import javax.swing.JPanel;

import kr.ac.jbnu.se.tetris.ui.seongeun.Board;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.CampusArt;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.MiniPiecePreview;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.UniversityPixelTheme;

/**
 * The tutorial layout uses the existing live Board and HOLD/NEXT previews.
 * Only the visual composition changes; game rules and keyboard controls stay intact.
 */
public final class TutorialScenePanel extends JPanel {
    private static final int DESIGN_W = 1448;
    private static final int DESIGN_H = 1086;
    private static final String ASSET_ROOT = "/ui/campus-rpg/tutorial/";
    private static final Color INK = new Color(0x13254D);
    private static final Color BROWN = new Color(0x51321D);
    private static final Color CREAM = new Color(0xFFFAEC);

    // All positions share one 1448x1086 coordinate system.
    // The playfield is always centered horizontally in that system.
    private static final int BACK_X = 1176, BACK_Y = 38, BACK_W = 228, BACK_H = 70;
    private static final int BOARD_X = 521, BOARD_Y = 250, BOARD_W = 406, BOARD_H = 808;
    private static final int FRAME_X = 510, FRAME_Y = 239, FRAME_W = 428, FRAME_H = 830;
    private static final int HOLD_FRAME_X = 312, NEXT_FRAME_X = 960;
    private static final int QUEUE_TOP = FRAME_Y;
    private static final int HOLD_X = 324, HOLD_Y = 299, HOLD_W = 152, HOLD_H = 141;
    private static final int NEXT_X = 973, NEXT_Y = 299, NEXT_W = 151, NEXT_H = 120;
    private static final int NEXT_GAP = 9;

    private final Board board;
    private final MiniPiecePreview hold;
    private final MiniPiecePreview[] next;
    private final JLabel stepLabel, instruction, timeValue, linesValue, comboValue;
    private final JButton back = new JButton("돌아가기 [ESC]");
    // The same illustrated play scene serves Tutorial, Sprint and Infinite.
    // These values change artwork and non-gameplay captions, never game state.
    private String displayMode = "Tutorial";
    private double uiScale = 1;
    private int originX, originY;

    public TutorialScenePanel(Board board, MiniPiecePreview hold, MiniPiecePreview[] next,
            JLabel stepLabel, JLabel instruction, JLabel timeValue, JLabel linesValue,
            JLabel comboValue, Runnable onBack) {
        this.board = board;
        this.hold = hold;
        this.next = next;
        this.stepLabel = stepLabel;
        this.instruction = instruction;
        this.timeValue = timeValue;
        this.linesValue = linesValue;
        this.comboValue = comboValue;
        setOpaque(false);
        setLayout(null);
        back.setOpaque(false);
        back.setContentAreaFilled(false);
        back.setBorderPainted(false);
        back.setFocusPainted(false);
        back.setFocusable(false);
        back.setForeground(INK);
        back.setHorizontalAlignment(SwingConstants.LEFT);
        back.setVerticalAlignment(SwingConstants.CENTER);
        back.setToolTipText("튜토리얼을 종료하고 이전 화면으로 돌아갑니다 (ESC)");
        back.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        back.addActionListener((ActionEvent e) -> onBack.run());
        add(back);
    }

    public void setDisplayMode(String modeName) {
        if (!"Tutorial".equals(modeName) && !"Sprint".equals(modeName) && !"Infinite".equals(modeName)) {
            throw new IllegalArgumentException("Unknown local mode: " + modeName);
        }
        displayMode = modeName;
        back.setToolTipText("Tutorial".equals(modeName)
                ? "튜토리얼을 종료하고 이전 화면으로 돌아갑니다 (ESC)"
                : "게임을 종료하고 모드 선택 화면으로 돌아갑니다 (ESC)");
        repaint();
    }

    // Package-private accessor for layout regression tests.
    String getDisplayMode() { return displayMode; }

    public void mountBoardAndPreviews() {
        add(board);
        add(hold);
        for (MiniPiecePreview preview : next) add(preview);
        // Reuse the existing dark-grid preview, matching story/battle UI.
        // Tutorial-specific cream preview would override battleHudStyle.
        hold.setTutorialStyle(false);
        hold.setBattleHudStyle(true);
        for (MiniPiecePreview preview : next) {
            preview.setTutorialStyle(false);
            preview.setBattleHudStyle(true);
        }
        revalidate();
        repaint();
    }

    public void unmountBoardAndPreviews() {
        remove(board);
        remove(hold);
        for (MiniPiecePreview preview : next) remove(preview);
        // These are shared with local modes; never leak tutorial styling into them.
        hold.setBattleHudStyle(false);
        for (MiniPiecePreview preview : next) preview.setBattleHudStyle(false);
        revalidate();
        repaint();
    }

    private int px(double logicalX) { return originX + (int) Math.round(logicalX * uiScale); }
    private int py(double logicalY) { return originY + (int) Math.round(logicalY * uiScale); }
    private int sz(double logical) { return Math.max(1, (int) Math.round(logical * uiScale)); }

    @Override public void doLayout() {
        super.doLayout();
        if (getWidth() <= 0 || getHeight() <= 0) return;
        uiScale = Math.min(getWidth() / (double) DESIGN_W, getHeight() / (double) DESIGN_H);
        originX = (getWidth() - sz(DESIGN_W)) / 2;
        originY = (getHeight() - sz(DESIGN_H)) / 2;

        // Keep the original live board and its controller; change layout only.
        board.setBounds(px(BOARD_X), py(BOARD_Y), sz(BOARD_W), sz(BOARD_H));
        hold.setBounds(px(HOLD_X), py(HOLD_Y), sz(HOLD_W), sz(HOLD_H));
        for (int i = 0; i < next.length; i++) {
            next[i].setBounds(px(NEXT_X), py(NEXT_Y + i * (NEXT_H + NEXT_GAP)),
                    sz(NEXT_W), sz(NEXT_H));
        }
        // Match the clickable region to the exact painted button bounds.
        int captionSize = sz(20);
        if (back.getFont() == null || back.getFont().getSize() != captionSize) {
            back.setFont(UniversityPixelTheme.font(captionSize, Font.BOLD));
        }
        int textInset = sz(62);
        if (back.getBorder() == null || back.getBorder().getBorderInsets(back).left != textInset) {
            back.setBorder(BorderFactory.createEmptyBorder(0, textInset, 0, 0));
        }
        back.setBounds(px(BACK_X), py(BACK_Y), sz(BACK_W), sz(BACK_H));
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (getWidth() <= 0 || getHeight() <= 0) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.translate(originX, originY);
            g.scale(uiScale, uiScale);
            paintStaticUI(g);
        } finally {
            g.dispose();
        }
    }

    private void paintStaticUI(Graphics2D g) {
        // Each local mode reuses the exact same playfield, HUD, mascot and controls.
        String logo = "Sprint".equals(displayMode) ? "sprint_mode_logo.png"
                : "Infinite".equals(displayMode) ? "infinite_mode_logo.png" : "tutorial_title.png";
        if ("Tutorial".equals(displayMode)) picture(g, logo, 54, 21, 292, 113);
        else picture(g, logo, 43, 1, 410, 146);

        // Keep a real Swing button (with text) for keyboard and automated UI tests.
        panel(g, BACK_X, BACK_Y, BACK_W, BACK_H, 16, BROWN, new Color(0xFFF9F0), 3f);
        picture(g, "icon_back.png", BACK_X + 17, BACK_Y + 14, 41, 42);

        panel(g, 154, 152, 1140, 70, 18, BROWN, CREAM, 3f);
        panel(g, 174, 161, 218, 52, 14, new Color(0xD9B875), new Color(0xFFE2A2), 2f);
        picture(g, "icon_book.png", 92, 152, 76, 74);
        final boolean tutorial = "Tutorial".equals(displayMode);
        final boolean sprint = "Sprint".equals(displayMode);
        String heading = tutorial ? stepLabel.getText() : sprint ? "SPRINT / 40" : "INFINITE";
        centeredLabel(g, heading, 283, 196, 25, new Color(0x6A371A));
        String caption = tutorial ? instruction.getText()
                : sprint ? "40줄을 지우면 클리어! 현재 " + linesValue.getText() + " / 40줄"
                : "제한 없이 테트리스를 즐겨보세요.";
        if (caption == null) caption = "";
        g.setFont(UniversityPixelTheme.font(23, Font.BOLD));
        while (caption.length() > 1 && g.getFontMetrics().stringWidth(caption) > 849) {
            caption = caption.substring(0, caption.length() - 2) + "…";
        }
        g.setColor(INK);
        g.drawString(caption, 410, 196);

        // The original Board paints live pieces and the game grid. Only the bezel
        // is repainted in the restrained dark outline used in story mode.
        panel(g, FRAME_X, FRAME_Y, FRAME_W, FRAME_H, 9,
                new Color(0x20292C), new Color(0xF0E5D6), 5f);
        panel(g, FRAME_X + 7, FRAME_Y + 7, FRAME_W - 14, FRAME_H - 14, 5,
                new Color(0x3B4547), new Color(0x33363C), 4f);

        // HOLD and NEXT share the playfield's top edge and use dark-grid slots.
        panel(g, HOLD_FRAME_X, QUEUE_TOP, 176, 217, 14,
                new Color(0x413831), new Color(0xFFF8EC), 3f);
        centeredLabel(g, "HOLD", HOLD_FRAME_X + 88, QUEUE_TOP + 43, 24, INK);
        panel(g, NEXT_FRAME_X, QUEUE_TOP, 176, 458, 14,
                new Color(0x413831), new Color(0xFFF8EC), 3f);
        centeredLabel(g, "NEXT", NEXT_FRAME_X + 88, QUEUE_TOP + 43, 24, INK);

        // Three slim information rows instead of a dominant statistics box.
        // Unified tutorial stats outer card
        panel(g, 1147, 236, 268, 218, 20, new Color(0x51321D), new Color(0xFFF8EC), 3f);
        statRow(g, 1155, 246, "icon_time.png", "시간", timeValue.getText());
        statRow(g, 1155, 314, "icon_lines.png", "지운 줄",
                "Sprint".equals(displayMode) ? linesValue.getText() + "/40" : linesValue.getText());
        statRow(g, 1155, 382, "icon_combo.png", "콤보", comboValue.getText());

        // Prominent illustrated controls: left lower area, safely below HOLD.
        // Retain the original tutorial_controls_panel.png without redrawing text.
        picture(g, "tutorial_controls_panel.png", 54, 594, 424, 409);

        // Supporting mascot: right lower area, below stats and beside NEXT.
        picture(g, "tutorial_student.png", 1070, 700, 350, 365);
    }

    private static void statRow(Graphics2D g, int x, int y, String icon, String caption, String value) {
        panel(g, x, y, 252, 58, 13, new Color(0xB5C3CE), new Color(0xFFFDF8), 2f);
        picture(g, icon, x + 9, y + 10, 37, 37);
        label(g, caption, x + 53, y + 37, 19, INK, Font.BOLD);
        String display = value == null ? "" : value;
        g.setFont(UniversityPixelTheme.font(21, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(INK);
        g.drawString(display, x + 239 - fm.stringWidth(display), y + 38);
    }

    private static void panel(Graphics2D g, int x, int y, int w, int h, int radius,
            Color outline, Color inside, float border) {
        g.setColor(outline);
        g.fillRoundRect(x, y, w, h, radius, radius);
        int pad = Math.max(2, Math.round(border));
        g.setColor(inside);
        g.fillRoundRect(x + pad, y + pad, w - 2 * pad, h - 2 * pad,
                Math.max(6, radius - 3), Math.max(6, radius - 3));
    }

    private static void centeredLabel(Graphics2D g, String text, int centerX, int baseline,
            int size, Color color) {
        g.setFont(UniversityPixelTheme.font(size, Font.BOLD));
        FontMetrics metrics = g.getFontMetrics();
        g.setColor(color);
        g.drawString(text, centerX - metrics.stringWidth(text) / 2, baseline);
    }

    private static void label(Graphics2D g, String text, int x, int baseline, int size,
            Color color, int style) {
        g.setFont(UniversityPixelTheme.font(size, style));
        g.setColor(color);
        g.drawString(text, x, baseline);
    }

    private static void picture(Graphics2D g, String file, int x, int y, int w, int h) {
        BufferedImage image = CampusArt.image(ASSET_ROOT + file);
        CampusArt.fit(g, image, x, y, w, h);
    }
}
