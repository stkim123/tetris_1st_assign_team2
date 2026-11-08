package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * 게임 타이틀식 세로 메뉴. 마우스를 올리거나 ↑↓ 키로 고르고, 클릭이나 Enter로 실행한다.
 * 선택된 항목은 금색 화살표와 밝은 띠로 표시된다.
 */
public final class GameMenu extends JPanel {
    private final List<Item> items = new ArrayList<Item>();
    private int selected;
    private final Timer pulse = new Timer(60, event -> repaint());

    public GameMenu() {
        setOpaque(false);
        setLayout(new GridLayout(0, 1, 0, 4));
        pulse.setCoalesce(true);
        bind(KeyEvent.VK_UP, "menu-up", () -> move(-1));
        bind(KeyEvent.VK_DOWN, "menu-down", () -> move(1));
        bind(KeyEvent.VK_W, "menu-up-w", () -> move(-1));
        bind(KeyEvent.VK_S, "menu-down-s", () -> move(1));
        bind(KeyEvent.VK_ENTER, "menu-enter", () -> { if (!items.isEmpty()) items.get(selected).doClick(); });
    }

    public Item add(String title, String description, Color accent) {
        Item item = new Item(title, description, accent);
        final int index = items.size();
        items.add(item);
        item.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) { select(index); }
        });
        add(item);
        return item;
    }

    // Trim only the TRANSPARENT borders at drawing time; the PNG file stays unchanged.
    private static Rectangle contentBounds(BufferedImage image) {
        int left = image.getWidth(), top = image.getHeight(), right = -1, bottom = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) < 40) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        if (right < left) return new Rectangle(0, 0, image.getWidth(), image.getHeight());
        int border = 5;
        left = Math.max(0, left - border);
        top = Math.max(0, top - border);
        right = Math.min(image.getWidth() - 1, right + border);
        bottom = Math.min(image.getHeight() - 1, bottom + border);
        return new Rectangle(left, top, right - left + 1, bottom - top + 1);
    }

    private void bind(int key, String name, Runnable action) {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), name);
        getActionMap().put(name, new AbstractAction() {
            // 숨겨진 메뉴는 키를 소비하지 않아 다른 화면의 조작을 방해하지 않는다.
            @Override public boolean isEnabled() { return GameMenu.this.isShowing(); }
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    private void move(int delta) {
        if (items.isEmpty()) return;
        select(Math.floorMod(selected + delta, items.size()));
    }

    public void select(int index) {
        if (index < 0 || index >= items.size()) return;
        selected = index;
        for (Component component : getComponents()) component.repaint();
    }

    @Override public void addNotify() { super.addNotify(); pulse.start(); }
    @Override public void removeNotify() { pulse.stop(); super.removeNotify(); }

    public final class Item extends JButton {
        private final Color accent;
        private String description;
        private String badge;
        private Color badgeColor;
        private BufferedImage skin;
        private Rectangle skinBounds;

        Item(String title, String description, Color accent) {
            super(title);
            this.accent = accent;
            this.description = description;
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setFocusable(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(300, 52));
        }

        /** Image already contains a label and an icon: do not draw the Java title on top of it. */
        public void setImageResource(String resourcePath) {
            try (InputStream stream = GameMenu.class.getResourceAsStream(resourcePath)) {
                if (stream == null) {
                    System.err.println("Missing lobby button image: " + resourcePath);
                    skin = null;
                    skinBounds = null;
                } else {
                    skin = ImageIO.read(stream);
                    skinBounds = skin == null ? null : contentBounds(skin);
                }
            } catch (IOException error) {
                System.err.println("Cannot read lobby button image: " + resourcePath + " / " + error.getMessage());
                skin = null;
                skinBounds = null;
            }
            repaint();
        }

        public void setDescription(String description) { this.description = description; repaint(); }

        public void setBadge(String badge, Color color) { this.badge = badge; this.badgeColor = color; repaint(); }

        private boolean isSelectedItem() { return items.indexOf(this) == selected; }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                int w = getWidth(), h = getHeight();
                if (w <= 8 || h <= 8) return;
                boolean selectedItem = isSelectedItem();
                boolean pressed = getModel().isPressed();
                boolean hover = getModel().isRollover();

                if (skin != null && skinBounds != null) {
                    paintSkin(g, w, h, selectedItem, pressed, hover);
                } else {
                    // Local Mode has no PNG yet: keep the menu usable with a matching cartoon fallback.
                    paintFallback(g, w, h, selectedItem, pressed);
                }
                if (badge != null) paintBadge(g, w, h);
            } finally { g.dispose(); }
        }

        private void paintSkin(Graphics2D g, int w, int h, boolean selectedItem,
                               boolean pressed, boolean hover) {
            // Keep the original PNG artwork aspect ratio. Never stretch it into
            // the whole GridLayout cell: the source images have different transparent padding.
            int maxWidth = Math.max(1, w - 12);
            int maxHeight = Math.max(1, h - 6);
            double scale = Math.min((double) maxWidth / skinBounds.width,
                    (double) maxHeight / skinBounds.height);
            int dw = Math.max(1, (int) Math.round(skinBounds.width * scale));
            int dh = Math.max(1, (int) Math.round(skinBounds.height * scale));
            int dx = (w - dw) / 2 + (pressed ? 1 : 0);
            int dy = (h - dh) / 2 + (pressed ? 1 : 0);

            if (selectedItem || hover) {
                g.setColor(selectedItem ? new Color(255, 229, 125, 65)
                        : new Color(255, 255, 255, 55));
                g.fillRoundRect(dx + 3, dy + 3, dw - 6, dh - 6, 22, 22);
            }
            // One scale for X/Y: rounded corners, icons and Korean text keep their proportions.
            g.drawImage(skin, dx, dy, dx + dw, dy + dh,
                    skinBounds.x, skinBounds.y,
                    skinBounds.x + skinBounds.width,
                    skinBounds.y + skinBounds.height, null);
            if (selectedItem) {
                g.setColor(new Color(255, 249, 207, 125));
                g.setStroke(new BasicStroke(1.2f));
                g.drawRoundRect(dx + 2, dy + 2, dw - 5, dh - 5, 22, 22);
            }
        }

        private void paintFallback(Graphics2D g, int w, int h, boolean selectedItem, boolean pressed) {
            int bh = h - 7;
            int bw = Math.min(w - 18, Math.round(bh * 4.9f));
            int x = (w - bw) / 2 + (pressed ? 2 : 0);
            int y = 3 + (pressed ? 2 : 0);
            g.setColor(new Color(0x34180C));
            g.fillRoundRect(x, y, bw, bh, 24, 24);
            g.setColor(new Color(0xF7E6CE));
            g.fillRoundRect(x + 3, y + 3, bw - 6, bh - 6, 20, 20);
            g.setColor(selectedItem ? new Color(0xFFE9BA) : new Color(0xFFF3DE));
            g.fillRoundRect(x + 6, y + 6, bw - 12, bh - 12, 16, 16);
            g.setColor(new Color(255, 255, 255, 110));
            g.drawRoundRect(x + 7, y + 7, bw - 14, bh - 14, 16, 16);
            g.setColor(new Color(0x36200F));
            g.setFont(UniversityPixelTheme.font(18, Font.BOLD));
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(getText(), (w - metrics.stringWidth(getText())) / 2,
                    (h - metrics.getHeight()) / 2 + metrics.getAscent());
        }

        private void paintBadge(Graphics2D g, int w, int h) {
            g.setFont(UniversityPixelTheme.font(10, Font.BOLD));
            FontMetrics metrics = g.getFontMetrics();
            int bw = Math.min(w - 14, metrics.stringWidth(badge) + 15);
            int bx = w - bw - 7, by = Math.max(3, h - 22);
            g.setColor(new Color(0x321E14));
            g.fillRoundRect(bx - 1, by - 1, bw + 2, 19, 12, 12);
            Color fill = badgeColor == null ? UniversityPixelTheme.PANEL_LIGHT : badgeColor;
            g.setColor(fill);
            g.fillRoundRect(bx, by, bw, 17, 10, 10);
            g.setColor(UniversityPixelTheme.readableOn(fill));
            g.drawString(badge, bx + 7, by + 12);
        }
    }
}
