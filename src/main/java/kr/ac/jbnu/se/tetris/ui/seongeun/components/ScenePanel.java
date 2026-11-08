package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.AlphaComposite;
import java.awt.event.HierarchyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.Timer;

/** 메뉴 화면 공통 배경. 캠퍼스 풍경을 밝은 콘크리트 면 아래에 은은하게 남긴다. */
public class ScenePanel extends JPanel {
    private static final BufferedImage CAMPUS = readCampus();
    private final Timer timer = new Timer(50, event -> repaint());
    private boolean animated = true;

    public ScenePanel() { this(null); }

    public ScenePanel(LayoutManager layout) {
        super(layout == null ? new FlowLayout() : layout);
        setBackground(UniversityPixelTheme.BG);
        timer.setCoalesce(true);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing() && animated) timer.start(); else timer.stop();
            }
        });
    }

    /** 정적인 화면이 필요한 경우 배경 움직임을 끈다. */
    public void setAnimated(boolean animated) {
        this.animated = animated;
        if (!animated) timer.stop(); else if (isShowing()) timer.start();
    }

    @Override public void removeNotify() { timer.stop(); super.removeNotify(); }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try { paintBackdrop(g, getWidth(), getHeight(), animated ? System.currentTimeMillis() : 0); }
        finally { g.dispose(); }
    }

    public static void paintBackdrop(Graphics2D g, int w, int h, long now) {
        g.setColor(new Color(0xA9E0F9));
        g.fillRect(0, 0, w, h);
        BufferedImage scene = CampusArt.image("/ui/campus-rpg/reference-scene.jpg");
        if (scene != null && w > 0 && h > 0) {
            double scale = Math.max(w / (double) scene.getWidth(), h / (double) scene.getHeight());
            int dw = (int) Math.ceil(scene.getWidth() * scale);
            int dh = (int) Math.ceil(scene.getHeight() * scale);
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(scene, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
        }
        g.setColor(new Color(252, 250, 241, 36));
        g.fillRect(0,0,w,h);
    }

    private static BufferedImage readCampus() {
        try {
            java.net.URL resource = ScenePanel.class.getResource("/ui/campus-rpg/university-bg.png");
            return resource == null ? null : ImageIO.read(resource);
        } catch (IOException unavailable) { return null; }
    }
}
