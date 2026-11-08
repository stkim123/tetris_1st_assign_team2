package kr.ac.jbnu.se.tetris.ui;

import java.awt.BorderLayout;
import java.util.Arrays;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import kr.ac.jbnu.se.tetris.battle.BattleManager;
import kr.ac.jbnu.se.tetris.battle.ParticipantSpec;
import kr.ac.jbnu.se.tetris.story.MonsterTier;
import kr.ac.jbnu.se.tetris.story.StageCatalog;
import kr.ac.jbnu.se.tetris.story.StoryProgressService;
import kr.ac.jbnu.se.tetris.ui.seongeun.model.PlayerData;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.BattlePanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.CharacterShopPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.LeaderboardPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.LocalGamePanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.LocalModePanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.LoginPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.MainLobbyPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.ResultPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.RoomListPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.SettingsPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.StoryStageSelectPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.WaitingRoomPanel;

/** 현재 게임 화면을 네트워크 없이 확인하는 개발용 미리보기. */
public final class UiPreviewMain {
    private UiPreviewMain() { }

    public static JPanel createPanel() {
        ScreenRouter.requireEdt();
        JTabbedPane tabs = new JTabbedPane();
        MainLobbyPanel lobby = new MainLobbyPanel();
        lobby.setStoryProgress(0, 9);
        tabs.addTab("로비", lobby);

        StageCatalog catalog = StageCatalog.loadDefault();
        StoryStageSelectPanel story = new StoryStageSelectPanel();
        story.updateProgress(new StoryProgressService(catalog).getCampaignProgress(), catalog);
        tabs.addTab("스토리", story);
        tabs.addTab("로컬 모드", new LocalModePanel());
        LocalGamePanel local = new LocalGamePanel();
        local.startMode("Infinite");
        tabs.addTab("테트리스", local);

        BattleManager pve = BattleManager.pve(Arrays.asList(
                new ParticipantSpec("local", "플레이어", 100),
                new ParticipantSpec("monster", "술", 30)), 17L, 450, 3500, 0);
        pve.start();
        BattlePanel pvePanel = new BattlePanel();
        pvePanel.setEncounter("university", 1, MonsterTier.NORMAL, "university:0");
        pvePanel.setState(pve.getState(), "local");
        tabs.addTab("몬스터 전투", pvePanel);

        BattleManager pvp = BattleManager.pvp(Arrays.asList(
                new ParticipantSpec("local", "플레이어", 100),
                new ParticipantSpec("opponent", "상대", 100)), 18L);
        pvp.start();
        BattlePanel pvpPanel = new BattlePanel();
        pvpPanel.setMode(true);
        pvpPanel.setState(pvp.getState(), "local");
        tabs.addTab("온라인 대전", pvpPanel);

        RoomListPanel rooms = new RoomListPanel();
        rooms.setConnectionFailed("서버 연결을 확인하세요");
        tabs.addTab("방 목록", rooms);
        WaitingRoomPanel waiting = new WaitingRoomPanel();
        waiting.setRoomId("123456");
        waiting.setPlayers(new PlayerData("플레이어", 1, "student", false),
                new PlayerData("상대", 1, "student", true));
        tabs.addTab("대기실", waiting);
        ResultPanel result = new ResultPanel();
        result.setResult("VICTORY", "플레이어", 12, 3, 64, 30);
        tabs.addTab("결과", result);
        tabs.addTab("계정", new LoginPanel());
        tabs.addTab("랭킹", new LeaderboardPanel());
        tabs.addTab("상점", new CharacterShopPanel());
        SettingsPanel settings = new SettingsPanel();
        settings.update(false, .32f, false, .65f, false);
        tabs.addTab("설정", settings);

        JPanel root = new JPanel(new BorderLayout());
        root.add(tabs, BorderLayout.CENTER);
        return root;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("현재 게임 화면 미리보기");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setContentPane(createPanel());
            frame.setSize(1020, 760);
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        });
    }
}
