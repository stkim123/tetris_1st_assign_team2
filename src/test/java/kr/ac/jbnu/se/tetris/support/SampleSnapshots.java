package kr.ac.jbnu.se.tetris.support;

import java.util.Arrays;
import kr.ac.jbnu.se.tetris.battle.BattleManager;
import kr.ac.jbnu.se.tetris.battle.BattleState;
import kr.ac.jbnu.se.tetris.battle.ParticipantSpec;

/** UI와 네트워크 테스트용 실제 엔진 기반 전투 사본 */
public final class SampleSnapshots {
    private SampleSnapshots() { }

    public static BattleState running(String localId, String opponentId) {
        return battle(localId, opponentId).getState();
    }

    public static BattleState finished(String localId, String opponentId, String winnerId) {
        BattleManager battle = battle(localId, opponentId);
        battle.forfeit(winnerId.equals(localId) ? opponentId : localId);
        return battle.getState();
    }

    private static BattleManager battle(String localId, String opponentId) {
        BattleManager battle = new BattleManager(Arrays.asList(
                new ParticipantSpec(localId, "학생", 100),
                new ParticipantSpec(opponentId, "상대 학생", 100)), 37L);
        if (!battle.start().isAccepted()) throw new IllegalStateException("Fixture battle start failed");
        return battle;
    }
}
