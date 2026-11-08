package kr.ac.jbnu.se.tetris.ui.seongeun.panels;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.BattleDialogue;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.PixelArena;

/** Verify story cues route to actor bubbles in FIFO order, never the generic status banner. */
public final class BattleDialogueRoutingTest {
    private BattleDialogueRoutingTest() { }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                BattlePanel panel = new BattlePanel();
                panel.setSize(1168, 900);
                panel.doLayout();
                PixelArena arena = (PixelArena) get(panel, "arena");
                BattleDialogue queue = (BattleDialogue) get(panel, "dialogue");
                JLabel status = (JLabel) get(panel, "status");
                String prior = status.getText();

                panel.showStoryDialogue("플레이어", "이번 학기는 살았다!");
                assert PixelArena.DialogueSpeaker.PLAYER == get(arena, "dialogueSpeaker");
                assert prior.equals(status.getText()) : "Player cue duplicated in generic status";

                panel.showStoryDialogue("몬스터", "오픈북이 아니다!");
                assert PixelArena.DialogueSpeaker.PLAYER == get(arena, "dialogueSpeaker")
                        : "A queued monster cue must not interrupt the player's current line";
                assert queue.texts().equals(Arrays.asList("이번 학기는 살았다!", "오픈북이 아니다!"))
                        : "Monster cue was not queued after the player cue";
                assert prior.equals(status.getText()) : "Monster cue duplicated in generic status";
                advanceCurrentLine(queue);
                assert PixelArena.DialogueSpeaker.MONSTER == get(arena, "dialogueSpeaker")
                        : "Monster's bubble should become active after player's line expires";
                assert "오픈북이 아니다!".equals(get(arena, "dialogueText"));

                panel.setFeedback("플레이어: 정말인가요?");
                assert PixelArena.DialogueSpeaker.MONSTER == get(arena, "dialogueSpeaker")
                        : "Legacy speaker-prefixed feedback must wait for the monster cue";
                assert queue.texts().equals(Arrays.asList("오픈북이 아니다!", "정말인가요?"))
                        : "Legacy speaker-prefixed feedback did not enter the FIFO queue";
                assert prior.equals(status.getText()) : "Legacy speaker cue duplicated in generic status";
                advanceCurrentLine(queue);
                assert PixelArena.DialogueSpeaker.PLAYER == get(arena, "dialogueSpeaker")
                        : "Legacy player cue did not route to the player after monster cue";
                assert "정말인가요?".equals(get(arena, "dialogueText"));

                panel.setFeedback("일반 전투 알림");
                assert status.getText().contains("일반 전투 알림")
                        : "General feedback should still show in the status banner";
                panel.resetFeedback();
                assert get(arena, "dialogueSpeaker") == null : "Dialogue should reset after battle";
                assert queue.texts().isEmpty() : "Pending dialogue should reset after battle";
                System.out.println("PASS BattleDialogueRoutingTest");
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError("Unable to inspect or advance story dialogue", ex);
            }
        });
    }

    /** Advance the real dialogue tick without sleeping several seconds in a headless build. */
    private static void advanceCurrentLine(BattleDialogue queue) throws ReflectiveOperationException {
        Field startedAt = BattleDialogue.class.getDeclaredField("currentStartedAt");
        startedAt.setAccessible(true);
        startedAt.setLong(queue, System.currentTimeMillis() - 10000L);
        Method step = BattleDialogue.class.getDeclaredMethod("step");
        step.setAccessible(true);
        step.invoke(queue);
    }

    private static Object get(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}
