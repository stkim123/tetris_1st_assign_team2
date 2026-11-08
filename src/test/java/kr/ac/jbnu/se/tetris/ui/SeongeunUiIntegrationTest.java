package kr.ac.jbnu.se.tetris.ui;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import kr.ac.jbnu.se.tetris.story.StageCatalog;
import kr.ac.jbnu.se.tetris.story.StoryProgressService;
import kr.ac.jbnu.se.tetris.ui.seongeun.model.PlayerData;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.RoomListPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.StoryStageSelectPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.WaitingRoomPanel;

/** 현재 스토리·온라인 화면의 잠금과 서버 상태 표시 계약. */
public final class SeongeunUiIntegrationTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            storyRespectsProgress();
            onlineControlsFollowConfirmedState();
        });
        System.out.println("PASS SeongeunUiIntegrationTest");
    }

    private static void storyRespectsProgress() {
        StageCatalog catalog = StageCatalog.loadDefault();
        StoryStageSelectPanel story = new StoryStageSelectPanel();
        List<String> started = new ArrayList<String>();
        String first = catalog.getStages().get(0).getEncounters().get(0).getId();
        story.setStageAction(0, first, event -> started.add(event.getActionCommand()));
        story.updateProgress(new StoryProgressService(catalog).getCampaignProgress(), catalog);
        JButton start = buttonNamed(story, "storyStart");
        check(start.isEnabled(), "first encounter is available");
        start.doClick();
        check(started.size() == 1 && first.equals(started.get(0)),
                "start sends the selected stable encounter ID");
    }

    private static void onlineControlsFollowConfirmedState() {
        RoomListPanel rooms = new RoomListPanel();
        rooms.setConnected(false);
        JButton create = buttonText(rooms, "방 만들기");
        check(create != null && !create.isEnabled(),
                "room creation is disabled before connection");
        rooms.setConnectionFailed("연결 실패");
        check(buttonText(rooms, "다시 연결") != null && buttonText(rooms, "다시 연결").isVisible(),
                "failed connection offers retry");
        rooms.setConnected(true);
        check(create.isEnabled(),
                "confirmed connection enables room creation");

        WaitingRoomPanel waiting = new WaitingRoomPanel();
        List<String> actions = new ArrayList<String>();
        waiting.setReadyAction(event -> actions.add("ready"));
        waiting.setRoomId("123456");
        waiting.setPlayers(new PlayerData("나", 1, "student", false),
                new PlayerData("상대", 1, "student", true));
        JButton ready = buttonNamed(waiting, "waitingReady");
        check("준비 완료".equals(ready.getText()), "unready player sees ready action");
        ready.doClick();
        check(actions.size() == 1, "ready click sends one intent");
        waiting.setPlayers(new PlayerData("나", 1, "student", true),
                new PlayerData("상대", 1, "student", true));
        check("준비 취소".equals(ready.getText()), "confirmed ready state changes action");
    }

    private static JButton buttonNamed(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton && name.equals(child.getName())) return (JButton) child;
            if (child instanceof Container) {
                JButton nested = buttonNamed((Container) child, name);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static JButton buttonText(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton && text.equals(((JButton) child).getText())) return (JButton) child;
            if (child instanceof Container) {
                JButton nested = buttonText((Container) child, text);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
