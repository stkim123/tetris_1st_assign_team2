package kr.ac.jbnu.se.tetris.ui.seongeun.panels;

import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.CampusBackButton;

/** Shared navigation, result text button, and lobby wallet reuse. */
public final class CampusNavigationStyleTest {
    private CampusNavigationStyleTest() { }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertBack(new StoryStageSelectPanel());
            assertBack(new CharacterShopPanel());
            assertBack(new RoomListPanel());
            assertBack(new SettingsPanel());
            assertBack(new LeaderboardPanel());
            assertBack(new LocalModePanel());

            CharacterShopPanel shop = new CharacterShopPanel();
            MainLobbyPanel.CoinBadge wallet = child(shop, MainLobbyPanel.CoinBadge.class);
            assert wallet != null : "Shop must reuse the actual lobby coin badge";
            wallet.setValue(1234567);
            assert wallet.getPreferredSize().width >= 168;

            ResultPanel result = new ResultPanel();
            AbstractButton lobby = button(result, "로비로");
            assert lobby != null && lobby.getPreferredSize().height >= 50;
            result.setReturnButtonText("로비로");
            assert !lobby.isVisible() : "Duplicate lobby exits should be hidden";
            result.setReturnButtonText("Story로");
            assert lobby.isVisible() : "Story results still need a lobby exit";
        });
        System.out.println("PASS CampusNavigationStyleTest");
    }

    private static void assertBack(Container root) {
        CampusBackButton back = child(root, CampusBackButton.class);
        assert back != null && "돌아가기".equals(back.getText());
        assert back.getPreferredSize().width >= 190;
        AtomicInteger clicked = new AtomicInteger();
        back.addActionListener(event -> clicked.incrementAndGet());
        back.doClick();
        assert clicked.get() == 1 : "Back event lost";
    }

    private static AbstractButton button(Container root, String label) {
        for (Component c : root.getComponents()) {
            if (c instanceof AbstractButton && label.equals(((AbstractButton) c).getText()))
                return (AbstractButton) c;
            if (c instanceof Container) {
                AbstractButton found = button((Container) c, label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T child(Container root, Class<T> type) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) return type.cast(c);
            if (c instanceof Container) {
                T found = child((Container) c, type);
                if (found != null) return found;
            }
        }
        return null;
    }
}
