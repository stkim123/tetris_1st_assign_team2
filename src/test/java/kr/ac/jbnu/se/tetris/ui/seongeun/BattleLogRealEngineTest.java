package kr.ac.jbnu.se.tetris.ui.seongeun;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.SwingUtilities;
import kr.ac.jbnu.se.tetris.ai.AIPlan;
import kr.ac.jbnu.se.tetris.ai.HeuristicStrategy;
import kr.ac.jbnu.se.tetris.ai.HeuristicWeights;
import kr.ac.jbnu.se.tetris.app.MonsterSession;
import kr.ac.jbnu.se.tetris.battle.BattleResult;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.story.MonsterTier;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.BattleLog;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.GameArt;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.BattlePanel;

/**
 * 합성 이벤트가 아니라 실제 전투 엔진(플레이어 AI vs 몬스터 AI)이 만든 이벤트를 BattlePanel에 흘려 넣어,
 * 줄 클리어·피해·가비지가 한 줄 알림으로 합쳐져 나오는지, 내/상대 알림이 모두 나오는지 확인한다.
 */
public final class BattleLogRealEngineTest {
    private static final long TURN_NANOS = 1_000_000_000L;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        final BattlePanel[] holder = new BattlePanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new BattlePanel();
            holder[0].setEncounter("university", 1, MonsterTier.NORMAL, "university:0");
            GameArt.sprite("university:0");
        });
        final BattlePanel panel = holder[0];
        Field field = BattlePanel.class.getDeclaredField("battleLog");
        field.setAccessible(true);
        final BattleLog log = (BattleLog) field.get(panel);

        MonsterSession session = new MonsterSession(1);
        HeuristicStrategy player = new HeuristicStrategy(HeuristicWeights.TETRIS);
        Set<String> seen = new LinkedHashSet<String>();
        long now = TURN_NANOS;
        long deadline = System.nanoTime() + 30_000_000_000L;
        try {
            for (int turn = 0; turn < 300 && !session.isFinished() && System.nanoTime() < deadline; turn++) {
                spawn(session);
                if (session.getOpponentState().getActivePiece() != null) {
                    AIPlan before = session.getLastPlan();
                    BattleResult previous = session.getLastBattleResult();
                    long until = System.nanoTime() + 10_000_000_000L;
                    do {
                        session.pulse(now);
                        if (session.getLastPlan() != before || session.isFinished()) break;
                        Thread.sleep(2);
                    } while (System.nanoTime() < until);
                    if (session.getLastBattleResult() != previous) feed(panel, log, session, seen);
                }
                now += TURN_NANOS;
                if (session.isFinished()) break;
                spawn(session);
                if (session.getPlayerState().getActivePiece() != null) {
                    AIPlan plan = player.plan(session.getPlayerState());
                    for (GameAction.Type action : plan.getActions()) {
                        session.submit(action);
                        feed(panel, log, session, seen);
                        if (session.isFinished()) break;
                    }
                }
            }
        } finally { session.close(); }

        System.out.println("distinct notifications: " + seen.size());
        int shown = 0;
        for (String text : seen) System.out.println("  " + text);

        String attack = "(나|일반 몬스터) · \\d+줄 클리어( 퍼펙트!)?( · \\d+콤보)?( → .*)?";
        boolean mineAttack = false, enemyAttack = false, mergedDamage = false;
        for (String text : seen) {
            if (text.matches(attack) || text.matches("(나|일반 몬스터) · \\d+줄 클리어.*")) {
                if (text.startsWith("나 · ")) mineAttack = true; else enemyAttack = true;
            }
            if (text.contains("→") && text.contains("피해")) mergedDamage = true;
            // 모든 알림은 내(나) 또는 상대(일반 몬스터)로 시작해야 한다.
            if (!text.startsWith("나 · ") && !text.startsWith("일반 몬스터 · "))
                throw new AssertionError("Unexpected notification subject: " + text);
        }
        check(!seen.isEmpty(), "real battle produced notifications");
        check(mineAttack, "player line-clear notification appeared");
        check(enemyAttack, "monster line-clear notification appeared");
        check(mergedDamage, "damage merged into the clear line");
        System.out.println("BattleLogRealEngineTest: PASS");
    }

    private static void feed(BattlePanel panel, BattleLog log, MonsterSession session, Set<String> seen) throws Exception {
        final BattleResult result = session.getLastBattleResult();
        if (result == null) return;
        SwingUtilities.invokeAndWait(() -> {
            panel.setState(session.getBattleState(), MonsterSession.PLAYER_ID);
            panel.applyEvents(result.getEvents(), MonsterSession.PLAYER_ID);
            seen.addAll(new ArrayList<String>(log.texts()));
        });
    }

    private static void spawn(MonsterSession session) {
        if (!session.isFinished() && (session.getPlayerState().isAwaitingSpawn()
                || session.getOpponentState().isAwaitingSpawn())) session.tick();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
