package kr.ac.jbnu.se.tetris.ui.seongeun;

import java.awt.*;
import java.awt.event.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.*;
import kr.ac.jbnu.se.tetris.core.*;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.ConcreteBlockSkin;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.UniversityPixelTheme;

/** 엔진 스냅샷을 픽셀 격자에 그리는 화면 전용 보드. */
public class Board extends JPanel {
    private static final int COLUMNS = 10, ROWS = 22;
    private static final Color EMPTY = new Color(0x293039);
    private static final Color GRID = new Color(0x414B55);
    private static final Color PLAYER_FRAME = new Color(0xB3BCC4);
    private static final Color ENEMY_FRAME = new Color(0xB3BCC4);
    private final JLabel statusbar;
    private GameState state;
    private Consumer<GameAction.Type> inputHandler;
    private Runnable pauseHandler;
    private Consumer<Integer> itemHandler;
    private String overlayText;
    private final boolean keyboardEnabled;
    private long placementAt, clearAt, garbageAt, attackAt, hitAt;
    private int effectX, effectY, clearCount, clearCombo;
    private boolean perfectClear;
    private Piece effectPiece;
    private boolean automaticEffects = true;
    // PvE 전투에서는 블록을 가리는 중앙 라인 삭제 알림을 사용하지 않는다.
    private boolean lineClearCalloutEnabled = true;
    private final Timer effects = new Timer(33, event -> {
        long latest = Math.max(Math.max(placementAt, clearAt), Math.max(garbageAt, Math.max(attackAt, hitAt)));
        if (!isShowing() || System.currentTimeMillis() - latest > 1000)
            ((Timer) event.getSource()).stop();
        repaint();
    });
    private final Map<Integer, Runnable> keyActions = new LinkedHashMap<Integer, Runnable>();
    private final KeyEventDispatcher keyDispatcher = event -> {
        if (event.getID() != KeyEvent.KEY_PRESSED || event.getModifiersEx() != 0
                || !isShowing() || state == null ||
                (state.getStatus() != GameState.Status.RUNNING
                        && state.getStatus() != GameState.Status.PAUSED)) return false;
        Runnable action = keyActions.get(event.getKeyCode());
        if (action == null || MenuSelectionManager.defaultManager().getSelectedPath().length != 0)
            return false;
        Window gameWindow = SwingUtilities.getWindowAncestor(this);
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (gameWindow == null || focusOwner == null
                || SwingUtilities.getWindowAncestor(focusOwner) != gameWindow) return false;
        action.run();
        return true;
    };

