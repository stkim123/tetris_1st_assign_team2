package kr.ac.jbnu.se.tetris.ui.seongeun;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import kr.ac.jbnu.se.tetris.story.*;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.*;

/** 스토리 전 스테이지와 상점 화면 크기 확인용 PNG. */
public final class MenuRenderSmoke {
    private MenuRenderSmoke() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Output directory required");
        Path folder = Paths.get(args[0]);
        SwingUtilities.invokeAndWait(() -> {
            try {
                java.nio.file.Files.createDirectories(folder);
                MainLobbyPanel lobby = new MainLobbyPanel();
                lobby.setStoryProgress(0, 9);
                write(lobby, folder.resolve("lobby.png"));
                write(lobby, folder.resolve("lobby-small.png"), 820, 650);
                StageCatalog catalog = StageCatalog.loadDefault();
                StoryStageSelectPanel story = new StoryStageSelectPanel();
                story.updateProgress(new StoryProgressService(catalog).getCampaignProgress(), catalog);
                write(story, folder.resolve("story.png"));
                write(story, folder.resolve("story-small.png"), 820, 650);
                SettingsPanel settings = new SettingsPanel();
                settings.update(false, .32f, false, .65f, false);
                write(settings, folder.resolve("settings.png"));
                write(settings, folder.resolve("settings-small.png"), 820, 650);
                write(new LocalModePanel(), folder.resolve("local-mode-small.png"), 820, 650);
                LoginPanel login = new LoginPanel();
                login.setOnlineOnly(true);
                login.setBackLabel("취소");
                login.setStatus("");
                write(login, folder.resolve("online-login-small.png"), 820, 650);
                RoomListPanel roomList = new RoomListPanel();
                roomList.setConnectionFailed("서버 연결을 확인하세요");
                write(roomList, folder.resolve("online-retry-small.png"), 820, 650);
                write(new WaitingRoomPanel(), folder.resolve("waiting-room-small.png"), 820, 650);
                ResultPanel result = new ResultPanel();
                result.setResult("VICTORY", "플레이어", 12, 3, 64, 30);
                write(result, folder.resolve("result-small.png"), 820, 650);
                write(new LeaderboardPanel(), folder.resolve("leaderboard-small.png"), 820, 650);
                CharacterShopPanel shop = new CharacterShopPanel();
                Map<String,Integer> prices = new LinkedHashMap<String,Integer>();
                prices.put("student", 0); prices.put("attacker", 100);
                prices.put("defender", 100); prices.put("utility", 120);
                shop.setData(80, Collections.singleton("student"), "student", prices);
                write(shop, folder.resolve("shop.png"));
            } catch (Exception failure) { throw new RuntimeException(failure); }
        });
        System.out.println("PASS MenuRenderSmoke");
    }
    private static void write(JComponent component, Path path) throws Exception {
        write(component, path, 1020, 720);
    }
    private static void write(JComponent component, Path path, int width, int height) throws Exception {
        kr.ac.jbnu.se.tetris.ui.seongeun.components.UniversityPixelTheme.apply(component);
        component.setSize(width, height);
        layout(component);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        component.printAll(graphics);
        graphics.dispose();
        ImageIO.write(image, "png", path.toFile());
    }
    private static void layout(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) if (child instanceof Container) layout((Container) child);
    }
}
