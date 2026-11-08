package kr.ac.jbnu.se.tetris.ui.seongeun.panels;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import kr.ac.jbnu.se.tetris.character.*;
import kr.ac.jbnu.se.tetris.app.BackgroundSkin;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.BackgroundSkinAssets;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.PixelButton;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.CampusBackButton;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.CampusArt;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.UniversityPixelTheme;

/** 획득한 재화로 전투 캐릭터를 구입하고 선택하는 로컬 화면. */
public final class CharacterShopPanel extends kr.ac.jbnu.se.tetris.ui.seongeun.components.ScenePanel {
    private final CharacterCatalog catalog = CharacterCatalog.loadDefault();
    private final MainLobbyPanel.CoinBadge wallet = new MainLobbyPanel.CoinBadge();
    private final PixelButton back = new CampusBackButton("돌아가기");
    private final Map<String, JPanel> cards = new LinkedHashMap<String, JPanel>();
    private final Map<String, JLabel> badges = new LinkedHashMap<String, JLabel>();
    private final Map<String, JButton> actions = new LinkedHashMap<String, JButton>();
    private Consumer<String> selection;
    private Consumer<BackgroundSkin> skinSelection;
    private final Map<BackgroundSkin, PixelButton> skinActions = new LinkedHashMap<BackgroundSkin, PixelButton>();
    private final Map<BackgroundSkin, JLabel> skinBadges = new LinkedHashMap<BackgroundSkin, JLabel>();

