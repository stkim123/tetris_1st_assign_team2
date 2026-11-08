package kr.ac.jbnu.se.tetris.app;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import kr.ac.jbnu.se.tetris.ai.AIPlan;
import kr.ac.jbnu.se.tetris.ai.HeuristicStrategy;
import kr.ac.jbnu.se.tetris.ai.HeuristicWeights;
import kr.ac.jbnu.se.tetris.app.session.LocalMatchSession;
import kr.ac.jbnu.se.tetris.app.session.SessionPhase;
import kr.ac.jbnu.se.tetris.app.session.SessionSnapshot;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.story.MonsterSpec;
import kr.ac.jbnu.se.tetris.story.Stage;
import kr.ac.jbnu.se.tetris.story.StageCatalog;

/** 현재 화면에서 실제 전투 엔진으로 패배·재도전·9전투 해금·최종 종료를 확인한다. */
public final class StoryFlowTest {
    public static void main(String[] args) throws Exception {
        final SeongeunApplication[] holder = new SeongeunApplication[1];
        try {
            edt(() -> {
                holder[0] = new SeongeunApplication(new Random(20260928L), null);
                button(holder[0].getScreens(), "lobbyStory").doClick();
                check("STORY_STAGE".equals(holder[0].getCurrentScreen()), "스토리 선택 화면");
                button(holder[0].getScreens(), "storyStart").doClick();
            });
            SeongeunApplication app = holder[0];
            String first = onEdt(() -> opponent(app));
            edt(() -> {
                for (int i = 0; i < 100 && !finished(app); i++) app.submit(GameAction.Type.HARD_DROP);
                check(finished(app), "첫 상대에게 실제 엔진으로 패배");
            });
            await(() -> "RESULT".equals(app.getCurrentScreen()), "패배 결과 화면");
            edt(() -> {
                button(app.getScreens(), "다시 도전").doClick();
                check("BATTLE".equals(app.getCurrentScreen()), "패배 재도전 전투 화면");
                check(first.equals(opponent(app)), "패배 재도전은 같은 상대");
            });

            int encounter = 0;
            // 기대 코인: 첫 클리어는 등급별 전액(일반 30/정예 50/보스 80), 재클리어는 전액의 20%.
            final int[] coins = { onEdt(() -> app.getSaveData().getCoins()) };
            for (Stage stage : StageCatalog.loadDefault().getStages()) {
                for (MonsterSpec monster : stage.getEncounters()) {
                    final int index = encounter;
                    check(onEdt(() -> monster.getName().equals(opponent(app))), "레벨 " + (index + 1) + " 상대");
                    edt(() -> winUsingRealEngine(app));
                    await(() -> "RESULT".equals(app.getCurrentScreen()), "레벨 " + (index + 1) + " 승리 결과");
                    check(onEdt(() -> app.getCampaignProgress().isEncounterCleared(monster.getId())),
                            "레벨 " + (index + 1) + " 진행 상태 저장");
                    coins[0] += firstClearReward(monster);
                    check(onEdt(() -> app.getSaveData().getCoins()) == coins[0],
                            "레벨 " + (index + 1) + " 첫 클리어 보상 " + firstClearReward(monster) + "코인");
                    if (encounter == 0) {
                        edt(() -> {
                            button(app.getScreens(), "다시 도전").doClick();
                            check(first.equals(opponent(app)), "승리 후 다시 도전도 같은 상대");
                            winUsingRealEngine(app);
                        });
                        await(() -> "RESULT".equals(app.getCurrentScreen()), "승리 재도전 결과");
                        coins[0] += firstClearReward(monster) / 5;
                        check(onEdt(() -> app.getSaveData().getCoins()) == coins[0],
                                "재클리어 보상은 첫 클리어의 20% (" + firstClearReward(monster) / 5 + "코인)");
                    }
                    if (encounter < 8) {
                        edt(() -> {
                            button(app.getScreens(), "다음 전투").doClick();
                            check("BATTLE".equals(app.getCurrentScreen()), "다음 전투 화면");
                            check(!monster.getName().equals(opponent(app)), "다음 상대 변경");
                            check(app.isStoryClockRunning(), "다음 전투 시계 시작");
                        });
                    } else {
                        edt(() -> {
                            check(!app.isStoryClockRunning(), "최종 승리 후 전투 시계 정지");
                            button(app.getScreens(), "Story로").doClick();
                            check("STORY_STAGE".equals(app.getCurrentScreen()), "최종 보스 뒤 선택 화면 복귀");
                            check(app.getMatchSnapshot() == null && !app.isStoryClockRunning(),
                                    "캠페인 종료 후 세션과 시계 정지");
                            check(button(app.getScreens(), "storyStart").isEnabled(), "완료한 마지막 전투 재도전 가능");
                        });
                    }
                    encounter++;
                }
            }
            check(encounter == 9, "정확히 아홉 전투");
        } finally {
            edt(() -> { if (holder[0] != null) holder[0].close(); });
        }
        System.out.println("PASS StoryFlowTest: current UI, 9 encounters, loss retry, win replay, final clock stop");
    }

