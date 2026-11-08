package kr.ac.jbnu.se.tetris.app;

import java.awt.Component;
import java.awt.Container;
import java.util.Random;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.AbstractButton;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;
import kr.ac.jbnu.se.tetris.app.session.SessionPhase;
import kr.ac.jbnu.se.tetris.core.GameAction;

/** 성은 원본 버튼에서 실제 세션까지 이어지는 최소 흐름 검증. */
public final class SeongeunApplicationTest {
    private SeongeunApplicationTest() { }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SeongeunApplication app = newTestApp(17);
            try {
                assert LOBBY.equals(app.getCurrentScreen());
                button(app.getScreens(), "설정").doClick();
                assert "SETTINGS".equals(app.getCurrentScreen());
                button(app.getScreens(), "튜토리얼 시작").doClick();
                assert LOCAL_GAME.equals(app.getCurrentScreen()) && app.isLocalGravityRunning();
                button(app.getScreens(), "돌아가기 [ESC]").doClick();
                assert "SETTINGS".equals(app.getCurrentScreen()) && !app.isLocalGravityRunning();
                button(app.getScreens(), "돌아가기").doClick();
                assert menuItem(app.getMenu(), "캐릭터 / 상점").isEnabled();
                button(app.getScreens(), "캐릭터 / 상점").doClick();
                assert "CHARACTER_SHOP".equals(app.getCurrentScreen());
                assert app.getSaveData().getCoins() == 0;
                button(app.getScreens(), "돌아가기").doClick();

                button(app.getScreens(), "로컬 모드").doClick();
                assert LOCAL_MODE.equals(app.getCurrentScreen());
                buttonNamed(app.getScreens(), "infiniteModeStart").doClick();
                assert LOCAL_GAME.equals(app.getCurrentScreen());
                assert !menuItem(app.getMenu(), "캐릭터 / 상점").isEnabled();
                menuItem(app.getMenu(), "캐릭터 / 상점").doClick();
                assert LOCAL_GAME.equals(app.getCurrentScreen());
                long before = app.getLocalState().getVersion();
                app.submit(GameAction.Type.HARD_DROP);
                assert app.getLocalState().getVersion() > before;
                assert app.isLocalGravityRunning();
                app.withLocalGamePaused(() -> {
                    assert app.getLocalState().getStatus() == kr.ac.jbnu.se.tetris.core.GameState.Status.PAUSED;
                    assert !app.isLocalGravityRunning();
                    long pausedVersion = app.getLocalState().getVersion();
                    app.submit(GameAction.Type.HARD_DROP);
                    assert app.getLocalState().getVersion() == pausedVersion;
                });
                assert app.isLocalGravityRunning();
                assert app.getLocalState().getStatus() == kr.ac.jbnu.se.tetris.core.GameState.Status.RUNNING;
                button(app.getScreens(), "돌아가기 [ESC]").doClick();
                assert LOCAL_MODE.equals(app.getCurrentScreen());
                assert app.getLocalState() == null;
                assert !app.isLocalGravityRunning();
                buttonNamed(app.getScreens(), "infiniteModeStart").doClick();
                escape(app);
                assert LOCAL_MODE.equals(app.getCurrentScreen());
                assert app.getLocalState() == null && !app.isLocalGravityRunning();

                button(app.getScreens(), "돌아가기").doClick();
                buttonNamed(app.getScreens(), "lobbyStory").doClick();
                assert STORY_STAGE.equals(app.getCurrentScreen());
                button(app.getScreens(), "도전하기").doClick();
                assert BATTLE.equals(app.getCurrentScreen());
                assert !menuItem(app.getMenu(), "캐릭터 / 상점").isEnabled();
                menuItem(app.getMenu(), "캐릭터 / 상점").doClick();
                assert BATTLE.equals(app.getCurrentScreen());
                assert app.getMatchSnapshot().getPhase() == SessionPhase.RUNNING;
                long battleBefore = app.getMatchSnapshot().getBattleState().getVersion();
                app.submit(GameAction.Type.HARD_DROP);
                assert app.getMatchSnapshot().getBattleState().getVersion() > battleBefore;
                assert app.isStoryClockRunning();
                app.withLocalGamePaused(() -> {
                    assert app.getMatchSnapshot().getPhase() == SessionPhase.PAUSED;
                    assert !app.isStoryClockRunning();
                    long pausedVersion = app.getMatchSnapshot().getBattleState().getVersion();
                    app.submit(GameAction.Type.HARD_DROP);
                    assert app.getMatchSnapshot().getBattleState().getVersion() == pausedVersion;
                });
                assert app.isStoryClockRunning();
                assert app.getMatchSnapshot().getPhase() == SessionPhase.RUNNING;
                assert BATTLE.equals(app.getCurrentScreen());
                assert !app.getCampaignProgress().isStageCleared(
                        kr.ac.jbnu.se.tetris.story.StageCatalog.loadDefault().getStages().get(0).getId());
                menuItem(app.getMenu(), "대전 포기").doClick();
                assert RESULT.equals(app.getCurrentScreen());
                assert !app.isStoryClockRunning();
                button(app.getScreens(), "Story로").doClick();
                assert STORY_STAGE.equals(app.getCurrentScreen());
                assert app.getMatchSnapshot() == null;
                button(app.getScreens(), "도전하기").doClick();
                escape(app);
                assert STORY_STAGE.equals(app.getCurrentScreen());
                assert app.getMatchSnapshot() == null && !app.isStoryClockRunning();
                assert app.getSaveData().getCoins() == 0;
            } finally {
                app.close();
            }
        });
        System.out.println("PASS SeongeunApplicationTest");
    }

    private static SeongeunApplication newTestApp(long seed) {
        try {
            Path directory = Files.createTempDirectory("tetris-ui-test-");
            directory.toFile().deleteOnExit();
            Path save = directory.resolve("save.properties");
            save.toFile().deleteOnExit();
            return new SeongeunApplication(new Random(seed), new PlayerSaveStore(save));
        } catch (java.io.IOException error) { throw new AssertionError(error); }
    }

    private static AbstractButton button(Component root, String text) {
        if (!root.isVisible()) throw new AssertionError("Hidden component");
        if (root instanceof AbstractButton && text.equals(((AbstractButton) root).getText())) {
            return (AbstractButton) root;
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                try { return button(child, text); }
                catch (AssertionError missing) { /* 다음 컴포넌트 검색 */ }
            }
        }
        throw new AssertionError("Button not found: " + text);
    }

    private static AbstractButton buttonNamed(Component root, String name) {
        if (!root.isVisible()) throw new AssertionError("Hidden component");
        if (root instanceof AbstractButton && name.equals(root.getName())) return (AbstractButton) root;
        if (root instanceof Container) for (Component child : ((Container) root).getComponents()) {
            try { return buttonNamed(child, name); }
            catch (AssertionError missing) { }
        }
        throw new AssertionError("Button not found: " + name);
    }

    private static AbstractButton menuItem(JMenuBar menuBar, String text) {
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            for (int j = 0; j < menu.getItemCount(); j++) {
                if (menu.getItem(j) != null && text.equals(menu.getItem(j).getText())) return menu.getItem(j);
            }
        }
        throw new AssertionError("Menu item not found: " + text);
    }

    private static void escape(SeongeunApplication app) {
        Object binding = app.getScreens().getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0));
        assert binding != null;
        javax.swing.Action action = app.getScreens().getActionMap().get(binding);
        assert action != null;
        action.actionPerformed(new java.awt.event.ActionEvent(app.getScreens(), 0, binding.toString()));
    }

    private static final String LOGIN = "LOGIN";
    private static final String LOBBY = "LOBBY";
    private static final String LOCAL_MODE = "LOCAL_MODE";
    private static final String LOCAL_GAME = "LOCAL_GAME";
    private static final String STORY_STAGE = "STORY_STAGE";
    private static final String BATTLE = "BATTLE";
    private static final String RESULT = "RESULT";
}
