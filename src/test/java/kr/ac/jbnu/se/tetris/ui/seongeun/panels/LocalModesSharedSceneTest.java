package kr.ac.jbnu.se.tetris.ui.seongeun.panels;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.CampusArt;

/** Regression checks: same game scene for tutorial, sprint and infinite modes. */
public final class LocalModesSharedSceneTest {
    private LocalModesSharedSceneTest() { }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assert CampusArt.image("/ui/campus-rpg/tutorial/sprint_mode_logo.png") != null;
            assert CampusArt.image("/ui/campus-rpg/tutorial/infinite_mode_logo.png") != null;
            assert CampusArt.image("/ui/campus-rpg/tutorial/tutorial_title.png") != null;
            assert CampusArt.image("/ui/campus-rpg/tutorial/tutorial_controls_panel.png") != null;

            LocalGamePanel local = new LocalGamePanel();
            local.setSize(1448, 1086);
            AtomicInteger exits = new AtomicInteger();
            local.setBackAction(event -> exits.incrementAndGet());
            TutorialScenePanel scene = null;
            for (Component child : local.getComponents()) {
                if (child instanceof TutorialScenePanel) scene = (TutorialScenePanel) child;
            }
            assert scene != null : "Shared scene not registered";
            Set<Long> renderedLogos = new HashSet<Long>();
            for (String mode : new String[] {"Tutorial", "Sprint", "Infinite", "Tutorial"}) {
                local.startMode(mode);
                local.doLayout();
                scene.doLayout();
                assert mode.equals(scene.getDisplayMode()) : "Wrong mode after switching: " + mode;
                assert local.getPlayerBoard().getParent() == scene : "Board detached: " + mode;
                assert local.getPlayerBoard().getWidth() > 0 : "No board layout: " + mode;
                assert local.getPlayerBoard().getX() + local.getPlayerBoard().getWidth() / 2 == local.getWidth() / 2
                        : "Board is not centered: " + mode;
                AbstractButton back = button(scene, "돌아가기 [ESC]");
                back.doClick();
                assert exits.get() > 0 : "Back callback missing: " + mode;

                BufferedImage buffer = new BufferedImage(1448, 1086, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = buffer.createGraphics();
                try { scene.paint(g); } finally { g.dispose(); }
                long hash = 1125899906842597L;
                for (int y = 20; y < 135; y += 3) {
                    for (int x = 54; x < 346; x += 3) hash = 31 * hash + buffer.getRGB(x, y);
                }
                renderedLogos.add(hash);
            }
            assert renderedLogos.size() == 3 : "The three mode logos must be visually different";
            assert exits.get() == 4 : "Back callback must remain active in every mode";
        });
        System.out.println("PASS LocalModesSharedSceneTest");
    }

    private static AbstractButton button(Component component, String title) {
        if (component instanceof AbstractButton && title.equals(((AbstractButton) component).getText())) {
            return (AbstractButton) component;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                AbstractButton found = button(child, title);
                if (found != null) return found;
            }
        }
        return null;
    }
}
