package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.io.File;

/** Headless visual checks of speaker routing, side placement and reset. */
public final class StoryBattleSpeechBubbleTest {
    private StoryBattleSpeechBubbleTest() { }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PixelArena arena = new PixelArena();
            arena.setEffectsEnabled(false);
            arena.setWidePve(true);
            arena.setSize(1060, 630);
            BufferedImage empty = render(arena);

            arena.showDialogue(PixelArena.DialogueSpeaker.PLAYER,
                    "교양 하나만 들으면 되는데요? 조금만 더 힘내자!");
            BufferedImage player = render(arena);
            int playerLeft = differences(empty, player, 4, 120, 375, 395);
            int playerRight = differences(empty, player, 690, 120, 1055, 395);
            assert playerLeft > 500 : "Player speech bubble not painted on left";
            assert playerRight == 0 : "Player speech must not overlap monster side";

            arena.showDialogue(PixelArena.DialogueSpeaker.MONSTER,
                    "말도 안 돼! 교양은 학점 날먹 과목이란 말이다!");
            BufferedImage monster = render(arena);
            int monsterLeft = differences(empty, monster, 4, 120, 375, 395);
            int monsterRight = differences(empty, monster, 690, 120, 1055, 395);
            assert monsterRight > 500 : "Monster speech bubble not painted on right";
            assert monsterLeft == 0 : "Monster speech must not overlap player side";

            arena.resetForEncounter();
            BufferedImage reset = render(arena);
            assert differences(empty, reset, 4, 120, 1055, 395) == 0
                    : "Old dialogue remained after resetting encounter";

            arena.showDialogue(PixelArena.DialogueSpeaker.PLAYER, "이 문장은 온라인 대전 화면에 나타나지 않아야 합니다.");
            arena.setOnline(true);
            BufferedImage online = render(arena);
            arena.clearDialogue();
            BufferedImage onlineCleared = render(arena);
            assert differences(online, onlineCleared, 4, 120, 1055, 395) == 0
                    : "Story-only dialogue appeared in online mode";
            System.out.println("PASS StoryBattleSpeechBubbleTest");
        });
    }

    private static BufferedImage render(PixelArena arena) {
        BufferedImage target = new BufferedImage(arena.getWidth(), arena.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        try { arena.paint(g); } finally { g.dispose(); }
        return target;
    }

    private static int differences(BufferedImage a, BufferedImage b, int x1, int y1, int x2, int y2) {
        int count = 0;
        for (int y = y1; y <= y2; y++) for (int x = x1; x <= x2; x++) {
            if (a.getRGB(x, y) != b.getRGB(x, y)) count++;
        }
        return count;
    }
}
