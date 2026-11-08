package kr.ac.jbnu.se.tetris.ui.seongeun;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameEngine;
import kr.ac.jbnu.se.tetris.core.SevenBagGenerator;

/** 실제 창의 포커스와 Java 키 전달 경로를 확인한다. OS 키 주입은 하지 않는다. */
public final class BoardKeyboardDesktopSmoke {
    private static JFrame frame;
    private static CardLayout cards;
    private static JPanel screens;
    private static Board battleBoard;
    private static Board localBoard;
    private static JButton battleButton;
    private static JButton localButton;
    private static JButton menuButton;
    private static final List<String> actions = new ArrayList<String>();

    private BoardKeyboardDesktopSmoke() { }

    public static void main(String[] args) throws Exception {
        if (java.awt.GraphicsEnvironment.isHeadless())
            throw new IllegalStateException("Desktop display is required");
        try {
            SwingUtilities.invokeAndWait(BoardKeyboardDesktopSmoke::open);
            focus(battleButton);
            press(KeyEvent.VK_LEFT);
            press(KeyEvent.VK_SPACE);
            press(KeyEvent.VK_P);
            press(KeyEvent.VK_1);
            check(actions.toString().equals("[battle:MOVE_LEFT, battle:HARD_DROP, battle:PAUSE, battle:item0]"),
                    "Battle board handles arrows, Space, P and item key with button focus: " + actions);
            SwingUtilities.invokeAndWait(() -> cards.show(screens, "local"));
            focus(localButton);
            press(KeyEvent.VK_RIGHT);
            press(KeyEvent.VK_C);
            check(actions.toString().endsWith("local:MOVE_RIGHT, local:HOLD]"),
                    "Visible local board handles controls; hidden battle board does not: " + actions);
            int actionCount = actions.size();
            SwingUtilities.invokeAndWait(() -> cards.show(screens, "menu"));
            focus(menuButton);
            press(KeyEvent.VK_LEFT);
            press(KeyEvent.VK_SPACE);
            check(actions.size() == actionCount, "Hidden boards leave menu keyboard input alone");
            System.out.println("PASS BoardKeyboardDesktopSmoke: focused button, scroll pane and card routing");
        } finally {
            SwingUtilities.invokeAndWait(() -> { if (frame != null) frame.dispose(); });
        }
    }

    private static void open() {
        GameEngine engine = new GameEngine(new SevenBagGenerator(7));
        engine.dispatch(new GameAction(GameAction.Type.START, "local", 0));
        battleBoard = new Board(new JLabel(), true);
        localBoard = new Board(new JLabel(), true);
        battleBoard.setState(engine.getState());
        localBoard.setState(engine.getState());
        battleBoard.setInputHandlers(type -> actions.add("battle:" + type), () -> actions.add("battle:PAUSE"));
        localBoard.setInputHandlers(type -> actions.add("local:" + type), () -> actions.add("local:PAUSE"));
        battleBoard.setItemHandler(index -> actions.add("battle:item" + index));
        localBoard.setItemHandler(index -> actions.add("local:item" + index));

        JPanel battle = new JPanel(new BorderLayout());
        battle.add(battleBoard, BorderLayout.CENTER);
        battleButton = new JButton("item button");
        battle.add(battleButton, BorderLayout.SOUTH);
        JPanel local = new JPanel(new BorderLayout());
        local.add(localBoard, BorderLayout.CENTER);
        localButton = new JButton("back button");
        local.add(localButton, BorderLayout.SOUTH);
        cards = new CardLayout();
        screens = new JPanel(cards);
        screens.add(new JScrollPane(battle), "battle");
        screens.add(local, "local");
        menuButton = new JButton("menu button");
        JPanel menu = new JPanel();
        menu.add(menuButton);
        screens.add(menu, "menu");

        frame = new JFrame("Board keyboard smoke");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setContentPane(screens);
        frame.setSize(500, 660);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.toFront();
        cards.show(screens, "battle");
    }

    private static void focus(Component target) throws Exception {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            AtomicReference<Component> owner = new AtomicReference<Component>();
            SwingUtilities.invokeAndWait(() -> {
                target.requestFocusInWindow();
                owner.set(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
            });
            if (owner.get() == target) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Could not focus the test button");
    }

    private static void press(int keyCode) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            Component owner = manager.getFocusOwner();
            if (owner == null) throw new AssertionError("No focused component");
            KeyEvent event = new KeyEvent(owner, KeyEvent.KEY_PRESSED,
                    System.currentTimeMillis(), 0, keyCode, KeyEvent.CHAR_UNDEFINED);
            if (!manager.dispatchEvent(event)) throw new AssertionError("Key not dispatched: " + keyCode);
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
