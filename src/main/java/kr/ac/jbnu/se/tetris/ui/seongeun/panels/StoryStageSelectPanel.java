package kr.ac.jbnu.se.tetris.ui.seongeun.panels;
import kr.ac.jbnu.se.tetris.app.BackgroundSkin;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.BackgroundSkinAssets;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import kr.ac.jbnu.se.tetris.ai.DifficultyProfile;
import kr.ac.jbnu.se.tetris.ai.DifficultyProfileCatalog;
import kr.ac.jbnu.se.tetris.story.*;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.*;

/**
 * 스토리 지도. 대학교·졸업·취업 세 지역을 잇는 길 위에 아홉 전투를 점으로 두고,
 * 플레이어 말이 현재 진행 위치에 서 있다. 점을 고르면 아래 상세 칸에서 정보를 보고 도전한다.
 * ← → 로 고르고 Enter로 시작할 수 있다.
 */
public class StoryStageSelectPanel extends JPanel {
    private static final float[][] NODES = {
        {0.07f, 0.74f}, {0.18f, 0.50f}, {0.28f, 0.28f},
        {0.40f, 0.66f}, {0.51f, 0.44f}, {0.61f, 0.24f},
        {0.73f, 0.68f}, {0.84f, 0.46f}, {0.94f, 0.26f}
    };
    private static final String[] CHAPTERS = { "대학교 과정", "졸업 과정", "취업 과정" };
    private static final BufferedImage PLAYER = readPlayer();
    private static final BufferedImage[] REGIONS = {
        readRegion("university-bg.png"), readRegion("graduation-bg.png"),
        readRegion("employment-bg.png")
    };

    private final PixelButton back = new CampusBackButton("돌아가기");
    private final StageCatalog catalog = StageCatalog.loadDefault();
    private final List<MonsterSpec> monsters = new ArrayList<MonsterSpec>();
    private final List<String> stageIds = new ArrayList<String>();
    private final Map<String, List<ActionListener>> listeners = new LinkedHashMap<String, List<ActionListener>>();
    private final JLabel completion = UniversityPixelTheme.chip("0 / 9 CLEAR", UniversityPixelTheme.GOLD);
    private BackgroundSkin backgroundSkin = BackgroundSkin.DEFAULT;
    private final MapView map = new MapView();
    private final PixelButton start = new PixelButton("도전하기");
    private final JLabel detailArt = new JLabel();
    private final JLabel detailTag = UniversityPixelTheme.label("LV 1 · 일반", 13, Font.BOLD, UniversityPixelTheme.GOLD);
    private final JLabel detailName = UniversityPixelTheme.label("술", 24, Font.BOLD, UniversityPixelTheme.TEXT);
    private final JLabel detailStats = UniversityPixelTheme.label(" ", 12, Font.PLAIN, UniversityPixelTheme.TEXT_SUB);
    private final JLabel detailReward = UniversityPixelTheme.chip("첫 승리 +30 코인", UniversityPixelTheme.GOLD);
    private final JLabel detailState = UniversityPixelTheme.chip("NEXT", UniversityPixelTheme.GOLD);
    private boolean[] unlocked = new boolean[9], cleared = new boolean[9];
    private int selected, current, tokenFrom = -1;
    private long tokenMovedAt;

    public StoryStageSelectPanel() {
        setLayout(new BorderLayout(0, 10));
        setBackground(UniversityPixelTheme.BG);
        setBorder(new EmptyBorder(14, 22, 14, 22));
        for (Stage stage : catalog.getStages())
            for (MonsterSpec monster : stage.getEncounters()) { monsters.add(monster); stageIds.add(stage.getId()); }
        back.secondary();
        back.setPreferredSize(new Dimension(190, 56));
        completion.setFont(UniversityPixelTheme.font(13, Font.BOLD));
        add(UniversityPixelTheme.screenHeader("STORY MODE", "스토리 전투", completion, back), BorderLayout.NORTH);
        add(map, BorderLayout.CENTER);
        add(detailPanel(), BorderLayout.SOUTH);
        start.setName("storyStart");
        start.addActionListener(event -> fire());
        bind(KeyEvent.VK_LEFT, "map-left", () -> select(Math.max(0, selected - 1)));
        bind(KeyEvent.VK_RIGHT, "map-right", () -> select(Math.min(monsters.size() - 1, selected + 1)));
        bind(KeyEvent.VK_ENTER, "map-enter", () -> { if (start.isEnabled()) start.doClick(); });
        select(0);
    }

