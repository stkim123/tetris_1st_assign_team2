package kr.ac.jbnu.se.tetris.app;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import kr.ac.jbnu.se.tetris.ai.AIPlan;
import kr.ac.jbnu.se.tetris.ai.HeuristicStrategy;
import kr.ac.jbnu.se.tetris.ai.HeuristicWeights;
import kr.ac.jbnu.se.tetris.app.session.LocalMatchSession;
import kr.ac.jbnu.se.tetris.app.session.SessionSnapshot;
import kr.ac.jbnu.se.tetris.battle.BattleEvent;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameEvent;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.BattleDialogue;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.BattlePanel;

/**
 * 스토리 전투 전체 흐름에서 대사집의 대사가 올바른 때에 나오는지 실제 엔진으로 확인한다:
 * 조우(시작) → 강공격(플레이어 4줄) → 30초 대화 → 패배 결과 대사 → 재도전 때 이전 대사 정리 → HP·위기 대사 → 승리 결과 대사.
 */
public final class StoryDialogueFlowTest {
    private static final String ENCOUNTER = "신입생인가? 좋아! 오늘은 내가 네 간을 시험해 주마!";
    private static final String STRONG = "폭탄주 콤보오오오!";
    private static final String TALK_PLAYER = "저 내일 9시 수업인데요?";
    private static final String TALK_MONSTER = "그런 건 2차에서 생각하는 거다!";
    private static final String HP_LINE = "어어?! 아직 1차도 안 끝났는데?!";
    private static final String CRISIS = "아직 집에 보낼 순 없다…!";

