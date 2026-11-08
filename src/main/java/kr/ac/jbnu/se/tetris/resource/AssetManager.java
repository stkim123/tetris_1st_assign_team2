package kr.ac.jbnu.se.tetris.resource;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/** classpath 리소스 조회·캐시 및 최종 이미지 누락 시 대체 이미지 제공 */
public final class AssetManager {
    private static final Logger LOG = Logger.getLogger(AssetManager.class.getName());
    private final Properties manifest = new Properties();
    private final Map<String, BufferedImage> originals = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private final Map<String, ImageIcon> scaled = new LinkedHashMap<String, ImageIcon>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, ImageIcon> entry) {
            return size() > 128;
        }
    };

    public AssetManager() {
        try (InputStream in = AssetManager.class.getResourceAsStream("/assets.properties")) {
            if (in == null) warn("manifest", "Missing assets.properties; using placeholders");
            else manifest.load(in);
        } catch (IOException ex) {
            warn("manifest", "Cannot read asset manifest: " + ex.getMessage());
        }
    }

    /** 자산 ID와 표시 크기별 아이콘을 조회하며 원본 비율을 유지해 크기를 조정 */
    public synchronized ImageIcon getIcon(String id, int width, int height) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Asset ID is required");
        if (width < 1 || height < 1 || width > 4096 || height > 4096) {
            throw new IllegalArgumentException("Asset size must be between 1 and 4096");
        }
        String key = id + ":" + width + ":" + height;
        ImageIcon cached = scaled.get(key);
        if (cached != null) return cached;
        BufferedImage source = originals.get(id);
        if (source == null) {
            source = load(id);
            // 미등록 ID의 무제한 캐시 방지
            if (originals.size() < 128) originals.put(id, source);
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double ratio = Math.min((double) width / source.getWidth(), (double) height / source.getHeight());
            int w = Math.max(1, (int) Math.round(source.getWidth() * ratio));
            int h = Math.max(1, (int) Math.round(source.getHeight() * ratio));
            g.drawImage(source, (width - w) / 2, (height - h) / 2, w, h, null);
        } finally { g.dispose(); }
        ImageIcon icon = new ImageIcon(image, id);
        scaled.put(key, icon);
        return icon;
    }

    /** 매니페스트의 경로에서 원본을 읽고 누락·오류 시 대체 이미지를 반환 */
    private BufferedImage load(String id) {
        String path = manifest.getProperty(id);
        if (path != null) {
            try (InputStream in = AssetManager.class.getResourceAsStream("/" + path)) {
                if (in != null) {
                    BufferedImage image = ImageIO.read(in);
                    if (image != null) return image;
                }
                warn(id, "Missing or invalid asset: " + id + " (" + path + ")");
            } catch (IOException ex) {
                warn(id, "Cannot read " + id + ": " + ex.getMessage());
            }
        } else warn(id, "Unknown asset ID: " + id);
        return placeholder();
    }

    private void warn(String id, String message) {
        if (warned.add(id)) LOG.warning(message);
    }

    private static BufferedImage placeholder() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(110, 120, 140));
            g.fillRect(2, 2, 60, 60);
            g.setColor(Color.WHITE);
            g.drawRect(2, 2, 59, 59);
            g.drawLine(8, 8, 55, 55);
            g.drawLine(55, 8, 8, 55);
        } finally { g.dispose(); }
        return image;
    }
}
