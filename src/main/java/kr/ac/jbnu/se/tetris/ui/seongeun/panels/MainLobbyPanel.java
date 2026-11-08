package kr.ac.jbnu.se.tetris.ui.seongeun.panels;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

import kr.ac.jbnu.se.tetris.ui.seongeun.components.GameArt;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.GameMenu;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.UniversityPixelTheme;

/**
 * 게임 타이틀 화면. 화면 전체에 한국 캠퍼스 야경을 깔고, 왼쪽에 로고와 세로 메뉴,
 * 오른쪽 아래에 다음 스토리 전투 카드를 둔다. ↑↓·Enter로도 고를 수 있다.
 */
public class MainLobbyPanel extends JPanel {
    private kr.ac.jbnu.se.tetris.app.BackgroundSkin backgroundSkin =
            kr.ac.jbnu.se.tetris.app.BackgroundSkin.DEFAULT;

    public void setBackgroundSkin(kr.ac.jbnu.se.tetris.app.BackgroundSkin skin) {
        backgroundSkin = skin == null ? kr.ac.jbnu.se.tetris.app.BackgroundSkin.DEFAULT : skin;
        nextCard.setBackgroundSkin(backgroundSkin);
        repaint();
    }
    private static final BufferedImage CAMPUS = loadCampus();
    private static final BufferedImage LOBBY_LOGO = loadLobbyLogo();
    private static final BufferedImage COIN_IMAGE = loadCoinImage();
    private static final BufferedImage PLAYER_MASCOT = loadOptionalImage("/ui/campus-rpg/poses/player-idle.png");
    // Transparent PNG assets supplied for the header.
    private static final BufferedImage STATUS_ONLINE_IMAGE = loadOptionalImage("/ui/campus-rpg/buttons/topbar_online.png");
    private static final BufferedImage STATUS_LOCAL_IMAGE = loadOptionalImage("/ui/campus-rpg/buttons/topbar_local.png");
    private static final BufferedImage PROFILE_IMAGE = loadOptionalImage("/ui/university/avatar_player.png");
    // Compute alpha bounds ONCE, not on every Swing repaint (40ms animation timer).
    private static final Rectangle STATUS_ONLINE_BOUNDS = visibleBounds(STATUS_ONLINE_IMAGE);
    private static final Rectangle STATUS_LOCAL_BOUNDS = visibleBounds(STATUS_LOCAL_IMAGE);
    private static final Rectangle PROFILE_BOUNDS = visibleBounds(PROFILE_IMAGE);
    // Optional: a wooden/parchment board without baked-in text or character artwork.
    private static final BufferedImage NEXT_STAGE_FRAME = loadOptionalImage("/ui/campus-rpg/cards/lobby_enemyBoard.png");
    private static final BufferedImage WINTER_NEXT_STAGE_FRAME = loadOptionalImage("/ui/campus-rpg/skins/winter/board_winter.png");
    // Stage preview art is loaded only once. Each uses its native 1672x941 aspect ratio.
    private static final BufferedImage UNIVERSITY_STAGE_IMAGE = loadOptionalImage("/ui/campus-rpg/university-battle-bg.png");
    private static final BufferedImage GRADUATION_STAGE_IMAGE = loadOptionalImage("/ui/campus-rpg/graduation-bg.png");
    private static final BufferedImage EMPLOYMENT_STAGE_IMAGE = loadOptionalImage("/ui/campus-rpg/employment-bg.png");
    private static final String SETTINGS_ICON_PATH = "/ui/campus-rpg/buttons/lobby_settings.png";
    private static final int SETTINGS_ICON_SIZE = 66;
    private static final String SIGNED_IN = "온라인 PvP 로그인 완료";
    private static final Color GOLD = UniversityPixelTheme.GOLD, MINT = UniversityPixelTheme.MINT;

    private final GameMenu menu = new GameMenu();
    private final GameMenu.Item storyButton = menu.add("스토리 시작", null, GOLD);
    private final GameMenu.Item onlineButton = menu.add("온라인 대전", null, MINT);
    private final GameMenu.Item localButton = menu.add("로컬 모드", null, MINT);
    private final GameMenu.Item tutorialButton = menu.add("튜토리얼", null, MINT);
    private final GameMenu.Item characterButton = menu.add("캐릭터 / 상점", null, GOLD);
    private final GameMenu.Item serverButton = menu.add("PvP 랭킹", null, MINT);
    private final JButton settingsButton = createSettingsIconButton();
    // Keep JLabel for compatibility with existing account-status UI tests.
    private final JLabel account = new ProfileBadge();
    private final PlayStatusBadge playStatus = new PlayStatusBadge();
    private final CoinBadge coins = new CoinBadge();
    private final Logo logo = new Logo();
    private final PlayerMascot mascot = new PlayerMascot();
    private final NextBattleCard nextCard = new NextBattleCard();
    private final Timer animation = new Timer(40, event -> {
        repaint();
        mascot.repaint();
    });

