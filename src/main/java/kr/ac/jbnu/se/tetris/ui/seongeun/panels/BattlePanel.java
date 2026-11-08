package kr.ac.jbnu.se.tetris.ui.seongeun.panels;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import kr.ac.jbnu.se.tetris.audio.AudioService;
import kr.ac.jbnu.se.tetris.battle.BattleEvent;
import kr.ac.jbnu.se.tetris.battle.BattleState;
import kr.ac.jbnu.se.tetris.battle.ParticipantState;
import kr.ac.jbnu.se.tetris.core.BoardState;
import kr.ac.jbnu.se.tetris.core.GameEvent;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.PieceType;
import kr.ac.jbnu.se.tetris.item.ItemSpec;
import kr.ac.jbnu.se.tetris.story.MonsterTier;
import kr.ac.jbnu.se.tetris.ui.seongeun.Board;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.CartoonBattleTopHud;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.BattleDialogue;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.BattleLog;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.GarbageMeter;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.MiniPiecePreview;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.OreFlightLayer;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.PixelArena;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.PixelButton;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.PixelMeter;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.UniversityPixelTheme;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.CampusArt;
import kr.ac.jbnu.se.tetris.ui.seongeun.model.PlayerData;

/**
 * PvE에서는 왼쪽 전투 무대, 가운데 플레이 보드, 오른쪽 조작 정보를 강의동 외벽으로 묶는다.
 * 온라인에서는 서로 같은 크기의 보드 두 개와 가운데 전투 무대를 유지한다.
 */
public class BattlePanel extends JPanel implements Scrollable {
    private static final Color BG = UniversityPixelTheme.BG, TEXT = UniversityPixelTheme.TEXT;
    private static final Color FACADE = new Color(0xB9B7AB);
    private static final Color FACADE_EDGE = new Color(0x888D87);
    private static final Color FACADE_LIGHT = new Color(0xE5E2D7);
    private static final int GAP = 8, METER = 12;
    private static final int PVE_LEFT_PILLAR = 24, PVE_RIGHT_PILLAR = 12;
    private static final BufferedImage UNIVERSITY_BATTLE_BG = readBackdrop(
            "/ui/campus-rpg/university-battle-bg.png");
    private static final BufferedImage UNIVERSITY_BG = readBackdrop("/ui/campus-rpg/university-bg.png");
    private static final BufferedImage GRADUATION_BG = readBackdrop("/ui/campus-rpg/graduation-bg.png");
    private static final BufferedImage EMPLOYMENT_BG = readBackdrop("/ui/campus-rpg/employment-bg.png");

    // UI 리소스는 최초 한 번만 로드하고 전투 갱신 시 재사용한다.
    private static final Map<String, ImageIcon> ITEM_ICONS = loadItemIcons();

    private static Map<String, ImageIcon> loadItemIcons() {
        Map<String, ImageIcon> icons = new HashMap<>();
        icons.put("damage_boost", loadItemIcon("item_damage.png"));
        icons.put("garbage_bomb", loadItemIcon("item_bomb.png"));
        icons.put("heal", loadItemIcon("item_heal.png"));
        icons.put("shield", loadItemIcon("item_shield.png"));
        icons.put("line_cleaner", loadItemIcon("item_line_cleaner.png"));
        icons.put("fever_charge", loadItemIcon("item_fever.png"));
        icons.put("time_warp", loadItemIcon("item_time.png"));
        icons.put("nullify", loadItemIcon("item_nullify.png"));
        return icons;
    }

    private static ImageIcon loadItemIcon(String filename) {
        String path = "/ui/campus-rpg/icons/" + filename;
        try (InputStream stream = BattlePanel.class.getResourceAsStream(path)) {
            if (stream == null) {
                System.err.println("아이템 아이콘 파일을 찾지 못했습니다: " + path);
                return null;
            }
            BufferedImage image = ImageIO.read(stream);
            if (image == null) return null;
            return new ImageIcon(image.getScaledInstance(48, 48, Image.SCALE_SMOOTH));
        } catch (IOException e) {
            System.err.println("아이템 아이콘 로딩 실패: " + path + " / " + e.getMessage());
            return null;
        }
    }