    public CharacterShopPanel() {
        setLayout(new BorderLayout(0, 14));
        setBackground(UniversityPixelTheme.BG);
        setBorder(new EmptyBorder(22, 35, 18, 35));

        back.secondary();
        back.setPreferredSize(new Dimension(190, 56));
        // Use the exact same capsule, coin artwork and currency typeface as the lobby.
        wallet.setValue(0);
        add(UniversityPixelTheme.screenHeader("CHARACTER SHOP", "캐릭터 / 상점", wallet, back), BorderLayout.NORTH);

        JPanel cards = new JPanel(new GridLayout(2, 2, 17, 17));
        cards.setOpaque(false);
        for (CharacterSpec spec : catalog.all()) cards.add(card(spec));
        // 카드가 화면 높이만큼 늘어나지 않도록 위쪽에 붙여 내용 크기만 쓰게 한다.
        JPanel holder = new JPanel(new BorderLayout()); holder.setOpaque(false);
        JPanel sections = new JPanel(); sections.setOpaque(false);
        sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));
        cards.setAlignmentX(Component.LEFT_ALIGNMENT);
        sections.add(cards);
        sections.add(Box.createVerticalStrut(24));
        JLabel skinsTitle = UniversityPixelTheme.label("배경 스킨 · 로비 / 스토리 전투", 23, Font.BOLD, UniversityPixelTheme.TEXT);
        skinsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sections.add(skinsTitle);
        sections.add(Box.createVerticalStrut(12));
        JPanel skins = new JPanel(new GridLayout(1, 2, 17, 17)); skins.setOpaque(false);
        skins.add(backgroundCard(BackgroundSkin.DEFAULT));
        skins.add(backgroundCard(BackgroundSkin.WINTER));
        skins.setAlignmentX(Component.LEFT_ALIGNMENT);
        sections.add(skins);
        holder.add(sections, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(null); scroll.setOpaque(false); scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        add(UniversityPixelTheme.label("코인은 스토리 전투를 처음 이길 때 받습니다 · 일반 30 · 엘리트 50 · 보스 80",
                12, Font.PLAIN, UniversityPixelTheme.TEXT_SUB), BorderLayout.SOUTH);
    }

    private JPanel card(CharacterSpec spec) {
        CampusArt.Card card = CampusArt.card(new BorderLayout(14, 10), CampusArt.BROWN, 14);
        JLabel crest = new JLabel(new ImageIcon(portrait(spec.getId())), SwingConstants.CENTER);
        crest.setOpaque(false);
        crest.setPreferredSize(new Dimension(137, 137));
        crest.setBorder(null);
        JPanel crestSpace = new JPanel(new GridBagLayout()); crestSpace.setOpaque(false);
        crestSpace.setPreferredSize(new Dimension(145, 145)); crestSpace.add(crest);
        card.add(crestSpace, BorderLayout.WEST);

        JPanel details = new JPanel(); details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(spec.getName());
        name.setFont(UniversityPixelTheme.font(23, Font.BOLD));
        name.setForeground(UniversityPixelTheme.TEXT);
        String extra = spec.getDamageBuff() > 0 ? "피해 +" + (int) (spec.getDamageBuff() * 100) + "%"
                : spec.getMaxHp() > 100 ? "최대 HP +" + (spec.getMaxHp() - 100)
                : spec.getItemSlots() > 3 ? "아이템 슬롯 " + spec.getItemSlots() + "칸" : "기본 능력치";
        JLabel stats = new JLabel("HP " + spec.getMaxHp() + "  ·  " + extra);
        stats.setForeground(UniversityPixelTheme.TEXT_SUB);
        stats.setFont(UniversityPixelTheme.font(13, Font.PLAIN));
        JLabel badge = UniversityPixelTheme.chip(" ", UniversityPixelTheme.PANEL_LIGHT);
        badges.put(spec.getId(), badge);
        details.add(badge); details.add(Box.createVerticalStrut(6));
        JLabel role = new JLabel(role(spec.getId()));
        role.setForeground(UniversityPixelTheme.TEXT);
        role.setFont(UniversityPixelTheme.font(12, Font.PLAIN));
        stats.setForeground(UniversityPixelTheme.GOLD);
        stats.setFont(UniversityPixelTheme.font(13, Font.BOLD));
        details.add(name); details.add(Box.createVerticalStrut(5)); details.add(role);
        details.add(Box.createVerticalStrut(8)); details.add(stats);
        card.add(details, BorderLayout.CENTER);
        JButton action = new PixelButton("");
        action.setPreferredSize(new Dimension(250, 55));
        action.addActionListener(event -> { if (selection != null) selection.accept(spec.getId()); });
        actions.put(spec.getId(), action);
        cards.put(spec.getId(), card);
        card.add(action, BorderLayout.SOUTH);
        return card;
    }

    private JPanel backgroundCard(BackgroundSkin skin) {
        CampusArt.Card card = CampusArt.card(new BorderLayout(12, 9), CampusArt.BROWN, 14);
        card.setPreferredSize(new Dimension(380, 200));
        BufferedImage image = skin == BackgroundSkin.WINTER
                ? BackgroundSkinAssets.image(skin, "lobby")
                : CampusArt.image("/ui/campus-rpg/university-bg.png");
        JLabel preview = new JLabel("", SwingConstants.CENTER);
        if (image != null) {
            BufferedImage thumbnail = new BufferedImage(200, 115, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumbnail.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                double scale = Math.max(200.0 / image.getWidth(), 115.0 / image.getHeight());
                int w = (int) Math.ceil(image.getWidth() * scale);
                int h = (int) Math.ceil(image.getHeight() * scale);
                g.drawImage(image, (200 - w) / 2, (115 - h) / 2, w, h, null);
            } finally { g.dispose(); }
            preview.setIcon(new ImageIcon(thumbnail));
        }
        preview.setPreferredSize(new Dimension(205, 117));
        card.add(preview, BorderLayout.WEST);
        JPanel info = new JPanel(); info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        JLabel title = UniversityPixelTheme.label(skin.getDisplayName(), 20, Font.BOLD, UniversityPixelTheme.TEXT);
        JLabel desc = UniversityPixelTheme.label(skin == BackgroundSkin.WINTER
                ? "겨울 로비 + 스토리 3개 과정" : "게임의 기존 기본 배경", 12, Font.PLAIN, UniversityPixelTheme.TEXT_SUB);
        JLabel badge = UniversityPixelTheme.chip("", UniversityPixelTheme.PANEL_LIGHT);
        info.add(title); info.add(Box.createVerticalStrut(6)); info.add(desc);
        info.add(Box.createVerticalStrut(9)); info.add(badge);
        card.add(info, BorderLayout.CENTER);
        PixelButton action = new PixelButton("");
        action.setPreferredSize(new Dimension(240, 47));
        action.addActionListener(event -> {
            if (skinSelection != null) skinSelection.accept(skin);
        });
        card.add(action, BorderLayout.SOUTH);
        skinActions.put(skin, action);
        skinBadges.put(skin, badge);
        return card;
    }

    public void setSkinData(int coins, Set<String> owned, String equipped) {
        wallet.setValue(coins);
        boolean ready = BackgroundSkinAssets.isWinterPackReady();
        for (Map.Entry<BackgroundSkin, PixelButton> entry : skinActions.entrySet()) {
            BackgroundSkin skin = entry.getKey();
            PixelButton button = entry.getValue();
            boolean mine = owned.contains(skin.getId());
            boolean worn = skin.getId().equals(equipped);
            if (worn) {
                button.setText("장착 중"); button.setEnabled(false); button.positive();
            } else if (mine) {
                button.setText("장착하기"); button.setEnabled(true); button.positive();
            } else if (skin == BackgroundSkin.WINTER && !ready) {
                button.setText("겨울 배경 PNG 확인 필요"); button.setEnabled(false); button.secondary();
            } else {
                button.setText("구매하기 · " + skin.getPrice() + " COINS");
                button.setEnabled(coins >= skin.getPrice()); button.primary();
            }
            UniversityPixelTheme.setChip(skinBadges.get(skin), worn ? "장착 중" : mine ? "보유" : "미보유",
                    worn ? UniversityPixelTheme.MINT : mine ? UniversityPixelTheme.LINE : UniversityPixelTheme.PANEL_LIGHT);
        }
    }

    public void setSkinSelectionAction(Consumer<BackgroundSkin> listener) { skinSelection = listener; }

    /** 픽셀 경계를 유지해 캐릭터 원화를 상점 카드 크기로 표시한다. */
    private static BufferedImage portrait(String id) {
        String path = "/ui/characters/" + id + ".png";
        try (InputStream stream = CharacterShopPanel.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing character portrait: " + path);
            BufferedImage original = ImageIO.read(stream);
            if (original == null) throw new IllegalStateException("Unreadable character portrait: " + path);
            BufferedImage scaled = new BufferedImage(135, 135, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = scaled.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.drawImage(original, 0, 0, 135, 135, null);
            } finally { graphics.dispose(); }
            return scaled;
        } catch (IOException failed) {
            throw new IllegalStateException("Cannot load character portrait: " + path, failed);
        }
    }

    private static String role(String id) {
        if ("attacker".equals(id)) return "줄을 지울 때 주는 피해가 커지는 공격형";
        if ("defender".equals(id)) return "체력이 높아 오래 버티는 방어형";
        if ("utility".equals(id)) return "아이템을 한 칸 더 보관하는 유틸형";
        return "균형 잡힌 기본 캐릭터 · PvP 기본형";
    }

    public void setData(int coins, Set<String> owned, String selected, Map<String, Integer> prices) {
        wallet.setValue(coins);
        for (Map.Entry<String, JButton> entry : actions.entrySet()) {
            String id = entry.getKey();
            JButton action = entry.getValue();
            PixelButton button = (PixelButton) action;
            boolean equipped = id.equals(selected), mine = owned.contains(id);
            if (equipped) { action.setText("장착 중"); action.setEnabled(false); }
            else if (mine) { action.setText("장착하기"); action.setEnabled(true); button.positive(); }
            else { int cost = prices.get(id);
                   action.setText(coins >= cost ? "구입  " + cost + " COINS" : cost + " COINS · 코인 부족");
                   action.setEnabled(coins >= cost); button.primary(); }
            UniversityPixelTheme.setChip(badges.get(id), equipped ? "장착 중" : mine ? "보유" : "미보유",
                    equipped ? UniversityPixelTheme.MINT : mine ? UniversityPixelTheme.LINE : UniversityPixelTheme.PANEL_LIGHT);
            // Rounded card border remains intact when an equipped character changes.
            cards.get(id).repaint();
        }
    }
    public void setSelectionAction(Consumer<String> listener) { selection = listener; }
    public void setBackAction(java.awt.event.ActionListener listener) { back.addActionListener(listener); }
}
