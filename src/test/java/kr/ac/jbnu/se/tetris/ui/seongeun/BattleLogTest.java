package kr.ac.jbnu.se.tetris.ui.seongeun;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import kr.ac.jbnu.se.tetris.battle.BattleEvent;
import kr.ac.jbnu.se.tetris.battle.BattleManager;
import kr.ac.jbnu.se.tetris.battle.ParticipantSpec;
import kr.ac.jbnu.se.tetris.story.MonsterTier;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.BattleLog;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.GameArt;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.UniversityPixelTheme;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.BattlePanel;

/** PvE 전투 알림 큐: 이벤트 묶음이 한 줄로 합쳐지고, 중복·최대 줄 수가 지켜지는지 검증한다. */
public final class BattleLogTest {
    private static final BattleEvent.Type DAMAGE = BattleEvent.Type.DAMAGE;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        final String shot = args.length > 0 ? args[0] : null;
        SwingUtilities.invokeAndWait(() -> {
            try {
                BattlePanel panel = new BattlePanel();
                panel.setEncounter("university", 1, MonsterTier.NORMAL, "university:0");
                GameArt.sprite("university:0");
                BattleLog log = log(panel);
                BattleManager manager = BattleManager.pve(Arrays.asList(
                        new ParticipantSpec("local", "플레이어", 100),
                        new ParticipantSpec("monster", "술", 30)), 17L, 450, 3500, 0);
                manager.start();
                panel.setState(manager.getState(), "local");

                // 1) 같은 묶음의 피해와 가비지는 한 줄로 합쳐진다.
                panel.applyEvents(Arrays.asList(ev(DAMAGE, 1, "monster", "local", 8, null),
                        ev(BattleEvent.Type.GARBAGE_SENT, 2, "monster", "local", 1, null)), "local");
                check(log.texts().equals(Arrays.asList("술 · 공격 → 피해 8 + 가비지 1줄")), "merged attack: " + log.texts());

                // 2) 같은 이벤트를 다시 넣어도 중복되지 않는다(이미 처리한 이벤트 ID).
                panel.applyEvents(Arrays.asList(ev(DAMAGE, 1, "monster", "local", 8, null)), "local");
                check(log.texts().size() == 1, "duplicate ignored: " + log.texts());

                // 3) 보스의 회복 사용, 보호막 자동 발동.
                panel.applyEvents(Arrays.asList(ev(BattleEvent.Type.ITEM_USED, 3, "monster", "monster", 1, "heal"),
                        ev(BattleEvent.Type.ITEM_USED, 4, "monster", "monster", 1, "shield")), "local");
                check(log.texts().contains("술 · 회복 사용") && log.texts().contains("술 · 보호막 발동"),
                        "monster items: " + log.texts());
                check(log.texts().size() <= BattleLog.MAX_LINES, "max lines respected: " + log.texts());

                // 4) 가비지 폭탄은 폭탄 사용과 가비지 줄 수가 한 줄로 합쳐진다.
                log.clear();
                panel.applyEvents(Arrays.asList(ev(BattleEvent.Type.ITEM_USED, 5, "monster", "local", 1, "garbage_bomb"),
                        ev(BattleEvent.Type.GARBAGE_SENT, 6, "monster", "local", 1, null)), "local");
                check(log.texts().equals(Arrays.asList("술 · 가비지 폭탄 → 가비지 1줄")), "bomb merged: " + log.texts());

                // 5) 내 무효화 사용은 제거된 아이템 이름과 함께 나온다.
                log.clear();
                panel.applyEvents(Arrays.asList(ev(BattleEvent.Type.ITEM_USED, 7, "local", "monster", 1, "nullify"),
                        ev(BattleEvent.Type.ITEM_REMOVED, 8, "local", "monster", 1, "heal")), "local");
                check(log.texts().equals(Arrays.asList("나 · 무효화 → 상대 회복 제거")), "nullify: " + log.texts());
                // 상대(몬스터)가 무효화를 쓰면 내 아이템이 제거된 것이다.
                log.clear();
                panel.applyEvents(Arrays.asList(ev(BattleEvent.Type.ITEM_USED, 15, "monster", "local", 1, "nullify"),
                        ev(BattleEvent.Type.ITEM_REMOVED, 16, "monster", "local", 1, "shield")), "local");
                check(log.texts().equals(Arrays.asList("술 · 무효화 → 내 보호막 제거")), "enemy nullify: " + log.texts());

                // 6) 보스의 아이템 획득만 알린다(내 획득은 기존 연출이 있다).
                log.clear();
                panel.applyEvents(Arrays.asList(ev(BattleEvent.Type.ITEM_ACQUIRED, 20, "monster", "monster", 1, "heal"),
                        ev(BattleEvent.Type.ITEM_ACQUIRED, 21, "local", "local", 1, "shield")), "local");
                check(log.texts().equals(Arrays.asList("술 · 아이템 획득 · 회복")), "acquire: " + log.texts());

                // 글자 크기 맞춤: 넓으면 12, 아주 좁으면 최소 10, 중간 폭에서는 그 사이 값이어야 한다.
                String typical = "술 · 3줄 클리어 · 2콤보 → 피해 18 + 가비지 2줄";
                check(BattleLog.fontSizeFor(typical, 1000) == 12, "wide room keeps 12px");
                check(BattleLog.fontSizeFor(typical, 40) == 10, "tiny room falls back to 10px");
                int mid = BattleLog.fontSizeFor(typical, 280);
                check(mid >= 10 && mid <= 12 && mid <= BattleLog.fontSizeFor(typical, 1000), "mid width shrinks: " + mid);

                if (shot != null) {
                    log.clear();
                    panel.applyEvents(Arrays.asList(ev(DAMAGE, 22, "monster", "local", 18, null),
                            ev(BattleEvent.Type.GARBAGE_SENT, 23, "monster", "local", 2, null),
                            ev(BattleEvent.Type.ITEM_USED, 24, "monster", "monster", 1, "heal"),
                            ev(BattleEvent.Type.ITEM_USED, 25, "monster", "local", 1, "garbage_bomb")), "local");
                    JScrollPane viewport = new JScrollPane(panel);
                    viewport.setBorder(null);
                    UniversityPixelTheme.apply(viewport);
                    viewport.setSize(1160, 780);
                    layout(viewport);
                    BufferedImage image = new BufferedImage(1160, 780, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    viewport.printAll(graphics);
                    graphics.dispose();
                    ImageIO.write(image, "png", Paths.get(shot).toFile());
                }
                System.out.println("BattleLogTest: PASS");
            } catch (Exception error) { throw new RuntimeException(error); }
        });
    }

    private static BattleEvent ev(BattleEvent.Type type, long id, String actor, String target, int amount, String reason) {
        return BattleEvent.fromWire(type, id, actor, target, amount, reason, null, -1, -1, -1);
    }

    private static BattleLog log(BattlePanel panel) throws Exception {
        Field field = BattlePanel.class.getDeclaredField("battleLog");
        field.setAccessible(true);
        return (BattleLog) field.get(panel);
    }

    private static void layout(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) if (child instanceof Container) layout((Container) child);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