    public static void main(String[] args) throws Exception {
        final SeongeunApplication[] holder = new SeongeunApplication[1];
        try {
            edt(() -> {
                holder[0] = new SeongeunApplication(new Random(20260928L), null);
                button(holder[0].getScreens(), "lobbyStory").doClick();
                button(holder[0].getScreens(), "storyStart").doClick();
            });
            final SeongeunApplication app = holder[0];

            // 1) 전투 시작: 몬스터의 조우 대사가 나온다.
            check(onEdt(() -> texts(app)).equals(Arrays.asList(ENCOUNTER)),
                    "조우 대사만 나옴: " + onEdt(() -> texts(app)));

            // 2) 강공격: 몬스터가 4줄 지운 것은 무시하고, 플레이어가 4줄 지우면 대사가 나온다.
            final String localId = onEdt(() -> app.getMatchSnapshot().getLocalParticipantId());
            edt(() -> showStoryDialogues(app, lineClear(localId.equals("monster") ? "local" : "monster", 4)));
            check(!onEdt(() -> texts(app)).contains(STRONG), "몬스터가 4줄 지운 것으로는 강공격 대사가 나오지 않음");
            edt(() -> showStoryDialogues(app, lineClear(localId, 3)));
            check(!onEdt(() -> texts(app)).contains(STRONG), "3줄 제거는 강공격이 아님");
            edt(() -> showStoryDialogues(app, lineClear(localId, 4)));
            check(onEdt(() -> texts(app)).equals(Arrays.asList(ENCOUNTER, STRONG)),
                    "플레이어 4줄 제거 시 강공격 대사: " + onEdt(() -> texts(app)));
            edt(() -> showStoryDialogues(app, lineClear(localId, 4)));
            check(onEdt(() -> texts(app)).size() == 2, "강공격 대사는 전투당 한 번");

            // 3) 시작 30초 뒤: 플레이어의 말 다음 몬스터의 대답.
            final LocalMatchSession session = currentSession(app);
            edt(() -> { session.stopClock(); session.advanceMillis(29000); });
            check(!onEdt(() -> texts(app)).contains(TALK_PLAYER), "30초 전에는 대화하지 않음");
            edt(() -> session.advanceMillis(1500));
            long elapsed = onEdt(() -> app.getMatchSnapshot().getBattleState().getElapsedMillis());
            check(elapsed >= 30000 && onEdt(() -> !finished(app)), "전투가 30초를 넘겨 진행 중: " + elapsed);
            check(onEdt(() -> texts(app)).equals(Arrays.asList(ENCOUNTER, STRONG, TALK_PLAYER, TALK_MONSTER)),
                    "30초에 플레이어 말 다음 몬스터 대답: " + onEdt(() -> texts(app)));
            edt(() -> session.advanceMillis(1000));
            check(onEdt(() -> texts(app)).size() == 4, "30초 대화는 한 번만");

            // 4) 패배: 결과 화면에 몬스터(승리) 다음 플레이어(패배) 대사.
            edt(() -> { for (int i = 0; i < 200 && !finished(app); i++) app.submit(GameAction.Type.HARD_DROP); });
            check(onEdt(() -> finished(app)), "하드드롭으로 패배");
            await(() -> "RESULT".equals(app.getCurrentScreen()), "패배 결과 화면");
            check(onEdt(() -> screenText(app)).contains("몬스터: 으하하! 오늘은 내가 쏜다! 3차 가자!")
                            && onEdt(() -> screenText(app)).contains("플레이어: 으윽… 속이 안 좋아…"),
                    "패배 결과 화면에 몬스터(승리)·플레이어(패배) 대사");

            // 5) 재도전: 이전 전투의 대기 대사가 이어지지 않고 새 조우 대사만 나온다.
            edt(() -> button(app.getScreens(), "다시 도전").doClick());
            check(onEdt(() -> texts(app)).equals(Arrays.asList(ENCOUNTER)),
                    "재도전 시작에는 새 조우 대사만: " + onEdt(() -> texts(app)));

            // 6) 승리: 몬스터 HP가 줄면 HP 대사, 20% 이하가 되면 위기 대사, 결과 화면에 승리 대사.
            edt(() -> winUsingRealEngine(app));
            List<String> afterWin = onEdt(() -> texts(app));
            check(afterWin.indexOf(HP_LINE) > 0 && afterWin.indexOf(CRISIS) > afterWin.indexOf(HP_LINE),
                    "HP 대사 다음 위기 대사: " + afterWin);
            check(afterWin.indexOf(TALK_PLAYER) < 0, "재도전에서 30초 전에는 대화 없음");
            await(() -> "RESULT".equals(app.getCurrentScreen()), "승리 결과 화면");
            check(onEdt(() -> screenText(app)).contains("몬스터: 크으… 오늘은 네가 계산해라…")
                            && onEdt(() -> screenText(app)).contains("플레이어: 후… 오늘은 1차만 하고 집에 가겠습니다."),
                    "승리 결과 화면에 몬스터(패배)·플레이어(승리) 대사");
        } finally {
            edt(() -> { if (holder[0] != null) holder[0].close(); });
        }
        System.out.println("PASS StoryDialogueFlowTest: encounter, strong attack, 30s talk, loss/retry, HP, crisis, win");
    }

    // ---------- 접근 도우미 ----------

    private static List<String> texts(SeongeunApplication app) {
        try {
            Field battleField = SeongeunApplication.class.getDeclaredField("battle");
            battleField.setAccessible(true);
            Field dialogueField = BattlePanel.class.getDeclaredField("dialogue");
            dialogueField.setAccessible(true);
            return ((BattleDialogue) dialogueField.get(battleField.get(app))).texts();
        } catch (ReflectiveOperationException error) { throw new AssertionError("대사 상자 접근 실패", error); }
    }

    private static void showStoryDialogues(SeongeunApplication app, List<BattleEvent> events) {
        try {
            Method method = SeongeunApplication.class.getDeclaredMethod("showStoryDialogues",
                    SessionSnapshot.class, List.class);
            method.setAccessible(true);
            method.invoke(app, app.getMatchSnapshot(), events);
        } catch (ReflectiveOperationException error) { throw new AssertionError("대사 연결 호출 실패", error); }
    }

