package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import javax.swing.SwingUtilities;

/** 대사 말풍선: 대기열 순서·제한, 표시 시간, 줄바꿈과 대사집 90줄이 2줄 안에 들어가는지 확인한다. */
public final class BattleDialogueTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        SwingUtilities.invokeAndWait(() -> {
            try {
                queueOrderAndLimits();
                displayTime();
                everyScriptedLineFitsTwoRows();
                longTextIsCut();
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        System.out.println("BattleDialogueTest: PASS");
    }

    private static void queueOrderAndLimits() {
        BattleDialogue dialogue = new BattleDialogue();
        check(!dialogue.isSpeaking() && dialogue.texts().isEmpty(), "처음에는 비어 있음");
        dialogue.enqueue(BattleDialogue.Speaker.MONSTER, "첫째");
        dialogue.enqueue(BattleDialogue.Speaker.PLAYER, "둘째");
        dialogue.enqueue(BattleDialogue.Speaker.NARRATION, "셋째");
        check(dialogue.isSpeaking() && dialogue.texts().equals(Arrays.asList("첫째", "둘째", "셋째")),
                "넣은 순서대로 한 줄씩: " + dialogue.texts());
        dialogue.enqueue(BattleDialogue.Speaker.MONSTER, "   ");
        dialogue.enqueue(BattleDialogue.Speaker.MONSTER, null);
        check(dialogue.texts().size() == 3, "빈 문구는 무시");

        dialogue.clear();
        check(!dialogue.isSpeaking() && dialogue.texts().isEmpty(), "clear 후 비어 있음");

        // 대기열이 길어지면 가장 오래된 대기 대사부터 버리고, 지금 말하는 대사는 유지한다.
        for (int i = 1; i <= 10; i++) dialogue.enqueue(BattleDialogue.Speaker.MONSTER, "대사" + i);
        List<String> texts = dialogue.texts();
        check(texts.size() == 1 + BattleDialogue.MAX_QUEUE, "현재 1줄 + 대기 최대 " + BattleDialogue.MAX_QUEUE + ": " + texts);
        check("대사1".equals(texts.get(0)) && "대사10".equals(texts.get(texts.size() - 1))
                        && !texts.contains("대사2") && !texts.contains("대사4"),
                "오래된 대기 대사부터 버림: " + texts);
        check(!dialogue.contains(5, 5), "대사 상자는 마우스 입력을 가로채지 않음");
    }

    private static void displayTime() {
        long shortTime = BattleDialogue.durationFor("짧다");
        long longTime = BattleDialogue.durationFor(repeat("가", 100));
        check(shortTime == 2600 && longTime == 5000, "표시 시간 하한 2.6초, 상한 5초: " + shortTime + "/" + longTime);
        check(BattleDialogue.durationFor(repeat("가", 20)) >= shortTime
                && BattleDialogue.durationFor(repeat("가", 20)) <= longTime, "중간 길이는 그 사이");
    }

    /** 대사집의 모든 대사는 좁은 말풍선(글자 영역 240px)에서도 최대 2줄이어야 한다. */
    private static void everyScriptedLineFitsTwoRows() throws Exception {
        Properties script = new Properties();
        try (Reader reader = new InputStreamReader(
                BattleDialogueTest.class.getResourceAsStream("/story/dialogues.properties"), StandardCharsets.UTF_8)) {
            script.load(reader);
        }
        check(script.size() == 90, "대사집 90줄: " + script.size());
        FontMetrics metrics = metrics();
        for (String key : script.stringPropertyNames()) {
            String text = script.getProperty(key);
            List<String> rows = BattleDialogue.wrap(text, metrics, 240, 3);
            check(rows.size() <= 2, key + " 은(는) 2줄을 넘음: " + rows);
            // 줄바꿈으로 글자가 사라지지 않는다(공백 제외 글자 수 보존).
            check(String.join("", rows).replace(" ", "").equals(text.replace(" ", "")), key + " 줄바꿈 중 글자 손실");
        }
    }

    private static void longTextIsCut() {
        FontMetrics metrics = metrics();
        List<String> rows = BattleDialogue.wrap(repeat("가", 200), metrics, 120, 2);
        check(rows.size() == 2 && rows.get(1).endsWith("…"), "너무 긴 문구는 2줄에서 …로 줄임: " + rows);
        check(metrics.stringWidth(rows.get(1)) <= 120, "줄인 줄도 칸 안에 들어감");
    }

    /** Java 8 호환: String.repeat(Java 11) 대신 직접 만든다. */
    private static String repeat(String unit, int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) text.append(unit);
        return text.toString();
    }

    private static FontMetrics metrics() {
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        return g.getFontMetrics(UniversityPixelTheme.font(13, Font.BOLD));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
