package kr.ac.jbnu.se.tetris.battle;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import kr.ac.jbnu.se.tetris.core.CoreSnapshots;
import kr.ac.jbnu.se.tetris.core.GameState;

/** 네트워크 표시와 독립 UI 예제를 위한 전투 사본 생성 경계 */
public final class BattleSnapshots {
    private BattleSnapshots() { }

    public static ParticipantState participant(String id, String name, int hp, int maxHp,
            GameState state, boolean eliminated) {
        return participant(id, name, hp, maxHp, state, eliminated, "student", 3,
                java.util.Collections.<String>emptyList(), 0, 0, 0, 0, 0, 500, 0, 0);
    }

    public static ParticipantState participant(String id, String name, int hp, int maxHp,
            GameState state, boolean eliminated, String characterId, int itemSlots,
            List<String> items, int fever, long feverRemainingMillis, long timeWarpRemainingMillis,
            int pendingGarbageLines, long garbageWaitRemainingMillis, int gravityMillis,
            int maxCombo, int totalDamage) {
        return participant(id, name, hp, maxHp, state, eliminated, characterId, itemSlots,
                items, items == null ? null : java.util.Collections.nCopies(items.size(), 1),
                fever, feverRemainingMillis, timeWarpRemainingMillis, pendingGarbageLines,
                garbageWaitRemainingMillis, gravityMillis, maxCombo, totalDamage);
    }

    public static ParticipantState participant(String id, String name, int hp, int maxHp,
            GameState state, boolean eliminated, String characterId, int itemSlots,
            List<String> items, List<Integer> itemCharges, int fever,
            long feverRemainingMillis, long timeWarpRemainingMillis,
            int pendingGarbageLines, long garbageWaitRemainingMillis, int gravityMillis,
            int maxCombo, int totalDamage) {
        if (id == null || id.trim().isEmpty() || id.length() > 128 || name == null
                || name.trim().isEmpty() || name.length() > 128 || maxHp <= 0 || hp < 0 || hp > maxHp
                || state == null || !id.equals(state.getActorId())
                || ((hp == 0 || state.getStatus() == GameState.Status.GAME_OVER) && !eliminated)
                || characterId == null || characterId.trim().isEmpty() || itemSlots < 1
                || items == null || items.size() > itemSlots || itemCharges == null
                || itemCharges.size() != items.size() || fever < 0 || fever > 100
                || feverRemainingMillis < 0 || timeWarpRemainingMillis < 0
                || pendingGarbageLines < 0 || garbageWaitRemainingMillis < 0
                || gravityMillis < 1 || maxCombo < 0 || totalDamage < 0) {
            throw new IllegalArgumentException("Invalid participant snapshot");
        }
        for (String item : items) if (item == null || item.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid item snapshot");
        }
        for (Integer charges : itemCharges) if (charges == null || charges < 1 || charges > 2) {
            throw new IllegalArgumentException("Invalid item charges");
        }
        return new ParticipantState(id, name, hp, maxHp, CoreSnapshots.copyOf(state), eliminated,
                characterId, itemSlots, items, itemCharges, fever, feverRemainingMillis, timeWarpRemainingMillis,
                pendingGarbageLines, garbageWaitRemainingMillis, gravityMillis, maxCombo, totalDamage);
    }

    public static BattleState battle(BattleState.Status status, long version,
            Map<String, ParticipantState> participants, String winnerId, String reason) {
        return battle(status, version, participants, winnerId, reason, 0);
    }

    public static BattleState battle(BattleState.Status status, long version,
            Map<String, ParticipantState> participants, String winnerId, String reason,
            long elapsedMillis) {
        if (status == null || version < 0 || participants == null || participants.size() < 2
                || participants.size() > 4 || elapsedMillis < 0)
            throw new IllegalArgumentException("Invalid battle snapshot");
        Map<String, ParticipantState> copied = new LinkedHashMap<String, ParticipantState>();
        for (Map.Entry<String, ParticipantState> entry : participants.entrySet()) {
            ParticipantState p = entry.getValue();
            if (p == null || !p.getId().equals(entry.getKey())) {
                throw new IllegalArgumentException("Participant key mismatch");
            }
            copied.put(entry.getKey(), participant(p.getId(), p.getName(), p.getHp(), p.getMaxHp(),
                    p.getGameState(), p.isEliminated(), p.getCharacterId(), p.getItemSlots(),
                    p.getItems(), p.getItemCharges(), p.getFever(), p.getFeverRemainingMillis(),
                    p.getTimeWarpRemainingMillis(), p.getPendingGarbageLines(),
                    p.getGarbageWaitRemainingMillis(), p.getGravityMillis(), p.getMaxCombo(),
                    p.getTotalDamage()));
        }
        if (status != BattleState.Status.FINISHED && (winnerId != null || reason != null)) {
            throw new IllegalArgumentException("Unfinished battle cannot have a result");
        }
        if (status == BattleState.Status.FINISHED && (reason == null || reason.trim().isEmpty())) {
            throw new IllegalArgumentException("Finished battle needs a reason");
        }
        if (winnerId != null && (!copied.containsKey(winnerId) || copied.get(winnerId).isEliminated())) {
            throw new IllegalArgumentException("Invalid winner");
        }
        return new BattleState(status, version, copied, winnerId, reason, elapsedMillis);
    }

    public static BattleState copyOf(BattleState state) {
        if (state == null) throw new IllegalArgumentException("Battle state is required");
        return battle(state.getStatus(), state.getVersion(), state.getParticipants(),
                state.getWinnerId(), state.getReason(), state.getElapsedMillis());
    }
}
