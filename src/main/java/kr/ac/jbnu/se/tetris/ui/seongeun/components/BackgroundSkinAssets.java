package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import kr.ac.jbnu.se.tetris.app.BackgroundSkin;

/** Optional bundled winter PNGs. Missing assets always fall back to the original scenery. */
public final class BackgroundSkinAssets {
    public static final String ROOT = "/ui/campus-rpg/skins/winter/";
    private static final Map<String, BufferedImage> CACHE = new HashMap<String, BufferedImage>();
    private static final String[] SCENES = { "lobby", "university", "graduation", "employment" };

    private BackgroundSkinAssets() { }

    public static String resourcePath(String scene) {
        if ("lobby".equals(scene)) return ROOT + "lobby_winter.png";
        if ("university".equals(scene)) return ROOT + "lobby_winter.png";
        if ("graduation".equals(scene)) return ROOT + "story_graduation_winter.png";
        if ("employment".equals(scene)) return ROOT + "story_job_winter.png";
        throw new IllegalArgumentException("Unknown skin scene: " + scene);
    }

    public static synchronized BufferedImage image(BackgroundSkin skin, String scene) {
        if (skin != BackgroundSkin.WINTER) return null;
        String path = resourcePath(scene);
        if (CACHE.containsKey(path)) return CACHE.get(path);
        BufferedImage image = null;
        try (InputStream input = BackgroundSkinAssets.class.getResourceAsStream(path)) {
            if (input != null) image = ImageIO.read(input);
        } catch (IOException ex) {
            System.err.println("Cannot load winter skin " + path + ": " + ex.getMessage());
        }
        CACHE.put(path, image);
        return image;
    }

    /** Don't sell a pack whose four PNGs have not been installed. */
    public static boolean isWinterPackReady() {
        for (String scene : SCENES) if (image(BackgroundSkin.WINTER, scene) == null) return false;
        return true;
    }
}
