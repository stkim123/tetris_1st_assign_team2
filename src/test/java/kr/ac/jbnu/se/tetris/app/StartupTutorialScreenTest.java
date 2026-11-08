package kr.ac.jbnu.se.tetris.app;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.Random;

import javax.swing.Action;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;

import kr.ac.jbnu.se.tetris.core.GameAction;

public final class StartupTutorialScreenTest {
    private StartupTutorialScreenTest() { }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            testTutorialStartup();
            testSkipPreferenceDoesNotSkipStartup();
        });
    }

    private static void testTutorialStartup() {
        SeongeunApplication app = new SeongeunApplication(new Random(31));
        try {
            assert "LOBBY".equals(app.getCurrentScreen());

            app.prepareFirstWindowScreen();

            assert "LOCAL_GAME".equals(app.getCurrentScreen());
            assert app.getLocalState() != null;
            assert app.isLocalGravityRunning();

            long before = app.getLocalState().getVersion();
            app.submit(GameAction.Type.HARD_DROP);
            assert app.getLocalState().getVersion() > before;

            Action back = app.getScreens().getActionMap().get("navigate-back");
            assert back != null;

            back.actionPerformed(new ActionEvent(app.getScreens(), 0, "navigate-back"));

            assert "LOBBY".equals(app.getCurrentScreen());
            assert app.getLocalState() == null;
            assert !app.isLocalGravityRunning();
        } finally {
            app.close();
        }
    }

    private static void testSkipPreferenceDoesNotSkipStartup() {
        SeongeunApplication app = new SeongeunApplication(new Random(32));
        try {
            JCheckBox skip = findCheckBox(app.getScreens(), "다시 보지 않기");
            assert skip != null;

            skip.doClick();

            app.prepareFirstWindowScreen();

            // "?ㅼ떆 蹂댁? ?딄린" ?ㅼ젙怨?臾닿??섍쾶 寃뚯엫 理쒖큹 ?ㅽ뻾? ?쒗넗由ъ뼹遺???쒖옉?쒕떎.
            assert "LOCAL_GAME".equals(app.getCurrentScreen());
            assert app.getLocalState() != null;
            assert app.isLocalGravityRunning();
        } finally {
            app.close();
        }
    }

    private static JCheckBox findCheckBox(Component root, String text) {
        if (root instanceof JCheckBox) {
            JCheckBox checkBox = (JCheckBox) root;
            if (text.equals(checkBox.getText())) return checkBox;
        }

        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                JCheckBox found = findCheckBox(child, text);
                if (found != null) return found;
            }
        }

        return null;
    }
}