    // PvE 아이템 버튼: 중앙 아이콘, 우상단 실제 남은 횟수, 하단 1~4번 단축키
    private static JButton createPveItemButton(String text) {
        JButton button = new JButton() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    int w = getWidth(), h = getHeight();
                    if (w < 15 || h < 15) return;
                    boolean active = isEnabled();
                    boolean pressed = getModel().isPressed();
                    boolean hover = getModel().isRollover();

                    // 두꺼운 둥근 어두운 프레임 + 안쪽 패널
                    g.setColor(new Color(0x101714));
                    g.fillRoundRect(0, 0, w, h - 2, 18, 18);
                    g.setColor(new Color(0x929E8B));
                    g.fillRoundRect(3, 3, w - 6, h - 8, 16, 16);
                    g.setColor(pressed ? new Color(0x253529)
                            : hover ? new Color(0x425B4C) : new Color(0x34443C));
                    g.fillRoundRect(6, 6, w - 12, h - 14, 13, 13);
                    g.setColor(new Color(0x233128));
                    g.setStroke(new BasicStroke(2f));
                    g.drawRoundRect(6, 6, w - 13, h - 15, 13, 13);
                    if (!active) {
                        g.setColor(new Color(0, 0, 0, 48));
                        g.fillRoundRect(6, 6, w - 12, h - 14, 13, 13);
                    }

                    // 아이콘은 실제 아이템 ID로 갱신된 기존 ImageIcon을 그대로 사용
                    Icon icon = getIcon();
                    if (icon != null) {
                        int iconX = (w - icon.getIconWidth()) / 2;
                        int iconY = Math.max(4, (h - icon.getIconHeight()) / 2 - 5);
                        icon.paintIcon(this, g, iconX, iconY);
                    }

                    // 우상단: 실제 아이템 남은 사용 횟수. 0개/빈 슬롯에는 숨김
                    Object countValue = getClientProperty("hudCharges");
                    int count = countValue instanceof Number ? ((Number) countValue).intValue() : 0;
                    if (icon != null && count > 0) {
                        int bw = 27, bh = 26, bx = w - bw - 2, by = 2;
                        g.setColor(new Color(0x1B100B));
                        g.fillRoundRect(bx - 2, by - 2, bw + 4, bh + 4, 12, 12);
                        g.setColor(new Color(0xC69362));
                        g.fillRoundRect(bx, by, bw, bh, 10, 10);
                        g.setColor(new Color(0x765036));
                        g.fillRoundRect(bx + 3, by + 4, bw - 6, bh - 7, 8, 8);
                        drawBadgeNumber(g, String.valueOf(count), bx, by, bw, bh, 17);
                    }

                    // 하단 중앙: 1~4번 단축키 배지(보유 횟수와 무관)
                    Object hotkeyValue = getClientProperty("hudHotkey");
                    if (hotkeyValue instanceof Number) {
                        String hotkey = String.valueOf(((Number) hotkeyValue).intValue());
                        int bw = 26, bh = 24, bx = (w - bw) / 2, by = h - bh - 1;
                        g.setColor(new Color(0x102923));
                        g.fillRoundRect(bx - 2, by - 2, bw + 4, bh + 4, 10, 10);
                        g.setColor(new Color(0x78DCC0));
                        g.fillRoundRect(bx, by, bw, bh, 8, 8);
                        g.setColor(new Color(0x43B99A));
                        g.fillRoundRect(bx + 3, by + 3, bw - 6, bh - 6, 6, 6);
                        drawBadgeNumber(g, hotkey, bx, by, bw, bh, 16);
                    }
                } finally {
                    g.dispose();
                }
            }
        };
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setFocusPainted(false);
        button.setFocusable(false);
        return button;
    }

    private static void drawBadgeNumber(Graphics2D g, String text,
                                        int x, int y, int w, int h, int size) {
        g.setFont(UniversityPixelTheme.font(size, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        int tx = x + (w - fm.stringWidth(text)) / 2;
        int ty = y + (h - fm.getHeight()) / 2 + fm.getAscent();
        g.setColor(new Color(0x18231E));
        g.drawString(text, tx + 1, ty + 2);
        g.setColor(new Color(0xFFF8E9));
        g.drawString(text, tx, ty);
    }
    // Compact, readable rounded message banner below the FEVER HUD.
    private static JLabel createBattleStatusBanner() {
        JLabel banner = new JLabel("") {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    int w = getWidth(), h = getHeight();
                    if (w < 10 || h < 10) return;
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    // Translucent light gray: let the campus art show through without washing out text.
                    g.setColor(new Color(34, 38, 41, 65));
                    g.fillRoundRect(1, 3, w - 2, h - 3, 17, 17);
                    g.setColor(new Color(238, 241, 244, 235));
                    g.fillRoundRect(0, 0, w, h - 3, 17, 17);
                    g.setColor(new Color(112, 119, 125, 200));
                    g.setStroke(new BasicStroke(1.5f));
                    g.drawRoundRect(1, 1, w - 3, h - 6, 15, 15);
                    g.setColor(new Color(255, 255, 255, 110));
                    g.drawRoundRect(3, 3, w - 7, h - 10, 13, 13);

                    // Small warning badge, with dark ink on the pale surface.
                    int cy = (h - 3) / 2;
                    g.setColor(new Color(0xE6B656));
                    g.fillOval(11, cy - 8, 16, 16);
                    g.setColor(new Color(0x4A3D2D));
                    g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawLine(19, cy - 4, 19, cy + 1);
                    g.fillOval(18, cy + 4, 2, 2);
                } finally {
                    g.dispose();
                }
                super.paintComponent(graphics);
            }
        };
        banner.setOpaque(false);
        banner.setFont(UniversityPixelTheme.font(14, Font.BOLD));
        banner.setForeground(UniversityPixelTheme.TEXT);
        banner.setBorder(new EmptyBorder(4, 37, 6, 12));
        return banner;
    }

    // Theme.apply() normally maps unrecognized white JLabel text to the dark theme TEXT.
    // Restore true white immediately before JLabel paints (including after theme re-application).
    private static JLabel whiteFeverLabel(String text) {
        JLabel label = new JLabel(text) {
            @Override protected void paintComponent(Graphics graphics) {
                if (!Color.WHITE.equals(getForeground())) setForeground(Color.WHITE);
                super.paintComponent(graphics);
            }
        };
        label.setForeground(Color.WHITE);
        label.setFont(UniversityPixelTheme.font(16, Font.BOLD));
        return label;
    }

    // Adjust the banner to its message length without covering all item slots.
    private void layoutStatusBanner() {
        int width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;
        int margin = pveMargin(width, height);
        int side = margin + 12, bottom = margin + 23;
        String message = status.getText() == null ? "" : status.getText();
        int available = Math.max(0, width - side * 2 - 8);
        int desired = status.getFontMetrics(status.getFont()).stringWidth(message) + 53;
        int bubbleWidth = Math.min(available, Math.max(160, desired));
        Rectangle bounds = new Rectangle(side + 4, height - bottom + 7, bubbleWidth, 38);
        if (!bounds.equals(status.getBounds())) status.setBounds(bounds);
        status.setVisible(!message.trim().isEmpty());
    }

    private final PixelArena arena = new PixelArena();
    // Sequencing-only model: old bottom dialogue panel must not cover the characters.
    private final BattleDialogue dialogue = new BattleDialogue();
    // Short-lived combat facts are separate from character speech and never enter its queue.
    private final BattleLog battleLog = new BattleLog();
    private final OreFlightLayer oreFlights = new OreFlightLayer();
    private final JLabel playerName = label("PLAYER"), enemyName = label("MONSTER");
    private final JLabel playerStatus = label("LINES 0"), enemyStatus = label("LINES 0");
    private final JLabel hold = label("HOLD"), next = label("NEXT");
    private final MiniPiecePreview holdPreview = new MiniPiecePreview("HOLD");
    private final MiniPiecePreview[] nextPreviews = {
        new MiniPiecePreview("1"), new MiniPiecePreview("2"), new MiniPiecePreview("3")
    };
    private final JLabel gauge = label("FEVER 0%"), pending = label("가비지 0");
    private final JLabel warp = label(" ");
    private final JLabel stats = label("<html>LINES 0<br>COMBO 0</html>");
    private final JLabel status = createBattleStatusBanner();
    private final JLabel clock = label("00:00");
    private final JLabel levelChip = CartoonBattleTopHud.levelChip("LV 1", UniversityPixelTheme.GOLD);
    private final JLabel title = label("MONSTER BATTLE");
    private final Board playerBoard = new Board(playerStatus, true);
    private final Board enemyBoard = new Board(enemyStatus, false);
    private final GarbageMeter garbage = new GarbageMeter();
    private final JButton[] slots = new JButton[4];
    private final boolean[] automaticSlots = new boolean[4];
    private final JButton resultTestButton = button("전투 정보");
    private final JButton backButton = CartoonBattleTopHud.quitButton("전투 포기 [ESC]");
    private final PixelMeter feverBar = new PixelMeter(100, UniversityPixelTheme.GOLD);
    private final JPanel topBar = new CartoonBattleTopHud(levelChip, title, clock, (PixelButton) backButton);
    private final JPanel leftColumn = column();
    private final JPanel rightColumn = column();
    // PvE 전용 HUD: 온라인 대전의 기존 좌우 열은 그대로 보존한다.
    private final JPanel pveHoldPanel = battlePreviewFrame(new BorderLayout(0, 3));
    private final JPanel pveNextPanel = battlePreviewFrame(null);
    private final JPanel pveFooter = new JPanel(null);
    private final JPanel pveFeverPanel = new JPanel(null) {
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                int w = getWidth(), h = getHeight();
                if (w < 5 || h < 5) return;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(0x273A35));
                g.fillRoundRect(0, 0, w, h, 22, 22);
                g.setStroke(new BasicStroke(2f));
                g.setColor(new Color(0x13201D));
                g.drawRoundRect(1, 1, w - 3, h - 3, 22, 22);
                g.setStroke(new BasicStroke(1.2f));
                g.setColor(new Color(0x9AAB9B));
                g.drawRoundRect(4, 4, w - 9, h - 9, 17, 17);
            } finally {
                g.dispose();
            }
        }
    };
    private final MiniPiecePreview pveHoldPreview = new MiniPiecePreview("HOLD");
    private final MiniPiecePreview[] pveNextPreviews = {
        new MiniPiecePreview("1"), new MiniPiecePreview("2"), new MiniPiecePreview("3")
    };
    private final JLabel pveFeverLabel = whiteFeverLabel("FEVER");
    private final JLabel pveFeverPercent = whiteFeverLabel("0%");
    private final PixelMeter pveFeverBar = new PixelMeter(100, UniversityPixelTheme.GOLD);
    private final JButton[] pveSlots = new JButton[4];
    private final JPanel enemyColumn = new JPanel(null);
    private IntConsumer itemAction;
    private long feedbackUntil;
    private AudioService audio;
    private long lastEventId;
    private ParticipantState previousLocal, previousEnemy;
    private boolean online;
    private String chapter = "university";
    private kr.ac.jbnu.se.tetris.app.BackgroundSkin backgroundSkin =
            kr.ac.jbnu.se.tetris.app.BackgroundSkin.DEFAULT;

    public void setBackgroundSkin(kr.ac.jbnu.se.tetris.app.BackgroundSkin skin) {
        backgroundSkin = skin == null ? kr.ac.jbnu.se.tetris.app.BackgroundSkin.DEFAULT : skin;
        arena.setBackgroundSkin(backgroundSkin);
        repaint();
    }
    private int feedbackGeneration;

    public BattlePanel() {
        // Preserve original FIFO/read duration and feed the current speaker to the arena.
        dialogue.setSpeechListener((speaker, text) -> {
            if (online || speaker == null || text == null) {
                arena.clearDialogue();
            } else if (speaker == BattleDialogue.Speaker.PLAYER) {
                arena.showDialogue(PixelArena.DialogueSpeaker.PLAYER, text);
            } else if (speaker == BattleDialogue.Speaker.MONSTER) {
                arena.showDialogue(PixelArena.DialogueSpeaker.MONSTER, text);
            } else {
                arena.clearDialogue();
            }
        });
        setLayout(null);
        setPreferredSize(new Dimension(850, 600));
        setBackground(BG);
        playerBoard.setAutomaticEffects(false); enemyBoard.setAutomaticEffects(false);
        // 스토리 전투에서는 중앙 라인 클리어 배너를 숨기고 타격/채굴 연출만 유지한다.
        playerBoard.setLineClearCalloutEnabled(false);

        // Compact pastel HUD; same labels, timer updates, and quit button callbacks.
        levelChip.setFont(UniversityPixelTheme.font(14, Font.BOLD));
        title.setFont(UniversityPixelTheme.font(15, Font.BOLD));
        title.setForeground(UniversityPixelTheme.TEXT);
        clock.setFont(UniversityPixelTheme.font(17, Font.BOLD));
        clock.setForeground(UniversityPixelTheme.TEXT);
        ((PixelButton) backButton).primary();
        backButton.setToolTipText("현재 전투를 포기하고 로비로 돌아갑니다.");
        add(topBar);

        buildLeftColumn();
        add(leftColumn);
        add(garbage);
        add(playerBoard);
        playerName.setForeground(UniversityPixelTheme.MINT);
        playerName.setHorizontalAlignment(SwingConstants.CENTER);
        add(playerName);
        buildRightColumn();
        add(rightColumn);
        buildPveHud();
        add(pveHoldPanel);
        add(pveNextPanel);
        add(pveFooter);
        add(arena);
        add(battleLog);
        // The battle log overlays the wide stage, but does not intercept mouse events.
        setComponentZOrder(battleLog, 1);
        buildEnemyColumn();
        add(enemyColumn);
        add(status);
        add(oreFlights);
        // Swing은 인덱스 0을 맨 앞에 그린다. 채굴 광원이 보드와 슬롯 위를 지나야 한다.
        setComponentZOrder(oreFlights, 0);
        // 넓은 투명 무대는 게임 보드와 HUD 뒤에 표시한다.
        setComponentZOrder(arena, getComponentCount() - 1);
        showPveHud(true);
    }

    /** PvE HOLD/NEXT outer shell. Existing PvP previews are not touched. */
    private static JPanel battlePreviewFrame(LayoutManager layout) {
        JPanel panel = new JPanel(layout) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    int w = getWidth(), h = getHeight();
                    if (w < 10 || h < 10) return;
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(new Color(0x302018));
                    g.fillRoundRect(0, 0, w, h, 20, 20);
                    g.setColor(new Color(0xF2D19D));
                    g.fillRoundRect(3, 3, w - 6, h - 6, 16, 16);
                    g.setColor(new Color(0xFFF8E9));
                    g.fillRoundRect(6, 6, w - 12, h - 12, 13, 13);
                    g.setColor(new Color(255, 255, 255, 72));
                    g.setStroke(new BasicStroke(1.4f));
                    g.drawRoundRect(5, 5, w - 11, h - 11, 14, 14);
                } finally { g.dispose(); }
            }
        };
        panel.setOpaque(false);
        return panel;
    }

    private void buildPveHud() {
        // PvE: the panel draws its own rounded outer frame; no beige Swing border.
        pveHoldPanel.setOpaque(false);
        pveHoldPanel.setBorder(new EmptyBorder(7, 8, 8, 8));
        JLabel holdTitle = label("HOLD");
        holdTitle.setHorizontalAlignment(SwingConstants.CENTER);
        holdTitle.setForeground(CampusArt.NAVY);
        holdTitle.setFont(UniversityPixelTheme.font(14, Font.BOLD));
        pveHoldPanel.add(holdTitle, BorderLayout.NORTH);
        pveHoldPreview.setBattleHudStyle(true);
        pveHoldPanel.add(pveHoldPreview, BorderLayout.CENTER);

        pveNextPanel.setLayout(new BoxLayout(pveNextPanel, BoxLayout.Y_AXIS));
        pveNextPanel.setOpaque(false);
        pveNextPanel.setBorder(new EmptyBorder(7, 7, 9, 7));
        JLabel nextTitle = label("NEXT");
        nextTitle.setForeground(CampusArt.NAVY);
        nextTitle.setFont(UniversityPixelTheme.font(14, Font.BOLD));
        nextTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        pveNextPanel.add(nextTitle);
        pveNextPanel.add(Box.createVerticalStrut(4));
        for (MiniPiecePreview preview : pveNextPreviews) {
            preview.setBattleHudStyle(true);
            preview.setAlignmentX(Component.CENTER_ALIGNMENT);
            preview.setMaximumSize(new Dimension(Integer.MAX_VALUE, 77));
            preview.setPreferredSize(new Dimension(92, 77));
            pveNextPanel.add(preview);
            pveNextPanel.add(Box.createVerticalStrut(4));
        }

        pveFooter.setOpaque(false);
        pveFeverPanel.setOpaque(false);
        pveFeverPanel.setBorder(null);
        pveFeverLabel.setText("FEVER");
        pveFeverLabel.setForeground(Color.WHITE);
        pveFeverLabel.setFont(UniversityPixelTheme.font(16, Font.BOLD));
        pveFeverPercent.setForeground(Color.WHITE);
        pveFeverPercent.setFont(UniversityPixelTheme.font(17, Font.BOLD));
        pveFeverPercent.setHorizontalAlignment(SwingConstants.RIGHT);
        pveFeverPanel.add(pveFeverLabel);
        pveFeverPanel.add(pveFeverBar);
        pveFeverPanel.add(pveFeverPercent);
        pveFooter.add(pveFeverPanel);
        for (int i = 0; i < pveSlots.length; i++) {
            final int index = i;
            JButton slot = createPveItemButton("빈 슬롯");
            slot.putClientProperty("hudHotkey", i + 1);
            slot.setFont(UniversityPixelTheme.font(12, Font.BOLD));
            slot.setFocusable(false);
            slot.addActionListener(event -> {
                if (automaticSlots[index]) setFeedback("자동 사용 아이템 · 조건이 맞으면 발동합니다.");
                else if (itemAction != null) itemAction.accept(index);
            });
            slot.setEnabled(false);
            pveSlots[i] = slot;
            pveFooter.add(slot);
        }
    }

    private void buildLeftColumn() {
        section(leftColumn, hold, UniversityPixelTheme.GOLD);
        holdPreview.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftColumn.add(holdPreview);
        leftColumn.add(Box.createVerticalStrut(14));
        section(leftColumn, label("ITEM"), UniversityPixelTheme.GOLD);
        JLabel keyHint = label("숫자 1–4 키로 사용");
        keyHint.setFont(UniversityPixelTheme.font(10, Font.PLAIN));
        keyHint.setForeground(UniversityPixelTheme.TEXT_SUB);
        keyHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftColumn.add(keyHint);
        leftColumn.add(Box.createVerticalStrut(4));
        for (int i = 0; i < slots.length; i++) {
            final int index = i;
            slots[i] = button((i + 1) + "  빈 슬롯");
            slots[i].setFont(UniversityPixelTheme.font(12, Font.BOLD));
            slots[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            slots[i].setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            slots[i].setPreferredSize(new Dimension(100, 38));
            slots[i].addActionListener(event -> {
                if (automaticSlots[index]) setFeedback("자동 사용 아이템 · 조건이 맞으면 발동합니다.");
                else if (itemAction != null) itemAction.accept(index);
            });
            slots[i].setEnabled(false);
            leftColumn.add(slots[i]);
            leftColumn.add(Box.createVerticalStrut(4));
        }
        leftColumn.add(Box.createVerticalGlue());
        String[][] keys = { {"←→", "이동"}, {"↑↓", "회전"}, {"SPACE", "낙하"}, {"C", "HOLD"}, {"P", "정지"} };
        for (String[] key : keys) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 1)); row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
            JLabel chip = UniversityPixelTheme.chip(key[0], UniversityPixelTheme.PANEL_LIGHT);
            chip.setFont(UniversityPixelTheme.font(10, Font.BOLD));
            row.add(chip);
            JLabel action = label(key[1]);
            action.setFont(UniversityPixelTheme.font(10, Font.PLAIN));
            action.setForeground(UniversityPixelTheme.TEXT_SUB);
            row.add(action);
            leftColumn.add(row);
        }
    }

    private void buildRightColumn() {
        section(rightColumn, next, UniversityPixelTheme.GOLD);
        for (MiniPiecePreview preview : nextPreviews) {
            preview.setAlignmentX(Component.LEFT_ALIGNMENT);
            rightColumn.add(preview);
            rightColumn.add(Box.createVerticalStrut(4));
        }
        rightColumn.add(Box.createVerticalStrut(10));
        gauge.setFont(UniversityPixelTheme.font(12, Font.BOLD));
        section(rightColumn, gauge, UniversityPixelTheme.GOLD);
        feverBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        rightColumn.add(feverBar);
        rightColumn.add(Box.createVerticalStrut(3));
        warp.setFont(UniversityPixelTheme.font(10, Font.BOLD));
        warp.setForeground(UniversityPixelTheme.MINT);
        warp.setAlignmentX(Component.LEFT_ALIGNMENT);
        rightColumn.add(warp);
        rightColumn.add(Box.createVerticalStrut(10));
        pending.setFont(UniversityPixelTheme.font(12, Font.BOLD));
        pending.setAlignmentX(Component.LEFT_ALIGNMENT);
        rightColumn.add(pending);
        rightColumn.add(Box.createVerticalGlue());
        // 보드가 쓰는 한 줄 상태 대신 좁은 칸에 맞게 두 줄 기록을 직접 보여 준다.
        playerStatus.setVisible(false);
        rightColumn.add(playerStatus);
        stats.setFont(UniversityPixelTheme.font(12, Font.BOLD));
        stats.setForeground(UniversityPixelTheme.TEXT_SUB);
        stats.setAlignmentX(Component.LEFT_ALIGNMENT);
        rightColumn.add(stats);
    }

    private void buildEnemyColumn() {
        enemyColumn.setOpaque(false);
        enemyName.setFont(UniversityPixelTheme.font(12, Font.BOLD));
        enemyName.setForeground(UniversityPixelTheme.CORAL);
        enemyName.setHorizontalAlignment(SwingConstants.CENTER);
        enemyStatus.setFont(UniversityPixelTheme.font(11, Font.BOLD));
        enemyStatus.setForeground(UniversityPixelTheme.TEXT_SUB);
        enemyStatus.setHorizontalAlignment(SwingConstants.CENTER);
        enemyColumn.add(enemyName);
        enemyColumn.add(enemyBoard);
        enemyColumn.add(enemyStatus);
    }

    private static JPanel column() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private static void section(JPanel parent, JLabel heading, Color color) {
        heading.setForeground(color);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.setBorder(new EmptyBorder(0, 0, 4, 0));
        parent.add(heading);
    }

    /** 두 전투 모드 모두 캠퍼스 풍경 위에 같은 강의동 외벽을 그린다. */
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            if (online) paintOnlineShell(g);
            else paintPveShell(g);
        } finally { g.dispose(); }
    }

    private void paintPveShell(Graphics2D g) {
        BufferedImage winterBackdrop = kr.ac.jbnu.se.tetris.ui.seongeun.components.BackgroundSkinAssets
                .image(backgroundSkin, chapter);
        BufferedImage backdrop = winterBackdrop != null ? winterBackdrop
                : "graduation".equals(chapter) ? GRADUATION_BG
                : "employment".equals(chapter) ? EMPLOYMENT_BG
                : UNIVERSITY_BATTLE_BG != null ? UNIVERSITY_BATTLE_BG : UNIVERSITY_BG;
        if (backdrop != null) paintBackdropCover(g, backdrop, getWidth(), getHeight());
        else {
            g.setColor(new Color(0xA2D785));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        Rectangle board = playerBoard.getBounds();
        if (board.width > 0 && board.height > 0) {
            g.setColor(new Color(0x27353B));
            g.fillRoundRect(board.x - 8, board.y - 8,
                    board.width + 16, board.height + 16, 14, 14);
            g.setColor(new Color(0x88BEA0));
            g.drawRoundRect(board.x - 8, board.y - 8,
                    board.width + 15, board.height + 15, 14, 14);
        }
    }

    private void paintOnlineShell(Graphics2D g) {
        int margin = pveMargin(getWidth(), getHeight());
        int side = margin + 12, top = margin + 54, bottom = margin + 23;
        paintCommonShell(g, margin, side, top, bottom,
                UNIVERSITY_BATTLE_BG != null ? UNIVERSITY_BATTLE_BG : UNIVERSITY_BG);
        if (arena.getWidth() <= 0 || playerBoard.getWidth() <= 0 || enemyBoard.getWidth() <= 0) return;
        int bodyBottom = getHeight() - bottom;
        // PvP에서는 두 보드가 같은 크기로 무대를 사이에 두고 대칭을 이룬다.
        Rectangle opponent = SwingUtilities.convertRectangle(enemyBoard.getParent(),
                enemyBoard.getBounds(), this);
        paintPillar(g, opponent.x + opponent.width, GAP, top, bodyBottom);
        paintPillar(g, arena.getX() + arena.getWidth(), GAP, top, bodyBottom);
        paintPillar(g, playerBoard.getX() + playerBoard.getWidth(), GAP, top, bodyBottom);
        g.setColor(FACADE_EDGE);
        g.drawRect(arena.getX() - 1, arena.getY() - 1, arena.getWidth() + 1, arena.getHeight() + 1);
        paintBoardFacade(g, opponent);
        paintBoardFacade(g, playerBoard.getBounds());
        g.setColor(UniversityPixelTheme.LINE);
        g.drawLine(rightColumn.getX() - 5, top + 14,
                rightColumn.getX() - 5, bodyBottom - 14);
    }

    private void paintCommonShell(Graphics2D g, int margin, int side, int top, int bottom,
                                  BufferedImage backdrop) {
        if (backdrop != null) {
            paintBackdropCover(g, backdrop, getWidth(), getHeight());
            // 프레임 바깥 풍경은 남기되 화면의 주인공인 보드와 전투원보다 조용하게 둔다.
            g.setColor(new Color(231, 225, 213, 66));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        int shellX = margin, shellY = margin;
        int shellW = Math.max(0, getWidth() - margin * 2);
        int shellH = Math.max(0, getHeight() - margin * 2);
        if (shellW < 40 || shellH < 40) return;
        int bodyBottom = getHeight() - bottom;
        // 외벽, 상인방, 하단 콘크리트 턱은 세 칸을 하나의 건물처럼 연결한다.
        g.setColor(FACADE_EDGE);
        g.fillRect(shellX, shellY, shellW, shellH);
        g.setColor(FACADE);
        g.fillRect(shellX + 2, shellY + 2, shellW - 4, shellH - 4);
        g.setColor(FACADE_LIGHT);
        g.fillRect(shellX + 3, shellY + 3, shellW - 6, top - shellY - 7);
        g.setColor(UniversityPixelTheme.PANEL_LIGHT);
        g.fillRect(side, top, Math.max(0, getWidth() - side * 2),
                Math.max(0, bodyBottom - top));
        g.setColor(FACADE_LIGHT);
        g.fillRect(shellX + 3, bodyBottom, shellW - 6, Math.max(0, getHeight() - margin - bodyBottom - 3));
        paintConcreteTexture(g, shellX + 3, shellY + 3, shellW - 6, top - shellY - 6);
        paintConcreteTexture(g, shellX + 3, bodyBottom, shellW - 6,
                Math.max(0, getHeight() - margin - bodyBottom - 3));
        g.setColor(FACADE_EDGE);
        g.drawLine(shellX + 2, top - 3, shellX + shellW - 3, top - 3);
        g.drawLine(shellX + 2, bodyBottom, shellX + shellW - 3, bodyBottom);
        g.setColor(new Color(0xF6F2E8));
        g.drawLine(shellX + 3, shellY + 3, shellX + shellW - 4, shellY + 3);
    }

    private static void paintPillar(Graphics2D g, int x, int width, int top, int bottom) {
        g.setColor(FACADE_EDGE);
        g.fillRect(x, top, width, bottom - top);
        g.setColor(FACADE);
        g.fillRect(x + 2, top, Math.max(0, width - 4), bottom - top);
        g.setColor(FACADE_LIGHT);
        g.drawLine(x + 2, top + 2, x + 2, bottom - 3);
        g.setColor(new Color(0xA1A49D));
        g.drawLine(x + width - 3, top + 2, x + width - 3, bottom - 3);
    }

    private static void paintConcreteTexture(Graphics2D g, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        g.setColor(new Color(77, 79, 72, 20));
        for (int row = 5; row < height - 3; row += 11)
            for (int col = 7 + ((row * 7) % 13); col < width - 3; col += 19)
                g.fillRect(x + col, y + row, 2, 1);
        g.setColor(new Color(255, 255, 255, 52));
        for (int col = 47; col < width - 10; col += 113)
            g.drawLine(x + col, y + 2, x + col, y + Math.min(height - 3, 7));
    }

    private static void paintBackdropCover(Graphics2D g, BufferedImage image, int width, int height) {
        if (width <= 0 || height <= 0) return;
        double cropScale = Math.min((double) image.getWidth() / width,
                (double) image.getHeight() / height);
        int sourceW = Math.max(1, (int) Math.round(width * cropScale));
        int sourceH = Math.max(1, (int) Math.round(height * cropScale));
        int sourceX = (image.getWidth() - sourceW) / 2;
        int sourceY = (image.getHeight() - sourceH) / 2;
        g.drawImage(image, 0, 0, width, height,
                sourceX, sourceY, sourceX + sourceW, sourceY + sourceH, null);
    }

    private static BufferedImage readBackdrop(String resource) {
        try (InputStream stream = BattlePanel.class.getResourceAsStream(resource)) {
            return stream == null ? null : ImageIO.read(stream);
        } catch (IOException exception) { return null; }
    }

    /** 칸은 그대로 두고 바깥 여백 5px만 강의동 외벽처럼 감싼다. */
    private static void paintBoardFacade(Graphics2D g, Rectangle board) {
        int x = board.x - 5, y = board.y - 7;
        int width = board.width + 10, height = board.height + 14;
        g.setColor(FACADE);
        g.fillRect(x, y, width, height);
        g.setColor(FACADE_EDGE);
        g.drawRect(x, y, width - 1, height - 1);
        g.setColor(FACADE_LIGHT);
        g.drawLine(x + 1, y + 1, x + width - 2, y + 1);
        g.drawLine(x + 1, y + height - 3, x + width - 2, y + height - 3);
        // 윗쪽 콘크리트 줄눈만 남겨 화면 전체에 창문 무늬가 반복되지 않게 한다.
        g.setColor(FACADE_EDGE);
        for (int joint = x + 28; joint < x + width - 10; joint += 32)
            g.drawLine(joint, y + 3, joint, y + 6);
    }

    /** PvE는 중앙 보드와 양옆 캐릭터, 하단 아이템 HUD를 사용한다. PvP는 기존 방식 유지. */
    @Override public void doLayout() {
        int width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;
        oreFlights.setBounds(0, 0, width, height);
        int margin = pveMargin(width, height);
        int side = margin + 12, top = margin + 68, bottom = margin + 23;
        topBar.setBounds(side + 4, margin + 6, Math.max(0, width - side * 2 - 8), 50);
        layoutStatusBanner();
        resultTestButton.setVisible(false);
        if (online) {
            showPveHud(false);
            enemyColumn.setVisible(true);
            layoutOnline(width, side, top, Math.max(100, height - top - bottom));
            return;
        }

        showPveHud(true);
        enemyColumn.setVisible(false);
        enemyColumn.setBounds(0, 0, 0, 0);
        playerName.setVisible(false);
        int interiorWidth = Math.max(0, width - side * 2);
        int footerHeight = Math.max(88, Math.min(96, height / 9));
        int footerTop = height - bottom - footerHeight;
        int boardAreaHeight = Math.max(220, footerTop - top - 12);
        int cell = Math.max(9, Math.min((boardAreaHeight - 8) / 22,
                Math.max(9, (interiorWidth - 400) / 10)));
        int boardWidth = cell * 10 + 2, boardHeight = cell * 22 + 2;
        int boardX = (width - boardWidth) / 2;
        int boardY = top + Math.max(0, (boardAreaHeight - boardHeight) / 2);
        playerBoard.setBounds(boardX, boardY, boardWidth, boardHeight);
        garbage.setBounds(boardX - METER - 4, boardY + 1,
                METER, Math.max(0, boardHeight - 2));

        // HOLD와 NEXT는 보드 상단 양옆에만 표시한다.
        int hudWidth = clamp(Math.round(width * 0.097f), 96, 116);
        pveHoldPanel.setBounds(boardX - hudWidth - 15, boardY + 18, hudWidth, 132);
        pveNextPanel.setBounds(boardX + boardWidth + 15, boardY + 18, hudWidth, 282);

        // 한 개의 투명 무대에서 캐릭터를 보드 좌우에 그리고, 보드는 그 위에 그린다.
        arena.setBounds(side, top, interiorWidth, Math.max(1, footerTop - top));
        // Reserve the upper-left free lane so combat notices do not cover either character.
        int noticeWidth = Math.max(150, Math.min(380, (interiorWidth - boardWidth) / 2 - 30));
        battleLog.setBounds(side + 12, top + 5, noticeWidth, BattleLog.preferredHeight());
        // 네 개의 아이템을 레퍼런스처럼 정사각형으로 묶고 우측에 정렬한다.
        int itemGap = 10;
        int itemSize = Math.max(54, Math.min(84, (interiorWidth - 230 - itemGap * 3) / 4));
        int itemGroupWidth = itemSize * 4 + itemGap * 3;
        int itemStartX = interiorWidth - itemGroupWidth;
        int feverWidth = Math.min(clamp(Math.round(interiorWidth * .38f), 190, 390),
                Math.max(130, itemStartX - 18));
        pveFooter.setBounds(side, footerTop, interiorWidth, footerHeight);
        pveFeverPanel.setBounds(0, 4, feverWidth, footerHeight - 7);
        for (int i = 0; i < pveSlots.length; i++) {
            pveSlots[i].setBounds(itemStartX + i * (itemSize + itemGap),
                    footerHeight - itemSize - 2, itemSize, itemSize);
        }
        pveFeverLabel.setBounds(16, 7, feverWidth - 32, 23);
        pveFeverBar.setBounds(14, 33, Math.max(20, feverWidth - 99), 27);
        pveFeverPercent.setBounds(feverWidth - 78, 29, 62, 30);
    }

    private void showPveHud(boolean pve) {
        leftColumn.setVisible(!pve);
        rightColumn.setVisible(!pve);
        pveHoldPanel.setVisible(pve);
        pveNextPanel.setVisible(pve);
        pveFooter.setVisible(pve);
        arena.setWidePve(pve);
        battleLog.setVisible(pve);
    }

    /** 온라인에서는 양쪽 보드가 같은 크기이고 무대가 공격 교차로가 된다. */
    private void layoutOnline(int width, int side, int areaTop, int areaHeight) {
        int areaWidth = Math.max(0, width - side * 2);
        int leftWidth = clamp(Math.round(width * 0.115f), 86, 120);
        int rightWidth = clamp(Math.round(width * 0.105f), 80, 108);
        int minimumArena = Math.max(120, Math.round(width * 0.16f));
        int fixed = leftWidth + rightWidth + METER + 2 + GAP * 4 + minimumArena;
        // 상대의 이름과 두 줄 기록이 보드 위아래에 들어갈 세로 여백을 남긴다.
        int cell = Math.max(9, Math.min((areaHeight - 76) / 22, (areaWidth - fixed - 24) / 20));
        int boardWidth = cell * 10 + 2, boardHeight = cell * 22 + 2;
        int boardY = areaTop + (areaHeight - boardHeight) / 2;
        int arenaWidth = Math.max(minimumArena, areaWidth - (leftWidth + rightWidth
                + METER + 2 + GAP * 4 + boardWidth * 2));

        int enemyX = side;
        int arenaX = enemyX + boardWidth + GAP;
        int meterX = arenaX + arenaWidth + GAP;
        int playerX = meterX + METER + 2;
        int leftX = playerX + boardWidth + GAP;
        int rightX = leftX + leftWidth + GAP;
        leftColumn.setBounds(leftX, boardY, leftWidth, boardHeight);
        playerBoard.setBounds(playerX, boardY, boardWidth, boardHeight);
        playerName.setBounds(playerX, boardY - 20, boardWidth, 17);
        playerName.setVisible(true);
        garbage.setBounds(meterX, boardY + 1, METER, boardHeight - 2);
        arena.setBounds(arenaX, areaTop, arenaWidth, areaHeight);
        battleLog.setBounds(arenaX + 8, areaTop + 8,
                Math.max(1, arenaWidth - 16), BattleLog.preferredHeight());
        enemyColumn.setBounds(enemyX, boardY - 20, boardWidth, boardHeight + 57);
        enemyName.setBounds(0, 0, boardWidth, 17);
        enemyBoard.setBounds(0, 20, boardWidth, boardHeight);
        enemyStatus.setBounds(0, boardHeight + 23, boardWidth, 34);
        rightColumn.setBounds(rightX, boardY, rightWidth, boardHeight);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static int pveMargin(int width, int height) {
        return clamp(26 + Math.min((width - 820) / 20, (height - 615) / 10), 26, 46);
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(UniversityPixelTheme.font(14, Font.BOLD));
        return label;
    }
    private static JButton button(String text) {
        PixelButton button = new PixelButton(text);
        button.setFont(UniversityPixelTheme.font(12, Font.BOLD));
        button.secondary();
        return button;
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(16, (orientation == SwingConstants.VERTICAL ? visible.height : visible.width) - 16);
    }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    /** 칸 크기를 창에 맞춰 줄이므로 세로 스크롤 없이 항상 한 화면에 보인다. */
    @Override public boolean getScrollableTracksViewportHeight() { return true; }

    public void setPlayers(PlayerData player, PlayerData enemy) {
        playerName.setText(player.getNickname());
        enemyName.setText(enemy.getNickname());
        feedbackGeneration++;
        arena.resetForEncounter();
        battleLog.clear();
    }
    public void startBattle() { playerBoard.start(); }
    public void setResultAction(ActionListener listener) { resultTestButton.addActionListener(listener); }
    public void setItemAction(IntConsumer action) { itemAction = action; }
    public void setBackAction(ActionListener action) { backButton.addActionListener(action); }
    public void setMode(boolean online) {
        if (this.online == online) return;
        dialogue.clear();
        arena.clearDialogue();
        battleLog.clear();
        this.online = online;
        playerBoard.setLineClearCalloutEnabled(online);
        if (online) {
            ((PixelButton) backButton).danger();
            backButton.setText("대전 포기 [ESC]");
            backButton.setToolTipText("진행 중인 온라인 대전을 기권하고 로비로 돌아갑니다.");
            UniversityPixelTheme.setChip(levelChip, "ONLINE", UniversityPixelTheme.MINT);
            title.setText("1:1 PvP 대전");
            arena.setChapter("university");
        } else {
            ((PixelButton) backButton).primary();
            backButton.setText("전투 포기 [ESC]");
            backButton.setToolTipText("현재 전투를 포기하고 로비로 돌아갑니다.");
        }
        arena.setOnline(online);
        revalidate();
        repaint();
    }
    public void setAudio(AudioService service) { audio = service; }
    public void setEncounter(String chapter, int level, MonsterTier pattern, String art) {
        online = false;
        playerBoard.setLineClearCalloutEnabled(false);
        this.chapter = chapter;
        ((PixelButton) backButton).primary();
        backButton.setText("전투 포기 [ESC]");
        backButton.setToolTipText("현재 전투를 포기하고 로비로 돌아갑니다.");
        UniversityPixelTheme.setChip(levelChip, "LV " + level,
                pattern == MonsterTier.BOSS ? UniversityPixelTheme.CORAL : UniversityPixelTheme.GOLD);
        String course = "graduation".equals(chapter) ? "졸업 과정" : "employment".equals(chapter) ? "취업 과정" : "대학교 과정";
        String tier = pattern == MonsterTier.BOSS ? "BOSS BATTLE" : pattern == MonsterTier.ELITE ? "ELITE" : "BATTLE";
        title.setText(course + "  ·  " + tier);
        arena.setChapter(chapter); arena.setMonsterPattern(pattern); arena.setMonsterArt(art); arena.setOnline(false);
        battleLog.clear();
        revalidate();
        repaint();
    }
    public void resetFeedback() {
        dialogue.clear();
        arena.clearDialogue();
        battleLog.clear();
        feedbackGeneration++;
        previousLocal = previousEnemy = null; lastEventId = 0; feedbackUntil = 0;
        playerBoard.resetEffects(); enemyBoard.resetEffects();
        oreFlights.reset();
        arena.resetForEncounter();
        garbage.setPending(0, 0);
    }
    public void finish(boolean won) { arena.showEffect(won ? PixelArena.Effect.VICTORY : PixelArena.Effect.DEFEAT, 0); }
    private void sound(AudioService.Event event) { if (audio != null) audio.play(event); }
    private void soundAtImpact(AudioService.Event event) {
        if (audio == null) return;
        final int generation = feedbackGeneration;
        Timer timer = new Timer(PixelArena.IMPACT_MS, action -> {
            if (generation == feedbackGeneration) sound(event);
        });
        timer.setRepeats(false);
        timer.start();
    }
    private void showHitAtImpact(Board target) {
        final int generation = feedbackGeneration;
        Timer timer = new Timer(PixelArena.IMPACT_MS, action -> {
            if (generation == feedbackGeneration) target.showHit();
        });
        timer.setRepeats(false);
        timer.start();
    }
    public void applyEvents(List<BattleEvent> events, String localId) {
        if (events == null || events.isEmpty()) return;
        // Reject repeated wire events before updating either visuals or combat notices.
        List<BattleEvent> accepted = new ArrayList<BattleEvent>();
        for (BattleEvent event : events) {
            if (event.getEventId() <= lastEventId) continue;
            lastEventId = event.getEventId();
            if (!online) accepted.add(event);
            boolean local = localId != null && localId.equals(event.getActorId());
            Board board = local ? playerBoard : enemyBoard;
            if (event.getType() == BattleEvent.Type.DAMAGE) {
                if (local) { playerBoard.showAttack(); showHitAtImpact(enemyBoard); }
                else { enemyBoard.showAttack(); showHitAtImpact(playerBoard); }
                arena.showEffect(local ? PixelArena.Effect.ATTACK : PixelArena.Effect.MONSTER_ATTACK,
                        event.getAmount(), local);
                soundAtImpact(local ? AudioService.Event.ATTACK : AudioService.Event.HIT);
                setFeedback((local ? "공격 적중  " : "피격  ") + event.getAmount() + " DAMAGE");
            } else if (event.getType() == BattleEvent.Type.GARBAGE_RECEIVED) {
                board.showGarbage(event.getAmount());
            } else if (event.getType() == BattleEvent.Type.ITEM_ACQUIRED) {
                boolean mined = local && showOreFlight(event);
                if (!mined) arena.showEffect(PixelArena.Effect.ITEM_ACQUIRE, event.getAmount(), !local);
                if (local) {
                    sound(AudioService.Event.ITEM_ACQUIRE);
                    setFeedback((mined ? "광석 채굴 · " : "아이템 획득 · ") + itemName(event.getReason()));
                }
            } else if (event.getType() == BattleEvent.Type.ITEM_USED) {
                boolean targetMonster = localId != null && !localId.equals(event.getTargetId());
                arena.showItemEffect(event.getReason(), targetMonster); sound(AudioService.Event.ITEM_USE);
                if (local) setFeedback("아이템 사용 · " + itemName(event.getReason()));
            } else if (event.getType() == BattleEvent.Type.ITEM_REMOVED) {
                if (localId != null && localId.equals(event.getTargetId())) setFeedback("아이템이 무효화됨 · " + itemName(event.getReason()));
            } else if (event.getType() == BattleEvent.Type.CORE_EVENT && event.getCoreEvent() != null) {
                GameEvent core = event.getCoreEvent();
                if (core.getType() == GameEvent.Type.PIECE_PLACED) {
                    board.showPlacement(core.getPiece(), core.getX(), core.getY());
                    arena.showPlacement(!local);
                    if (local) sound(AudioService.Event.DROP);
                } else if (core.getType() == GameEvent.Type.LINE_CLEAR) {
                    board.showLineClear(core.getLineCount(), core.getCombo(), core.isPerfectClear());
                    arena.showClear(core.getLineCount(), core.getCombo(), core.isPerfectClear(), !local);
                    if (local) sound(AudioService.Event.LINE_CLEAR);
                } else if (local && core.getType() == GameEvent.Type.PIECE_ROTATED) sound(AudioService.Event.ROTATE);
            }
        }
        // Render completed notices only once all related events in this engine update are known.
        if (!online) recordBattleNotices(accepted, localId);
    }

    private interface BattleNotice {
        BattleLog.Side side();
        String text();
    }

    private static final class ClearNotice implements BattleNotice {
        final BattleLog.Side side;
        final String subject;
        final int lines, combo;
        final boolean perfect;
        Integer damage, garbage;

        ClearNotice(BattleLog.Side side, String subject, GameEvent clear) {
            this.side = side;
            this.subject = subject;
            this.lines = clear.getLineCount();
            this.combo = clear.getCombo();
            this.perfect = clear.isPerfectClear();
        }

        @Override public BattleLog.Side side() { return side; }

        @Override public String text() {
            StringBuilder notice = new StringBuilder(subject).append(" · ").append(lines).append("줄 클리어");
            if (perfect) notice.append(" 퍼펙트!");
            if (combo > 0) notice.append(" · ").append(combo).append("콤보");
            if (damage != null || garbage != null) {
                notice.append(" → ");
                if (damage != null) notice.append(damage).append(" 피해");
                if (garbage != null) {
                    if (damage != null) notice.append(" · ");
                    notice.append("가비지 ").append(garbage).append("줄");
                }
            }
            return notice.toString();
        }
    }

    /** Damage and items without a LINE_CLEAR source are still legitimate battle facts. */
    private static final class ActionNotice implements BattleNotice {
        final BattleLog.Side side;
        final String subject, actor, target;
        String action;
        Integer damage, garbage;
        String removedItem, removedFrom;

        ActionNotice(BattleLog.Side side, String subject, String actor, String target, String action) {
            this.side = side;
            this.subject = subject;
            this.actor = actor;
            this.target = target;
            this.action = action;
        }

        @Override public BattleLog.Side side() { return side; }

        boolean sameParticipants(BattleEvent event) {
            return java.util.Objects.equals(actor, event.getActorId())
                    && java.util.Objects.equals(target, event.getTargetId());
        }

        @Override public String text() {
            StringBuilder value = new StringBuilder(subject).append(" · ").append(action);
            if (removedItem != null) {
                value.append(" → ").append(removedFrom).append(' ').append(itemName(removedItem)).append(" 제거");
            } else if (damage != null || garbage != null) {
                value.append(" → ");
                if (damage != null) value.append("피해 ").append(damage);
                if (garbage != null) {
                    if (damage != null) value.append(" + ");
                    value.append("가비지 ").append(garbage).append("줄");
                }
            }
            return value.toString();
        }
    }

    /**
     * Formats one batch of accepted engine events. Linked LINE_CLEAR events share a core ID;
     * standalone damage/garbage and item consequences are paired in event order.
     */
    private void recordBattleNotices(List<BattleEvent> events, String localId) {
        List<BattleNotice> output = new ArrayList<BattleNotice>();
        Map<String, ClearNotice> clearByCore = new LinkedHashMap<String, ClearNotice>();
        ActionNotice attack = null;
        ActionNotice item = null;
        for (BattleEvent event : events) {
            String actor = event.getActorId();
            boolean local = localId != null && localId.equals(actor);
            BattleLog.Side side = logSide(local);
            String subject = actorLabel(local);
            BattleEvent.Type type = event.getType();

            GameEvent core = event.getCoreEvent();
            if (core != null && core.getType() == GameEvent.Type.LINE_CLEAR) {
                String coreActor = actor == null ? core.getActorId() : actor;
                String key = coreActor + ":" + core.getEventId();
                ClearNotice notice = clearByCore.get(key);
                if (notice == null) {
                    notice = new ClearNotice(side, subject, core);
                    clearByCore.put(key, notice);
                    output.add(notice);
                }
                if (type == BattleEvent.Type.DAMAGE) notice.damage = event.getAmount();
                if (type == BattleEvent.Type.GARBAGE_SENT) notice.garbage = event.getAmount();
                continue;
            }
            if (type == BattleEvent.Type.DAMAGE) {
                // A direct attack may have no backing Tetris line clear (synthetic/wire event).
                if (attack == null || !attack.sameParticipants(event) || attack.damage != null) {
                    attack = new ActionNotice(side, subject, actor, event.getTargetId(), "공격");
                    output.add(attack);
                }
                attack.damage = event.getAmount();
                item = null;
            } else if (type == BattleEvent.Type.GARBAGE_SENT) {
                if (item != null && "가비지 폭탄".equals(item.action)
                        && item.sameParticipants(event) && item.garbage == null) {
                    item.garbage = event.getAmount();
                    item = null;
                    attack = null;
                } else {
                    if (attack == null || !attack.sameParticipants(event) || attack.garbage != null) {
                        attack = new ActionNotice(side, subject, actor, event.getTargetId(), "공격");
                        output.add(attack);
                    }
                    attack.garbage = event.getAmount();
                }
            } else if (type == BattleEvent.Type.ITEM_USED) {
                String id = event.getReason();
                if ("garbage_bomb".equals(id) && attack != null
                        && attack.sameParticipants(event) && attack.damage == null
                        && attack.garbage != null) {
                    // BattleManager submits the bomb's garbage event before ITEM_USED.
                    attack.action = "가비지 폭탄";
                    attack = null;
                    item = null;
                } else {
                    String action = itemAction(id);
                    item = new ActionNotice(side, subject, actor, event.getTargetId(), action);
                    output.add(item);
                    attack = null;
                    if (!"가비지 폭탄".equals(action) && !"무효화".equals(action)) item = null;
                }
            } else if (type == BattleEvent.Type.ITEM_REMOVED) {
                if (item != null && "무효화".equals(item.action) && item.sameParticipants(event)) {
                    item.removedItem = event.getReason();
                    item.removedFrom = local ? "상대" : "내";
                } else {
                    ActionNotice removed = new ActionNotice(side, subject, actor, event.getTargetId(),
                            itemName(event.getReason()) + " 제거");
                    output.add(removed);
                }
                item = null;
                attack = null;
            } else if (type == BattleEvent.Type.ITEM_ACQUIRED) {
                // Player pickups already have an ore-flight / inventory effect.
                if (!local) output.add(new ActionNotice(side, subject, actor, event.getTargetId(),
                        "아이템 획득 · " + itemName(event.getReason())));
                attack = null;
                item = null;
            } else if (type == BattleEvent.Type.CORE_EVENT) {
                // Placement starts a separate engine action; never group its attacks with the last one.
                attack = null;
                item = null;
            }
        }
        for (BattleNotice notice : output) battleLog.push(notice.side(), notice.text());
    }

    private static String itemAction(String id) {
        if ("heal".equals(id)) return "회복 사용";
        if ("shield".equals(id)) return "보호막 발동";
        if ("garbage_bomb".equals(id)) return "가비지 폭탄";
        if ("nullify".equals(id)) return "무효화";
        return itemName(id) + " 사용";
    }

    private static BattleLog.Side logSide(boolean local) {
        return local ? BattleLog.Side.PLAYER : BattleLog.Side.ENEMY;
    }

    private String actorLabel(boolean local) {
        if (local) return "나";
        String name = enemyName.getText();
        return name == null || name.trim().isEmpty() || "MONSTER".equals(name)
                ? "일반 몬스터" : name;
    }

    private boolean showOreFlight(BattleEvent event) {
        int x = event.getItemSourceX(), y = event.getItemSourceY();
        int index = event.getItemSlotIndex();
        if (x < 0 || x >= 10 || y < 0 || y >= 22 || index < 0 || index >= slots.length
                || playerBoard.getWidth() <= 0 || (online ? slots[index] : pveSlots[index]).getWidth() <= 0) return false;
        Point from = SwingUtilities.convertPoint(playerBoard, playerBoard.cellCenter(x, y), this);
        JButton targetSlot = online ? slots[index] : pveSlots[index];
        Point to = SwingUtilities.convertPoint(targetSlot,
                new Point(targetSlot.getWidth() / 2, targetSlot.getHeight() / 2), this);
        oreFlights.launch(from, to, targetSlot.getWidth(), targetSlot.getHeight());
        return true;
    }
    /** Story dialogue is presented near its actual speaker, not in the status ticker. */
    public void showStoryDialogue(String speaker, String text) {
        if (online || speaker == null || text == null || text.trim().isEmpty()) return;
        if ("플레이어".equals(speaker) || "PLAYER".equalsIgnoreCase(speaker))
            dialogue.enqueue(BattleDialogue.Speaker.PLAYER, text);
        else if ("몬스터".equals(speaker) || "MONSTER".equalsIgnoreCase(speaker))
            dialogue.enqueue(BattleDialogue.Speaker.MONSTER, text);
        else setFeedback(speaker + ": " + text); // Narration remains in the status ticker.
    }

    public void setFeedback(String text) {
        // Compatibility for story cues from other callers: never show a second box.
        if (!online && text != null) {
            int delimiter = text.indexOf(':');
            if (delimiter < 0) delimiter = text.indexOf('：');
            if (delimiter > 0) {
                String speaker = text.substring(0, delimiter).trim();
                if ("플레이어".equals(speaker) || "몬스터".equals(speaker)
                        || "PLAYER".equalsIgnoreCase(speaker) || "MONSTER".equalsIgnoreCase(speaker)) {
                    showStoryDialogue(speaker, text.substring(delimiter + 1).trim());
                    return;
                }
            }
        }
        feedbackUntil = System.currentTimeMillis() + 2500;
        status.setText("▶ " + text);
        status.setForeground(UniversityPixelTheme.CORAL);
        layoutStatusBanner();
    }
    public Board getPlayerBoard() { return playerBoard; }
    public Board getEnemyBoard() { return enemyBoard; }

    public void setState(BattleState state, String localId) {
        if (state == null || localId == null) return;
        ParticipantState local = state.getParticipant(localId);
        if (local == null) return;
        ParticipantState enemy = null;
        for (ParticipantState participant : state.getParticipants().values())
            if (!localId.equals(participant.getId())) { enemy = participant; break; }
        if (enemy == null) return;
        if (previousLocal != null && local.getHp() > previousLocal.getHp()) sound(AudioService.Event.HEAL);
        if (previousEnemy != null && enemy.getHp() > previousEnemy.getHp()) sound(AudioService.Event.HEAL);
        if (online) {
            onlineEffects(previousLocal, local, playerBoard, false);
            onlineEffects(previousEnemy, enemy, enemyBoard, true);
        }
        if (previousLocal != null && !previousLocal.isFeverActive() && local.isFeverActive()) {
            arena.showEffect(PixelArena.Effect.FEVER, 0); sound(AudioService.Event.FEVER);
        }
        previousLocal = local; previousEnemy = enemy;
        playerName.setText(local.getName());
        enemyName.setText(enemy.getName());
        playerBoard.setState(local.getGameState());
        enemyBoard.setState(enemy.getGameState());
        GameState enemyGame = enemy.getGameState();
        enemyStatus.setText("<html>LINES " + enemyGame.getLinesCleared()
                + "<br>COMBO " + Math.max(0, enemyGame.getCombo()) + "</html>");
        arena.update(local.getName(), local.getHp(), local.getMaxHp(),
                enemy.getName(), enemy.getHp(), enemy.getMaxHp());
        arena.setFever(local.isFeverActive());
        GameState game = local.getGameState();
        stats.setText("<html>LINES " + game.getLinesCleared() + "<br>COMBO " + Math.max(0, game.getCombo()) + "</html>");
        holdPreview.setPiece(game.getHoldPiece(), game.getHoldItemId() != null,
                game.getHoldOreCellIndex());
        pveHoldPreview.setPiece(game.getHoldPiece(), game.getHoldItemId() != null,
                game.getHoldOreCellIndex());
        for (int index = 0; index < nextPreviews.length; index++)
            nextPreviews[index].setPiece(index < game.getNextPieces().size()
                    ? game.getNextPieces().get(index) : PieceType.EMPTY, false);
        for (int index = 0; index < pveNextPreviews.length; index++)
            pveNextPreviews[index].setPiece(index < game.getNextPieces().size()
                    ? game.getNextPieces().get(index) : PieceType.EMPTY, false);
        gauge.setText(local.isFeverActive() ? String.format("FEVER %.1fs", local.getFeverRemainingMillis() / 1000.0)
                : "FEVER " + local.getFever() + "%");
        feverBar.setValue(local.isFeverActive() ? 100 : local.getFever());
        feverBar.setFill(local.isFeverActive() ? UniversityPixelTheme.MINT : UniversityPixelTheme.GOLD);
        pveFeverLabel.setText("FEVER");
        pveFeverPercent.setText(local.isFeverActive() ? "100%" : local.getFever() + "%");
        pveFeverBar.setValue(local.isFeverActive() ? 100 : local.getFever());
        pveFeverBar.setFill(local.isFeverActive() ? new Color(0xA0F777) : new Color(0x93EB70));
        warp.setText(local.getTimeWarpRemainingMillis() > 0
                ? String.format("시간 왜곡 %.1fs", local.getTimeWarpRemainingMillis() / 1000.0) : " ");
        int incoming = local.getPendingGarbageLines();
        garbage.setPending(incoming, local.getGarbageWaitRemainingMillis());
        pending.setText(incoming == 0 ? "가비지 0" : String.format("가비지 %d · %.1fs", incoming,
                local.getGarbageWaitRemainingMillis() / 1000.0));
        pending.setForeground(incoming == 0 ? UniversityPixelTheme.TEXT_SUB : UniversityPixelTheme.CORAL);
        List<String> items = local.getItems();
        List<Integer> charges = local.getItemCharges();
        for (int i = 0; i < slots.length; i++) {
            boolean locked = i >= local.getItemSlots();
            boolean available = !locked && i < items.size() && items.get(i) != null;
            String item = available ? items.get(i) : null;
            int count = available && i < charges.size() ? charges.get(i) : 1;
            boolean automatic = "damage_boost".equals(item) || "shield".equals(item);
            boolean waitingForEarlierSlot = item != null && items.indexOf(item) != i;
            slots[i].setText((i + 1) + " " + (locked ? "잠김" : item == null ? "빈 슬롯"
                    : ItemSpec.categoryOf(item) + " " + itemShortName(item)
                    + (count > 1 ? "×" + count : "")));
            automaticSlots[i] = automatic;
            slots[i].setBackground(available ? automatic ? UniversityPixelTheme.MINT
                    : UniversityPixelTheme.GOLD : UniversityPixelTheme.PANEL_LIGHT);
            slots[i].setToolTipText(locked ? "유틸형 캐릭터가 4번 슬롯을 사용할 수 있습니다."
                    : item == null ? "비어 있는 슬롯"
                    : itemName(item) + " [" + ItemSpec.categoryOf(item) + "] · " + count + "회 · "
                    + (automatic ? "조건이 맞으면 자동 사용"
                    : waitingForEarlierSlot ? "같은 아이템의 앞 슬롯부터 사용"
                    : "클릭 또는 " + (i + 1) + " 키"));
            slots[i].setEnabled(item != null
                    && !waitingForEarlierSlot
                    && state.getStatus() == BattleState.Status.RUNNING);
            // 실제 보유 아이템 ID를 기준으로 PNG를 연결한다. 빈 슬롯/잠금에는 아이콘 없음.
            ImageIcon icon = item == null ? null : ITEM_ICONS.get(item);
            pveSlots[i].setIcon(icon);
            pveSlots[i].setDisabledIcon(icon);
            pveSlots[i].putClientProperty("hudCharges", item == null ? 0 : Math.max(0, count));
            pveSlots[i].setText("");
            pveSlots[i].setToolTipText(slots[i].getToolTipText());
            pveSlots[i].setEnabled(slots[i].isEnabled());
        }
        clock.setText(String.format("%02d:%02d", state.getElapsedMillis() / 60000,
                state.getElapsedMillis() / 1000 % 60));
        if (System.currentTimeMillis() >= feedbackUntil) {
            status.setForeground(UniversityPixelTheme.TEXT);
            status.setText(state.getStatus() == BattleState.Status.PAUSED ? "일시정지 · P 키로 계속"
                    : "줄을 지우면 공격 · 여러 줄을 한 번에, 연속으로 지울수록 강해집니다 · ESC 포기");
            layoutStatusBanner();
        }
    }
    private void onlineEffects(ParticipantState previous, ParticipantState current, Board board, boolean monster) {
        if (previous == null) return;
        int lines = current.getGameState().getLinesCleared() - previous.getGameState().getLinesCleared();
        BoardState before = previous.getGameState().getBoard();
        BoardState after = current.getGameState().getBoard();
        // 구 서버는 출처 ID를 보내지 않는다. 4칸 정착 또는 줄 삭제도 착지로 인정한다.
        if (latestPlacedOrigin(after) > latestPlacedOrigin(before) || lines > 0
                || oldWirePlacement(before, after))
            arena.showPlacement(monster);
        if (lines > 0) {
            board.showLineClear(lines, current.getGameState().getCombo(), false);
            arena.showClear(lines, current.getGameState().getCombo(), false, monster);
            if (!monster) sound(AudioService.Event.LINE_CLEAR);
        }
        if (current.getHp() < previous.getHp()) {
            showHitAtImpact(board);
            (monster ? playerBoard : enemyBoard).showAttack();
            arena.showEffect(monster ? PixelArena.Effect.ATTACK : PixelArena.Effect.MONSTER_ATTACK,
                    previous.getHp() - current.getHp(), monster);
            soundAtImpact(monster ? AudioService.Event.ATTACK : AudioService.Event.HIT);
            setFeedback((monster ? "공격 적중  " : "피격  ")
                    + (previous.getHp() - current.getHp()) + " DAMAGE");
        }
    }
    private static long latestPlacedOrigin(BoardState board) {
        long latest = 0;
        for (int y = 0; y < board.getHeight(); y++)
            for (int x = 0; x < board.getWidth(); x++)
                if (board.getCell(x, y) != PieceType.EMPTY)
                    latest = Math.max(latest, board.getOriginId(x, y));
        return latest;
    }
    private static boolean oldWirePlacement(BoardState before, BoardState after) {
        if (latestPlacedOrigin(before) != 0 || latestPlacedOrigin(after) != 0) return false;
        int newlyFilled = 0, oldGarbage = 0, newGarbage = 0;
        for (int y = 0; y < before.getHeight(); y++) for (int x = 0; x < before.getWidth(); x++) {
            PieceType oldCell = before.getCell(x, y), newCell = after.getCell(x, y);
            if (oldCell == PieceType.GARBAGE) oldGarbage++;
            if (newCell == PieceType.GARBAGE) newGarbage++;
            if (oldCell == PieceType.EMPTY && newCell != PieceType.EMPTY
                    && newCell != PieceType.GARBAGE) newlyFilled++;
        }
        return newlyFilled == 4 && newGarbage <= oldGarbage;
    }
    private static String itemName(String id) {
        switch (id) {
            case "damage_boost": return "공격 증폭";
            case "garbage_bomb": return "가비지 폭탄";
            case "heal": return "회복";
            case "shield": return "보호막";
            case "line_cleaner": return "줄 정리";
            case "fever_charge": return "피버 충전";
            case "time_warp": return "시간 왜곡";
            case "nullify": return "무효화";
            default: return id;
        }
    }
    private static String itemShortName(String id) {
        switch (id) {
            case "damage_boost": return "증폭";
            case "garbage_bomb": return "폭탄";
            case "heal": return "회복";
            case "shield": return "방어";
            case "line_cleaner": return "정리";
            case "fever_charge": return "피버";
            case "time_warp": return "왜곡";
            case "nullify": return "무효화";
            default: return id;
        }
    }
}
