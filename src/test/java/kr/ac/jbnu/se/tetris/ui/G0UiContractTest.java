package kr.ac.jbnu.se.tetris.ui;

import java.util.Arrays;
import javax.swing.JTabbedPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import kr.ac.jbnu.se.tetris.battle.BattleManager;
import kr.ac.jbnu.se.tetris.battle.BattleState;
import kr.ac.jbnu.se.tetris.battle.ParticipantSpec;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.BattlePanel;

/** 현재 전투 화면이 PvE와 PvP 상태를 각각 독립적으로 표시하는지 검증한다. */
public final class G0UiContractTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BattleManager pve = BattleManager.pve(Arrays.asList(
                    new ParticipantSpec("local", "플레이어", 100),
                    new ParticipantSpec("monster", "술", 30)), 37L, 450, 3500, 0);
            pve.start();
            BattlePanel panel = new BattlePanel();
            BattleState pveState = pve.getState();
            panel.setState(pveState, "local");
            check(panel.getPlayerBoard().getGameState() == pveState.getParticipant("local").getGameState(),
                    "PvE local board receives its own state");
            check(panel.getEnemyBoard().getGameState() == pveState.getParticipant("monster").getGameState(),
                    "PvE monster board receives monster state");
            pve.submit("local", GameAction.Type.HARD_DROP);
            pveState = pve.getState();
            panel.setState(pveState, "local");
            check(panel.getPlayerBoard().getGameState().getVersion()
                    == pveState.getParticipant("local").getGameState().getVersion(),
                    "new placement refreshes the visible board");

            BattleManager pvp = BattleManager.pvp(Arrays.asList(
                    new ParticipantSpec("local", "나", 100),
                    new ParticipantSpec("peer", "상대", 100)), 38L);
            pvp.start();
            panel.setMode(true);
            BattleState pvpState = pvp.getState();
            panel.setState(pvpState, "peer");
            check(panel.getPlayerBoard().getGameState() == pvpState.getParticipant("peer").getGameState(),
                    "online perspective follows local participant ID");
            check(panel.getEnemyBoard().getGameState() == pvpState.getParticipant("local").getGameState(),
                    "online opponent remains distinct");
            JPanel preview = UiPreviewMain.createPanel();
            check(((JTabbedPane) preview.getComponent(0)).getTabCount() >= 12,
                    "preview covers the current screen set");
        });
        System.out.println("PASS G0UiContractTest");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
