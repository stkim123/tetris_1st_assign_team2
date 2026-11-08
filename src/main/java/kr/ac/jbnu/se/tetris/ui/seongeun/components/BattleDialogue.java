package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * 전투 중 몬스터와 플레이어의 대사를 한 번에 한 줄씩 순서대로 보여 주는 말풍선.
 * 조우·대화·위기처럼 읽을 시간이 필요한 대사용이라, 짧게 스쳐 가는 전투 알림(BattleLog)과 분리했다.
 */
public final class BattleDialogue extends JComponent {
    /** 말하는 사람. 이름표 색으로 구분한다. */
    public enum Speaker { MONSTER, PLAYER, NARRATION }

    /** 대기열이 이보다 길어지면 가장 오래된 대기 대사부터 버린다(전투 중 대사가 밀려 쌓이지 않게). */
    public static final int MAX_QUEUE = 6;
    public static final int HEIGHT = 84;
    static final long FADE_IN_MILLIS = 180;
    static final long FADE_OUT_MILLIS = 300;
    private static final int PADDING = 10;
    private static final int MAX_TEXT_LINES = 2;

    private static final class Line {
        final Speaker speaker;
        final String text;
        Line(Speaker speaker, String text) { this.speaker = speaker; this.text = text; }
    }

    private final Deque<Line> pending = new ArrayDeque<Line>();
    private Line current;
    private long currentStartedAt;
    private long currentDuration;
    private final Timer timer = new Timer(50, event -> step());
    // The queue owns dialogue order/timing; the arena owns where the bubble is drawn.
    // Do not mount this legacy box as a visible Swing component.
    private BiConsumer<Speaker, String> speechListener;

    public void setSpeechListener(BiConsumer<Speaker, String> listener) {
        speechListener = listener;
        notifySpeaker();
    }

    private void notifySpeaker() {
        if (speechListener != null)
            speechListener.accept(current == null ? null : current.speaker,
                    current == null ? null : current.text);
    }

    public BattleDialogue() {
        setOpaque(false);
        setFocusable(false);
    }

    /** 대사가 마우스 입력을 가로채지 않게 한다. */
    @Override public boolean contains(int x, int y) { return false; }

    /** 대사 한 줄을 대기열 끝에 넣는다. 앞 대사가 끝나면 이어서 나온다. */
    public void enqueue(Speaker speaker, String text) {
        if (text == null || text.trim().isEmpty()) return;
        pending.addLast(new Line(speaker == null ? Speaker.NARRATION : speaker, text.trim()));
        while (pending.size() > MAX_QUEUE) pending.removeFirst();
        if (current == null) startNext(System.currentTimeMillis());
        if (!timer.isRunning()) timer.start();
        repaint();
    }

    public void clear() {
        pending.clear();
        current = null;
        timer.stop();
        notifySpeaker();
        repaint();
    }

    /** 지금 보이는 대사와 기다리는 대사의 문구(순서대로). 테스트용. */
    public List<String> texts() {
        List<String> result = new ArrayList<String>();
        if (current != null) result.add(current.text);
        for (Line line : pending) result.add(line.text);
        return result;
    }

    /** 지금 화면에 보이는 대사가 있는지. */
    public boolean isSpeaking() { return current != null; }

    /** 한 줄이 화면에 머무는 시간: 글자 수에 비례하되 2.6~5초. */
    public static long durationFor(String text) {
        long millis = 1800L + 90L * (text == null ? 0 : text.length());
        return Math.max(2600L, Math.min(5000L, millis));
    }

    private void startNext(long now) {
        current = pending.pollFirst();
        if (current != null) {
            currentStartedAt = now;
            currentDuration = durationFor(current.text);
        }
        notifySpeaker();
    }

    private void step() {
        long now = System.currentTimeMillis();
        if (current != null && now - currentStartedAt >= currentDuration) startNext(now);
        if (current == null) timer.stop();
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        Line line = current;
        if (line == null || getWidth() <= 0) return;
        paintLine(graphics, line);
    }

