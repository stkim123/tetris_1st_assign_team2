package kr.ac.jbnu.se.tetris.ui.seongeun.panels;

import java.awt.Component;
import java.lang.reflect.Field;
import java.util.Arrays;
import javax.swing.SwingUtilities;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.BattleDialogue;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.PixelArena;

/** Confirms queued conversation, actor-side speech, and clear behavior without assets. */
public final class StorySpeechQueueTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                BattlePanel panel = new BattlePanel();
                BattleDialogue queue = (BattleDialogue) field(panel, "dialogue");
                PixelArena arena = (PixelArena) field(panel, "arena");
                if (queue.getParent() != null) throw new AssertionError("Old dialogue panel became visible");
                panel.showStoryDialogue("몬스터", "첫 번째 대사");
                panel.showStoryDialogue("플레이어", "두 번째 대사");
                if (!queue.texts().equals(Arrays.asList("첫 번째 대사", "두 번째 대사")))
                    throw new AssertionError("Queued dialogue order changed: " + queue.texts());
                if (!"MONSTER".equals(String.valueOf(field(arena, "dialogueSpeaker"))))
                    throw new AssertionError("First bubble should belong to monster");
                if (!"첫 번째 대사".equals(field(arena, "dialogueText")))
                    throw new AssertionError("Visible bubble does not match queued current text");
                Field startedAt = BattleDialogue.class.getDeclaredField("currentStartedAt");
                startedAt.setAccessible(true);
                startedAt.setLong(queue, System.currentTimeMillis() - 10000L);
                java.lang.reflect.Method advance = BattleDialogue.class.getDeclaredMethod("step");
                advance.setAccessible(true);
                advance.invoke(queue);
                if (!"PLAYER".equals(String.valueOf(field(arena, "dialogueSpeaker")))
                        || !"두 번째 대사".equals(field(arena, "dialogueText")))
                    throw new AssertionError("Next queued line should move bubble to player side");
                queue.clear();
                if (!queue.texts().isEmpty() || field(arena, "dialogueText") != null)
                    throw new AssertionError("Old dialogue survived reset");
                panel.showStoryDialogue("플레이어", "새 대사");
                if (!"PLAYER".equals(String.valueOf(field(arena, "dialogueSpeaker"))))
                    throw new AssertionError("Player bubble not routed to player side");
                queue.clear();
                panel.setMode(true);
                panel.showStoryDialogue("몬스터", "온라인에서 보이면 안 됨");
                if (!queue.texts().isEmpty()) throw new AssertionError("Online mode received story dialogue");
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        System.out.println("PASS StorySpeechQueueTest: ordered speech, side routing, reset, online isolation");
    }
    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
