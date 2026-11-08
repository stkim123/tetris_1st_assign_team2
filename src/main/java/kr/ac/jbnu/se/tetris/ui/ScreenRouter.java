package kr.ac.jbnu.se.tetris.ui;

import java.awt.CardLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** 화면 종류·게임 상태와 독립적인 화면 ID 및 수명주기 관리 */
public final class ScreenRouter {
    private final CardLayout layout = new CardLayout();
    private final JPanel container = new JPanel(layout);
    private final Map<String, Screen> screens = new LinkedHashMap<>();
    private Screen current;

    public void register(Screen screen) {
        requireEdt();
        Objects.requireNonNull(screen, "screen");
        String id = Objects.requireNonNull(screen.getId(), "screen id");
        if (id.trim().isEmpty() || screens.containsKey(id)) {
            throw new IllegalArgumentException("Invalid or duplicate screen: " + id);
        }
        JPanel panel = Objects.requireNonNull(screen.getPanel(), "screen panel");
        screens.put(id, screen);
        container.add(panel, id);
    }

    public void show(String id) {
        requireEdt();
        Screen next = screens.get(id);
        if (next == null) throw new IllegalArgumentException("Unknown screen: " + id);
        if (next == current) return;
        if (current != null) current.onExit();
        current = next;
        layout.show(container, id);
        next.onEnter();
        container.revalidate();
        container.repaint();
    }

    public JPanel getContainer() { return container; }
    public String getCurrentId() { return current == null ? null : current.getId(); }

    public void close() {
        requireEdt();
        if (current != null) current.onExit();
        current = null;
    }

    public static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Swing operations must run on the EDT");
        }
    }
}
