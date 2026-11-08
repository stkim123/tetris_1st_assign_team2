package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * 전투 알림 큐. 최근 몇 줄만 잠깐 보여 주고 사라진다.
 * 보스(상대)의 공격·아이템 사용처럼 보드에 직접 보이지 않는 일을 알려 주는 용도이다.
 */
public final class BattleLog extends JComponent {
    /** 알림의 주체. 색 막대로 구분한다. */
    public enum Side { PLAYER, ENEMY, SYSTEM }

    public static final int MAX_LINES = 3;
    public static final int LINE_HEIGHT = 22;
    private static final int LINE_GAP = 3;
    static final long LIFE_MILLIS = 3200;
    static final long FADE_MILLIS = 600;

    private static final class Entry {
        final Side side;
        final String text;
        final long bornAt;
        Entry(Side side, String text, long bornAt) { this.side = side; this.text = text; this.bornAt = bornAt; }
    }

    private final Deque<Entry> entries = new ArrayDeque<Entry>();
    private final Timer timer = new Timer(80, event -> prune());

    public BattleLog() {
        setOpaque(false);
        setFocusable(false);
    }

    /** 알림이 마우스 입력을 가로채지 않게 한다(아래 버튼이 그대로 눌린다). */
    @Override public boolean contains(int x, int y) { return false; }

    /** 필요한 높이: 최대 줄 수만큼. */
    public static int preferredHeight() { return MAX_LINES * (LINE_HEIGHT + LINE_GAP); }

    public void push(Side side, String text) {
        if (text == null || text.trim().isEmpty()) return;
        entries.addLast(new Entry(side == null ? Side.SYSTEM : side, text.trim(), System.currentTimeMillis()));
        while (entries.size() > MAX_LINES) entries.removeFirst();
        if (!timer.isRunning()) timer.start();
        repaint();
    }

    public void clear() {
        entries.clear();
        timer.stop();
        repaint();
    }

    /** 지금 보이는 알림 문구(오래된 것부터). 테스트용. */
    public List<String> texts() {
        List<String> result = new ArrayList<String>();
        for (Entry entry : entries) result.add(entry.text);
        return result;
    }

    private void prune() {
        long now = System.currentTimeMillis();
        while (!entries.isEmpty() && now - entries.peekFirst().bornAt >= LIFE_MILLIS) entries.removeFirst();
        if (entries.isEmpty()) timer.stop();
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        if (entries.isEmpty() || getWidth() <= 0) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            long now = System.currentTimeMillis();
            // 위에서 아래로 쌓는다: 가장 오래된 알림이 맨 위(몬스터 체력바 바로 아래)에 오고, 새 알림이 그 아래에 붙는다.
            Entry[] ordered = entries.toArray(new Entry[0]);
            for (int i = 0; i < ordered.length; i++) {
                int y = i * (LINE_HEIGHT + LINE_GAP);
                Entry entry = ordered[i];
                long age = now - entry.bornAt;
                float alpha = age > LIFE_MILLIS - FADE_MILLIS
                        ? Math.max(0f, (LIFE_MILLIS - age) / (float) FADE_MILLIS) : 1f;
                if (alpha <= 0f) continue;
                g.setComposite(AlphaComposite.SrcOver.derive(alpha));
                // 글자 크기를 12→11→10으로 줄여 가며 칸에 맞추고, 그래도 넘칠 때만 …로 줄인다.
                g.setFont(UniversityPixelTheme.font(fontSizeFor(entry.text, getWidth()), Font.BOLD));
                FontMetrics metrics = g.getFontMetrics();
                String text = fit(entry.text, metrics, getWidth() - PADDING);
                int width = Math.min(getWidth(), metrics.stringWidth(text) + PADDING);
                g.setColor(new Color(0x17, 0x24, 0x2A, 205));
                g.fillRect(0, y, width, LINE_HEIGHT);
                g.setColor(accent(entry.side));
                g.fillRect(0, y, 5, LINE_HEIGHT);
                g.setColor(UniversityPixelTheme.TEXT_ON_DARK);
                g.drawString(text, 13, y + (LINE_HEIGHT + metrics.getAscent() - metrics.getDescent()) / 2);
            }
        } finally { g.dispose(); }
    }

    private static final int PADDING = 22;
    private static final int[] FONT_SIZES = { 12, 11, 10 };

    /** 문구가 maxWidth 안에 들어가는 가장 큰 글자 크기(12→11→10). 하나도 안 맞으면 10. */
    public static int fontSizeFor(String text, int maxWidth) {
        BufferedImage canvas = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            for (int size : FONT_SIZES)
                if (g.getFontMetrics(UniversityPixelTheme.font(size, Font.BOLD)).stringWidth(text) + PADDING <= maxWidth)
                    return size;
            return FONT_SIZES[FONT_SIZES.length - 1];
        } finally { g.dispose(); }
    }

    private static Color accent(Side side) {
        switch (side) {
            case PLAYER: return UniversityPixelTheme.MINT;
            case ENEMY: return UniversityPixelTheme.CORAL;
            default: return UniversityPixelTheme.GOLD;
        }
    }

    /** 칸보다 길면 끝을 …로 줄인다. */
    static String fit(String text, FontMetrics metrics, int maxWidth) {
        if (maxWidth <= 0 || metrics.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "…";
        int end = text.length();
        while (end > 1 && metrics.stringWidth(text.substring(0, end) + ellipsis) > maxWidth) end--;
        return text.substring(0, end) + ellipsis;
    }
}