    private void paintLine(Graphics graphics, Line line) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            long age = System.currentTimeMillis() - currentStartedAt;
            float alpha = 1f;
            if (age < FADE_IN_MILLIS) alpha = Math.max(0.15f, age / (float) FADE_IN_MILLIS);
            else if (age > currentDuration - FADE_OUT_MILLIS)
                alpha = Math.max(0f, (currentDuration - age) / (float) FADE_OUT_MILLIS);
            if (alpha <= 0f) return;
            g.setComposite(AlphaComposite.SrcOver.derive(alpha));

            Font textFont = UniversityPixelTheme.font(13, Font.BOLD);
            Font chipFont = UniversityPixelTheme.font(11, Font.BOLD);
            FontMetrics textMetrics = g.getFontMetrics(textFont);
            int textWidth = Math.max(40, getWidth() - PADDING * 2);
            List<String> rows = wrap(line.text, textMetrics, textWidth, MAX_TEXT_LINES);
            int rowHeight = textMetrics.getHeight() + 1;
            int boxHeight = Math.min(getHeight(), 24 + rows.size() * rowHeight + PADDING);
            int top = getHeight() - boxHeight;
            Color accent = accent(line.speaker);

            g.setColor(new Color(0x17, 0x24, 0x2A, 232));
            g.fillRect(0, top, getWidth(), boxHeight);
            g.setColor(accent);
            g.fillRect(0, top, getWidth(), 3);
            g.drawRect(0, top, getWidth() - 1, boxHeight - 1);

            // 이름표
            g.setFont(chipFont);
            FontMetrics chipMetrics = g.getFontMetrics();
            String name = speakerName(line.speaker);
            int chipWidth = chipMetrics.stringWidth(name) + 14;
            g.setColor(accent);
            g.fillRect(PADDING, top + 8, chipWidth, 16);
            g.setColor(UniversityPixelTheme.readableOn(accent));
            g.drawString(name, PADDING + 7, top + 8 + (16 + chipMetrics.getAscent() - chipMetrics.getDescent()) / 2);

            // 본문
            g.setFont(textFont);
            g.setColor(UniversityPixelTheme.TEXT_ON_DARK);
            int y = top + 30 + textMetrics.getAscent();
            for (String row : rows) {
                g.drawString(row, PADDING, y);
                y += rowHeight;
            }
        } finally { g.dispose(); }
    }

    static String speakerName(Speaker speaker) {
        switch (speaker) {
            case MONSTER: return "몬스터";
            case PLAYER: return "플레이어";
            default: return "내레이션";
        }
    }

    private static Color accent(Speaker speaker) {
        switch (speaker) {
            case MONSTER: return UniversityPixelTheme.CORAL;
            case PLAYER: return UniversityPixelTheme.MINT;
            default: return UniversityPixelTheme.GOLD;
        }
    }

    /** 글자 단위로 줄바꿈한다(한글은 띄어쓰기 없이도 읽힌다). 최대 줄 수를 넘으면 마지막 줄 끝을 …로 줄인다. */
    static List<String> wrap(String text, FontMetrics metrics, int maxWidth, int maxLines) {
        List<String> rows = new ArrayList<String>();
        StringBuilder row = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            char c = text.charAt(index);
            if (row.length() > 0 && metrics.stringWidth(row.toString() + c) > maxWidth) {
                // 단어 중간이 아니라 가능한 한 공백에서 끊는다.
                int space = row.lastIndexOf(" ");
                if (c != ' ' && space > row.length() / 2) {
                    rows.add(row.substring(0, space));
                    row = new StringBuilder(row.substring(space + 1));
                } else {
                    rows.add(row.toString());
                    row = new StringBuilder();
                }
                if (rows.size() == maxLines) break;
                continue;
            }
            if (!(row.length() == 0 && c == ' ')) row.append(c);
            index++;
        }
        if (rows.size() < maxLines && row.length() > 0) rows.add(row.toString());
        boolean cut = index < text.length() || rows.size() > maxLines;
        if (rows.size() > maxLines) rows = new ArrayList<String>(rows.subList(0, maxLines));
        if (cut && !rows.isEmpty()) {
            String last = rows.get(rows.size() - 1);
            while (last.length() > 1 && metrics.stringWidth(last + "…") > maxWidth) last = last.substring(0, last.length() - 1);
            rows.set(rows.size() - 1, last + "…");
        }
        return rows;
    }
}
