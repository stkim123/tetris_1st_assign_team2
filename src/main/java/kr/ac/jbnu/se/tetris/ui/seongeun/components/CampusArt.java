package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Decorated Swing primitives. All real controls and game data remain live components. */
public final class CampusArt {
    public static final Color INK = new Color(0x2D1B13);
    public static final Color NAVY = new Color(0x102747);
    public static final Color CREAM = new Color(0xFFF9EC);
    public static final Color WHITE = new Color(0xFFFDF6);
    public static final Color BROWN = new Color(0x8C5330);
    public static final Color GOLD = new Color(0xFFBD30);
    public static final Color GREEN = new Color(0x168D69);
    public static final Color BLUE = new Color(0x4C96E8);
    private static final Map<String, BufferedImage> CACHE = new HashMap<String, BufferedImage>();
    private CampusArt() { }

    public static BufferedImage image(String path) {
        if (CACHE.containsKey(path)) return CACHE.get(path);
        BufferedImage art = null;
        try {
            java.net.URL url = CampusArt.class.getResource(path);
            if (url != null) art = ImageIO.read(url);
        } catch (IOException ignored) { }
        CACHE.put(path, art);
        return art;
    }

    public static void fit(Graphics2D g, Image image, int x, int y, int w, int h) {
        if (image == null || w < 1 || h < 1) return;
        int iw = image.getWidth(null), ih = image.getHeight(null);
        if (iw <= 0 || ih <= 0) return;
        double factor = Math.min(w / (double) iw, h / (double) ih);
        int dw = Math.max(1, (int) Math.round(iw * factor)), dh = Math.max(1, (int) Math.round(ih * factor));
        g.drawImage(image, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, null);
    }

    public static JLabel picture(String path, int width, int height) {
        JLabel label=new JLabel();label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setPreferredSize(new Dimension(width,height));
        BufferedImage img=image(path);
        if(img!=null){
            BufferedImage target=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=target.createGraphics();
            try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                fit(g,img,0,0,width,height);
            } finally{g.dispose();}
            label.setIcon(new ImageIcon(target));
        }
        return label;
    }

    public static JLabel icon(String key, int width, int height) {
        BufferedImage img = image("/ui/campus-rpg/reference-icons/" + key + ".png");
        JLabel label = new JLabel();
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setPreferredSize(new Dimension(width, height));
        if (img != null) {
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                fit(g,img,0,0,width,height);
            } finally { g.dispose(); }
            label.setIcon(new ImageIcon(scaled));
        }
        return label;
    }

    public static Card card(LayoutManager layout, Color border, int padding) {
        return new Card(layout, border, padding);
    }

    public static final class Card extends JPanel {
        private final Color edge;
        public Card(LayoutManager layout, Color edge, int padding) {
            super(layout);
            this.edge = edge;
            setOpaque(false);
            setBorder(new EmptyBorder(padding + 4, padding + 5, padding + 8, padding + 5));
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                g.setColor(new Color(42, 29, 22, 65));
                g.fillRoundRect(5, 7, Math.max(0,w-9), Math.max(0,h-9), 32, 32);
                g.setColor(edge);g.fillRoundRect(1, 1, Math.max(0,w-5),Math.max(0,h-9),30,30);
                g.setPaint(new GradientPaint(0,4,CREAM,0,h,WHITE));
                g.fillRoundRect(5, 5, Math.max(0,w-13), Math.max(0,h-17),25,25);
                g.setColor(new Color(255,255,255,175));
                g.setStroke(new BasicStroke(2f));g.drawRoundRect(9,9,Math.max(0,w-21),Math.max(0,h-25),21,21);
            } finally { g.dispose(); }
        }
    }

    public static JLabel text(String text, int size, Color color) {
        JLabel label=new JLabel(text);
        label.setFont(UniversityPixelTheme.font(size, Font.BOLD));
        label.setForeground(color);
        return label;
    }

    public static JPanel heading(String english,String korean) {
        JPanel p=new JPanel();p.setOpaque(false);
        p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));
        JLabel top=text(english,14,NAVY);
        JLabel main=text(korean,38,NAVY);
        p.add(top);p.add(main);
        return p;
    }
}