    /** 실제 엔진이 낼 법한 줄 제거 이벤트(테스트에서만 비공개 생성자로 만든다). */
    private static List<BattleEvent> lineClear(String actorId, int lines) {
        try {
            Constructor<GameEvent> constructor = GameEvent.class.getDeclaredConstructor(GameEvent.Type.class,
                    long.class, long.class, long.class, String.class, kr.ac.jbnu.se.tetris.core.Piece.class,
                    int.class, int.class, int.class, String.class);
            constructor.setAccessible(true);
            GameEvent core = constructor.newInstance(GameEvent.Type.LINE_CLEAR, 1L, 1L, 1L, actorId, null, 0, 0, lines, null);
            return Arrays.asList(BattleEvent.fromWire(BattleEvent.Type.CORE_EVENT, 1L, actorId, null, 0, null, core, -1, -1, -1));
        } catch (ReflectiveOperationException error) { throw new AssertionError("줄 제거 이벤트 생성 실패", error); }
    }

    private static LocalMatchSession currentSession(SeongeunApplication app) {
        try {
            Field field = SeongeunApplication.class.getDeclaredField("match");
            field.setAccessible(true);
            return (LocalMatchSession) field.get(app);
        } catch (ReflectiveOperationException error) { throw new AssertionError("스토리 세션 접근 실패", error); }
    }

    private static boolean finished(SeongeunApplication app) {
        SessionSnapshot snapshot = app.getMatchSnapshot();
        return snapshot == null || snapshot.getBattleState().getStatus() == kr.ac.jbnu.se.tetris.battle.BattleState.Status.FINISHED;
    }

    private static GameState playerState(SeongeunApplication app) {
        SessionSnapshot snapshot = app.getMatchSnapshot();
        return snapshot.getBattleState().getParticipant(snapshot.getLocalParticipantId()).getGameState();
    }

    private static void winUsingRealEngine(SeongeunApplication app) {
        HeuristicStrategy helper = new HeuristicStrategy(HeuristicWeights.SAFE, 1800, Long.MAX_VALUE);
        LocalMatchSession session = currentSession(app);
        session.stopClock();
        for (int i = 0; i < 500 && !finished(app); i++) {
            if (playerState(app).getActivePiece() == null) session.advanceGravity();
            if (finished(app)) break;
            AIPlan plan = helper.plan(playerState(app));
            for (GameAction.Type action : plan.getActions()) app.submit(action);
            session.advanceGravity();
        }
        SessionSnapshot snapshot = app.getMatchSnapshot();
        check(finished(app) && snapshot.getLocalParticipantId().equals(snapshot.getBattleState().getWinnerId()),
                "실제 엔진으로 승리");
    }

    /** 화면 트리의 모든 문구를 한 문자열로 모은다. */
    private static String screenText(SeongeunApplication app) {
        StringBuilder out = new StringBuilder();
        collect(app.getScreens(), out);
        return out.toString();
    }

    private static void collect(Component component, StringBuilder out) {
        if (component instanceof JTextComponent) out.append(((JTextComponent) component).getText()).append('\n');
        else if (component instanceof JLabel) out.append(((JLabel) component).getText()).append('\n');
        if (component instanceof Container)
            for (Component child : ((Container) component).getComponents()) collect(child, out);
    }

    private static AbstractButton button(Component root, String label) {
        if (root instanceof AbstractButton) {
            AbstractButton button = (AbstractButton) root;
            if (label.equals(button.getText()) || label.equals(button.getName())) return button;
        }
        if (root instanceof Container)
            for (Component child : ((Container) root).getComponents()) {
                AbstractButton found = button(child, label);
                if (found != null) return found;
            }
        return null;
    }

    private static void edt(Runnable action) throws Exception { onEdt(() -> { action.run(); return null; }); }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        final Object[] holder = new Object[2];
        SwingUtilities.invokeAndWait(() -> {
            try { holder[0] = action.call(); } catch (Throwable error) { holder[1] = error; }
        });
        if (holder[1] instanceof Error) throw (Error) holder[1];
        if (holder[1] != null) throw new AssertionError(holder[1]);
        @SuppressWarnings("unchecked") T value = (T) holder[0];
        return value;
    }

    private static void await(BooleanSupplier condition, String label) throws Exception {
        long until = System.nanoTime() + 15_000_000_000L;
        while (System.nanoTime() < until) {
            if (onEdt(() -> condition.getAsBoolean())) return;
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out: " + label);
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