    private static boolean finished(SeongeunApplication app) {
        return app.getMatchSnapshot().getPhase() == SessionPhase.FINISHED;
    }

    private static String opponent(SeongeunApplication app) {
        SessionSnapshot snapshot = app.getMatchSnapshot();
        for (String id : snapshot.getBattleState().getParticipants().keySet())
            if (!id.equals(snapshot.getLocalParticipantId()))
                return snapshot.getBattleState().getParticipant(id).getName();
        throw new AssertionError("상대가 없습니다");
    }

    private static void winUsingRealEngine(SeongeunApplication app) {
        HeuristicStrategy helper = new HeuristicStrategy(HeuristicWeights.SAFE, 1800, Long.MAX_VALUE);
        LocalMatchSession session = currentStorySession(app);
        // 테스트에서만 실제 세션의 중력을 직접 전진시켜 타이머 스케줄링을 결과에서 배제한다.
        session.stopClock();
        for (int i = 0; i < 500 && !finished(app); i++) {
            GameState game = playerState(app);
            if (game.getActivePiece() == null) session.advanceGravity();
            if (finished(app)) break;
            AIPlan plan = helper.plan(playerState(app));
            for (GameAction.Type action : plan.getActions()) app.submit(action);
            session.advanceGravity();
        }
        SessionSnapshot snapshot = app.getMatchSnapshot();
        check(finished(app) && snapshot.getLocalParticipantId().equals(snapshot.getBattleState().getWinnerId()),
                "실제 엔진으로 승리");
    }

    private static GameState playerState(SeongeunApplication app) {
        SessionSnapshot snapshot = app.getMatchSnapshot();
        return snapshot.getBattleState().getParticipant(snapshot.getLocalParticipantId()).getGameState();
    }

    private static LocalMatchSession currentStorySession(SeongeunApplication app) {
        try {
            Field field = SeongeunApplication.class.getDeclaredField("match");
            field.setAccessible(true);
            return (LocalMatchSession) field.get(app);
        } catch (ReflectiveOperationException error) { throw new AssertionError("스토리 세션 접근 실패", error); }
    }

    private static AbstractButton button(Component root, String label) {
        AbstractButton found = findButton(root, label);
        if (found == null) throw new AssertionError("버튼 없음: " + label);
        return found;
    }
    private static AbstractButton findButton(Component root, String label) {
        if (root instanceof AbstractButton) {
            AbstractButton button = (AbstractButton) root;
            if (label.equals(button.getName()) || label.equals(button.getText())) return button;
        }
        if (root instanceof Container) for (Component child : ((Container) root).getComponents()) {
            AbstractButton found = findButton(child, label);
            if (found != null) return found;
        }
        return null;
    }

    private static void edt(Runnable action) throws Exception { onEdt(() -> { action.run(); return null; }); }
    private static <T> T onEdt(java.util.concurrent.Callable<T> action) throws Exception {
        AtomicReference<T> value = new AtomicReference<T>();
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try { value.set(action.call()); }
            catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError("EDT failure", failure.get());
        return value.get();
    }
    private static void await(BooleanSupplier condition, String label) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!onEdt(() -> condition.getAsBoolean()) && System.nanoTime() < deadline) Thread.sleep(10);
        check(onEdt(() -> condition.getAsBoolean()), label);
    }
    private static int firstClearReward(MonsterSpec monster) {
        switch (monster.getTier()) {
            case NORMAL: return 30;
            case ELITE: return 50;
            default: return 80;
        }
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
