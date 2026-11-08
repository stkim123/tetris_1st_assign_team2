package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.HierarchyEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import kr.ac.jbnu.se.tetris.story.MonsterTier;

/**
 * 보드 옆 전투 무대. PvE에서는 몬스터, PvP에서는 양쪽 플레이어를 보여 주고
 * 근접 돌진·피격 경직·화면 흔들림·데미지 숫자·지연 HP 바로 전투 상태를 보여 준다.
 * 피해와 승패는 계산하지 않고 전달받은 상태만 연출한다.
 */
public final class PixelArena extends JComponent {
    public enum Effect {
        ATTACK, MONSTER_ATTACK, DAMAGE, HEAL, ITEM_ACQUIRE, ITEM_USE,
        LINE_CLEAR, FEVER, VICTORY, DEFEAT
    }

    private static final Color GOLD = UniversityPixelTheme.GOLD;
    private static final Color MINT = UniversityPixelTheme.MINT;
    private static final Color CYAN = new Color(0x6FE9F3);
    private static final Color CORAL = UniversityPixelTheme.CORAL;
    private static final Color PINK = new Color(0xFF92E2);
    private static final Color WHITE = new Color(0xFFF7E8);
    private static final Color[] SHARDS = {
        CYAN, MINT, WHITE, new Color(114, 157, 255), CYAN, MINT
    };
    private static final BufferedImage PLAYER_IDLE = readArt("/ui/campus-rpg/poses/player-idle.png");
    private static final BufferedImage PLAYER_ART = PLAYER_IDLE != null ? PLAYER_IDLE
            : readArt("/ui/university/avatar_player.png");
    private static final BufferedImage FALLBACK_MONSTER = readArt("/ui/university/avatar_professor.png");
    // 포즈 그림이 아직 없거나 일부만 있을 때는 원본 전투원 그림으로 자연스럽게 돌아간다.
    private static final BufferedImage PLAYER_WINDUP = readArt("/ui/campus-rpg/poses/player-windup.png");
    private static final BufferedImage PLAYER_STRIKE = readArt("/ui/campus-rpg/poses/player-strike.png");
    private static final BufferedImage PLAYER_HURT = readArt("/ui/campus-rpg/poses/player-hurt.png");
    private static final BufferedImage PROFESSOR_WINDUP = readArt("/ui/campus-rpg/poses/professor-windup.png");
    private static final BufferedImage PROFESSOR_STRIKE = readArt("/ui/campus-rpg/poses/professor-strike.png");
    private static final BufferedImage PROFESSOR_HURT = readArt("/ui/campus-rpg/poses/professor-hurt.png");
    private static final BufferedImage UNIVERSITY_BATTLE_BG = readArt("/ui/campus-rpg/university-battle-bg.png");
    private static final BufferedImage UNIVERSITY_BG = UNIVERSITY_BATTLE_BG != null ? UNIVERSITY_BATTLE_BG
            : readArt("/ui/campus-rpg/university-bg.png");
    private static final BufferedImage GRADUATION_BG = readArt("/ui/campus-rpg/graduation-bg.png");
    private static final BufferedImage EMPLOYMENT_BG = readArt("/ui/campus-rpg/employment-bg.png");
    private static final Map<String, BufferedImage> ART_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> ART_LOADING = ConcurrentHashMap.newKeySet();
    private static final Map<String, BufferedImage> SCALED = new LinkedHashMap<String, BufferedImage>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) { return size() > 24; }
    };
    private static final Map<String, BufferedImage> BACKDROP_CACHE = new LinkedHashMap<String, BufferedImage>(8, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) { return size() > 12; }
    };
    private static final int EFFECT_LIMIT = 24;
    /** 직접 타격이 맞는 시각. 피해 숫자와 HP 감소도 이때 보인다. */
    public static final int IMPACT_MS = 360;

    private final ArrayDeque<Visual> visuals = new ArrayDeque<>();
    private final Timer animation;
    private int playerHp = 100, playerMax = 100, monsterHp = 100, monsterMax = 100;
    private int playerTrailFrom = 100, monsterTrailFrom = 100;
    private long playerTrailAt, monsterTrailAt;
    private String playerName = "PLAYER", monsterName = "MONSTER";
    private String monsterArtPath;
    private String chapter = "university";
    private kr.ac.jbnu.se.tetris.app.BackgroundSkin backgroundSkin =
            kr.ac.jbnu.se.tetris.app.BackgroundSkin.DEFAULT;

    public void setBackgroundSkin(kr.ac.jbnu.se.tetris.app.BackgroundSkin skin) {
        backgroundSkin = skin == null ? kr.ac.jbnu.se.tetris.app.BackgroundSkin.DEFAULT : skin;
        repaint();
    }
    private boolean online;
    private boolean widePve;
    private MonsterTier pattern = MonsterTier.NORMAL;
    private long playerHitAt = -1, monsterHitAt = -1, playerAttackAt = -1, monsterAttackAt = -1;
    private long playerPlacementAt = -1, monsterPlacementAt = -1;
    private long shakeAt = -1;
    private int shakePower;
    private boolean feverActive;
    private long finishedAt = -1;
    private long introAt = -1;
    private boolean won;
    private boolean effectsEnabled = true;
    private boolean initialized;
    private int serial;
    // Story-only speech is visual UI state; it never modifies HP, controls or battle rules.
    public enum DialogueSpeaker { PLAYER, MONSTER }
    private DialogueSpeaker dialogueSpeaker;
    private String dialogueText;
    private long dialogueUntil;
    private final Timer dialogueTimer = new Timer(5000, event -> clearDialogue());


    public PixelArena() {
        setPreferredSize(new Dimension(380, 520));
        setMinimumSize(new Dimension(200, 300));
        animation = new Timer(33, event -> {
            expireVisuals();
            repaint();
        });
        animation.setCoalesce(true);
        dialogueTimer.setRepeats(false);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (effectsEnabled && isShowing()) animation.start();
                else animation.stop();
            }
        });
    }

    @Override public void addNotify() {
        super.addNotify();
        if (effectsEnabled && isShowing()) animation.start();
    }

    @Override public void removeNotify() {
        animation.stop();
        super.removeNotify();
    }

    /** Show a speech bubble beside the actor. Only story PvE callers should invoke this. */
    public void showDialogue(DialogueSpeaker speaker, String text) {
        if (speaker == null || text == null || text.trim().isEmpty()) {
            clearDialogue();
            return;
        }
        dialogueSpeaker = speaker;
        dialogueText = text.trim();
        dialogueUntil = System.currentTimeMillis() + 5000;
        dialogueTimer.restart();
        repaint();
    }

    public void clearDialogue() {
        dialogueTimer.stop();
        dialogueSpeaker = null;
        dialogueText = null;
        dialogueUntil = 0;
        repaint();
    }

    /** 몬스터의 등급에 맞춰 무대 조명과 오라 색을 바꾼다. */
    public void setMonsterPattern(MonsterTier pattern) {
        this.pattern = pattern == null ? MonsterTier.NORMAL : pattern;
        repaint();
    }

    /** 상대 아틀라스 키(예: university:0) 또는 /ui/puzzle-rpg/ 경로. */
    public void setMonsterArt(String resourcePath) {
        monsterArtPath = resourcePath;
        if (resourcePath != null && !ART_CACHE.containsKey(resourcePath)
                && ART_LOADING.add(resourcePath)) {
            Thread loader = new Thread(() -> {
                BufferedImage image = resourcePath.startsWith("/ui/puzzle-rpg/")
                        ? readArt(resourcePath) : GameArt.sprite(resourcePath);
                if (image != null) ART_CACHE.put(resourcePath, trim(image));
                ART_LOADING.remove(resourcePath);
                SwingUtilities.invokeLater(this::repaint);
            }, "tetris-arena-art");
            loader.setDaemon(true);
            loader.start();
        }
        repaint();
    }

    public void setOnline(boolean online) {
        this.online = online;
        repaint();
    }

    /** PvE에서 플레이어와 몬스터를 중앙 보드 양옆에 그리는 투명 오버레이 모드. */
    public void setWidePve(boolean enabled) {
        if (widePve == enabled) return;
        widePve = enabled;
        repaint();
    }

    /** university, graduation, employment에 맞춰 배경 무대를 바꾼다. */
    public void setChapter(String chapter) {
        if ("graduation".equals(chapter) || "employment".equals(chapter))
            this.chapter = chapter;
        else this.chapter = "university";
        repaint();
    }

    /** 피버 중에는 무대 테두리가 분홍빛으로 맥동한다. */
    public void setFever(boolean active) {
        if (feverActive != active) { feverActive = active; repaint(); }
    }

    /** 새 전투의 첫 스냅샷을 기준점으로 삼아 이전 상대의 피격 연출을 남기지 않는다. */
    public void resetForEncounter() {
        visuals.clear();
        clearDialogue();
        initialized = false;
        playerName = "PLAYER";
        monsterName = "MONSTER";
        playerHp = playerMax = monsterHp = monsterMax = 100;
        playerTrailFrom = monsterTrailFrom = 100;
        playerTrailAt = monsterTrailAt = 0;
        playerHitAt = monsterHitAt = playerAttackAt = monsterAttackAt = shakeAt = -1;
        playerPlacementAt = monsterPlacementAt = -1;
        finishedAt = -1;
        feverActive = false;
        introAt = effectsEnabled ? System.currentTimeMillis() : -1;
        if (isShowing()) animation.start();
        repaint();
    }

    /** 저사양 환경에서 움직임을 멈추고 현재 상태만 그린다. */
    public void setEffectsEnabled(boolean enabled) {
        effectsEnabled = enabled;
        if (enabled && isShowing()) animation.start();
        else {
            animation.stop();
            visuals.clear();
            playerAttackAt = monsterAttackAt = playerHitAt = monsterHitAt = shakeAt = -1;
            playerPlacementAt = monsterPlacementAt = -1;
        }
        repaint();
    }

    public void showEffect(Effect effect, int amount) {
        showEffect(effect, amount, false);
    }

    /** 피스가 놓일 때마다 발판과 자세가 잠깐 반응한다. 실제 공격 판정은 만들지 않는다. */
    public void showPlacement(boolean opponent) {
        if (!effectsEnabled || finishedAt >= 0) return;
        if (opponent) monsterPlacementAt = System.currentTimeMillis();
        else playerPlacementAt = System.currentTimeMillis();
        repaint();
    }

    /** targetMonster는 효과를 받는 쪽이 몬스터(상대)인지 나타낸다. */
    public void showEffect(Effect effect, int amount, boolean targetMonster) {
        if (effect == null) return;
        long now = System.currentTimeMillis();
        if (effect == Effect.VICTORY || effect == Effect.DEFEAT) {
            finishedAt = now;
            won = effect == Effect.VICTORY;
            shake(won ? 10 : 14, now);
        }
        if (!effectsEnabled) { repaint(); return; }
        long start = now;
        if (effect == Effect.ATTACK) playerAttackAt = now;
        if (effect == Effect.MONSTER_ATTACK) monsterAttackAt = now;
        if (effect == Effect.DAMAGE) {
            // 전투원의 타격 자세가 닿은 뒤에 숫자와 섬광이 보이도록 맞춘다.
            start = now + IMPACT_MS;
            if (targetMonster) monsterHitAt = start; else playerHitAt = start;
            int power = online ? 6 + Math.min(9, amount / 3)
                    : targetMonster ? 5 + Math.min(8, amount / 3) : 8 + Math.min(10, amount / 2);
            shake(power, start);
        }
        add(new Visual(effect, Math.max(0, amount), targetMonster, start, serial++, null, 0, false));
    }

    /** 줄 삭제 문구. 콤보와 퍼펙트 클리어를 함께 크게 띄운다. */
    public void showClear(int lines, int combo, boolean perfect, boolean enemySide) {
        if (!effectsEnabled || lines <= 0) return;
        add(new Visual(Effect.LINE_CLEAR, lines, enemySide, System.currentTimeMillis(), serial++,
                null, Math.max(0, combo), perfect));
    }

    public void showItemEffect(String itemId) { showItemEffect(itemId, false); }

    /** 아이템마다 식별 가능한 연출을 준다. targetMonster는 효과를 받는 쪽이다. */
    public void showItemEffect(String itemId, boolean targetMonster) {
        if (itemId == null || !effectsEnabled) return;
        add(new Visual(Effect.ITEM_USE, 0, targetMonster, System.currentTimeMillis(), serial++, itemId, 0, false));
    }

    private void add(Visual visual) {
        if (visuals.size() >= EFFECT_LIMIT) visuals.removeFirst();
        visuals.addLast(visual);
        if (isShowing()) animation.start();
        repaint();
    }

    private void shake(int power, long at) {
        if (!effectsEnabled) return;
        long now = System.currentTimeMillis();
        boolean active = shakeAt >= 0 && now - shakeAt < 320;
        if (!active || power >= shakePower) { shakePower = power; shakeAt = at; }
    }

    public void update(String player, int hp, int maxHp, String monster, int enemyHp, int enemyMax) {
        String nextPlayer = player == null ? "PLAYER" : player;
        String nextMonster = monster == null ? "MONSTER" : monster;
        long now = System.currentTimeMillis();
        if (initialized && playerName.equals(nextPlayer) && hp != playerHp) {
            playerTrailFrom = shownTrail(playerTrailFrom, playerHp, playerTrailAt, now);
            playerTrailAt = now;
            if (hp < playerHp) {
                if (effectsEnabled && now - monsterAttackAt > 500) monsterAttackAt = now;
                showEffect(Effect.DAMAGE, playerHp - hp, false);
            } else showEffect(Effect.HEAL, hp - playerHp, false);
        }
        if (initialized && monsterName.equals(nextMonster) && enemyHp != monsterHp) {
            monsterTrailFrom = shownTrail(monsterTrailFrom, monsterHp, monsterTrailAt, now);
            monsterTrailAt = now;
            if (enemyHp < monsterHp) {
                if (effectsEnabled && now - playerAttackAt > 500) playerAttackAt = now;
                showEffect(Effect.DAMAGE, monsterHp - enemyHp, true);
            } else showEffect(Effect.HEAL, enemyHp - monsterHp, true);
        }
        if (!initialized || !playerName.equals(nextPlayer)) { playerTrailFrom = hp; playerTrailAt = 0; }
        if (!initialized || !monsterName.equals(nextMonster)) { monsterTrailFrom = enemyHp; monsterTrailAt = 0; }
        playerName = nextPlayer;
        monsterName = nextMonster;
        playerHp = Math.max(0, hp);
        playerMax = Math.max(1, maxHp);
        monsterHp = Math.max(0, enemyHp);
        monsterMax = Math.max(1, enemyMax);
        initialized = true;
        repaint();
    }

    /** 흰색 '깎인 HP' 조각은 착탄 뒤 잠시 머물렀다가 실제 값으로 줄어든다. */
    private static int shownTrail(int from, int to, long at, long now) {
        if (at <= 0) return to;
        long age = now - at;
        if (age < IMPACT_MS + 350) return from;
        float progress = Math.min(1f, (age - IMPACT_MS - 350) / 450f);
        return Math.round(from + (to - from) * progress);
    }

    private static int shownMain(int from, int to, long at, long now) {
        return at > 0 && now - at < IMPACT_MS ? from : to;
    }

    // ───────────────────────────── 그리기 ─────────────────────────────

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            long now = System.currentTimeMillis();
            Stage stage = new Stage(w, h);

            if (!widePve || online) {
                g.setColor(UniversityPixelTheme.BLACK);
                g.fillRect(0, 0, w, h);
            }
            Graphics2D world = (Graphics2D) g.create();
            int[] shake = shakeOffset(now);
            world.translate(shake[0], shake[1]);
            if (!widePve || online) paintBackdrop(world, stage, now);
            paintMonster(world, stage, now);
            paintPlayer(world, stage, now);
            paintVisuals(world, stage, now);
            world.dispose();

            paintHitFlash(g, w, h, now);
            paintDanger(g, w, h, now);
            paintPlates(g, stage, now);
            paintStoryDialogue(g, stage, now);
            paintIntro(g, stage, now);
            paintFinish(g, stage, now);
            if (!widePve || online) paintFrame(g, w, h, now);
        } finally { g.dispose(); }
    }

    /** Speech bubbles use vector drawing: no PNG or extra asset is necessary. */
    private void paintStoryDialogue(Graphics2D g, Stage s, long now) {
        if (online || !widePve || dialogueSpeaker == null || dialogueText == null
                || now >= dialogueUntil) return;
        boolean player = dialogueSpeaker == DialogueSpeaker.PLAYER;
        int laneWidth = Math.max(160, Math.round(s.w * 0.35f));
        // NEXT preview occupies part of the right lane; monster dialogue uses a narrower card.
        int width = Math.max(140, Math.min(player ? 304 : 236, laneWidth - 18));
        g.setFont(UniversityPixelTheme.font(16, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = speechLines(dialogueText, fm, width - 28, 3);
        int bodyHeight = 40 + lines.size() * 23;
        int actorX = player ? s.playerCenterX() : s.monsterX;
        int actorTop = player ? s.portraitY : s.monsterFoot - s.monsterSize;
        int zoneX = player ? 12 : s.w - width - 12;
        // Keep bubbles above the HP bars and sprite silhouettes rather than covering them.
        int y = Math.max(12, Math.min(s.h - bodyHeight - 25, actorTop - bodyHeight - 58));
        int tailX = clampSpeech(actorX, zoneX + 26, zoneX + width - 26);
        int tailY = Math.min(s.h - 5, y + bodyHeight + 21);
        int baseX = clampSpeech(tailX, zoneX + 18, zoneX + width - 18);

        Graphics2D bubble = (Graphics2D) g.create();
        try {
            bubble.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Path2D.Float tail = new Path2D.Float();
            tail.moveTo(baseX - 13, y + bodyHeight - 4);
            tail.lineTo(tailX, tailY);
            tail.lineTo(baseX + 12, y + bodyHeight - 4);
            tail.closePath();
            bubble.setColor(new Color(85, 58, 41, 90));
            bubble.fillRoundRect(zoneX + 2, y + 4, width, bodyHeight, 25, 25);
            bubble.setColor(new Color(0xFFF9EC));
            bubble.fill(tail);
            bubble.fillRoundRect(zoneX, y, width, bodyHeight, 24, 24);
            bubble.setColor(new Color(0x54351F));
            bubble.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            bubble.draw(tail);
            bubble.drawRoundRect(zoneX + 1, y + 1, width - 2, bodyHeight - 2, 24, 24);
            // Hide the tail's joining line without obscuring the outline elsewhere.
            bubble.setColor(new Color(0xFFF9EC));
            bubble.fillRect(baseX - 11, y + bodyHeight - 5, 22, 5);
            bubble.setFont(UniversityPixelTheme.font(13, Font.BOLD));
            bubble.setColor(player ? new Color(0x08795D) : new Color(0xA34235));
            bubble.drawString(player ? "플레이어" : "몬스터", zoneX + 15, y + 22);
            bubble.setColor(new Color(0x14264B));
            bubble.setFont(UniversityPixelTheme.font(16, Font.BOLD));
            int baseline = y + 43;
            for (String line : lines) {
                bubble.drawString(line, zoneX + 15, baseline);
                baseline += 23;
            }
        } finally { bubble.dispose(); }
    }

    private static int clampSpeech(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    /** Wrap Korean and Latin copy without depending on the source PNG or HTML rendering. */
    private static List<String> speechLines(String text, FontMetrics metrics, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\n' || (current.length() > 0
                    && metrics.stringWidth(current.toString() + ch) > maxWidth)) {
                if (current.length() > 0) lines.add(current.toString().trim());
                current.setLength(0);
                if (ch == '\n') continue;
            }
            current.append(ch);
        }
        if (current.length() > 0) lines.add(current.toString().trim());
        if (lines.isEmpty()) lines.add("");
        if (lines.size() > maxLines) {
            List<String> clipped = new ArrayList<String>(lines.subList(0, maxLines));
            String last = clipped.get(maxLines - 1);
            while (!last.isEmpty() && metrics.stringWidth(last + "…") > maxWidth)
                last = last.substring(0, last.length() - 1);
            clipped.set(maxLines - 1, last + "…");
            return clipped;
        }
        return lines;
    }

    /** 무대 안 주요 위치. 크기가 바뀌어도 비율로 계산한다. */
    private final class Stage {
        final int w, h, top, bottom, horizon;
        final int monsterSize, monsterX, monsterFoot;
        final int portraitW, portraitH, portraitX, portraitY;

        Stage(int w, int h) {
            this.w = w; this.h = h;
            if (widePve && !online) {
                top = 40;
                bottom = h - 22;
                horizon = h - 100;
                monsterSize = Math.max(64, Math.min(Math.round(w * 0.18f), Math.round(h * 0.29f)));
                monsterX = w - Math.max(105, Math.round(w * 0.12f));
                monsterFoot = h - 45;
                portraitW = Math.max(56, Math.min(Math.round(w * 0.16f), Math.round(h * 0.23f)));
                portraitH = portraitW * 7 / 6;
                portraitX = Math.max(12, Math.round(w * 0.12f) - portraitW / 2);
                portraitY = monsterFoot - portraitH;
                return;
            }
            top = 62;
            bottom = h - 58;
            int span = Math.max(80, bottom - top);
            horizon = top + Math.round(span * 0.60f);
            // PvP는 두 플레이어를 같은 크기와 거리로, PvE는 몬스터를 크게 보여 준다.
            monsterSize = online
                    ? Math.max(52, Math.min(Math.round(w * 0.37f), Math.round(span * 0.44f)))
                    : Math.max(60, Math.min(Math.round((w - 20) * 0.56f), Math.round(span * 0.66f)));
            monsterX = Math.round(w * (online ? 0.28f : 0.31f));
            monsterFoot = horizon + Math.round(monsterSize * 0.10f);
            portraitW = online ? monsterSize : Math.max(46, Math.min(Math.round(w * (PLAYER_IDLE == null ? 0.36f : 0.42f)),
                    Math.round(span * (PLAYER_IDLE == null ? 0.31f : 0.37f))));
            portraitH = online ? portraitW : portraitW * 7 / 6;
            portraitX = online ? Math.round(w * 0.72f) - portraitW / 2 : w - portraitW - 14;
            // 근접 공격 때 두 전투원이 실제로 만나는 높이. PvE의 작은 주인공만 약간 앞쪽에 둔다.
            portraitY = monsterFoot - portraitH + (online ? 0 : Math.min(16, span / 20));
        }

        int monsterCenterY() { return monsterFoot - monsterSize / 2; }
        int playerCenterX() { return portraitX + portraitW / 2; }
        int playerCenterY() { return portraitY + portraitH / 2; }
        int calloutY() { return top + Math.round((horizon - top) * 0.42f); }
    }

    private int[] shakeOffset(long now) {
        if (shakeAt < 0) return new int[] { 0, 0 };
        long age = now - shakeAt;
        if (age < 0 || age > 300) return new int[] { 0, 0 };
        float fade = 1f - age / 300f;
        int amp = Math.round(shakePower * fade);
        return new int[] { (int) Math.round(Math.sin(age / 14.0) * amp),
                (int) Math.round(Math.cos(age / 11.0) * amp * 0.6) };
    }

    private void paintBackdrop(Graphics2D g, Stage s, long now) {
        int w = s.w, h = s.h;
        Color skyTop, skyBottom, accent = tierColor();
        if ("graduation".equals(chapter)) { skyTop = new Color(0x646C6B); skyBottom = new Color(0xA09B8D); }
        else if ("employment".equals(chapter)) { skyTop = new Color(0x657875); skyBottom = new Color(0xA8A897); }
        else { skyTop = new Color(0x76847D); skyBottom = new Color(0xB2AA98); }
        BufferedImage winterBackdrop = online ? null : BackgroundSkinAssets.image(backgroundSkin, chapter);
        BufferedImage backdrop = winterBackdrop != null ? winterBackdrop
                : "graduation".equals(chapter) ? GRADUATION_BG
                : "employment".equals(chapter) ? EMPLOYMENT_BG : UNIVERSITY_BG;
        if (backdrop == null) {
            g.setPaint(new GradientPaint(0, 0, skyTop, 0, s.horizon, skyBottom));
            g.fillRect(-20, -20, w + 40, s.horizon + 20);
        } else {
            g.drawImage(backdrop(backdrop, w, h), 0, 0, null);
            // 원본 광장의 바닥과 줄눈을 그대로 살려 건물 안쪽에서도 한 장소처럼 보이게 한다.
            g.setColor(new Color(242, 227, 199, 13));
            g.fillRect(0, 0, w, h);
        }
        if (backdrop == null) paintSilhouette(g, s);

        if (backdrop == null) {
            g.setPaint(new GradientPaint(0, s.horizon, new Color(0xA39D90),
                    0, h, new Color(0x606965)));
            g.fillRect(-20, s.horizon, w + 40, h - s.horizon + 20);
            g.setColor(new Color(233, 228, 210, 38));
            for (int i = -2; i <= 2; i++)
                g.drawLine(w / 2 + i * 24, s.horizon, w / 2 + i * w / 3, h);
            for (int i = 1; i < 4; i++) {
                float t = i / 4f;
                int y = s.horizon + Math.round((h - s.horizon) * t * t);
                g.drawLine(-20, y, w + 20, y);
            }
        }
        // PvE는 몬스터 조명을 키우고, PvP는 양쪽 선수에게 같은 크기의 조명을 준다.
        paintSpotlight(g, s.monsterX, s.monsterFoot, s.monsterSize, s.top, accent);
        if (online) {
            paintSpotlight(g, s.playerCenterX(), s.monsterFoot, s.portraitW, s.top, MINT);
            g.setColor(new Color(255, 247, 232, 85));
            g.fillRect(w / 2 - 1, s.top + 18, 2, s.horizon - s.top - 26);
        }
        paintFighterMark(g, s.monsterX, s.monsterFoot, s.monsterSize, accent, now, monsterPlacementAt);
        paintFighterMark(g, s.playerCenterX(), s.portraitY + s.portraitH,
                s.portraitW, MINT, now, playerPlacementAt);
        // 풍경 위의 상시 입자는 줄이고 실제 공격 때만 큰 광원을 쓴다.
        if (effectsEnabled) {
            for (int i = 0; i < 6; i++) {
                long cycle = 5200 + i * 310;
                float t = ((now + i * 777) % cycle) / (float) cycle;
                int x = Math.floorMod(i * 61 + 7, Math.max(1, w)) + (int) (Math.sin(t * 6.28 + i) * 10);
                int y = h - Math.round((h - s.top) * t);
                g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (55 * Math.sin(t * Math.PI))));
                g.fillRect(x, y, 2, 2);
            }
        }
    }

    /** 큰 배경 원본은 화면 크기마다 한 번만 중앙 크롭해 Swing 프레임 그리기 비용을 줄인다. */
    private static BufferedImage backdrop(BufferedImage source, int width, int height) {
        String key = System.identityHashCode(source) + ":" + width + "x" + height;
        synchronized (BACKDROP_CACHE) {
            BufferedImage cached = BACKDROP_CACHE.get(key);
            if (cached != null) return cached;
        }
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D canvas = result.createGraphics();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double ratio = Math.max(width / (double) source.getWidth(), height / (double) source.getHeight());
            int scaledW = Math.max(width, (int) Math.ceil(source.getWidth() * ratio));
            int scaledH = Math.max(height, (int) Math.ceil(source.getHeight() * ratio));
            int imageX = (width - scaledW) / 2;
            if (source == UNIVERSITY_BATTLE_BG)
                imageX = Math.round((width - scaledW) * 0.34f);
            canvas.drawImage(source, imageX, (height - scaledH) / 2, scaledW, scaledH, null);
        } finally { canvas.dispose(); }
        synchronized (BACKDROP_CACHE) { BACKDROP_CACHE.put(key, result); }
        return result;
    }

    private static void paintSpotlight(Graphics2D g, int centerX, int foot, int size, int top, Color color) {
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 23));
        g.fillPolygon(new int[] { centerX - 18, centerX + 18, centerX + size / 2 + 20,
                centerX - size / 2 - 20 },
                new int[] { top - 10, top - 10, foot + 8, foot + 8 }, 4);
    }

    /** 입력이 느린 구간에도 두 전투원이 무대에 살아 있음을 보여 주는 낮은 대비의 발판. */
    private void paintFighterMark(Graphics2D g, int x, int foot, int size, Color color,
                                  long now, long placementAt) {
        int half = Math.max(22, size * 2 / 5);
        int glow = effectsEnabled && finishedAt < 0
                ? Math.round(18 + 12 * (1f + (float) Math.sin(now / 680.0)) / 2f) : 18;
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), glow));
        g.fillOval(x - half, foot - 8, half * 2, 19);
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 100));
        g.drawLine(x - half, foot, x - half / 2, foot);
        g.drawLine(x + half / 2, foot, x + half, foot);
        long age = now - placementAt;
        if (effectsEnabled && placementAt >= 0 && age >= 0 && age < 220) {
            float progress = age / 220f;
            int spread = half + Math.round(12 * progress);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(),
                    Math.round(140 * (1f - progress))));
            g.drawLine(x - spread, foot + 2, x - spread / 2, foot + 2);
            g.drawLine(x + spread / 2, foot + 2, x + spread, foot + 2);
        }
    }

    private void paintSilhouette(Graphics2D g, Stage s) {
        int w = s.w, base = s.horizon;
        g.setColor(new Color(0x0A0726));
        if ("employment".equals(chapter)) {
            for (int x = -10, i = 0; x < w + 10; x += 34, i++) {
                int height = 40 + Math.floorMod(i * 37, 70);
                g.setColor(new Color(0x0A1430));
                g.fillRect(x, base - height, 30, height);
                g.setColor(new Color(255, 209, 102, 70));
                for (int y = base - height + 6; y < base - 6; y += 10)
                    for (int wx = x + 5; wx < x + 26; wx += 8)
                        if (Math.floorMod(wx * 7 + y * 3 + i, 5) == 0) g.fillRect(wx, y, 3, 4);
            }
        } else if ("graduation".equals(chapter)) {
            int hallW = Math.min(w - 40, 260), hx = (w - hallW) / 2;
            g.fillPolygon(new int[] { hx - 10, w / 2, hx + hallW + 10 }, new int[] { base - 70, base - 104, base - 70 }, 3);
            g.fillRect(hx, base - 70, hallW, 70);
            g.setColor(new Color(0x2A1B48));
            for (int x = hx + 12; x < hx + hallW - 10; x += 30) g.fillRect(x, base - 62, 10, 62);
        } else {
            g.fillRect(0, base - 34, w, 34);
            int towerX = Math.round(w * 0.20f);
            g.fillRect(towerX, base - 96, 34, 96);
            g.fillPolygon(new int[] { towerX - 4, towerX + 17, towerX + 38 }, new int[] { base - 96, base - 124, base - 96 }, 3);
            g.setColor(new Color(255, 209, 102, 150));
            g.fillOval(towerX + 8, base - 86, 18, 18);
            g.setColor(new Color(0x0A0726));
            for (int x = towerX + 50; x < w; x += 46) g.fillRect(x, base - 52 - Math.floorMod(x, 18), 38, 52);
            g.setColor(new Color(255, 209, 102, 60));
            for (int x = 6; x < w; x += 14) if (Math.floorMod(x * 13, 3) == 0) g.fillRect(x, base - 24, 4, 5);
        }
    }

    private Color tierColor() {
        if (online) return CORAL;
        return pattern == MonsterTier.BOSS ? CORAL : GOLD;
    }

    private void paintMonster(Graphics2D g, Stage s, long now) {
        BufferedImage art = online ? PLAYER_ART
                : monsterArtPath == null ? null : ART_CACHE.get(monsterArtPath);
        if (art == null) art = FALLBACK_MONSTER;
        int size = s.monsterSize;
        long attackAge = now - monsterAttackAt;
        float bob = effectsEnabled && (attackAge < 0 || attackAge >= 720)
                ? (float) Math.sin(now / 380.0) : 0f;
        int x = s.monsterX - size / 2, y = s.monsterFoot - size + Math.round(bob * 4);
        boolean professor = !online && (monsterArtPath == null || "university:2".equals(monsterArtPath)
                || monsterArtPath.endsWith("avatar_professor.png"));
        art = professor ? poseArt(art, PROFESSOR_WINDUP, PROFESSOR_STRIKE, PROFESSOR_HURT,
                attackAge, now - monsterHitAt)
                : online ? poseArt(art, PLAYER_WINDUP, PLAYER_STRIKE, PLAYER_HURT,
                attackAge, now - monsterHitAt) : art;
        float pose = effectsEnabled ? attackPose(attackAge) : 0f;
        int advance = attackAdvance(s, true);
        if (!online && pattern == MonsterTier.BOSS) advance = Math.max(7, advance / 3);
        x += (widePve && !online ? -1 : 1) * Math.round(pose * advance);
        y -= attackLift(attackAge, size);
        long hitAge = now - monsterHitAt;
        boolean hit = monsterHitAt >= 0 && hitAge >= 0 && hitAge < 300;
        if (hit) {
            x -= hitRecoil(hitAge, Math.max(12, size / 9));
            x += (int) Math.round(Math.sin(hitAge / 15.0) * 3 * (1f - hitAge / 300f));
            y += hitRecoil(hitAge, Math.max(4, size / 25)) / 2;
        }
        if (introAt >= 0 && now - introAt < 520) {
            // 전투 시작: 몬스터가 왼쪽 밖에서 미끄러져 들어온다.
            float t = (now - introAt) / 520f;
            x -= Math.round((1 - (1 - (1 - t) * (1 - t))) * s.w * 0.8f);
        }

        float fall = 0f;
        if (finishedAt >= 0 && won) fall = Math.min(1f, (now - finishedAt) / 900f);
        if (monsterHp <= 0 && finishedAt < 0) fall = 0f;

        // 그림자.
        g.setColor(new Color(0, 0, 0, 110));
        int shadowW = Math.round(size * (0.62f - bob * 0.03f));
        g.fillOval(x + size / 2 - shadowW / 2, s.monsterFoot - 8, shadowW, 16);
        // 보스·엘리트 오라.
        if (!online && pattern != MonsterTier.NORMAL && fall < 1f) {
            Color aura = tierColor();
            int pulse = effectsEnabled ? (int) (20 * (1 + Math.sin(now / 220.0))) : 20;
            for (int ring = 0; ring < 3; ring++) {
                g.setColor(new Color(aura.getRed(), aura.getGreen(), aura.getBlue(), Math.max(0, 40 - ring * 12 + pulse / 3)));
                int pad = 10 + ring * 12 + pulse / 4;
                g.fillOval(x - pad + size / 10, y - pad + size / 10, size - size / 5 + pad * 2, size - size / 5 + pad * 2);
            }
        }
        Composite prior = g.getComposite();
        if (fall > 0)
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.30f, 1f - fall * 0.70f)));
        boolean mirrored = !online && !widePve;
        BufferedImage scaled = scaled(art, size, size, mirrored);
        if (attackAge >= 130 && attackAge < IMPACT_MS)
            paintAfterimages(g, scaled, x, y, -1, (attackAge - 130) / (float) (IMPACT_MS - 130));
        paintCombatPose(g, scaled, x, y, size, size, now, attackAge, hitAge, fall, 1,
                finishedAt >= 0 && !won, CORAL);
        g.setComposite(prior);
        if (hit) {
            float flash = 1f - hitAge / 300f;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f * flash));
            paintCombatPose(g, silhouette(scaled), x, y, size, size, now, attackAge, hitAge, fall, 1,
                    false, WHITE);
            g.setComposite(prior);
            paintHitFracture(g, x + size / 2, y + size / 2, size, hitAge, CORAL);
        }
    }

    private void paintPlayer(Graphics2D g, Stage s, long now) {
        int x = s.portraitX, y = s.portraitY, pw = s.portraitW, ph = s.portraitH;
        long attackAge = now - playerAttackAt;
        float pose = effectsEnabled ? attackPose(attackAge) : 0f;
        int advance = attackAdvance(s, false);
        x += (widePve && !online ? 1 : -1) * Math.round(pose * advance);
        y -= attackLift(attackAge, ph);
        if (effectsEnabled && (attackAge < 0 || attackAge >= 720))
            y += Math.round(Math.sin(now / (online ? 380.0 : 430.0)) * (online ? 4 : 2));
        long hitAge = now - playerHitAt;
        boolean hit = playerHitAt >= 0 && hitAge >= 0 && hitAge < 300;
        if (hit) {
            x += hitRecoil(hitAge, online ? Math.max(12, pw / 9) : Math.max(12, pw / 5));
            x += (int) Math.round(Math.sin(hitAge / 14.0) * 3 * (1f - hitAge / 300f));
            y += hitRecoil(hitAge, Math.max(5, ph / 9)) / 2;
        }
        float fall = finishedAt >= 0 && !won ? Math.min(1f, (now - finishedAt) / 900f) : 0f;
        Color trim = feverActive ? PINK : MINT;
        BufferedImage playerArt = poseArt(PLAYER_ART, PLAYER_WINDUP, PLAYER_STRIKE, PLAYER_HURT,
                attackAge, hitAge);
        BufferedImage sprite = playerArt == null ? null : scaled(playerArt, pw, ph, !widePve);
        if (sprite != null && attackAge >= 130 && attackAge < IMPACT_MS)
            paintAfterimages(g, sprite, x, y, 1, (attackAge - 130) / (float) (IMPACT_MS - 130));
        g.setColor(new Color(0, 0, 0, 100));
        g.fillOval(x + pw / 8, s.portraitY + ph - 4, pw * 3 / 4, 11);
        if (PLAYER_IDLE == null) {
            // 이전 초상은 불투명한 액자 그림이므로 테두리를 유지한다.
            g.setColor(UniversityPixelTheme.BLACK);
            g.fillRect(x + 4, y + 4, pw + 4, ph + 4);
            g.setColor(trim);
            g.fillRect(x - 3, y - 3, pw + 6, ph + 6);
            g.setColor(UniversityPixelTheme.BLACK);
            g.fillRect(x - 1, y - 1, pw + 2, ph + 2);
        }
        Composite prior = g.getComposite();
        if (fall > 0) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.30f, 1f - fall * 0.70f)));
        paintCombatPose(g, sprite, x, y, pw, ph, now, attackAge, hitAge, fall, -1,
                finishedAt >= 0 && won, MINT);
        g.setComposite(prior);
        if (hit) paintHitFracture(g, x + pw / 2, y + ph / 2, Math.min(pw, ph), hitAge, CORAL);
        // 이름표.
        g.setFont(UniversityPixelTheme.font(11, Font.BOLD));
        String label = online ? "YOU" : "PLAYER";
        FontMetrics m = g.getFontMetrics();
        g.setColor(trim);
        g.fillRect(x - 3, y + ph + 3, m.stringWidth(label) + 10, 15);
        g.setColor(UniversityPixelTheme.BLACK);
        g.drawString(label, x + 2, y + ph + 14);
    }

    /** 같은 일러스트를 발 고정 변형으로 그려 준비·돌진·경직·회복을 구분한다. */
    private void paintCombatPose(Graphics2D g, BufferedImage sprite, int x, int y, int width, int height,
                                 long now, long attackAge, long hitAge, float knockout,
                                 int facing, boolean victor, Color fallback) {
        float breathe = effectsEnabled && finishedAt < 0 ? (float) Math.sin(now / 520.0) : 0f;
        float scaleX = 1f + breathe * 0.014f;
        float scaleY = 1f - breathe * 0.018f;
        float lean = facing * breathe * 0.012f;
        long placementAt = facing > 0 ? monsterPlacementAt : playerPlacementAt;
        long placementAge = now - placementAt;
        if (effectsEnabled && placementAt >= 0 && placementAge >= 0 && placementAge < 220
                && (attackAge < 0 || attackAge >= 720) && (hitAge < 0 || hitAge >= 300)) {
            float tap = (float) Math.sin(placementAge * Math.PI / 220f);
            scaleX += 0.025f * tap;
            scaleY -= 0.055f * tap;
        }
        if (effectsEnabled && attackAge >= 0 && attackAge < 100) {
            float windup = smooth(attackAge / 100f);
            scaleX += 0.08f * windup;
            scaleY -= 0.11f * windup;
            lean -= facing * 0.075f * windup;
        } else if (effectsEnabled && attackAge >= 100 && attackAge < IMPACT_MS) {
            float strike = smooth((attackAge - 100) / (float) (IMPACT_MS - 100));
            scaleX += 0.06f * (1f - strike);
            scaleY += 0.07f * (float) Math.sin(strike * Math.PI);
            lean += facing * 0.11f * (1f - strike);
        } else if (effectsEnabled && attackAge >= IMPACT_MS && attackAge < 720) {
            // 65ms 접촉 정지는 화면상의 전투원만 멈춘다. 게임 로직/키 입력은 계속 진행한다.
            float settle = attackAge < 425 ? 1f : 1f - smooth((attackAge - 425) / 295f);
            scaleX += 0.035f * settle;
            scaleY -= 0.035f * settle;
            lean += facing * 0.045f * settle;
        }
        if (effectsEnabled && hitAge >= 0 && hitAge < 300) {
            float stagger = 1f - smooth(hitAge / 300f);
            scaleX += 0.11f * stagger;
            scaleY -= 0.16f * stagger;
            lean -= facing * 0.12f * stagger;
        }
        if (knockout > 0f) {
            float collapse = smooth(knockout);
            scaleX += 0.16f * collapse;
            scaleY *= 1f - 0.55f * collapse;
            lean = -facing * 0.18f * collapse;
        }
        int hop = 0;
        if (victor && effectsEnabled && finishedAt >= 0 && now - finishedAt < 900) {
            float progress = (now - finishedAt) / 900f;
            hop = Math.round((float) Math.pow(Math.sin(progress * Math.PI * 2), 2) * height * 0.11f * (1f - progress));
            scaleY += hop / (float) height * 0.3f;
        }
        Graphics2D posed = (Graphics2D) g.create();
        try {
            posed.translate(x + width / 2.0, y + height - hop);
            posed.shear(lean, 0);
            posed.scale(scaleX, scaleY);
            if (sprite != null) posed.drawImage(sprite, -width / 2, -height, null);
            else {
                posed.setColor(fallback);
                posed.fillRect(-width / 2, -height, width, height);
            }
        } finally { posed.dispose(); }
    }

    private void paintVisuals(Graphics2D g, Stage s, long now) {
        for (Visual visual : visuals) {
            long age = now - visual.started;
            int duration = duration(visual.effect);
            if (age < 0 || age > duration) continue;
            float p = age / (float) duration;
            switch (visual.effect) {
                case ATTACK: paintPlayerAttack(g, s, age, visual.serial); break;
                case MONSTER_ATTACK: paintMonsterAttack(g, s, age, visual.serial); break;
                case DAMAGE: paintNumber(g, s, visual, age, "-" + visual.amount,
                        visual.targetMonster ? CYAN : CORAL); break;
                case HEAL:
                    paintHealing(g, visual.targetMonster ? s.monsterX : s.playerCenterX(),
                            visual.targetMonster ? s.monsterCenterY() : s.playerCenterY(), p);
                    paintNumber(g, s, visual, age, "+" + visual.amount, MINT);
                    break;
                case LINE_CLEAR: paintCallout(g, s, visual, age); break;
                case ITEM_ACQUIRE:
                    paintBurst(g, visual.targetMonster ? s.monsterX : s.playerCenterX(),
                            visual.targetMonster ? s.monsterCenterY() : s.portraitY, p, visual.serial, GOLD, 12);
                    paintFloating(g, visual.targetMonster ? s.monsterX : s.playerCenterX() + 20,
                            (visual.targetMonster ? s.monsterCenterY() : s.portraitY) - 10, p, "ITEM GET!", GOLD, 14);
                    break;
                case ITEM_USE: {
                    int tx = visual.targetMonster ? s.monsterX : s.playerCenterX();
                    int ty = visual.targetMonster ? s.monsterCenterY() : s.playerCenterY();
                    Color color = itemColor(visual.itemId);
                    paintItemEffect(g, s.w, s.h, tx, ty, p, visual, color);
                    paintFloating(g, tx, ty - 30, p, itemLabel(visual.itemId), color, 16);
                    break;
                }
                case FEVER: paintFeverBurst(g, s, p, now); break;
                default: break;
            }
        }
    }

    /** 일반 공격은 캐릭터가 직접 들이받고 되돌아오는 근접 타격이다. */
    private void paintPlayerAttack(Graphics2D g, Stage s, long age, int seed) {
        paintHumanAttack(g, s, age, seed, false);
    }

    /** PvP의 두 선수는 동일한 직접 타격 프레임을 좌우 반전해 쓴다. */
    private void paintHumanAttack(Graphics2D g, Stage s, long age, int seed, boolean rival) {
        int direction = widePve && !online ? (rival ? -1 : 1) : (rival ? 1 : -1);
        int actorX = rival ? s.monsterX : s.playerCenterX();
        int actorY = rival ? s.monsterCenterY() : s.playerCenterY();
        int foot = rival || online ? s.monsterFoot : s.portraitY + s.portraitH;
        int targetX = rival ? s.playerCenterX() : s.monsterX;
        int targetY = rival ? s.playerCenterY() : s.monsterCenterY();
        int actorSize = rival ? s.monsterSize : s.portraitW;
        int targetSize = rival ? s.portraitW : s.monsterSize;
        int contactX = targetX - direction * Math.max(8, targetSize / 5);
        int contactY = targetY;
        int travelled = Math.round(attackPose(age) * attackAdvance(s, rival));
        Color color = rival ? CORAL : feverActive ? PINK : CYAN;
        if (age < 105) {
            paintCharge(g, actorX - direction * actorSize / 4, actorY,
                    Math.max(0f, age / 105f), color, seed, Math.max(16, actorSize / 4));
        } else if (age < IMPACT_MS) {
            // 잔광은 전투원의 실제 이동 거리 안에만 남는다. 날아가는 탄환은 없다.
            float travel = Math.max(0f, Math.min(1f, (age - 105) / 175f));
            int trailX = actorX + direction * travelled;
            paintDashTrail(g, actorX, trailX, actorY + actorSize / 6,
                    foot, color, travel, direction);
            if (age >= 275) paintMeleeArc(g, contactX, contactY,
                    Math.max(22, targetSize * 2 / 3), (age - 275) / 85f, color, direction);
        }
        if (age >= IMPACT_MS) {
            float p = Math.min(1f, (age - IMPACT_MS) / 340f);
            // 처음 70ms 동안 타격 장면을 붙잡고, 이후에는 회복 동작과 파편을 보여 준다.
            if (p < 0.21f) paintMeleeArc(g, contactX, contactY,
                    Math.max(24, targetSize * 3 / 4), p / 0.21f, WHITE, direction);
            paintBurst(g, contactX, contactY, p, seed, color, 18);
            paintBlockDebris(g, contactX, contactY, p, seed);
            paintShockwave(g, contactX, contactY, p, Math.round(targetSize * 0.75f), color, seed);
            if (p < 0.25f) paintHitFracture(g, targetX, targetY, targetSize, Math.round(p * 340), color);
        }
    }

    /** 두 몸체가 실제로 겹칠 만큼만 전진한다. 좁은 PvP 무대에서도 화면 밖으로 나가지 않는다. */
    private static int attackAdvance(Stage s, boolean rival) {
        int actor = rival ? s.monsterSize : s.portraitW;
        int target = rival ? s.portraitW : s.monsterSize;
        int gap = Math.abs(s.playerCenterX() - s.monsterX);
        return Math.max(12, Math.min(Math.round(s.w * 0.29f), gap - actor / 4 - target / 4));
    }

    private static BufferedImage poseArt(BufferedImage base, BufferedImage windup, BufferedImage strike,
                                         BufferedImage hurt, long attackAge, long hitAge) {
        if (hitAge >= 0 && hitAge < 300 && hurt != null) return hurt;
        if (attackAge >= 0 && attackAge < 130 && windup != null) return windup;
        if (attackAge >= 130 && attackAge < 425 && strike != null) return strike;
        return base;
    }

    private static void paintDashTrail(Graphics2D g, int originX, int headX, int bodyY, int foot,
                                       Color color, float progress, int direction) {
        int tail = originX - direction * 9;
        int alpha = Math.round(170 * (0.45f + progress * 0.55f));
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha / 3));
        g.fillPolygon(new int[] { tail, headX, headX - direction * 8, tail },
                new int[] { bodyY - 12, bodyY - 5, bodyY + 9, bodyY + 14 }, 4);
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
        g.fillRect(Math.min(tail, headX), foot - 7, Math.max(2, Math.abs(headX - tail)), 3);
        g.setColor(new Color(255, 247, 232, Math.max(0, alpha - 45)));
        g.fillRect(headX - direction * 8, bodyY + 3, 8, 3);
    }

    /** 무기/주먹의 짧은 궤적. 타격 범위에만 그려 원거리 탄환으로 보이지 않게 한다. */
    private static void paintMeleeArc(Graphics2D g, int x, int y, int radius, float p,
                                      Color color, int direction) {
        float grow = smooth(p);
        int alpha = Math.max(0, Math.round(245 * (1f - grow * 0.8f)));
        int reach = Math.max(10, Math.round(radius * (0.30f + grow * 0.70f)));
        Stroke previous = g.getStroke();
        g.setStroke(new BasicStroke(Math.max(3f, radius / 10f), BasicStroke.CAP_SQUARE,
                BasicStroke.JOIN_BEVEL));
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
        g.drawLine(x - direction * reach / 2, y - reach, x + direction * reach / 2, y + reach);
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(255, 247, 232, alpha));
        g.drawLine(x - direction * reach / 2 - direction * 3, y - reach,
                x + direction * reach / 2 - direction * 3, y + reach);
        g.setStroke(previous);
    }

    /** 피격체 표면의 금과 밝은 접촉점. 실제 피해를 받은 순간부터만 보인다. */
    private static void paintHitFracture(Graphics2D g, int x, int y, int size, long hitAge, Color color) {
        if (hitAge < 0 || hitAge >= 180) return;
        float fade = 1f - hitAge / 180f;
        int reach = Math.max(10, Math.round(size * (0.21f + hitAge / 700f)));
        g.setColor(new Color(255, 247, 232, Math.round(210 * fade)));
        g.fillRect(x - 5, y - 5, 10, 10);
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(240 * fade)));
        g.drawLine(x - reach, y - reach / 2, x - 4, y - 2);
        g.drawLine(x + 4, y + 2, x + reach, y + reach / 2);
        g.drawLine(x - reach / 2, y + reach, x - 2, y + 4);
        g.drawLine(x + 2, y - 4, x + reach / 2, y - reach);
    }

    /** PvE 일반/엘리트는 직접 공격하고 보스만 원거리 기술을 쓴다. */
    private void paintMonsterAttack(Graphics2D g, Stage s, long age, int seed) {
        if (online) {
            paintHumanAttack(g, s, age, seed, true);
            return;
        }
        if (pattern != MonsterTier.BOSS) {
            paintHumanAttack(g, s, age, seed, true);
            return;
        }
        paintBossProjectile(g, s, age, seed);
    }

    private void paintBossProjectile(Graphics2D g, Stage s, long age, int seed) {
        Color motif = "graduation".equals(chapter) ? PINK
                : "employment".equals(chapter) ? CYAN : GOLD;
        boolean boss = pattern == MonsterTier.BOSS;
        int fromX = s.monsterX + s.monsterSize / 4, fromY = s.monsterCenterY();
        int toX = s.playerCenterX(), toY = s.playerCenterY();
        if (age < 140) paintCharge(g, fromX, fromY, age / 140f, motif, seed, boss ? 32 : 27);
        paintArcStreak(g, fromX, fromY, toX, toY, age, 140, seed, motif, -30);
        if (age >= 80 && age < IMPACT_MS) {
            float t = (age - 80) / (float) (IMPACT_MS - 80);
            float ease = t * t;
            int x = Math.round(fromX + (toX - fromX) * ease);
            int y = Math.round(fromY + (toY - fromY) * ease - (float) Math.sin(t * Math.PI) * 30);
            for (int trail = 5; trail >= 0; trail--) {
                float tt = Math.max(0, t - trail * 0.05f), te = tt * tt;
                int tx = Math.round(fromX + (toX - fromX) * te);
                int ty = Math.round(fromY + (toY - fromY) * te - (float) Math.sin(tt * Math.PI) * 30);
                int size = 20 - trail * 3;
                g.setColor(new Color(motif.getRed(), motif.getGreen(), motif.getBlue(), 200 - trail * 30));
                g.fillRect(tx - size / 2, ty - size / 2, size, size);
            }
            int orb = clampSize(s.w / 14, 16, 28);
            g.setColor(new Color(motif.getRed(), motif.getGreen(), motif.getBlue(), 70));
            g.fillOval(x - orb, y - orb, orb * 2, orb * 2);
            paintMonsterProjectile(g, x, y, orb, motif, boss);
        }
        if (age >= IMPACT_MS) {
            float p = Math.min(1f, (age - IMPACT_MS) / 360f);
            paintShockwave(g, toX, toY, p, Math.round(s.portraitW * (boss ? 1.32f : 1.15f)), motif, seed);
            paintSlash(g, toX, toY, Math.round(s.portraitW * 0.75f), p, motif,
                    "university".equals(chapter));
            paintBurst(g, toX, toY, p, seed, motif, boss ? 20 : 12);
            if ("graduation".equals(chapter)) paintBlockDebris(g, toX, toY, p, seed);
        }
    }

    private void paintMonsterProjectile(Graphics2D g, int x, int y, int size, Color motif, boolean boss) {
        int half = size / 2;
        if ("graduation".equals(chapter)) {
            // 논문/재수강 F를 상징하는 접힌 문서.
            g.setColor(WHITE);
            g.fillRect(x - half, y - half, size, size);
            g.setColor(motif);
            g.drawRect(x - half, y - half, size, size);
            g.fillRect(x - half + 4, y - half + 4, size / 2, 3);
            g.fillRect(x - half + 4, y - half + 8, 3, size / 2);
        } else if ("employment".equals(chapter)) {
            // 코딩 테스트/면접의 꺾쇠 기호.
            g.setColor(new Color(6, 16, 39));
            g.fillRect(x - half, y - half, size, size);
            g.setColor(motif);
            g.drawRect(x - half, y - half, size, size);
            g.drawLine(x - 3, y - 7, x - 9, y);
            g.drawLine(x - 9, y, x - 3, y + 7);
            g.drawLine(x + 3, y - 7, x + 9, y);
            g.drawLine(x + 9, y, x + 3, y + 7);
        } else {
            // 교정 분필과 강의 보드의 가장 짧은 형태.
            g.setColor(new Color(27, 20, 48));
            g.fillRect(x - half, y - half, size, size);
            g.setColor(motif);
            g.drawRect(x - half, y - half, size, size);
            g.setColor(WHITE);
            g.fillRect(x - half + 3, y - 3, size - 6, 6);
        }
        if (boss) {
            g.setColor(CORAL);
            drawDiamond(g, x, y, half + 7);
        }
    }

    /** 공격 시작점의 픽셀 링과 안쪽으로 모이는 불꽃. 공격 중에만 그린다. */
    private static void paintCharge(Graphics2D g, int x, int y, float p, Color color, int seed, int radius) {
        int alpha = Math.round(185 * p);
        int outer = Math.round(radius * (1f - 0.42f * p));
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
        g.drawRect(x - outer, y - outer, outer * 2, outer * 2);
        g.drawRect(x - outer + 3, y - outer + 3, (outer - 3) * 2, (outer - 3) * 2);
        for (int i = 0; i < 8; i++) {
            double angle = (i + seed % 8) * Math.PI / 4;
            int distance = Math.round(radius * (1.5f - p));
            int px = x + (int) Math.round(Math.cos(angle) * distance);
            int py = y + (int) Math.round(Math.sin(angle) * distance);
            g.fillRect(px - 2, py - 2, 5, 5);
        }
        int core = Math.round(5 + 14 * p);
        g.setColor(new Color(255, 247, 232, Math.round(230 * p)));
        g.fillRect(x - core / 2, y - core / 2, core, core);
    }

    /** 여러 조각의 이동을 묶어 주는 짧은 발광 궤적. 착탄 뒤에는 남지 않는다. */
    private static void paintArcStreak(Graphics2D g, int fromX, int fromY, int toX, int toY,
                                       long age, int startMs, int seed, Color color, int arc) {
        if (age < startMs || age >= IMPACT_MS) return;
        float t = (age - startMs) / (float) (IMPACT_MS - startMs);
        float head = t * t * (3f - 2f * t);
        float tail = Math.max(0f, head - 0.25f);
        int hx = Math.round(fromX + (toX - fromX) * head);
        int hy = Math.round(fromY + (toY - fromY) * head - (float) Math.sin(head * Math.PI) * arc);
        int tx = Math.round(fromX + (toX - fromX) * tail);
        int ty = Math.round(fromY + (toY - fromY) * tail - (float) Math.sin(tail * Math.PI) * arc);
        Stroke prior = g.getStroke();
        g.setStroke(new BasicStroke(11f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 65));
        g.drawLine(tx, ty, hx, hy);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 205));
        g.drawLine(tx, ty, hx, hy);
        g.setStroke(prior);
        g.setColor(WHITE);
        g.fillRect(hx - 4, hy - 4, 8, 8);
        // 빛줄기 양옆의 점은 매 프레임 같은 좌표를 써서 불규칙하게 깜박이지 않는다.
        int offset = seed % 2 == 0 ? 9 : -9;
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 180));
        g.fillRect(hx - 2, hy + offset - 2, 4, 4);
    }

    /** 착탄 순간 두 겹의 각진 파문과 방사형 파편을 만든다. */
    private static void paintShockwave(Graphics2D g, int x, int y, float p, int radius, Color color, int seed) {
        if (p >= 0.8f) return;
        float fade = 1f - p / 0.8f;
        int outer = Math.round(radius * (0.18f + p));
        int inner = Math.max(3, Math.round(outer * 0.64f));
        Stroke prior = g.getStroke();
        g.setStroke(new BasicStroke(4f));
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(190 * fade)));
        drawDiamond(g, x, y, outer);
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(255, 247, 232, Math.round(225 * fade)));
        drawDiamond(g, x, y, inner);
        g.setStroke(prior);
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(220 * fade)));
        for (int i = 0; i < 8; i++) {
            double angle = (i + (seed % 3) * 0.2) * Math.PI / 4;
            int near = outer + 4;
            int far = outer + Math.round(14 * fade);
            g.drawLine(x + (int) (Math.cos(angle) * near), y + (int) (Math.sin(angle) * near),
                    x + (int) (Math.cos(angle) * far), y + (int) (Math.sin(angle) * far));
        }
    }

    private static void drawDiamond(Graphics2D g, int x, int y, int radius) {
        g.drawLine(x, y - radius, x + radius, y);
        g.drawLine(x + radius, y, x, y + radius);
        g.drawLine(x, y + radius, x - radius, y);
        g.drawLine(x - radius, y, x, y - radius);
    }

    private static int clampSize(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private static void paintShards(Graphics2D g, int fromX, int fromY, int toX, int toY,
                                    long age, int startMs, int endMs, int seed, int arc, int shard) {
        for (int i = 0; i < 5; i++) {
            long local = age - startMs - i * 26;
            float t = local / (float) (endMs - startMs - 4 * 26);
            if (t <= 0f || t >= 1f) continue;
            float ease = t < .5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
            int height = 50 + (i % 3) * 22;
            for (int trail = 4; trail >= 0; trail--) {
                float tt = Math.max(0f, ease - trail * 0.045f);
                int x = Math.round(fromX + (toX - fromX) * tt);
                int y = Math.round(fromY + (toY - fromY) * tt + arc * (float) Math.sin(tt * Math.PI) * height);
                Color color = SHARDS[(i + seed) % SHARDS.length];
                int size = trail == 0 ? shard : Math.max(3, shard - 2 - trail * 3);
                g.setColor(trail == 0 ? color : new Color(color.getRed(), color.getGreen(), color.getBlue(), 160 - trail * 35));
                g.fillRect(x - size / 2, y - size / 2, size, size);
                if (trail == 0) {
                    g.setColor(color.brighter());
                    g.fillRect(x - size / 2 + 1, y - size / 2 + 1, size - 4, 3);
                    g.setColor(UniversityPixelTheme.BLACK);
                    g.drawRect(x - size / 2, y - size / 2, size, size);
                }
            }
        }
    }

    private static void paintBlockDebris(Graphics2D g, int x, int y, float p, int seed) {
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2 + (i - 4.5) * 0.32 + seed * 0.1;
            float speed = 60 + (i % 3) * 25;
            int px = x + (int) (Math.cos(angle) * speed * p);
            int py = y + (int) (Math.sin(angle) * speed * p + 120 * p * p);
            Color color = SHARDS[(i + seed) % SHARDS.length];
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, (int) (255 * (1 - p)))));
            g.fillRect(px - 3, py - 3, 7, 7);
        }
    }

    private static void paintSlash(Graphics2D g, int x, int y, int length, float p, Color color, boolean claws) {
        if (p > 0.85f) return;
        Stroke prior = g.getStroke();
        float grow = Math.min(1f, p * 4f);
        int alpha = (int) (255 * (1f - p / 0.85f));
        int lines = claws ? 3 : 2;
        for (int i = 0; i < lines; i++) {
            int offset = (i - (lines - 1) / 2) * 14;
            int sx = x - length / 2 + offset, sy = y - length / 2;
            int ex = Math.round(sx + length * grow), ey = Math.round(sy + length * grow);
            if (!claws && i == 1) { sx = x + length / 2; ex = Math.round(sx - length * grow); }
            g.setStroke(new BasicStroke(claws ? 5f : 6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            g.setColor(new Color(0, 0, 0, alpha / 2));
            g.drawLine(sx + 2, sy + 2, ex + 2, ey + 2);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            g.drawLine(sx, sy, ex, ey);
            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(255, 255, 255, alpha));
            g.drawLine(sx, sy, ex, ey);
        }
        g.setStroke(prior);
    }

    private void paintNumber(Graphics2D g, Stage s, Visual visual, long age, String text, Color color) {
        int x = visual.targetMonster ? s.monsterX + s.monsterSize / 5 : s.playerCenterX();
        int y = visual.targetMonster ? s.monsterCenterY() - s.monsterSize / 6 : s.portraitY + 10;
        float pop = age < 120 ? 1.9f - 0.9f * (age / 120f) : 1f;
        float rise = Math.max(0, age - 300) / 600f;
        int alpha = age < 650 ? 255 : (int) Math.max(0, 255 * (1 - (age - 650) / 250f));
        boolean big = visual.amount >= 10;
        int size = Math.max(18, Math.min(40, (visual.targetMonster ? 28 : 24) + visual.amount / 2));
        drawPop(g, text, x, y - Math.round(rise * 28), size, pop, color, alpha);
        if (big && visual.effect == Effect.DAMAGE)
            drawPop(g, visual.targetMonster ? "HIT!" : "OUCH!", x, y - Math.round(rise * 28) - size,
                    12, pop, WHITE, alpha);
    }

    private void paintCallout(Graphics2D g, Stage s, Visual visual, long age) {
        String text = visual.perfect ? "PERFECT!" : visual.amount >= 4 ? "TETRIS!"
                : visual.amount == 3 ? "TRIPLE" : visual.amount == 2 ? "DOUBLE" : "SINGLE";
        boolean enemy = visual.targetMonster;
        Color color = visual.perfect ? PINK : enemy ? CORAL : visual.amount >= 4 ? GOLD : MINT;
        int base = enemy ? 18 : visual.amount >= 4 || visual.perfect ? 40 : 30 + visual.amount * 2;
        base = Math.min(base, Math.max(16, s.w / 7));
        float pop = age < 140 ? 0.4f + 0.85f * (age / 140f) : age < 220 ? 1.25f - 0.25f * ((age - 140) / 80f) : 1f;
        int alpha = age < 750 ? 255 : (int) Math.max(0, 255 * (1 - (age - 750) / 250f));
        int y = enemy ? s.top + 28 : s.calloutY();
        int x = enemy ? 14 + textWidth(g, text, base) / 2 : s.w / 2;
        if (!enemy && (visual.amount >= 4 || visual.perfect) && age < 500) {
            // 테트리스는 화면을 가로지르는 금빛 띠를 함께 그린다.
            float band = age / 500f;
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (90 * (1 - band))));
            g.fillRect(0, y - base, s.w, base + 14);
        }
        drawPop(g, text, x, y, base, pop, color, alpha);
        if (visual.combo > 0)
            drawPop(g, visual.combo + " COMBO", x, y + Math.round(base * 0.75f), Math.max(12, base / 2), pop, WHITE, alpha);
    }

    private void paintFeverBurst(Graphics2D g, Stage s, float p, long now) {
        int cx = s.w / 2, cy = s.calloutY();
        Stroke prior = g.getStroke();
        g.setStroke(new BasicStroke(3f));
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8 + p * 1.5;
            int inner = Math.round(20 + 40 * p), outer = Math.round(inner + 60 * (1 - p) + 20);
            g.setColor(new Color(255, 146, 226, (int) (200 * (1 - p))));
            g.drawLine(cx + (int) (Math.cos(angle) * inner), cy + (int) (Math.sin(angle) * inner),
                    cx + (int) (Math.cos(angle) * outer), cy + (int) (Math.sin(angle) * outer));
        }
        g.setStroke(prior);
        float pop = p < 0.12f ? 0.5f + p / 0.12f * 0.7f : 1.2f - Math.min(0.2f, (p - 0.12f));
        drawPop(g, "FEVER!!", cx, cy, Math.min(42, s.w / 6), pop, PINK, (int) (255 * Math.min(1f, (1 - p) * 3)));
    }

    private static void paintHealing(Graphics2D g, int x, int y, float p) {
        for (int i = 0; i < 6; i++) {
            int crossX = x - 30 + i * 12;
            int crossY = y + 30 - Math.round(70 * p) - (i % 2) * 14;
            g.setColor(new Color(78, 227, 154, (int) (255 * (1 - p))));
            g.fillRect(crossX + 3, crossY, 4, 14);
            g.fillRect(crossX - 2, crossY + 5, 14, 4);
        }
    }

    private void paintHitFlash(Graphics2D g, int w, int h, long now) {
        if (playerHitAt >= 0 && now - playerHitAt >= 0 && now - playerHitAt < 260) {
            float t = 1f - (now - playerHitAt) / 260f;
            g.setColor(new Color(243, 116, 94, (int) (112 * t)));
            g.fillRect(online ? w / 2 : 0, 0, online ? w - w / 2 : w, h);
            g.setColor(new Color(255, 207, 172, (int) (220 * t)));
            g.fillRect(online ? w / 2 : w - 5, 0, 5, h);
        }
        if (monsterHitAt >= 0 && now - monsterHitAt >= 0 && now - monsterHitAt < 240) {
            float t = 1f - (now - monsterHitAt) / 240f;
            g.setColor(new Color(255, 240, 184, Math.max(0, (int) (105 * t))));
            g.fillRect(0, 0, online ? w / 2 : w, h);
            g.setColor(new Color(255, 231, 156, (int) (210 * t)));
            g.fillRect(0, 0, 5, h);
        }
    }

    /** HP가 30% 이하이면 화면 가장자리에 붉은 맥박을 준다. */
    private void paintDanger(Graphics2D g, int w, int h, long now) {
        if (!initialized || playerHp <= 0 || playerHp * 10 > playerMax * 3 || finishedAt >= 0) return;
        int alpha = effectsEnabled ? (int) (70 + 60 * Math.sin(now / 160.0)) : 90;
        for (int i = 0; i < 4; i++) {
            g.setColor(new Color(230, 119, 99, Math.max(0, alpha - i * 22)));
            g.fillRect(i * 4, 0, 4, h);
            g.fillRect(w - (i + 1) * 4, 0, 4, h);
            g.fillRect(0, i * 4, w, 4);
            g.fillRect(0, h - (i + 1) * 4, w, 4);
        }
    }

    private void paintPlates(Graphics2D g, Stage s, long now) {
        if (widePve && !online) {
            int playerMain = shownMain(playerTrailFrom, playerHp, playerTrailAt, now);
            int playerTrail = shownTrail(playerTrailFrom, playerHp, playerTrailAt, now);
            int monsterMain = shownMain(monsterTrailFrom, monsterHp, monsterTrailAt, now);
            int monsterTrail = shownTrail(monsterTrailFrom, monsterHp, monsterTrailAt, now);

            int hpWidth = Math.min(150, Math.max(110, s.w / 8));
            int playerX = Math.max(6, Math.min(s.w - hpWidth - 6, s.playerCenterX() - hpWidth / 2));
            int monsterX = Math.max(6, Math.min(s.w - hpWidth - 6, s.monsterX - hpWidth / 2));
            int playerY = Math.max(48, s.portraitY - 42);
            int monsterY = Math.max(48, s.monsterFoot - s.monsterSize - 42);

            paintReferenceHpBar(g, playerX, playerY, hpWidth, playerMain, playerTrail, playerHp, playerMax, false);
            paintReferenceHpBar(g, monsterX, monsterY, hpWidth, monsterMain, monsterTrail, monsterHp, monsterMax, true);
            return;
        }
        int monsterMain = shownMain(monsterTrailFrom, monsterHp, monsterTrailAt, now);
        int monsterTrail = shownTrail(monsterTrailFrom, monsterHp, monsterTrailAt, now);
        String tier = online ? "RIVAL" : pattern == MonsterTier.BOSS ? "BOSS" : pattern == MonsterTier.ELITE ? "ELITE" : "MONSTER";
        paintHpPlate(g, 8, 8, s.w - 16, 46, monsterName, tier, monsterMain, monsterTrail, monsterHp, monsterMax,
                CORAL, now - monsterHitAt >= 0 && now - monsterHitAt < 200 && monsterHitAt >= 0);
        int playerMain = shownMain(playerTrailFrom, playerHp, playerTrailAt, now);
        int playerTrail = shownTrail(playerTrailFrom, playerHp, playerTrailAt, now);
        paintHpPlate(g, 8, s.h - 52, s.w - 16, 44, playerName, feverActive ? "FEVER" : online ? "YOU" : "HP", playerMain, playerTrail,
                playerHp, playerMax, feverActive ? PINK : MINT,
                now - playerHitAt >= 0 && now - playerHitAt < 200 && playerHitAt >= 0);
    }

    /** PvE HP: 흰색 테두리 숫자 + 도톰한 녹색/빨간색 캡슐 게이지. */
    private static void paintReferenceHpBar(Graphics2D graphics, int x, int y, int width,
                                            int main, int trail, int hp, int max, boolean enemy) {
        if (width <= 18) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int maxHp = Math.max(1, max);
            String numbers = Math.max(0, hp) + "/" + maxHp;
            g.setFont(UniversityPixelTheme.font(15, Font.BOLD));
            FontMetrics fm = g.getFontMetrics();
            int textX = x + (width - fm.stringWidth(numbers)) / 2;
            int textY = y + fm.getAscent();
            g.setColor(new Color(0x1F1D1B));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) g.drawString(numbers, textX + dx, textY + dy);
                }
            }
            g.setColor(Color.WHITE);
            g.drawString(numbers, textX, textY);

            int bx = x + 1, barY = y + 21, bw = width - 2, barH = 20;
            int innerX = bx + 3, innerY = barY + 3, innerW = bw - 6, innerH = barH - 6;
            g.setColor(new Color(0x101818));
            g.fillRoundRect(bx, barY, bw, barH, 18, 18);
            g.setColor(new Color(0x3B4142));
            g.fillRoundRect(innerX, innerY, innerW, innerH, 13, 13);

            java.awt.Shape oldClip = g.getClip();
            g.clip(new java.awt.geom.RoundRectangle2D.Float(innerX, innerY, innerW, innerH, 13, 13));
            int trailW = (int) Math.round(innerW * Math.max(0, Math.min(maxHp, trail)) / (double) maxHp);
            int mainW = (int) Math.round(innerW * Math.max(0, Math.min(maxHp, main)) / (double) maxHp);
            g.setColor(new Color(0xF1E8C9));
            g.fillRect(innerX, innerY, trailW, innerH);
            g.setColor(enemy ? new Color(0xF35B50) : new Color(0x87E765));
            g.fillRect(innerX, innerY, mainW, innerH);
            g.setColor(new Color(255, 255, 255, 78));
            g.fillRect(innerX, innerY + 1, mainW, Math.max(2, innerH / 3));
            g.setClip(oldClip);
        } finally {
            g.dispose();
        }
    }

    private static void paintHpPlate(Graphics2D g, int x, int y, int width, int height, String name, String tag,
                                     int main, int trail, int hp, int max, Color fill, boolean flash) {
        g.setColor(UniversityPixelTheme.BLACK);
        g.fillRect(x, y, width, height);
        g.setColor(flash ? new Color(0xF4D6CB) : UniversityPixelTheme.PANEL);
        g.fillRect(x + 2, y + 2, width - 4, height - 4);
        g.setFont(UniversityPixelTheme.font(10, Font.BOLD));
        FontMetrics tagMetrics = g.getFontMetrics();
        int tagWidth = tagMetrics.stringWidth(tag) + 10;
        g.setColor(fill);
        g.fillRect(x + 6, y + 6, tagWidth, 14);
        g.setColor(UniversityPixelTheme.readableOn(fill));
        g.drawString(tag, x + 11, y + 17);
        boolean compact = width < 200;
        g.setFont(UniversityPixelTheme.font(compact ? 11 : 13, Font.BOLD));
        FontMetrics metrics = g.getFontMetrics();
        String numbers = compact ? main + "/" + max : main + " / " + max;
        int numbersWidth = metrics.stringWidth(numbers);
        int numbersX = x + width - numbersWidth - 8;
        int nameX = x + tagWidth + 12;
        if (!compact && numbersX > nameX) {
            String fittedName = fit(name, metrics, numbersX - nameX - 5);
            if (!fittedName.isEmpty()) {
                g.setColor(UniversityPixelTheme.TEXT);
                g.drawString(fittedName, nameX, y + 18);
            }
        }
        g.setColor(main * 10 <= max * 3 ? CORAL : UniversityPixelTheme.TEXT_SUB);
        g.drawString(numbers, numbersX, y + 18);
        int barX = x + 6, barY = y + 24, barW = width - 12, barH = height - 30;
        g.setColor(UniversityPixelTheme.BLACK);
        g.fillRect(barX, barY, barW, barH);
        g.setColor(UniversityPixelTheme.PANEL_LIGHT);
        g.fillRect(barX + 2, barY + 2, barW - 4, barH - 4);
        int inner = barW - 4;
        int trailW = (int) ((long) inner * Math.max(0, trail) / max);
        int mainW = (int) ((long) inner * Math.max(0, main) / max);
        g.setColor(WHITE);
        g.fillRect(barX + 2, barY + 2, trailW, barH - 4);
        Color barColor = main * 10 <= max * 3 ? CORAL : fill;
        g.setColor(barColor);
        g.fillRect(barX + 2, barY + 2, mainW, barH - 4);
        g.setColor(new Color(255, 255, 255, 80));
        g.fillRect(barX + 2, barY + 2, mainW, Math.max(1, (barH - 4) / 3));
        g.setColor(new Color(0, 0, 0, 90));
        for (int tick = barX + 2 + inner / 10; tick < barX + barW - 2; tick += Math.max(4, inner / 10))
            g.fillRect(tick, barY + 2, 1, barH - 4);
    }

    /** 전투 시작 안내: 상대 이름과 READY → FIGHT! */
    private void paintIntro(Graphics2D g, Stage s, long now) {
        // PvE는 중앙 보드가 무대 위에 놓여 READY/FIGHT 배너가 뒤에 가려진다.
        // 진행을 가리는 중복 연출을 제거하고 온라인 대전의 시작 연출만 유지한다.
        if (widePve && !online) return;
        if (introAt < 0 || finishedAt >= 0) return;
        long age = now - introAt;
        if (age > 1500) { introAt = -1; return; }
        int y = s.calloutY() + 10;
        int bandH = Math.min(64, s.w / 4);
        float open = Math.min(1f, age / 180f), close = age > 1250 ? (age - 1250) / 250f : 0f;
        int bandW = Math.round(s.w * open * (1 - close));
        g.setColor(new Color(3, 3, 13, 220));
        g.fillRect((s.w - bandW) / 2, y - bandH + 12, bandW, bandH);
        g.setColor(GOLD);
        g.fillRect((s.w - bandW) / 2, y - bandH + 12, bandW, 3);
        g.fillRect((s.w - bandW) / 2, y + 9, bandW, 3);
        if (close > 0) return;
        if (age < 700) {
            drawPop(g, "VS  " + monsterName, s.w / 2, y - bandH + 30, 13, 1f, UniversityPixelTheme.TEXT_ON_DARK, Math.min(255, (int) (age * 2)));
            drawPop(g, "READY?", s.w / 2, y, Math.min(40, s.w / 6), age < 140 ? 0.6f + age / 350f : 1f, WHITE, 255);
        } else {
            long fight = age - 700;
            float pop = fight < 120 ? 2.2f - 1.2f * (fight / 120f) : 1f;
            drawPop(g, "FIGHT!", s.w / 2, y, Math.min(46, s.w / 5), pop, GOLD, 255);
        }
    }

    private void paintFinish(Graphics2D g, Stage s, long now) {
        if (finishedAt < 0) return;
        long age = now - finishedAt;
        if (!effectsEnabled) age = 2000;
        float p = Math.min(1f, age / 500f);
        g.setColor(new Color(5, 4, 20, Math.round(120 * p)));
        g.fillRect(0, s.top - 4, s.w, s.bottom - s.top + 8);
        String text = won ? (online ? "WIN!" : "K.O.!") : "DEFEAT";
        Color color = won ? GOLD : CORAL;
        int y = s.calloutY() + 10;
        float band = Math.min(1f, age / 220f);
        g.setColor(UniversityPixelTheme.BLACK);
        int bandH = Math.min(70, s.w / 4);
        g.fillRect(0, y - bandH + 12, Math.round(s.w * band), bandH);
        g.setColor(color);
        g.fillRect(0, y - bandH + 12, Math.round(s.w * band), 3);
        g.fillRect(s.w - Math.round(s.w * band), y + 9, Math.round(s.w * band), 3);
        float pop = age < 160 ? 2f - age / 160f : 1f;
        drawPop(g, text, s.w / 2, y, Math.min(48, s.w / 5), pop, color, 255);
        if (won && age < 1200) paintBurst(g, s.w / 2, y - 14, Math.min(1f, age / 1200f), 3, GOLD, 24);
    }

    private void paintFrame(Graphics2D g, int w, int h, long now) {
        Color frame = feverActive && effectsEnabled
                ? Color.getHSBColor((now % 1500) / 1500f, 0.55f, 1f) : UniversityPixelTheme.LINE;
        g.setColor(UniversityPixelTheme.BLACK);
        g.drawRect(0, 0, w - 1, h - 1);
        g.setColor(frame);
        g.drawRect(1, 1, w - 3, h - 3);
        if (feverActive) g.drawRect(2, 2, w - 5, h - 5);
    }

    private static void paintBurst(Graphics2D g, int x, int y, float progress,
                                   int serial, Color color, int count) {
        int opacity = Math.max(0, Math.round(255 * (1f - progress)));
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), opacity));
        for (int index = 0; index < count; index++) {
            double angle = (index * 6.28318 / count) + serial * .27;
            int distance = 8 + Math.round(60 * progress * (0.64f + (index % 4) * .13f));
            int px = x + (int) (Math.cos(angle) * distance);
            int py = y + (int) (Math.sin(angle) * distance);
            int size = Math.max(2, 7 - Math.round(progress * 5));
            g.fillRect(px, py, size, size);
        }
        if (progress < .4f) {
            int radius = 10 + Math.round(progress * 90);
            Stroke prior = g.getStroke();
            g.setStroke(new BasicStroke(3f));
            g.drawOval(x - radius, y - radius, radius * 2, radius * 2);
            g.setStroke(prior);
        }
    }

    private static void paintItemEffect(Graphics2D g, int w, int h, int x, int y,
                                        float progress, Visual visual, Color color) {
        String id = visual.itemId;
        g.setColor(color);
        if ("heal".equals(id)) {
            paintHealing(g, x, y, progress);
        } else if ("shield".equals(id)) {
            int radius = 35 + Math.round(18 * progress);
            Stroke prior = g.getStroke();
            g.setStroke(new BasicStroke(3f));
            g.drawOval(x - radius, y - radius, radius * 2, radius * 2);
            g.drawOval(x - radius + 6, y - radius + 6, (radius - 6) * 2, (radius - 6) * 2);
            g.setStroke(prior);
        } else if ("damage_boost".equals(id)) {
            for (int i = 0; i < 4; i++) {
                int baseY = y + 30 - i * 16 - Math.round(progress * 30);
                g.fillPolygon(new int[] { x - 12, x, x + 12 }, new int[] { baseY, baseY - 12, baseY }, 3);
            }
        } else if ("garbage_bomb".equals(id)) {
            paintBurst(g, x, y, progress, visual.serial, color, 22);
            paintBlockDebris(g, x, y, progress, visual.serial);
        } else if ("line_cleaner".equals(id)) {
            int sweep = (int) ((w + 90) * progress) - 45;
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 90));
            g.fillRect(0, y - 8, sweep, 16);
            g.setColor(color);
            g.fillRect(sweep - 3, y - 20, 6, 40);
        } else if ("fever_charge".equals(id)) {
            paintBurst(g, x, y, progress, visual.serial, color, 18);
        } else if ("time_warp".equals(id)) {
            int radius = 30 + Math.round(progress * 16);
            Stroke prior = g.getStroke();
            g.setStroke(new BasicStroke(3f));
            g.drawOval(x - radius, y - radius, radius * 2, radius * 2);
            double hand = progress * Math.PI * 4;
            g.drawLine(x, y, x + (int) (Math.cos(hand) * (radius - 8)), y + (int) (Math.sin(hand) * (radius - 8)));
            g.setStroke(prior);
        } else if ("nullify".equals(id)) {
            int radius = 18 + Math.round(34 * progress);
            Stroke prior = g.getStroke();
            g.setStroke(new BasicStroke(4f));
            g.drawLine(x - radius, y - radius, x + radius, y + radius);
            g.drawLine(x + radius, y - radius, x - radius, y + radius);
            g.setStroke(prior);
        } else {
            paintBurst(g, x, y, progress, visual.serial, color, 10);
        }
    }

    private static void paintFloating(Graphics2D g, int x, int y, float p, String text, Color color, int size) {
        int alpha = p < 0.8f ? 255 : (int) (255 * (1 - (p - 0.8f) / 0.2f));
        drawPop(g, text, x, y - Math.round(24 * p), size, 1f, color, alpha);
    }

    /** 검은 외곽선을 두른 큰 글자. 가운데 정렬, scale은 팝업 효과용. */
    private static void drawPop(Graphics2D g, String text, int cx, int baseline, int size, float scale, Color color, int alpha) {
        if (alpha <= 0) return;
        Graphics2D t = (Graphics2D) g.create();
        try {
            t.setFont(UniversityPixelTheme.font(size, Font.BOLD));
            FontMetrics m = t.getFontMetrics();
            int width = m.stringWidth(text);
            AffineTransform at = new AffineTransform();
            at.translate(cx, baseline - m.getAscent() / 3.0);
            at.scale(scale, scale);
            at.translate(-width / 2.0, m.getAscent() / 3.0);
            t.transform(at);
            t.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, alpha / 255f)));
            t.setColor(UniversityPixelTheme.BLACK);
            int o = Math.max(2, size / 12);
            for (int dx = -o; dx <= o; dx += o) for (int dy = -o; dy <= o; dy += o)
                if (dx != 0 || dy != 0) t.drawString(text, dx, dy);
            t.drawString(text, o, o + 1);
            t.setColor(color);
            t.drawString(text, 0, 0);
        } finally { t.dispose(); }
    }

    private static int textWidth(Graphics2D g, String text, int size) {
        return g.getFontMetrics(UniversityPixelTheme.font(size, Font.BOLD)).stringWidth(text);
    }

    private static String itemLabel(String id) {
        if ("heal".equals(id)) return "회복!";
        if ("shield".equals(id)) return "보호막!";
        if ("damage_boost".equals(id)) return "공격 증폭!";
        if ("garbage_bomb".equals(id)) return "가비지 폭탄!";
        if ("line_cleaner".equals(id)) return "줄 정리!";
        if ("fever_charge".equals(id)) return "피버 충전!";
        if ("time_warp".equals(id)) return "시간 왜곡!";
        if ("nullify".equals(id)) return "무효화!";
        return "ITEM!";
    }

    private static Color itemColor(String id) {
        if ("garbage_bomb".equals(id)) return CORAL;
        if ("time_warp".equals(id)) return new Color(0x7BBEFF);
        if ("nullify".equals(id)) return new Color(0xAD85EC);
        if ("fever_charge".equals(id)) return PINK;
        if ("damage_boost".equals(id)) return GOLD;
        return MINT;
    }

    private static int duration(Effect effect) {
        switch (effect) {
            case ATTACK: case MONSTER_ATTACK: return IMPACT_MS + 380;
            case DAMAGE: case HEAL: return 900;
            case LINE_CLEAR: return 1000;
            case FEVER: return 1200;
            case VICTORY: case DEFEAT: return 0;
            default: return 900;
        }
    }

    /** 준비 동작, 전진, 착탄 경직, 복귀를 타임스탬프 하나로 계산한다. */
    private static float attackPose(long age) {
        if (age < 0 || age >= 720) return 0f;
        if (age < 100) return -0.14f * smooth(age / 100f);
        if (age < 310) return -0.14f + 1.14f * smooth((age - 100) / 210f);
        if (age < 425) return 1f;
        return 1f - smooth((age - 425) / 295f);
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static int attackLift(long age, int spriteHeight) {
        if (age < 100 || age >= 520) return 0;
        if (age < 310) return Math.round((float) Math.sin(Math.PI * (age - 100) / 420f)
                * spriteHeight * 0.10f);
        int contactHeight = Math.round(spriteHeight * 0.10f);
        if (age < 425) return contactHeight;
        return Math.round(contactHeight * (1f - smooth((age - 425) / 95f)));
    }

    private static int hitRecoil(long age, int distance) {
        if (age < 0 || age >= 300) return 0;
        float impulse = (float) Math.sin(Math.min(1f, age / 60f) * Math.PI / 2);
        float fade = 1f - age / 300f;
        return Math.round(distance * impulse * fade * fade);
    }

    /** 돌진 중 원래 자리에 남는 잔상. 좌우 방향은 대상의 이동 반대편이다. */
    private static void paintAfterimages(Graphics2D g, BufferedImage sprite, int x, int y, int direction, float phase) {
        Composite prior = g.getComposite();
        float fade = 1f - Math.abs(phase - 0.55f) * 1.3f;
        for (int i = 3; i >= 1; i--) {
            float opacity = Math.max(0.03f, (0.16f - i * 0.025f) * fade);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            g.drawImage(sprite, x + direction * i * 12, y + i * 3, null);
        }
        g.setComposite(prior);
    }

    private void expireVisuals() {
        long now = System.currentTimeMillis();
        visuals.removeIf(visual -> now - visual.started > duration(visual.effect) && now >= visual.started);
    }

    private static String fit(String text, FontMetrics metrics, int maxWidth) {
        if (maxWidth <= 0) return "";
        String value = text == null ? "" : text;
        if (metrics.stringWidth(value) <= maxWidth) return value;
        while (!value.isEmpty() && metrics.stringWidth(value + "…") > maxWidth)
            value = value.substring(0, value.length() - 1);
        return value.isEmpty() ? "" : value + "…";
    }

    /** 크기·좌우 반전별로 축소한 그림을 보관해 매 프레임 큰 원본을 다시 줄이지 않는다. */
    private static BufferedImage scaled(BufferedImage source, int width, int height, boolean mirrored) {
        String key = System.identityHashCode(source) + ":" + width + "x" + height + (mirrored ? "m" : "");
        synchronized (SCALED) {
            BufferedImage cached = SCALED.get(key);
            if (cached != null) return cached;
        }
        // 원본 비율을 지키며 상자 안에 맞춘다.
        double ratio = Math.min(width / (double) source.getWidth(), height / (double) source.getHeight());
        int sw = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int sh = Math.max(1, (int) Math.round(source.getHeight() * ratio));
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, ratio < 0.5
                ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int x = (width - sw) / 2, y = height - sh;
        if (mirrored) g.drawImage(source, x + sw, y, -sw, sh, null);
        else g.drawImage(source, x, y, sw, sh, null);
        g.dispose();
        synchronized (SCALED) { SCALED.put(key, result); }
        return result;
    }

    /** 피격 섬광용 흰 실루엣. */
    private static BufferedImage silhouette(BufferedImage source) {
        String key = System.identityHashCode(source) + ":white";
        synchronized (SCALED) {
            BufferedImage cached = SCALED.get(key);
            if (cached != null) return cached;
        }
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            int alpha = source.getRGB(x, y) >>> 24;
            if (alpha > 0) result.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
        }
        synchronized (SCALED) { SCALED.put(key, result); }
        return result;
    }

    /** 투명 여백을 잘라 몬스터가 상자 크기를 꽉 채우게 한다. */
    private static BufferedImage trim(BufferedImage source) {
        int minX = source.getWidth(), minY = source.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < source.getHeight(); y += 2) for (int x = 0; x < source.getWidth(); x += 2) {
            if ((source.getRGB(x, y) >>> 24) > 16) {
                if (x < minX) minX = x; if (x > maxX) maxX = x;
                if (y < minY) minY = y; if (y > maxY) maxY = y;
            }
        }
        if (maxX < minX || maxY < minY) return source;
        minX = Math.max(0, minX - 2); minY = Math.max(0, minY - 2);
        maxX = Math.min(source.getWidth() - 1, maxX + 2); maxY = Math.min(source.getHeight() - 1, maxY + 2);
        return source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static BufferedImage readArt(String path) {
        try (InputStream stream = PixelArena.class.getResourceAsStream(path)) {
            return stream == null ? null : ImageIO.read(stream);
        } catch (IOException exception) { return null; }
    }

    private static final class Visual {
        final Effect effect;
        final int amount;
        final boolean targetMonster;
        final long started;
        final int serial;
        final String itemId;
        final int combo;
        final boolean perfect;
        Visual(Effect effect, int amount, boolean targetMonster, long started,
               int serial, String itemId, int combo, boolean perfect) {
            this.effect = effect;
            this.amount = amount;
            this.targetMonster = targetMonster;
            this.started = started;
            this.serial = serial;
            this.itemId = itemId;
            this.combo = combo;
            this.perfect = perfect;
        }
    }
}