    public Board(JLabel statusbar, boolean keyboardEnable) {
        this.statusbar = statusbar;
        keyboardEnabled = keyboardEnable;
        setBackground(EMPTY);
        setPreferredSize(new Dimension(260, 572));
        setMinimumSize(new Dimension(120, 264));
        setFocusable(true);
        if (keyboardEnable) bindKeys();
    }
    @Override public void addNotify() {
        super.addNotify();
        if (keyboardEnabled)
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher);
    }
    @Override public void removeNotify() {
        effects.stop();
        if (keyboardEnabled)
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher);
        super.removeNotify();
    }
    public void start() { requestFocusInWindow(); }
    public void setState(GameState next) {
        if (automaticEffects && next != null && state != null && next.getLinesCleared() > state.getLinesCleared())
            showLineClear(next.getLinesCleared() - state.getLinesCleared(), next.getCombo(), false);
        state = next;
        if (statusbar != null && next != null) {
            if (next.getStatus() == GameState.Status.PAUSED) statusbar.setText("일시정지");
            else if (next.getStatus() == GameState.Status.GAME_OVER) statusbar.setText("게임 종료");
            else statusbar.setText("LINES  " + next.getLinesCleared() + "    COMBO  " + Math.max(0, next.getCombo()));
        }
        repaint();
    }
    public void setAutomaticEffects(boolean enabled) { automaticEffects = enabled; }
    /** 라인 삭제 이펙트는 유지하고 중앙 텍스트 배너만 켜거나 끈다. */
    public void setLineClearCalloutEnabled(boolean enabled) { lineClearCalloutEnabled = enabled; repaint(); }
    public GameState getGameState() { return state; }
    /** 채굴 이펙트의 출발점. 코어 보드는 아래에서 위로 y가 증가한다. */
    public Point cellCenter(int x, int y) {
        if (x < 0 || x >= COLUMNS || y < 0 || y >= ROWS)
            throw new IllegalArgumentException("Cell outside board");
        int cell = Math.max(1, Math.min(getWidth() / COLUMNS, getHeight() / ROWS));
        int left = (getWidth() - cell * COLUMNS) / 2;
        int top = (getHeight() - cell * ROWS) / 2;
        return new Point(left + x * cell + cell / 2,
                top + (ROWS - 1 - y) * cell + cell / 2);
    }
    public void resetEffects() {
        effects.stop(); placementAt = clearAt = garbageAt = attackAt = hitAt = 0;
        effectPiece = null; repaint();
    }
    public void showPlacement(Piece piece, int x, int y) {
        effectPiece = piece; effectX = x; effectY = y; placementAt = System.currentTimeMillis();
        animate();
    }
    public void showLineClear(int count, int combo, boolean perfect) {
        clearCount = Math.max(1, count); clearCombo = combo; perfectClear = perfect;
        clearAt = System.currentTimeMillis(); animate();
    }
    public void showGarbage(int count) { garbageAt = System.currentTimeMillis(); animate(); }
    /** 공격이 실제 피해로 이어질 때 보드의 무대 쪽 가장자리가 발광한다. */
    public void showAttack() { attackAt = System.currentTimeMillis(); animate(); }
    /** 피격을 테트리스 보드에서도 즉시 알아차릴 수 있도록 짧게 반응한다. */
    public void showHit() { hitAt = System.currentTimeMillis(); animate(); }
    private void animate() { if (isShowing()) effects.start(); repaint(); }
    public void setInputHandlers(Consumer<GameAction.Type> input, Runnable pause) {
        inputHandler = input;
        pauseHandler = pause;
    }
    public void setItemHandler(Consumer<Integer> handler) { itemHandler = handler; }
    public void setOverlayText(String text) { overlayText = text; repaint(); }
    private void bindKeys() {
        bind(KeyEvent.VK_LEFT, "left", () -> submit(GameAction.Type.MOVE_LEFT));
        bind(KeyEvent.VK_RIGHT, "right", () -> submit(GameAction.Type.MOVE_RIGHT));
        bind(KeyEvent.VK_UP, "rotate-left", () -> submit(GameAction.Type.ROTATE_LEFT));
        bind(KeyEvent.VK_DOWN, "rotate-right", () -> submit(GameAction.Type.ROTATE_RIGHT));
        bind(KeyEvent.VK_SPACE, "drop", () -> submit(GameAction.Type.HARD_DROP));
        bind(KeyEvent.VK_D, "soft-drop", () -> submit(GameAction.Type.SOFT_DROP));
        bind(KeyEvent.VK_C, "hold", () -> submit(GameAction.Type.HOLD));
        bind(KeyEvent.VK_P, "pause", () -> {
            if (state != null && (state.getStatus() == GameState.Status.RUNNING
                    || state.getStatus() == GameState.Status.PAUSED) && pauseHandler != null) pauseHandler.run();
        });
        for (int index = 0; index < 4; index++) {
            final int slot = index;
            bind(KeyEvent.VK_1 + index, "item-" + index, () -> {
                if (state != null && state.getStatus() == GameState.Status.RUNNING && itemHandler != null)
                    itemHandler.accept(slot);
            });
        }
    }
    private void bind(int key, String name, Runnable action) {
        keyActions.put(key, action);
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { if (isShowing()) action.run(); }
        });
    }
    private void submit(GameAction.Type action) {
        if (state != null && state.getStatus() == GameState.Status.RUNNING
                && state.getActivePiece() != null && inputHandler != null) inputHandler.accept(action);
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            // 가비지를 받으면 보드가 짧게 흔들려 맞았다는 것을 몸으로 느끼게 한다.
            long now = System.currentTimeMillis();
            long garbageAge = now - garbageAt, hitAge = now - hitAt;
            if (garbageAt > 0 && garbageAge >= 0 && garbageAge < 280
                    || hitAt > 0 && hitAge >= 0 && hitAge < 240) {
                long age = garbageAt > hitAt ? garbageAge : hitAge;
                int duration = garbageAt > hitAt ? 280 : 240;
                int amp = Math.round(6 * (1 - age / (float) duration));
                g.translate((int) Math.round(Math.sin(age / 12.0) * amp),
                        (int) Math.round(Math.cos(age / 9.0) * amp / 2.0));
            }
            int cell = Math.max(1, Math.min(getWidth() / COLUMNS, getHeight() / ROWS));
            int width = cell * COLUMNS, height = cell * ROWS;
            int left = (getWidth() - width) / 2, top = (getHeight() - height) / 2;
            g.setColor(EMPTY);
            g.fillRect(left, top, width, height);
            g.setColor(GRID);
            for (int x = 0; x <= COLUMNS; x++) g.drawLine(left + x * cell, top, left + x * cell, top + height);
            for (int y = 0; y <= ROWS; y++) g.drawLine(left, top + y * cell, left + width, top + y * cell);
            if (state != null && state.getBoard() != null) {
                BoardState board = state.getBoard();
                for (int y = 0; y < ROWS; y++) for (int x = 0; x < COLUMNS; x++) {
                    PieceType type = board.getCell(x, y);
                    if (type != PieceType.EMPTY)
                        drawCell(g, left, top, cell, x, y, type, false, board.getItemId(x, y) != null);
                }
                Piece piece = state.getActivePiece();
                if (piece != null) {
                    for (int i = 0; i < 4; i++) {
                        int x = state.getPieceX() + piece.x(i), y = state.getGhostY() - piece.y(i);
                        if (x >= 0 && x < COLUMNS && y >= 0 && y < ROWS && board.getCell(x, y) == PieceType.EMPTY)
                            drawCell(g, left, top, cell, x, y, piece.getType(), true, piece.hasOreAt(i));
                    }
                    for (int i = 0; i < 4; i++) {
                        int x = state.getPieceX() + piece.x(i), y = state.getPieceY() - piece.y(i);
                        if (x >= 0 && x < COLUMNS && y >= 0 && y < ROWS)
                            drawCell(g, left, top, cell, x, y, piece.getType(), false, piece.hasOreAt(i));
                    }
                }
            }
            g.setColor(keyboardEnabled ? PLAYER_FRAME : ENEMY_FRAME);
            g.setStroke(new BasicStroke(keyboardEnabled ? 3f : 2f));
            g.drawRect(left, top, width, height);
            paintEffects(g, left, top, cell, width, height);
            if (overlayText != null || state != null && (state.getStatus() == GameState.Status.PAUSED
                    || state.getStatus() == GameState.Status.GAME_OVER)) {
                g.setColor(new Color(8, 14, 27, 198));
                g.fillRect(left, top, width, height);
                g.setColor(Color.WHITE);
                g.setFont(kr.ac.jbnu.se.tetris.ui.seongeun.components.UniversityPixelTheme.font(Math.max(14, cell), Font.BOLD));
                String label = overlayText != null ? overlayText
                        : state.getStatus() == GameState.Status.PAUSED ? "PAUSED" : "GAME OVER";
                g.drawString(label, left + (width - g.getFontMetrics().stringWidth(label)) / 2, top + height / 2);
            }
        } finally { g.dispose(); }
    }
    private void paintEffects(Graphics2D g, int left, int top, int cell, int width, int height) {
        long now = System.currentTimeMillis();
        long landing = now - placementAt;
        if (effectPiece != null && landing >= 0 && landing < 240) {
            g.setColor(new Color(255, 247, 232, (int) (150 * (1 - landing / 240.0))));
            for (int i = 0; i < 4; i++) {
                int px = left + (effectX + effectPiece.x(i)) * cell;
                int py = top + (ROWS - 1 - effectY + effectPiece.y(i)) * cell;
                g.fillRect(px + 1, top, cell - 2, Math.max(0, py - top));
                g.drawRect(px - 2, py - 2, cell + 3, cell + 3);
            }
        }
        long clear = now - clearAt;
        if (clearAt > 0 && clear >= 0 && clear < 800) {
            double progress = clear / 800.0;
            int beamY = top + height - (int) (height * progress);
            g.setColor(new Color(255, 209, 102, (int) (150 * (1 - progress))));
            g.fillRect(left, beamY, width, Math.max(2, cell / 3));
            for (int i = 0; i < 28 + clearCount * 8; i++) {
                int x = left + Math.floorMod(i * 47, Math.max(1, width));
                int y = top + height - (int) (progress * (80 + i % 7 * 28)) - i % 4 * cell;
                g.setColor(i % 2 == 0 ? new Color(78, 227, 154, (int) (220 * (1 - progress)))
                        : new Color(255, 209, 102, (int) (220 * (1 - progress))));
                g.fillRect(x, y, Math.max(2, cell / 6), Math.max(2, cell / 6));
            }
            // 스토리 모드는 보드 중앙을 항상 노출하고 라인 삭제 빔/입자만 표시한다.
            if (lineClearCalloutEnabled) {
                g.setColor(new Color(11, 11, 46, 210));
                g.fillRect(left + 4, top + height / 2 - 22, width - 8, 48);
                g.setColor(perfectClear ? new Color(255, 209, 102) : new Color(78, 227, 154));
                g.setFont(kr.ac.jbnu.se.tetris.ui.seongeun.components.UniversityPixelTheme.font(Math.max(11, Math.min(22, width / 12)), Font.BOLD));
                String text = perfectClear ? "PERFECT CLEAR" : clearCount == 4 ? "TETRIS!" : clearCount + " LINE CLEAR";
                g.drawString(text, left + (width - g.getFontMetrics().stringWidth(text)) / 2, top + height / 2);
                if (clearCombo > 0) {
                    g.setFont(kr.ac.jbnu.se.tetris.ui.seongeun.components.UniversityPixelTheme.font(Math.max(10, Math.min(16, width / 15)), Font.BOLD));
                    String combo = "COMBO " + clearCombo;
                    g.drawString(combo, left + (width - g.getFontMetrics().stringWidth(combo)) / 2, top + height / 2 + 20);
                }
            }
        }
        long garbage = now - garbageAt;
        if (garbageAt > 0 && garbage >= 0 && garbage < 500) {
            g.setColor(new Color(230, 142, 125, (int) (230 * (1 - garbage / 500.0))));
            g.setStroke(new BasicStroke(4)); g.drawRect(left, top, width, height);
        }
        long attack = now - attackAt;
        if (attackAt > 0 && attack >= 0 && attack < 520) {
            float fade = 1f - attack / 520f;
            int edge = keyboardEnabled ? left : left + width;
            int direction = keyboardEnabled ? -1 : 1;
            g.setColor(new Color(255, 207, 108, Math.round(215 * fade)));
            g.fillRect(edge - (keyboardEnabled ? 0 : 4), top, 4, height);
            g.setColor(new Color(255, 246, 196, Math.round(135 * fade)));
            for (int i = 0; i < 5; i++) {
                int y = top + Math.floorMod(i * 79 + 23, Math.max(1, height));
                int distance = Math.round(24 * attack / 520f);
                g.fillRect(edge + direction * distance - 2, y, 5, 2);
            }
        }
        long hit = now - hitAt;
        if (hitAt > 0 && hit >= 0 && hit < 420) {
            float fade = 1f - hit / 420f;
            g.setColor(new Color(229, 110, 91, Math.round(85 * fade)));
            g.fillRect(left, top, width, height);
            g.setColor(new Color(255, 146, 128, Math.round(240 * fade)));
            g.setStroke(new BasicStroke(3f));
            g.drawRect(left + 1, top + 1, width - 2, height - 2);
        }
    }
    private static void drawCell(Graphics2D g, int left, int top, int size, int x, int y,
                                 PieceType type, boolean ghost, boolean ore) {
        int px = left + x * size, py = top + (ROWS - 1 - y) * size;
        ConcreteBlockSkin.paint(g, px, py, size, type, ghost, ore);
    }
}
