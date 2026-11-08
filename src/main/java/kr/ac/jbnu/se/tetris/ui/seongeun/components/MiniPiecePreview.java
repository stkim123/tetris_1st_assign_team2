package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.*;
import javax.swing.*;
import kr.ac.jbnu.se.tetris.core.Piece;
import kr.ac.jbnu.se.tetris.core.PieceType;

/** HOLD와 NEXT를 실제 미노 모양으로 보여 주는 작은 픽셀 창. */
public final class MiniPiecePreview extends JComponent {
    private final String title;
    private PieceType type;
    private boolean item;
    private int oreCellIndex = -1;
    // PvE preview slots use a bright cartoon appearance. PvP uses the original skin.
    private boolean battleHudStyle;
    private boolean tutorialStyle;

    /** Tutorial uses the same live HOLD/NEXT data with light cards, not the combat skin. */
    public void setTutorialStyle(boolean enabled) { tutorialStyle = enabled; repaint(); }

    public void setBattleHudStyle(boolean enabled) {
        battleHudStyle = enabled;
        repaint();
    }
    public MiniPiecePreview(String title) {
        this.title = title;
        setPreferredSize(new Dimension(68, 70));
        setMinimumSize(new Dimension(66, 68));
        setMaximumSize(new Dimension(68, 70));
        setToolTipText(title);
    }
    public void setPiece(PieceType type, boolean item) {
        setPiece(type, item, -1);
    }
    public void setPiece(PieceType type, boolean item, int oreCellIndex) {
        this.type = type == PieceType.EMPTY ? null : type;
        this.item = item;
        this.oreCellIndex = item ? oreCellIndex : -1;
        repaint();
    }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            if (tutorialStyle) {
                paintTutorialPreview(g);
                return;
            }
            if (battleHudStyle) {
                paintBattleHudPreview(g);
                return;
            }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setColor(UniversityPixelTheme.BLACK); g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(UniversityPixelTheme.PANEL); g.fillRect(3, 3, getWidth() - 6, getHeight() - 6);
            g.setColor(UniversityPixelTheme.LINE); g.drawRect(5, 5, getWidth() - 11, getHeight() - 11);
            g.setFont(UniversityPixelTheme.font(12, Font.BOLD));
            g.setColor(UniversityPixelTheme.GOLD); g.drawString(title, 7, 15);
            if (type == null) {
                g.setColor(UniversityPixelTheme.TEXT_SUB);
                g.drawString("없음", 19, 46);
                return;
            }
            Piece piece = new Piece(type);
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            for (int index = 0; index < 4; index++) {
                minX = Math.min(minX, piece.x(index)); maxX = Math.max(maxX, piece.x(index));
                minY = Math.min(minY, piece.y(index)); maxY = Math.max(maxY, piece.y(index));
            }
            int cell = 11, width = (maxX - minX + 1) * cell, height = (maxY - minY + 1) * cell;
            int left = (getWidth() - width) / 2, top = 22 + (getHeight() - 22 - height) / 2;
            for (int index = 0; index < 4; index++) {
                int x = left + (piece.x(index) - minX) * cell;
                int y = top + (maxY - piece.y(index)) * cell;
                ConcreteBlockSkin.paint(g, x, y, cell, type, false, item && index == oreCellIndex);
            }
            if (item) {
                g.setColor(UniversityPixelTheme.GOLD);
                g.fillRect(getWidth() - 12, 3, 6, 6);
            }
        } finally { g.dispose(); }
    }

    private void paintTutorialPreview(Graphics2D g) {
        int w = getWidth(), h = getHeight();
        if (w < 16 || h < 16) return;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xACDAF2)); g.fillRoundRect(0, 0, w, h, 20, 20);
        g.setColor(new Color(0xFDF9EF)); g.fillRoundRect(3, 3, w - 6, h - 6, 17, 17);
        g.setColor(new Color(0x8FBFDD)); g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(3, 3, w - 7, h - 7, 17, 17);
        if (type == null) {
            g.setColor(new Color(0x626C84));
            g.setFont(UniversityPixelTheme.font(Math.max(11, Math.min(19, w / 6)), Font.PLAIN));
            String empty = "없음";
            g.drawString(empty, (w - g.getFontMetrics().stringWidth(empty)) / 2,
                    (h + g.getFontMetrics().getAscent()) / 2 - 3);
            return;
        }
        Piece piece = new Piece(type);
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int i = 0; i < 4; i++) {
            minX = Math.min(minX, piece.x(i)); maxX = Math.max(maxX, piece.x(i));
            minY = Math.min(minY, piece.y(i)); maxY = Math.max(maxY, piece.y(i));
        }
        int cols = maxX - minX + 1, rows = maxY - minY + 1;
        int cell = Math.max(7, Math.min(27, Math.min((w - 19) / cols, (h - 19) / rows)));
        int left = (w - cols * cell) / 2, top = (h - rows * cell) / 2;
        Color fill = battlePieceColor(type);
        for (int i = 0; i < 4; i++) {
            int x = left + (piece.x(i) - minX) * cell;
            int y = top + (maxY - piece.y(i)) * cell;
            g.setColor(new Color(0x182039)); g.fillRoundRect(x, y, cell, cell, 4, 4);
            g.setColor(fill); g.fillRoundRect(x + 2, y + 2, cell - 4, cell - 4, 3, 3);
            g.setColor(new Color(255, 255, 255, 190));
            g.drawLine(x + 4, y + 3, x + cell - 5, y + 3);
            if (item && i == oreCellIndex) {
                g.setColor(new Color(0xFFDD67));
                g.fillOval(x + cell / 2 - 4, y + cell / 2 - 4, 8, 8);
            }
        }
    }

    /** PvE-only: inset dark tile with recognizable high-saturation tetromino colors. */
    private void paintBattleHudPreview(Graphics2D g) {
        int w = getWidth(), h = getHeight();
        if (w < 16 || h < 16) return;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(0x101B1B));
        g.fillRoundRect(0, 0, w, h, 10, 10);
        g.setColor(new Color(0x56685B));
        g.fillRoundRect(3, 3, w - 6, h - 6, 8, 8);
        g.setColor(new Color(0x1C292B));
        g.fillRoundRect(6, 6, w - 12, h - 12, 5, 5);
        g.setColor(new Color(0x394A4B));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(6, 6, w - 13, h - 13, 5, 5);

        // Subtle 4-column board grid; keeps pieces visually readable.
        g.setColor(new Color(0x344243));
        int grid = Math.max(12, (w - 18) / 4);
        for (int x = 8 + grid; x < w - 8; x += grid) g.drawLine(x, 8, x, h - 9);
        for (int y = 8 + grid; y < h - 8; y += grid) g.drawLine(8, y, w - 9, y);

        if (type == null) return; // Empty HOLD displays an empty slot, no extra text.
        Piece piece = new Piece(type);
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int i = 0; i < 4; i++) {
            minX = Math.min(minX, piece.x(i)); maxX = Math.max(maxX, piece.x(i));
            minY = Math.min(minY, piece.y(i)); maxY = Math.max(maxY, piece.y(i));
        }
        int cols = maxX - minX + 1, rows = maxY - minY + 1;
        int cell = Math.max(10, Math.min(22, Math.min((w - 22) / cols, (h - 22) / rows)));
        int left = (w - cols * cell) / 2;
        int top = (h - rows * cell) / 2;
        Color block = battlePieceColor(type);
        for (int i = 0; i < 4; i++) {
            int x = left + (piece.x(i) - minX) * cell;
            int y = top + (maxY - piece.y(i)) * cell;
            // Dark outline, rich color and a single highlight; no concrete texture.
            g.setColor(new Color(0x0D191D));
            g.fillRoundRect(x, y, cell, cell, 4, 4);
            g.setColor(block);
            g.fillRoundRect(x + 1, y + 1, cell - 2, cell - 2, 3, 3);
            g.setColor(new Color(255, 255, 255, 105));
            g.drawLine(x + 3, y + 2, x + cell - 4, y + 2);
            g.setColor(new Color(0, 0, 0, 70));
            g.drawLine(x + 2, y + cell - 3, x + cell - 3, y + cell - 3);
            if (item && i == oreCellIndex) {
                g.setColor(new Color(0xFFE47B));
                g.fillOval(x + cell / 2 - 3, y + cell / 2 - 3, 6, 6);
            }
        }
    }

    private static Color battlePieceColor(PieceType type) {
        switch (type) {
            case I: return new Color(0x37C9F5);
            case O: return new Color(0xFFCD45);
            case T: return new Color(0xC45CF2);
            case S: return new Color(0x86E855);
            case Z: return new Color(0xF36961);
            case J: return new Color(0x4E9BFF);
            case L: return new Color(0xFF9C47);
            default: return new Color(0xA0A8B1);
        }
    }

}
