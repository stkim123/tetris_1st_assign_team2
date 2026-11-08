package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import kr.ac.jbnu.se.tetris.core.PieceType;

/** Clean, saturated cartoon skin shared by the board, HOLD and NEXT. */
public final class ConcreteBlockSkin {
    private static final String ORE_PATH = "/ui/campus-rpg/ore-tile.png";
    private static final BufferedImage ORE_IMAGE = loadOreImage();
    private static final Rectangle ORE_VISIBLE_AREA = findVisibleArea(ORE_IMAGE);

    private ConcreteBlockSkin() { }

    public static Color color(PieceType type) {
        if (type == null) return new Color(0xABB5BD);
        switch (type) {
            case I: return new Color(0x58CCF6);
            case O: return new Color(0xFFD84F);
            case T: return new Color(0xC06AF4);
            case S: return new Color(0xA2DC4C);
            case Z: return new Color(0xFF6D6A);
            case J: return new Color(0x539FF5);
            case L: return new Color(0xFF9E47);
            case GARBAGE: return new Color(0x8996A3);
            default: return new Color(0xABB5BD);
        }
    }

    public static void paint(Graphics2D g, int x, int y, int size,
                             PieceType type, boolean ghost, boolean ore) {
        if (size < 2 || type == null || type == PieceType.EMPTY) return;
        Color color = color(type);
        if (ghost) {
            int inset = size >= 8 ? 2 : 1;
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 45));
            g.fillRect(x + inset, y + inset, Math.max(1, size - inset * 2), Math.max(1, size - inset * 2));
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 195));
            g.drawRect(x + inset, y + inset, Math.max(0, size - inset * 2 - 1), Math.max(0, size - inset * 2 - 1));
            if (ore) paintOreMark(g, x, y, size, true);
            return;
        }

        // An item/ore cell uses the actual PNG. Ordinary cells keep their bright cartoon colors.
        if (ore && ORE_IMAGE != null && ORE_VISIBLE_AREA != null) {
            paintOreImage(g, x, y, size);
            return;
        }

        g.setColor(new Color(0x17242C));
        g.fillRect(x, y, size, size);
        int inset = size >= 6 ? 1 : 0;
        int inner = size - inset * 2;
        g.setColor(darken(color, 0.22));
        g.fillRect(x + inset, y + inset, inner, inner);
        if (inner >= 3) {
            g.setColor(color);
            g.fillRect(x + inset + 1, y + inset + 1, inner - 2, inner - 2);
        }
        if (size >= 8) {
            // Small, bevelled highlights like the reference, not noisy textures.
            int highlightX = x + inset + 2;
            int highlightY = y + inset + 2;
            int highlightW = Math.max(1, inner - 4);
            g.setColor(brighten(color, 0.28));
            g.drawLine(highlightX, highlightY, highlightX + highlightW - 1, highlightY);
            g.drawLine(highlightX, highlightY, highlightX, highlightY + Math.max(1, inner / 3));
            g.setColor(darken(color, 0.15));
            g.drawLine(x + size - inset - 2, y + inset + 2,
                    x + size - inset - 2, y + size - inset - 2);
            g.drawLine(x + inset + 2, y + size - inset - 2,
                    x + size - inset - 2, y + size - inset - 2);
        }
        if (ore) paintOreMark(g, x, y, size, false);
    }

    /** Render the supplied artwork without stretching or including its transparent margins. */
    private static void paintOreImage(Graphics2D g, int x, int y, int size) {
        g.setColor(new Color(0x17242C));
        g.fillRect(x, y, size, size);

        int padding = size >= 12 ? 1 : 0;
        int available = Math.max(1, size - 2 * padding);
        double scale = Math.min((double) available / ORE_VISIBLE_AREA.width,
                (double) available / ORE_VISIBLE_AREA.height);
        int width = Math.max(1, (int) Math.round(ORE_VISIBLE_AREA.width * scale));
        int height = Math.max(1, (int) Math.round(ORE_VISIBLE_AREA.height * scale));
        int dx = x + (size - width) / 2;
        int dy = y + (size - height) / 2;

        Graphics2D imageGraphics = (Graphics2D) g.create();
        try {
            imageGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            imageGraphics.drawImage(ORE_IMAGE, dx, dy, dx + width, dy + height,
                    ORE_VISIBLE_AREA.x, ORE_VISIBLE_AREA.y,
                    ORE_VISIBLE_AREA.x + ORE_VISIBLE_AREA.width,
                    ORE_VISIBLE_AREA.y + ORE_VISIBLE_AREA.height, null);
        } finally {
            imageGraphics.dispose();
        }
    }

    private static BufferedImage loadOreImage() {
        try (InputStream input = ConcreteBlockSkin.class.getResourceAsStream(ORE_PATH)) {
            if (input == null) {
                System.err.println("Missing item block image: " + ORE_PATH);
                return null;
            }
            return ImageIO.read(input);
        } catch (IOException error) {
            System.err.println("Cannot load item block image " + ORE_PATH + ": " + error.getMessage());
            return null;
        }
    }

    /** Ignore the transparent outer canvas of the PNG before fitting it in one block. */
    private static Rectangle findVisibleArea(BufferedImage image) {
        if (image == null) return null;
        int left = image.getWidth(), top = image.getHeight(), right = -1, bottom = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) <= 8) continue;
                if (x < left) left = x;
                if (x > right) right = x;
                if (y < top) top = y;
                if (y > bottom) bottom = y;
            }
        }
        return right < left ? null : new Rectangle(left, top, right - left + 1, bottom - top + 1);
    }

    /** Retain a legible ore indicator when the PNG cannot be loaded or for ghost cells. */
    private static void paintOreMark(Graphics2D g, int x, int y, int size, boolean ghost) {
        if (size < 8) return;
        int unit = Math.max(1, size / 8);
        int cx = x + size / 2, cy = y + size / 2;
        g.setColor(ghost ? new Color(255, 222, 116, 150) : new Color(0x513619));
        g.fillRect(cx - 2 * unit, cy - unit, 4 * unit, 3 * unit);
        g.setColor(ghost ? new Color(255, 232, 142, 210) : new Color(0xFFB82B));
        g.fillRect(cx - 2 * unit, cy - unit, 2 * unit, 2 * unit);
        g.fillRect(cx, cy, 2 * unit, 2 * unit);
        g.setColor(new Color(0xFFF4AD));
        g.fillRect(cx - unit, cy - unit, 2 * unit, unit);
        g.fillRect(cx, cy, unit, unit);
    }

    private static Color darken(Color c, double fraction) {
        return new Color((int) Math.round(c.getRed() * (1 - fraction)),
                (int) Math.round(c.getGreen() * (1 - fraction)),
                (int) Math.round(c.getBlue() * (1 - fraction)));
    }

    private static Color brighten(Color c, double fraction) {
        return new Color((int) Math.round(c.getRed() + (255 - c.getRed()) * fraction),
                (int) Math.round(c.getGreen() + (255 - c.getGreen()) * fraction),
                (int) Math.round(c.getBlue() + (255 - c.getBlue()) * fraction));
    }
}
