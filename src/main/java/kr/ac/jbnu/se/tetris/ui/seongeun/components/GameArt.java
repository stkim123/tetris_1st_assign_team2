package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/** 원본 아틀라스를 보존하며 전투별 영역을 런타임에 선택한다. */
public final class GameArt {
    private static final Map<String, BufferedImage> CACHE = new HashMap<String, BufferedImage>();
    private static final Map<String, BufferedImage> ATLASES = new HashMap<String, BufferedImage>();
    private GameArt() { }
    public static String keyForLevel(int level) {
        return (level <= 3 ? "university" : level <= 6 ? "graduation" : "employment")
                + ":" + ((level - 1) % 3);
    }
    public static synchronized BufferedImage sprite(String key) {
        if (key == null) return null;
        if (CACHE.containsKey(key)) return CACHE.get(key);
        BufferedImage sprite = null;
        try {
            String[] parts = key.split(":");
            if (parts.length != 2) return null;
            int index = Integer.parseInt(parts[1]);
            if (index < 0 || index > 2) return null;
            String individual = "university:0".equals(key) ? "alcohol" : "university:1".equals(key)
                    ? "course" : "university:2".equals(key) ? "professor" : "graduation:0".equals(key)
                    ? "retake" : "graduation:1".equals(key) ? "gpt" : "graduation:2".equals(key) ? "capstone" : "employment:0".equals(key) ? "codingtest"
                    : "employment:1".equals(key) ? "interviewer" : "employment:2".equals(key) ? "company" : null;
            if (individual != null) {
                java.net.URL clean = GameArt.class.getResource("/ui/puzzle-rpg/" + individual + ".png");
                if (clean != null) {
                    sprite = ImageIO.read(clean); CACHE.put(key, sprite); return sprite;
                }
            }
            java.net.URL url = GameArt.class.getResource("/ui/puzzle-rpg/" + parts[0] + ".png");
            if (url == null) return null;
            BufferedImage atlas = ATLASES.get(parts[0]);
            if (atlas == null) { atlas = ImageIO.read(url); ATLASES.put(parts[0], atlas); }
            int[][] regions = "graduation".equals(parts[0])
                    ? new int[][] {{0, 458}, {432, 902}, {808, 1536}}
                    : "employment".equals(parts[0])
                    ? new int[][] {{0, 535}, {531, 915}, {914, 1536}}
                    : new int[][] {{0, 528}, {535, 1068}, {1010, 1536}};
            int x = regions[index][0] * atlas.getWidth() / 1536;
            int end = regions[index][1] * atlas.getWidth() / 1536;
            sprite = atlas.getSubimage(x, 0, end - x, atlas.getHeight());
        } catch (IOException | RuntimeException ignored) { }
        CACHE.put(key, sprite);
        return sprite;
    }
    public static ImageIcon icon(String key, int width, int height) {
        BufferedImage source = sprite(key);
        if (source == null) return null;
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        double scale = Math.min((double) width / source.getWidth(), (double) height / source.getHeight());
        int w = (int) (source.getWidth() * scale), h = (int) (source.getHeight() * scale);
        graphics.drawImage(source, (width - w) / 2, (height - h) / 2, w, h, null);
        graphics.dispose();
        return new ImageIcon(scaled);
    }
}