    private JPanel detailPanel() {
        CampusArt.Card panel = CampusArt.card(new BorderLayout(16, 0), CampusArt.BROWN, 12);
        panel.setPreferredSize(new Dimension(10, 172));
        detailArt.setPreferredSize(new Dimension(104, 104));
        panel.add(detailArt, BorderLayout.WEST);
        JPanel text = new JPanel(); text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JPanel tags = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); tags.setOpaque(false);
        tags.setAlignmentX(Component.LEFT_ALIGNMENT);
        tags.add(detailState); tags.add(detailTag);
        text.add(Box.createVerticalGlue());
        text.add(tags); text.add(Box.createVerticalStrut(4));
        text.add(detailName); text.add(Box.createVerticalStrut(4));
        text.add(detailStats); text.add(Box.createVerticalStrut(6));
        detailReward.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(detailReward);
        text.add(Box.createVerticalGlue());
        panel.add(text, BorderLayout.CENTER);
        JPanel action = new JPanel(); action.setOpaque(false);
        action.setLayout(new BoxLayout(action, BoxLayout.Y_AXIS));
        start.setFont(UniversityPixelTheme.font(18, Font.BOLD));
        start.setPreferredSize(new Dimension(225, 67));
        start.setMaximumSize(new Dimension(225, 67));
        start.setAlignmentX(Component.CENTER_ALIGNMENT);
        action.add(Box.createVerticalGlue()); action.add(start); action.add(Box.createVerticalGlue());
        panel.add(action, BorderLayout.EAST);
        return panel;
    }

    private void bind(int key, String name, Runnable action) {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public boolean isEnabled() { return StoryStageSelectPanel.this.isShowing(); }
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    private void fire() {
        if (selected < 0 || selected >= monsters.size() || !unlocked[selected]) return;
        String id = monsters.get(selected).getId();
        List<ActionListener> registered = listeners.get(id);
        if (registered == null) return;
        ActionEvent event = new ActionEvent(start, ActionEvent.ACTION_PERFORMED, id);
        for (ActionListener listener : new ArrayList<ActionListener>(registered)) listener.actionPerformed(event);
    }

    private void select(int index) {
        if (index < 0 || index >= monsters.size()) return;
        selected = index;
        MonsterSpec monster = monsters.get(index);
        int level = index + 1;
        DifficultyProfile difficulty = DifficultyProfileCatalog.forEncounter(monster);
        boolean boss = monster.getTier() == MonsterTier.BOSS;
        detailArt.setIcon(GameArt.icon(GameArt.keyForLevel(level), 104, 104));
        detailTag.setText("LV " + level + " · " + tierName(monster.getTier()) + " · " + CHAPTERS[Math.min(2, index / 3)]);
        detailTag.setForeground(boss ? UniversityPixelTheme.CORAL : UniversityPixelTheme.GOLD);
        detailName.setText(monster.getName());
        detailStats.setText("몬스터 HP " + difficulty.getMonsterHp() + "   ·   낙하 속도 " + difficulty.getPlayerGravityMillis()
                + "ms   ·   몬스터 아이템 LV " + difficulty.getMonsterItemLevel());
        int reward = boss ? 80 : monster.getTier() == MonsterTier.ELITE ? 50 : 30;
        UniversityPixelTheme.setChip(detailReward, cleared[index] ? "보상 획득 완료" : "첫 승리 +" + reward + " 코인",
                cleared[index] ? UniversityPixelTheme.PANEL_LIGHT : UniversityPixelTheme.GOLD);
        boolean next = index == current && unlocked[index] && !cleared[index];
        UniversityPixelTheme.setChip(detailState, cleared[index] ? "CLEAR" : next ? "NEXT" : unlocked[index] ? "도전 가능" : "잠김",
                cleared[index] ? UniversityPixelTheme.MINT : next ? UniversityPixelTheme.GOLD : UniversityPixelTheme.PANEL_LIGHT);
        start.setEnabled(unlocked[index]);
        start.setText(cleared[index] ? "다시 도전" : unlocked[index] ? "도전하기" : "잠김");
        if (cleared[index]) start.secondary(); else start.primary();
        start.setToolTipText(unlocked[index] ? null : "이전 전투를 이기면 열립니다.");
        map.repaint();
    }

    private static String tierName(MonsterTier tier) {
        return tier == MonsterTier.NORMAL ? "일반" : tier == MonsterTier.ELITE ? "엘리트" : "BOSS";
    }

    public void updateProgress(CampaignProgress progress, StageCatalog stages) {
        int count = 0, next = -1, index = 0;
        for (Stage stage : stages.getStages()) for (MonsterSpec monster : stage.getEncounters()) {
            if (index >= unlocked.length) break;
            unlocked[index] = progress.isEncounterUnlocked(stage.getId(), monster.getId());
            cleared[index] = progress.isEncounterCleared(monster.getId());
            if (cleared[index]) count++;
            if (next < 0 && unlocked[index] && !cleared[index]) next = index;
            index++;
        }
        int newCurrent = next < 0 ? monsters.size() - 1 : next;
        // 진행 위치가 바뀌었으면 말이 길을 따라 걸어가게 한다.
        if (tokenFrom >= 0 && newCurrent != current) { tokenFrom = current; tokenMovedAt = System.currentTimeMillis(); }
        else if (tokenFrom < 0) tokenFrom = newCurrent;
        current = newCurrent;
        completion.setText(count + " / " + monsters.size() + " CLEAR");
        select(current);
    }

    public void setBackgroundSkin(BackgroundSkin skin) {
        backgroundSkin = skin == null ? BackgroundSkin.DEFAULT : skin;
        map.repaint();
    }

    public void setBackAction(ActionListener listener) { back.addActionListener(listener); }
    public void setStageAction(int stageIndex, String encounterId, ActionListener listener) {
        List<ActionListener> registered = listeners.get(encounterId);
        if (registered == null) { registered = new ArrayList<ActionListener>(); listeners.put(encounterId, registered); }
        registered.add(listener);
    }
    public void setStageAction(int stageIndex, MonsterTier tier, ActionListener listener) {
        for (MonsterSpec monster : catalog.getStages().get(stageIndex).getEncounters())
            if (monster.getTier() == tier) { setStageAction(stageIndex, monster.getId(), listener); return; }
    }
    public void setStage1NormalAction(ActionListener l) { setStageAction(0, MonsterTier.NORMAL, l); }
    public void setStage1EliteAction(ActionListener l) { setStageAction(0, MonsterTier.ELITE, l); }
    public void setStage1BossAction(ActionListener l) { setStageAction(0, MonsterTier.BOSS, l); }
    public void setStage2NormalAction(ActionListener l) { setStageAction(1, MonsterTier.NORMAL, l); }
    public void setStage2EliteAction(ActionListener l) { setStageAction(1, MonsterTier.ELITE, l); }
    public void setStage2BossAction(ActionListener l) { setStageAction(1, MonsterTier.BOSS, l); }
    public void setStage3NormalAction(ActionListener l) { setStageAction(2, MonsterTier.NORMAL, l); }
    public void setStage3EliteAction(ActionListener l) { setStageAction(2, MonsterTier.ELITE, l); }
    public void setStage3BossAction(ActionListener l) { setStageAction(2, MonsterTier.BOSS, l); }

    private static BufferedImage readPlayer() {
        try { return ImageIO.read(StoryStageSelectPanel.class.getResource("/ui/university/avatar_player.png")); }
        catch (Exception unavailable) { return null; }
    }

    private static BufferedImage readRegion(String name) {
        try { return ImageIO.read(StoryStageSelectPanel.class.getResource("/ui/campus-rpg/" + name)); }
        catch (Exception unavailable) { return null; }
    }

    /** 지도 그림. 지역 배경, 길, 전투 점, 플레이어 말을 그린다. */
    private final class MapView extends JComponent {
        private final Timer timer = new Timer(40, event -> repaint());
        private final Map<String, Image> icons = new LinkedHashMap<String, Image>();
        private int hover = -1;

        MapView() {
            timer.setCoalesce(true);
            addHierarchyListener(event -> {
                if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                    if (isShowing()) timer.start(); else timer.stop();
                }
            });
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent event) {
                    int found = nodeAt(event.getX(), event.getY());
                    if (found != hover) { hover = found; repaint(); }
                    setCursor(Cursor.getPredefinedCursor(found >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                }
                @Override public void mouseExited(MouseEvent event) { hover = -1; repaint(); }
                @Override public void mouseClicked(MouseEvent event) {
                    int found = nodeAt(event.getX(), event.getY());
                    if (found < 0) return;
                    select(found);
                    if (event.getClickCount() >= 2 && start.isEnabled()) start.doClick();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        @Override public void removeNotify() { timer.stop(); super.removeNotify(); }

        private int radius() { return Math.max(18, Math.min(getWidth() / 24, getHeight() / 8)); }

        private Point node(int index) {
            int w = getWidth(), h = getHeight();
            return new Point(Math.round(NODES[index][0] * w), Math.round(28 + NODES[index][1] * (h - 56)));
        }

        private int nodeAt(int x, int y) {
            for (int i = 0; i < monsters.size(); i++) {
                Point p = node(i);
                int r = radius() + (monsters.get(i).getTier() == MonsterTier.BOSS ? 6 : 0);
                if (Math.abs(x - p.x) <= r && Math.abs(y - p.y) <= r) return i;
            }
            return -1;
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                int w = getWidth(), h = getHeight();
                long now = System.currentTimeMillis();
                paintRegions(g, w, h, now);
                paintRoad(g);
                for (int i = 0; i < monsters.size(); i++) paintNode(g, i, now);
                paintToken(g, now);
                g.setColor(new Color(255,255,255,190));
                g.setStroke(new BasicStroke(3f));
                g.drawRoundRect(1, 1, w - 3, h - 3, 22, 22);
            } finally { g.dispose(); }
        }

        private void paintRegions(Graphics2D g, int w, int h, long now) {
            Color[][] skies = {
                { new Color(0xCED2CD), new Color(0xAEB9B4) },
                { new Color(0xC5CBD0), new Color(0xA7B4BA) },
                { new Color(0xD1D0C7), new Color(0xB6BDB6) }
            };
            for (int region = 0; region < 3; region++) {
                int x0 = region * w / 3, x1 = (region + 1) * w / 3;
                g.setPaint(new GradientPaint(0, 0, skies[region][0], 0, h, skies[region][1]));
                g.fillRect(x0, 0, x1 - x0, h);
                // Story map winter chapter backgrounds: same scene assets as story battle.
                BufferedImage scene = null;
                if (backgroundSkin == BackgroundSkin.WINTER) {
                    String sceneId = region == 0 ? "university" : region == 1 ? "graduation" : "employment";
                    scene = BackgroundSkinAssets.image(BackgroundSkin.WINTER, sceneId);
                }
                if (scene == null) scene = REGIONS[region];
                if (scene != null) {
                    int sourceWidth = Math.max(1, scene.getWidth() / 3);
                    int sourceX = Math.min(scene.getWidth() - sourceWidth,
                            Math.max(0, scene.getWidth() / 2 - sourceWidth / 2));
                    g.drawImage(scene, x0, 0, x1, h, sourceX, 0,
                            sourceX + sourceWidth, scene.getHeight(), null);
                }
                g.setColor(new Color(255, 255, 255, 12));
                g.fillRect(x0, 0, x1 - x0, h);
                if (region > 0) {
                    g.setColor(new Color(255, 255, 255, 195));
                    g.fillRect(x0 - 2, 0, 4, h);
                }
                g.setFont(UniversityPixelTheme.font(13, Font.BOLD));
                String label = "CH." + (region + 1) + "  " + CHAPTERS[region];
                FontMetrics m = g.getFontMetrics();
                g.setColor(new Color(255, 244, 220, 248));
                g.fillRoundRect(x0 + 10, 9, m.stringWidth(label) + 24, 33, 18, 18);
                g.setColor(CampusArt.BROWN);
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(x0 + 10, 9, m.stringWidth(label) + 24, 33, 18, 18);
                g.setColor(CampusArt.NAVY);
                g.drawString(label, x0 + 22, 31);
            }
        }

        private void paintRoad(Graphics2D g) {
            Stroke prior = g.getStroke();
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i + 1 < monsters.size(); i++) {
                    Point a = node(i), b = node(i + 1);
                    if (pass == 0) {
                        g.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.setColor(new Color(255, 253, 240, 215));
                    } else {
                        boolean done = cleared[i];
                        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                                1f, new float[] { 10f, 7f }, 0f));
                        g.setColor(done ? CampusArt.BROWN : new Color(0x8D9199));
                    }
                    g.drawLine(a.x, a.y, b.x, b.y);
                }
            }
            g.setStroke(prior);
        }

        private Image icon(int level, int size) {
            String key = level + ":" + size;
            Image image = icons.get(key);
            if (image == null) {
                ImageIcon loaded = GameArt.icon(GameArt.keyForLevel(level), size, size);
                if (loaded == null) return null;
                image = loaded.getImage();
                icons.put(key, image);
            }
            return image;
        }

        private void paintNode(Graphics2D g, int index, long now) {
            Point p = node(index);
            boolean boss = monsters.get(index).getTier() == MonsterTier.BOSS;
            int r = radius() + (boss ? 6 : 0);
            boolean isSelected = index == selected, isHover = index == hover;
            boolean isNext = index == current && unlocked[index] && !cleared[index];
            if (isNext) {
                int pulse = (int) ((now % 1000) / 1000f * 18);
                g.setColor(new Color(40, 121, 111, 160 - pulse * 8));
                g.setStroke(new BasicStroke(3f));
                g.drawRect(p.x - r - 4 - pulse, p.y - r - 4 - pulse, (r + 4 + pulse) * 2, (r + 4 + pulse) * 2);
                g.setStroke(new BasicStroke(1f));
            }
            int lift = isSelected ? (int) Math.round(Math.abs(Math.sin(now / 260.0)) * 4) : 0;
            int x = p.x - r, y = p.y - r - lift, size = r * 2;
            g.setColor(new Color(57, 73, 78, 83));
            g.fillOval(x + 4, y + 5 + lift, size, size);
            Color frame = isSelected ? UniversityPixelTheme.GOLD : cleared[index] ? UniversityPixelTheme.MINT
                    : boss ? UniversityPixelTheme.CORAL : UniversityPixelTheme.LINE;
            g.setColor(frame);
            g.fillOval(x - 3, y - 3, size + 6, size + 6);
            g.setColor(isHover ? UniversityPixelTheme.LINE : UniversityPixelTheme.PANEL);
            g.fillOval(x, y, size, size);
            Image art = icon(index + 1, size - 6);
            if (art != null) g.drawImage(art, x + 3, y + 3, null);
            if (!unlocked[index]) {
                g.setColor(new Color(225, 226, 217, 181));
                g.fillOval(x, y, size, size);
                // 자물쇠.
                int lx = p.x - 8, ly = p.y - 2 - lift;
                g.setColor(UniversityPixelTheme.TEXT_SUB);
                g.fillRect(lx, ly, 16, 12);
                g.drawRect(lx + 3, ly - 8, 9, 9);
                g.setColor(UniversityPixelTheme.TEXT);
                g.fillRect(lx + 7, ly + 4, 2, 4);
            }
            if (cleared[index]) {
                int sx = x + size - 10, sy = y - 8;
                g.setColor(UniversityPixelTheme.LINE);
                g.fillRect(sx - 1, sy - 1, 20, 20);
                g.setColor(UniversityPixelTheme.MINT);
                g.fillRect(sx, sy, 18, 18);
                g.setColor(UniversityPixelTheme.BLACK);
                g.setStroke(new BasicStroke(3f));
                g.drawLine(sx + 4, sy + 9, sx + 8, sy + 13);
                g.drawLine(sx + 8, sy + 13, sx + 14, sy + 5);
                g.setStroke(new BasicStroke(1f));
            }
            g.setFont(UniversityPixelTheme.font(boss ? 12 : 11, Font.BOLD));
            String label = boss ? "BOSS · LV " + (index + 1) : "LV " + (index + 1);
            FontMetrics m = g.getFontMetrics();
            int lw = m.stringWidth(label) + 10;
            g.setColor(new Color(241, 239, 230, 240));
            g.fillRoundRect(p.x - lw / 2, y + size + 6, lw, 23, 12, 12);
            g.setColor(boss ? UniversityPixelTheme.CORAL : isSelected ? UniversityPixelTheme.GOLD : UniversityPixelTheme.TEXT);
            g.drawString(label, p.x - lw / 2 + 5, y + size + 22);
            if (isSelected) {
                int ay = y - 18 - (int) Math.round(Math.sin(now / 180.0) * 4);
                g.setColor(UniversityPixelTheme.BLACK);
                g.fillPolygon(new int[] { p.x - 9, p.x + 11, p.x + 1 }, new int[] { ay + 1, ay + 1, ay + 13 }, 3);
                g.setColor(UniversityPixelTheme.GOLD);
                g.fillPolygon(new int[] { p.x - 10, p.x + 10, p.x }, new int[] { ay, ay, ay + 12 }, 3);
            }
        }

        /** 플레이어 말. 진행이 바뀌면 이전 점에서 새 점까지 길을 따라 걷는다. */
        private void paintToken(Graphics2D g, long now) {
            if (PLAYER == null || monsters.isEmpty()) return;
            float t = tokenMovedAt == 0 ? 1f : Math.min(1f, (now - tokenMovedAt) / 1100f);
            int from = tokenFrom < 0 ? current : tokenFrom;
            float position = from + (current - from) * (t < .5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2);
            int a = Math.max(0, Math.min(monsters.size() - 1, (int) Math.floor(position)));
            int b = Math.min(monsters.size() - 1, a + 1);
            float local = position - a;
            Point pa = node(a), pb = node(b);
            int x = Math.round(pa.x + (pb.x - pa.x) * local), y = Math.round(pa.y + (pb.y - pa.y) * local);
            if (t >= 1f) tokenFrom = current;
            int r = radius();
            int tw = Math.max(26, r * 9 / 10), th = tw * 7 / 6;
            boolean walking = t < 1f;
            int hop = (int) Math.round(Math.abs(Math.sin(now / (walking ? 90.0 : 300.0))) * (walking ? 6 : 3));
            int tx = Math.max(4, x - r - tw / 2), ty = y + r - th + 10 - hop;
            g.setColor(new Color(0, 0, 0, 120));
            g.fillOval(tx + 2, y + r - 4, tw - 4, 8);
            g.setColor(UniversityPixelTheme.MINT);
            g.fillRect(tx - 2, ty - 2, tw + 4, th + 4);
            g.drawImage(PLAYER, tx, ty, tw, th, null);
            g.setFont(UniversityPixelTheme.font(10, Font.BOLD));
            g.setColor(UniversityPixelTheme.MINT);
            g.fillRect(tx - 2, ty - 15, 26, 13);
            g.setColor(UniversityPixelTheme.BLACK);
            g.drawString("YOU", tx + 1, ty - 5);
        }
    }
}
