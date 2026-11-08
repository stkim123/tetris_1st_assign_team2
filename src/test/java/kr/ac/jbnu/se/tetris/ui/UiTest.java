package kr.ac.jbnu.se.tetris.ui;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import kr.ac.jbnu.se.tetris.ui.seongeun.Board;

/** 화면 라우터 수명주기와 현재 보드의 키 등록을 창 없이 검증한다. */
public final class UiTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            routerLifecycle();
            currentBoardKeys();
        });
        boolean rejected = false;
        try { new ScreenRouter().register(screen("off-edt", new ArrayList<String>())); }
        catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "off-EDT screen mutation rejected");
        System.out.println("PASS UiTest: " + checks + " checks");
    }

    private static Screen screen(String id, List<String> calls) {
        return new Screen() {
            private final JPanel panel = new JPanel();
            @Override public String getId() { return id; }
            @Override public JPanel getPanel() { return panel; }
            @Override public void onEnter() { calls.add(id + "+"); }
            @Override public void onExit() { calls.add(id + "-"); }
        };
    }

    private static void routerLifecycle() {
        List<String> calls = new ArrayList<String>();
        ScreenRouter router = new ScreenRouter();
        router.register(screen("a", calls));
        router.register(screen("b", calls));
        router.show("a"); router.show("a"); router.show("b"); router.close();
        check(calls.equals(Arrays.asList("a+", "a-", "b+", "b-")),
                "router transition order and idempotence");
        check(router.getCurrentId() == null, "router close clears active screen");
        boolean duplicate = false;
        try { router.register(screen("a", calls)); }
        catch (IllegalArgumentException expected) { duplicate = true; }
        check(duplicate, "duplicate screen rejected");
        boolean unknown = false;
        try { router.show("missing"); }
        catch (IllegalArgumentException expected) { unknown = true; }
        check(unknown, "unknown screen rejected");
    }

    private static void currentBoardKeys() {
        Board board = new Board(new JLabel(), true);
        int[] keys = {KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN,
                KeyEvent.VK_D, KeyEvent.VK_SPACE, KeyEvent.VK_C, KeyEvent.VK_P,
                KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4};
        for (int key : keys) {
            Object action = board.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .get(KeyStroke.getKeyStroke(key, 0));
            check(action != null && board.getActionMap().get(action) != null,
                    "current board key binding " + key);
        }
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }
}