    public MainLobbyPanel() {
        setLayout(null);
        setBackground(UniversityPixelTheme.BG);
        storyButton.setName("lobbyStory");
        onlineButton.setName("lobbyOnline");
        localButton.setName("lobbyLocal");

        // Keep original GameMenu.Item buttons and their listeners; change only their rendering.
        storyButton.setImageResource("/ui/campus-rpg/buttons/lobby_story.png");
        onlineButton.setImageResource("/ui/campus-rpg/buttons/lobby_online.png");
        tutorialButton.setImageResource("/ui/campus-rpg/buttons/lobby_tutorial.png");
        characterButton.setImageResource("/ui/campus-rpg/buttons/lobby_shop.png");
        serverButton.setImageResource("/ui/campus-rpg/buttons/lobby_ranking.png");
        // Match the user's local-mode PNG; if missing, GameMenu keeps its drawn fallback.
        localButton.setImageResource("/ui/campus-rpg/buttons/lobby_localmode.png");
        settingsButton.setToolTipText("소리 · 온라인 계정 · 튜토리얼 설정");
        account.setFont(UniversityPixelTheme.lobbyFont(18, Font.BOLD));
        add(logo); add(menu); add(nextCard); add(mascot); add(coins); add(playStatus); add(account); add(settingsButton);
        nextCard.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { storyButton.doClick(); }
        });
        animation.setCoalesce(true);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) { animation.start(); logo.shownAt = System.currentTimeMillis(); menu.select(0); }
                else animation.stop();
            }
        });
        setAccountStatus("로컬 플레이");
    }

    /** Existing JButton text is preserved for headless UI tests; visually draw only the PNG. */
    private JButton createSettingsIconButton() {
        JButton button = new JButton("설정") {
            @Override protected void paintComponent(Graphics graphics) {
                Icon icon = getIcon();
                if (!(icon instanceof ImageIcon)) {
                    super.paintComponent(graphics);
                    return;
                }
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    int size = Math.max(1, Math.min(52, Math.min(getWidth(), getHeight()) - 4));
                    g.drawImage(((ImageIcon) icon).getImage(), (getWidth() - size) / 2,
                            (getHeight() - size) / 2, size, size, null);
                } finally { g.dispose(); }
            }
        };
        ImageIcon icon = loadScaledIcon(SETTINGS_ICON_PATH, SETTINGS_ICON_SIZE, SETTINGS_ICON_SIZE);
        if (icon != null) button.setIcon(icon);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setFocusPainted(false);
        button.setFocusable(false);
        button.setPreferredSize(new Dimension(SETTINGS_ICON_SIZE, SETTINGS_ICON_SIZE));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.getAccessibleContext().setAccessibleName("설정");
        return button;
    }

    /** 프로젝트 리소스 PNG를 한 번만 읽어 버튼 아이콘으로 사용한다. */
    private static ImageIcon loadScaledIcon(String path, int width, int height) {
        URL url = MainLobbyPanel.class.getResource(path);
        if (url == null) {
            System.err.println("Missing resource: " + path);
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(url);
            if (image == null) return null;
            Image scaled = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (IOException error) {
            System.err.println("Cannot load icon: " + path + " (" + error.getMessage() + ")");
            return null;
        }
    }

    /** Load PNG once, do not throw if the optional decorative resource has not been supplied. */
    private static BufferedImage loadOptionalImage(String path) {
        try (InputStream stream = MainLobbyPanel.class.getResourceAsStream(path)) {
            return stream == null ? null : ImageIO.read(stream);
        } catch (IOException | IllegalArgumentException error) {
            System.err.println("Cannot load lobby image " + path + ": " + error.getMessage());
            return null;
        }
    }

    /** The two supplied status skins already contain their own text and icons. */
    private static final class PlayStatusBadge extends JComponent {
        private boolean online;
        PlayStatusBadge() { setOpaque(false); setToolTipText("현재 플레이 상태"); }
        void setOnline(boolean online) { this.online = online; repaint(); }
        @Override public Dimension getPreferredSize() { return new Dimension(185, 68); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                BufferedImage image = online ? STATUS_ONLINE_IMAGE : STATUS_LOCAL_IMAGE;
                if (image != null) {
                    // The local skin in particular has large transparent margins.
                    Rectangle crop = online ? STATUS_ONLINE_BOUNDS : STATUS_LOCAL_BOUNDS;
                    if (crop != null) {
                        double scale = Math.min(getWidth() / (double) crop.width,
                                getHeight() / (double) crop.height);
                        int dw = Math.max(1, (int) Math.round(crop.width * scale));
                        int dh = Math.max(1, (int) Math.round(crop.height * scale));
                        int x = (getWidth() - dw) / 2, y = (getHeight() - dh) / 2;
                        g.drawImage(image, x, y, x + dw, y + dh,
                                crop.x, crop.y, crop.x + crop.width, crop.y + crop.height, null);
                        return;
                    }
                }
                // Missing PNG fallback is still readable and dynamic.
                int w = getWidth(), h = getHeight();
                g.setColor(new Color(0x302521));
                g.fillRoundRect(1, 2, Math.max(1, w - 2), Math.max(1, h - 4), h - 4, h - 4);
                g.setColor(online ? new Color(0x258650) : new Color(0xE9DDF9));
                g.fillRoundRect(4, 5, Math.max(1, w - 8), Math.max(1, h - 10), h - 10, h - 10);
                g.setFont(UniversityPixelTheme.lobbyFont(Math.max(12, Math.min(17, h / 3)), Font.BOLD));
                g.setColor(online ? Color.WHITE : UniversityPixelTheme.TEXT);
                String label = online ? "온라인 플레이" : "로컬 플레이";
                FontMetrics fm = g.getFontMetrics();
                g.drawString(label, (w - fm.stringWidth(label)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            } finally { g.dispose(); }
        }
    }

    /** Connected top-right profile card. The header background is drawn by the lobby. */
    private static final class ProfileBadge extends JLabel {
        ProfileBadge() {
            super("Player");
            setOpaque(false);
            setFont(UniversityPixelTheme.lobbyFont(18, Font.BOLD));
            setToolTipText("플레이어 정보");
        }

        @Override public Dimension getPreferredSize() { return new Dimension(226, 68); }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                int w = getWidth(), h = getHeight();
                if (w <= 0 || h <= 0) return;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int avatarSize = Math.min(65, Math.max(24, h - 4));
                int avX = 3, avY = (h - avatarSize) / 2;
                g.setColor(new Color(0x3D2E26));
                g.fillRoundRect(avX, avY, avatarSize, avatarSize, 13, 13);
                g.setColor(new Color(0x65ADF0));
                g.fillRoundRect(avX + 2, avY + 2, avatarSize - 4, avatarSize - 4, 10, 10);
                if (PROFILE_IMAGE != null && PROFILE_BOUNDS != null) {
                    Shape previousClip = g.getClip();
                    g.clip(new java.awt.geom.RoundRectangle2D.Float(avX + 2, avY + 2,
                            avatarSize - 4, avatarSize - 4, 10, 10));
                    g.drawImage(PROFILE_IMAGE, avX + 2, avY + 2,
                            avX + avatarSize - 2, avY + avatarSize - 2,
                            PROFILE_BOUNDS.x, PROFILE_BOUNDS.y,
                            PROFILE_BOUNDS.x + PROFILE_BOUNDS.width,
                            PROFILE_BOUNDS.y + PROFILE_BOUNDS.height, null);
                    g.setClip(previousClip);
                }

                String display = getText();
                if (display == null || display.trim().isEmpty()) display = "Player";
                int textLeft = avX + avatarSize + 9;
                int available = Math.max(0, w - textLeft - 5);
                g.setFont(UniversityPixelTheme.lobbyFont(Math.max(13, Math.min(20, h / 3)), Font.BOLD));
                FontMetrics fm = g.getFontMetrics();
                display = NextBattleCard.fitText(fm, display, available);
                g.setColor(new Color(0x2E2119));
                g.drawString(display, textLeft, (h - fm.getHeight()) / 2 + fm.getAscent());
            } finally { g.dispose(); }
        }

        @Override protected void paintBorder(Graphics graphics) {
            // The shared rounded outer frame is painted behind the avatar and settings.
        }
    }

    /** Draw sprites without transparent padding; preserve original aspect ratio. */
    private static Rectangle visibleBounds(BufferedImage image) {
        if (image == null) return null;
        int l = image.getWidth(), t = image.getHeight(), r = -1, b = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) <= 20) continue;
                l = Math.min(l, x); t = Math.min(t, y);
                r = Math.max(r, x); b = Math.max(b, y);
            }
        }
        return r < l ? null : new Rectangle(l, t, r - l + 1, b - t + 1);
    }

    /** 코인 PNG가 왼쪽 둥근 끝을 덮는 캡슐형 배지. */
    public static final class CoinBadge extends JComponent {
        private static final int BADGE_HEIGHT = 52;
        private static final int ICON_SIZE = 50;
        private static final int BODY_HEIGHT = 44;
        private static final int TEXT_LEFT = 59;
        private static final int TEXT_RIGHT_PADDING = 11;
        private static final int MIN_WIDTH = 168;
        // 원본 PNG의 넓은 투명 여백을 무시하고 실제 코인 그림만 확대한다.
        private static final int[] COIN_CROP = findCoinCrop();

        private int value;

        public CoinBadge() {
            setOpaque(false);
            setFont(UniversityPixelTheme.lobbyFont(24, Font.BOLD));
            setToolTipText("보유 코인");
        }

        public void setValue(int amount) {
            value = amount;
            revalidate();
            repaint();
        }

        /** 이미지 자체에 포함된 투명 여백을 제거할 잘라내기 좌표를 한 번만 계산. */
        private static int[] findCoinCrop() {
            if (COIN_IMAGE == null) return null;
            int left = COIN_IMAGE.getWidth();
            int top = COIN_IMAGE.getHeight();
            int right = -1;
            int bottom = -1;
            for (int y = 0; y < COIN_IMAGE.getHeight(); y++) {
                for (int x = 0; x < COIN_IMAGE.getWidth(); x++) {
                    int alpha = (COIN_IMAGE.getRGB(x, y) >>> 24) & 0xff;
                    if (alpha > 24) {
                        left = Math.min(left, x);
                        top = Math.min(top, y);
                        right = Math.max(right, x);
                        bottom = Math.max(bottom, y);
                    }
                }
            }
            return right >= left && bottom >= top
                    ? new int[] {left, top, right + 1, bottom + 1} : null;
        }

        @Override
        public Dimension getPreferredSize() {
            String text = String.format(java.util.Locale.ROOT, "%,d", value);
            FontMetrics fm = getFontMetrics(UniversityPixelTheme.lobbyFont(24, Font.BOLD));
            int requiredWidth = TEXT_LEFT + fm.stringWidth(text) + TEXT_RIGHT_PADDING;
            return new Dimension(Math.max(MIN_WIDTH, requiredWidth), BADGE_HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

                int w = getWidth();
                int h = getHeight();
                if (w <= 0 || h <= 0) return;

                // 배지의 둥근 왼쪽 끝은 코인 이미지 뒤로 숨겨진다.
                int iconSize = Math.min(ICON_SIZE, h);
                int iconY = (h - iconSize) / 2;
                int bodyX = iconSize / 2 - 1;
                int bodyH = Math.min(BODY_HEIGHT, h - 4);
                int bodyY = (h - bodyH) / 2;
                int bodyW = Math.max(1, w - bodyX - 3);

                // 부드러운 입체 그림자, 진한 외곽선, 진짜 알약 형태 배경.
                g.setColor(new Color(33, 37, 27, 105));
                g.fillRoundRect(bodyX + 1, bodyY + 3, bodyW, bodyH, bodyH, bodyH);
                g.setColor(new Color(54, 42, 31));
                g.fillRoundRect(bodyX, bodyY, bodyW, bodyH, bodyH, bodyH);
                g.setPaint(new GradientPaint(bodyX, bodyY + 2, new Color(107, 127, 78),
                        bodyX, bodyY + bodyH - 2, new Color(66, 82, 55)));
                g.fillRoundRect(bodyX + 2, bodyY + 2, Math.max(1, bodyW - 4), bodyH - 4,
                        bodyH - 4, bodyH - 4);
                g.setColor(new Color(207, 229, 172, 90));
                g.drawRoundRect(bodyX + 4, bodyY + 4, Math.max(1, bodyW - 8), bodyH - 8,
                        bodyH - 8, bodyH - 8);

                // 아이콘을 배경보다 나중에 그려 겹치는 부분을 깔끔하게 덮는다.
                if (COIN_IMAGE != null && COIN_CROP != null) {
                    g.drawImage(COIN_IMAGE, 0, iconY, iconSize, iconY + iconSize,
                            COIN_CROP[0], COIN_CROP[1], COIN_CROP[2], COIN_CROP[3], null);
                } else {
                    g.setColor(new Color(255, 207, 58));
                    g.fillOval(2, iconY + 2, iconSize - 4, iconSize - 4);
                    g.setColor(new Color(80, 44, 24));
                    g.drawOval(2, iconY + 2, iconSize - 5, iconSize - 5);
                }

                // Force Jalnan on EVERY repaint: UniversityPixelTheme.apply() can replace
                // the component's stored font with NanumSquareRound.
                String text = String.format(java.util.Locale.ROOT, "%,d", value);
                g.setFont(UniversityPixelTheme.lobbyFont(24, Font.BOLD));
                FontMetrics fm = g.getFontMetrics();
                // Center inside the visible capsule to the right of the overlapping coin.
                int usableLeft = Math.max(TEXT_LEFT, iconSize + 5);
                int usableRight = Math.max(usableLeft + 1, w - TEXT_RIGHT_PADDING);
                int textX = usableLeft + (usableRight - usableLeft - fm.stringWidth(text)) / 2;
                int textY = (h - fm.getHeight()) / 2 + fm.getAscent();

                java.awt.font.TextLayout layout = new java.awt.font.TextLayout(
                        text, g.getFont(), g.getFontRenderContext());
                Shape glyphs = layout.getOutline(java.awt.geom.AffineTransform.getTranslateInstance(textX, textY));
                g.setColor(new Color(25, 29, 21, 165));
                g.translate(1, 2);
                g.fill(glyphs);
                g.translate(-1, -2);
                g.setStroke(new BasicStroke(1.05f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(31, 40, 25, 225));
                g.draw(glyphs);
                g.setColor(new Color(0xFFF9E9));
                g.fill(glyphs);
            } finally {
                g.dispose();
            }
        }
    }

    @Override public void doLayout() {
        int w = getWidth(), h = getHeight();
        int pad = UniversityPixelTheme.GUTTER;

        // Top bar, right to left: settings / player / online-local badge / currency.
        // Shrink as a group on narrow windows, keeping all badges aligned.
        float topScale = Math.min(1f, Math.max(0.70f, (w - 310f) / 790f));
        int gap = Math.max(4, Math.round(8 * topScale));
        int y = 15;
        int settingsW = Math.round(68 * topScale);
        int profileW = Math.round(226 * topScale);
        int statusW = Math.round(185 * topScale);
        int badgeH = Math.round(68 * topScale);
        Dimension coinPref = coins.getPreferredSize();
        int coinW = Math.round(coinPref.width * topScale);
        int right = w - pad;

        settingsButton.setBounds(right - settingsW, y, settingsW, badgeH);
        right -= settingsW;
        account.setBounds(right - profileW, y, profileW, badgeH);
        right -= profileW + gap;
        playStatus.setBounds(right - statusW, y, statusW, badgeH);
        right -= statusW + gap;
        coins.setBounds(right - coinW, y, coinW, badgeH);

        // The logo keeps its own aspect ratio; reserve enough height for six tall buttons.
        int leftZone = Math.max(300, Math.min(440, Math.round(w * 0.38f)));
        int logoHeight = Math.max(160, Math.min(240, Math.round(h * 0.26f)));
        int logoWidth = Math.min(leftZone, Math.round(logoHeight * (1448f / 1086f)));
        logo.setBounds(pad + (leftZone - logoWidth) / 2, 14, logoWidth, logoHeight);

        int menuWidth = Math.max(280, Math.min(335, Math.round(w * 0.29f)));
        int menuX = pad + (leftZone - menuWidth) / 2;
        int menuY = logo.getY() + logoHeight + 6;
        int itemGap = 4; // The GameMenu GridLayout currently uses a 4px vertical gap.
        int room = Math.max(1, h - menuY - 34);
        int itemHeight = Math.max(48, Math.min(90, (room - itemGap * 5) / 6));
        int menuHeight = itemHeight * 6 + itemGap * 5;
        menu.setBounds(menuX, menuY, menuWidth, menuHeight);

        // The user's illustrated board is square (1254x1254). Never stretch it into a tall card.
        // Enlarged square board: the decorative PNG has generous transparent margins.
        int boardSize = Math.max(275, Math.min(480, Math.round(w * 0.40f)));
        // Keep the square board below the top bar while leaving room for the student.
        boardSize = Math.min(boardSize, Math.max(240, Math.round(h * 0.64f)));
        nextCard.setBounds(w - pad - boardSize, h - 20 - boardSize, boardSize, boardSize);

        // Use the existing full-body player artwork as a small lobby mascot.
        // Hide it when there is not enough room between menu and notice board.
        int mascotLeft = menuX + menuWidth + 6;
        int mascotRight = nextCard.getX() + Math.round(boardSize * 0.12f);
        int free = mascotRight - mascotLeft;
        int mascotSize = Math.min(220, free - 18);
        if (PLAYER_MASCOT == null || mascotSize < 105) {
            mascot.setVisible(false);
            mascot.setBounds(0, 0, 0, 0);
        } else {
            mascot.setVisible(true);
            int mascotX = mascotLeft + Math.max(0, (free - mascotSize) / 2);
            int mascotY = Math.min(h - mascotSize - 48, Math.round(h * 0.59f));
            mascot.setBounds(mascotX, Math.max(90, mascotY), mascotSize, mascotSize);
        }
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics); // Clear the previous frame, including positions used before a resize.
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            int w = getWidth(), h = getHeight();
            g.setPaint(new GradientPaint(0, 0, new Color(0xD9DBD2), 0, h, UniversityPixelTheme.BG));
            g.fillRect(0, 0, w, h);
            BufferedImage selectedBackground = kr.ac.jbnu.se.tetris.ui.seongeun.components.BackgroundSkinAssets
                    .image(backgroundSkin, "lobby");
            BufferedImage lobbyArt = selectedBackground != null ? selectedBackground : CAMPUS;
            if (lobbyArt != null) {
                int artWidth = Math.max(w, lobbyArt.getWidth() * h / lobbyArt.getHeight());
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(lobbyArt, (w - artWidth) / 2, 0, artWidth, h, null);
            }
            // 배경은 공간감을 주되 메뉴와 제목 뒤에서는 연한 콘크리트색으로 물러난다.
            g.setPaint(new GradientPaint(0, 0,
                    backgroundSkin == kr.ac.jbnu.se.tetris.app.BackgroundSkin.WINTER
                            ? new Color(238, 242, 251, 115) : new Color(228, 229, 220, 205),
                    Math.round(w * 0.67f), 0, new Color(228, 229, 220, 18)));
            g.fillRect(0, 0, w, h);
            g.setPaint(new GradientPaint(0, h - 110, new Color(220, 222, 212, 0),
                    0, h, new Color(220, 222, 212, 184)));
            g.fillRect(0, h - 110, w, 110);
            // One connected profile/settings card, matching the reference top bar.
            if (account.getWidth() > 0 && settingsButton.getWidth() > 0) {
                int x = account.getX(), y = account.getY();
                int right = settingsButton.getX() + settingsButton.getWidth();
                int bw = Math.max(1, right - x), bh = Math.max(1, account.getHeight());
                g.setColor(new Color(0x30231F));
                g.fillRoundRect(x, y, bw, bh, 20, 20);
                g.setColor(new Color(0xFFFAF1));
                g.fillRoundRect(x + 3, y + 3, Math.max(1, bw - 6),
                        Math.max(1, bh - 6), 16, 16);
            }
        } finally { g.dispose(); }
    }

    /** PNG logo replaces the old pixel blocks, TETRIS MONSTER, and CAMPUS QUEST lettering. */
    private static final class Logo extends JComponent {
        long shownAt;

        @Override protected void paintComponent(Graphics graphics) {
            if (LOBBY_LOGO == null || getWidth() <= 0 || getHeight() <= 0) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                long age = shownAt == 0 ? 1000 : Math.max(0, System.currentTimeMillis() - shownAt);
                float progress = Math.min(1.0f, age / 350f);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, progress));

                // Fit inside the header without cropping or stretching the transparent PNG.
                double scale = Math.min(getWidth() / (double) LOBBY_LOGO.getWidth(),
                        getHeight() / (double) LOBBY_LOGO.getHeight());
                int dw = Math.max(1, (int) Math.round(LOBBY_LOGO.getWidth() * scale));
                int dh = Math.max(1, (int) Math.round(LOBBY_LOGO.getHeight() * scale));
                int x = (getWidth() - dw) / 2;
                int y = (getHeight() - dh) / 2 + Math.round((1 - progress) * -10);
                g.drawImage(LOBBY_LOGO, x, y, dw, dh, null);
            } finally { g.dispose(); }
        }
    }

    /** Existing student pose: decoration only, never intercepts menu or board clicks. */
    private static final class PlayerMascot extends JComponent {
        PlayerMascot() { setOpaque(false); setEnabled(false); }
        @Override protected void paintComponent(Graphics graphics) {
            if (PLAYER_MASCOT == null || getWidth() < 1 || getHeight() < 1) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                // Subtle idle animation: a 4px bob plus 1.2-degree sway (no sprite sheet needed).
                double phase = System.currentTimeMillis() / 620.0;
                double bob = Math.sin(phase) * 4.0;
                double tilt = Math.sin(phase * 0.58) * Math.toRadians(1.2);
                int margin = Math.max(5, Math.round(Math.min(getWidth(), getHeight()) * 0.04f));
                int artWidth = Math.max(1, getWidth() - margin * 2);
                int artHeight = Math.max(1, getHeight() - margin * 2 - 5);
                g.rotate(tilt, getWidth() / 2.0, getHeight() - margin);
                g.drawImage(PLAYER_MASCOT, margin, margin + (int) Math.round(bob),
                        artWidth, artHeight, null);
            } finally { g.dispose(); }
        }
    }

    /** Dynamic stage information painted over the common wooden PNG. */
    private static final class NextBattleCard extends JComponent {
        String eyebrow = "NEXT BATTLE", title = "술", detail = "LV 1 · 일반 · 대학교 과정";
        String artKey = GameArt.keyForLevel(1);
        int cleared, total = 9;
        boolean boss;
        private BufferedImage cachedSprite;
        private Rectangle cachedSpriteBounds;
        private String cachedKey;
        private kr.ac.jbnu.se.tetris.app.BackgroundSkin backgroundSkin =
                kr.ac.jbnu.se.tetris.app.BackgroundSkin.DEFAULT;

        void setBackgroundSkin(kr.ac.jbnu.se.tetris.app.BackgroundSkin skin) {
            backgroundSkin = skin == null ? kr.ac.jbnu.se.tetris.app.BackgroundSkin.DEFAULT : skin;
            repaint();
        }

        NextBattleCard() {
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("다음 스토리 전투");
        }

        @Override protected void paintComponent(Graphics graphics) {
            if (getWidth() <= 0 || getHeight() <= 0) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                int side = Math.min(getWidth(), getHeight());
                g.translate((getWidth() - side) / 2, (getHeight() - side) / 2);
                g.scale(side / 400.0, side / 400.0);

                BufferedImage boardFrame = backgroundSkin == kr.ac.jbnu.se.tetris.app.BackgroundSkin.WINTER
                        && WINTER_NEXT_STAGE_FRAME != null ? WINTER_NEXT_STAGE_FRAME : NEXT_STAGE_FRAME;
                if (boardFrame != null) {
                    g.drawImage(boardFrame, 0, 0, 400, 400, null);
                } else {
                    g.setColor(new Color(0x725032));
                    g.fillRoundRect(23, 36, 354, 350, 25, 25);
                    g.setColor(new Color(0xFFF8E8));
                    g.fillRoundRect(65, 80, 270, 262, 18, 18);
                }

                final int innerX = 80, innerWidth = 240;
                // A single stage art is selected from the live chapter name.
                String raw = detail == null ? "" : detail;
                int separator = raw.lastIndexOf('·');
                String chapter = separator < 0 ? raw : raw.substring(separator + 1).trim();
                String levelTier = separator < 0 ? "" : raw.substring(0, separator).trim();
                if (chapter.isEmpty()) chapter = "다음 스테이지";

                g.setColor(new Color(0x35291F));
                g.setFont(UniversityPixelTheme.lobbyFont(14, Font.BOLD));
                g.drawString(fitText(g.getFontMetrics(), eyebrow, innerWidth), innerX, 99);
                g.setFont(UniversityPixelTheme.lobbyFont(21, Font.BOLD));
                g.drawString(fitText(g.getFontMetrics(), chapter, innerWidth), innerX, 129);

                // A cover-fit preview fills the complete viewport. A differing image ratio
                // is center-cropped rather than letterboxed or stretched.
                final int previewX = innerX, previewY = 137, previewW = innerWidth, previewH = 114;
                paintStagePreview(g, stagePreviewFor(chapter), previewX, previewY, previewW, previewH);

                g.setColor(new Color(0x70543B));
                g.setFont(UniversityPixelTheme.lobbyFont(11, Font.PLAIN));
                g.drawString(fitText(g.getFontMetrics(), levelTier, innerWidth), innerX, 268);

                // Keep ENEMY/BOSS and the real monster name on ONE line.
                String role = boss ? "BOSS" : "ENEMY";
                g.setFont(UniversityPixelTheme.lobbyFont(15, Font.BOLD));
                FontMetrics roleFm = g.getFontMetrics();
                int labelY = 289;
                g.setColor(new Color(0x413025));
                g.drawString(role, innerX, labelY);
                int nameX = innerX + roleFm.stringWidth(role) + 10;
                int nameSpace = Math.max(35, 211 - nameX);
                String bossText = title == null ? "" : title;
                int bossFontSize = 19;
                Font bossFont = UniversityPixelTheme.lobbyFont(bossFontSize, Font.BOLD);
                while (bossFontSize > 13 && g.getFontMetrics(bossFont).stringWidth(bossText) > nameSpace) {
                    bossFont = UniversityPixelTheme.lobbyFont(--bossFontSize, Font.BOLD);
                }
                g.setFont(bossFont);
                g.drawString(fitText(g.getFontMetrics(), bossText, nameSpace), nameX, labelY);

                // Restore the project's REAL monster art. Its alpha-only margins are
                // cropped once and the visible character is enlarged in the bottom-right.
                if (cachedKey == null || !cachedKey.equals(artKey)) {
                    cachedKey = artKey;
                    cachedSprite = GameArt.sprite(artKey);
                    cachedSpriteBounds = visibleBounds(cachedSprite);
                }
                if (cachedSprite != null && cachedSpriteBounds != null) {
                    Rectangle crop = cachedSpriteBounds;
                    final int boxX = 203, boxY = 218, boxW = 134;
                    final int boxH = backgroundSkin == kr.ac.jbnu.se.tetris.app.BackgroundSkin.WINTER ? 93 : 104;
                    double scale = Math.min(boxW / (double) crop.width,
                            boxH / (double) crop.height);
                    int dw = Math.max(1, (int) Math.round(crop.width * scale));
                    int dh = Math.max(1, (int) Math.round(crop.height * scale));
                    int dx = boxX + (boxW - dw) / 2;
                    int dy = boxY + boxH - dh;
                    g.drawImage(cachedSprite, dx, dy, dx + dw, dy + dh,
                            crop.x, crop.y, crop.x + crop.width,
                            crop.y + crop.height, null);
                }

                // Move progression UP to separate it from the wooden frame below.
                g.setColor(new Color(0x413025));
                g.setFont(UniversityPixelTheme.lobbyFont(12, Font.BOLD));
                int progressY = backgroundSkin == kr.ac.jbnu.se.tetris.app.BackgroundSkin.WINTER ? 307 : 318;
                g.drawString("진행도 " + cleared + " / " + total, innerX, progressY);
                final int barY = backgroundSkin == kr.ac.jbnu.se.tetris.app.BackgroundSkin.WINTER ? 318 : 331;
                final int barH = 8;
                g.setColor(new Color(0xD4C0A2));
                g.fillRoundRect(innerX, barY, innerWidth, barH, 8, 8);
                int amount = (int) Math.round(innerWidth * Math.max(0.0,
                        Math.min(1.0, cleared / (double) Math.max(1, total))));
                if (amount > 0) {
                    g.setColor(new Color(0x35D46A));
                    g.fillRoundRect(innerX, barY, amount, barH, 8, 8);
                }
                g.setColor(new Color(0x8E7656));
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(innerX, barY, innerWidth, barH, 8, 8);
            } finally { g.dispose(); }
        }

        /** Resolve the story chapter using actual stage labels, never the lobby wallpaper. */
        private BufferedImage stagePreviewFor(String chapter) {
            if (backgroundSkin == kr.ac.jbnu.se.tetris.app.BackgroundSkin.WINTER) {
                String scene = chapter != null && chapter.contains("졸업") ? "graduation"
                        : chapter != null && chapter.contains("취업") ? "employment" : "university";
                BufferedImage winter = kr.ac.jbnu.se.tetris.ui.seongeun.components.BackgroundSkinAssets
                        .image(backgroundSkin, scene);
                if (winter != null) return winter;
            }
            if (chapter != null) {
                if (chapter.contains("졸업")) return GRADUATION_STAGE_IMAGE;
                if (chapter.contains("취업")) return EMPLOYMENT_STAGE_IMAGE;
            }
            return UNIVERSITY_STAGE_IMAGE;
        }

        /** Edge-to-edge stage art using a centered COVER crop (no blank bands). */
        private static void paintStagePreview(Graphics2D g, BufferedImage art,
                int x, int y, int w, int h) {
            Shape oldClip = g.getClip();
            g.clip(new java.awt.geom.RoundRectangle2D.Float(x, y, w, h, 10, 10));
            g.setColor(new Color(0xE5D9C5));
            g.fillRect(x, y, w, h);
            if (art != null && art.getWidth() > 0 && art.getHeight() > 0) {
                int srcW = art.getWidth(), srcH = art.getHeight();
                double aspect = w / (double) h;
                int cropW = srcW, cropH = srcH;
                if (srcW / (double) srcH > aspect) {
                    cropW = Math.max(1, (int) Math.round(srcH * aspect));
                } else {
                    cropH = Math.max(1, (int) Math.round(srcW / aspect));
                }
                int srcX = (srcW - cropW) / 2;
                int srcY = (srcH - cropH) / 2;
                g.drawImage(art, x, y, x + w, y + h,
                        srcX, srcY, srcX + cropW, srcY + cropH, null);
            }
            g.setClip(oldClip);
            g.setColor(new Color(0x926B48));
            g.setStroke(new BasicStroke(1.4f));
            g.drawRoundRect(x, y, w, h, 10, 10);
        }

        private static String fitText(FontMetrics fm, String value, int availableWidth) {
            String text = value == null ? "" : value;
            if (availableWidth < 20) return "";
            if (fm.stringWidth(text) <= availableWidth) return text;
            while (text.length() > 1 && fm.stringWidth(text + "…") > availableWidth) {
                text = text.substring(0, text.length() - 1);
            }
            return text + "…";
        }
    }

    private static BufferedImage loadLobbyLogo() {
        String path = "/ui/campus-rpg/tetris-monster-logo.png";
        try (InputStream stream = MainLobbyPanel.class.getResourceAsStream(path)) {
            if (stream == null) {
                System.err.println("Missing lobby logo image: " + path);
                return null;
            }
            return ImageIO.read(stream);
        } catch (IOException error) {
            System.err.println("Cannot load lobby logo: " + error.getMessage());
            return null;
        }
    }

    private static BufferedImage loadCoinImage() {
        String path = "/ui/campus-rpg/icons/lobby_coin.png";
        try (InputStream stream = MainLobbyPanel.class.getResourceAsStream(path)) {
            if (stream == null) {
                System.err.println("Missing coin image: " + path);
                return null;
            }
            return ImageIO.read(stream);
        } catch (IOException error) {
            System.err.println("Cannot load coin image: " + error.getMessage());
            return null;
        }
    }

    private static BufferedImage loadCampus() {
        try {
            return ImageIO.read(MainLobbyPanel.class.getResource("/ui/campus-rpg/university-bg.png"));
        } catch (IOException | IllegalArgumentException unavailable) {
            return null;
        }
    }

    public void setStoryAction(ActionListener listener) { storyButton.addActionListener(listener); }
    public void setOnlineBattleAction(ActionListener listener) { onlineButton.addActionListener(listener); }
    public void setLocalModeAction(ActionListener listener) { localButton.addActionListener(listener); }
    public void setCharacterAction(ActionListener listener) { characterButton.addActionListener(listener); }
    public void setTutorialAction(ActionListener listener) { tutorialButton.addActionListener(listener); }
    public void setServerAction(ActionListener listener) { serverButton.addActionListener(listener); }
    public void setSettingsAction(ActionListener listener) { settingsButton.addActionListener(listener); }
    public void setAccountStatus(String text) {
        boolean signedIn = text != null && text.startsWith(SIGNED_IN);
        String nickname = signedIn ? text.substring(SIGNED_IN.length())
                .replaceFirst("^\\s*[·:|]\\s*", "").trim() : "";
        if (nickname.isEmpty()) nickname = "Player";
        // Do not display local-play status again inside the profile: the PNG badge owns it.
        account.setText(nickname);
        account.setToolTipText(signedIn ? text : "게스트 플레이어 (Player)");
        account.setOpaque(false);
        playStatus.setOnline(signedIn);
        playStatus.setToolTipText(signedIn ? "온라인 플레이" : "로컬 플레이");
        // The reference has clean PNG buttons without badges spilling outside their edges.
        onlineButton.setBadge(null, UniversityPixelTheme.PANEL_LIGHT);
        serverButton.setBadge(null, UniversityPixelTheme.PANEL_LIGHT);
        onlineButton.setToolTipText(signedIn ? "온라인 대전" : "로그인 후 이용할 수 있습니다");
        serverButton.setToolTipText(signedIn ? "PvP 랭킹" : "로그인 후 이용할 수 있습니다");
        revalidate();
        repaint();
    }

    /** 스토리 진행도를 메뉴 배지와 다음 전투 카드에 보여 준다. */
    public void setStoryProgress(int cleared, int total) {
        storyButton.setText(cleared == 0 ? "스토리 시작" : cleared >= total ? "다시 플레이" : "이어하기");
        // Do not overlay floating progress badges on baked-in PNG artwork.
        String progress = cleared + " / " + total;
        storyButton.setBadge(null, GOLD);
        storyButton.setToolTipText(storyButton.getText() + " · " + progress);
        nextCard.cleared = cleared; nextCard.total = total;
        repaint();
    }

    /** 다음 도전 전투. 모두 깼다면 마지막 보스를 다시 보여 준다. */
    public void setNextBattle(int level, String monsterName, String tierName, String chapterName, boolean boss, boolean allCleared) {
        nextCard.eyebrow = allCleared ? "ALL CLEAR · 다시 도전" : "NEXT STAGE";
        nextCard.title = monsterName;
        nextCard.detail = "LV " + level + " · " + tierName + " · " + chapterName;
        nextCard.artKey = GameArt.keyForLevel(level);
        nextCard.boss = boss;
        repaint();
    }

    public void setCoins(int amount) {
        coins.setValue(amount);
        revalidate();
        repaint();
    }
}
