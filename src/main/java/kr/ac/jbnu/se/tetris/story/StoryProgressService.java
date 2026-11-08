package kr.ac.jbnu.se.tetris.story;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import kr.ac.jbnu.se.tetris.battle.BattleResult;
import kr.ac.jbnu.se.tetris.battle.BattleState;

/** 실제 전투 결과만 누적 해금에 반영하는 단일 진행 관리자 */
public final class StoryProgressService {
    public enum ResultDisposition { APPLIED_WIN, APPLIED_LOSS, IN_PROGRESS, DUPLICATE, STALE, CONFLICT }

    private final StageCatalog catalog;
    private final Set<String> completedEncounterIds = new LinkedHashSet<String>();
    private final Set<String> usedRunIds = new HashSet<String>();
    private EncounterRun activeRun;
    private StageProgress activeProgress;
    private long terminalVersion;
    private String terminalWinnerId;
    private String terminalReason;

    public StoryProgressService(StageCatalog catalog) {
        if (catalog == null) throw new IllegalArgumentException("Stage catalog is required");
        this.catalog = catalog;
    }

    public synchronized CampaignProgress getCampaignProgress() {
        return new CampaignProgress(catalog, completedEncounterIds);
    }

    /** 저장된 캠페인 완료 기록 복원. 순차 해금 경로만 허용한다. */
    public synchronized void restoreCompletedEncounterIds(Set<String> completed) {
        if (completed == null || activeRun != null) {
            throw new IllegalArgumentException("Progress may only be restored before starting a run");
        }
        Set<String> expected = new LinkedHashSet<String>();
        boolean gap = false;
        for (Stage stage : catalog.getStages()) {
            for (MonsterSpec monster : stage.getEncounters()) {
                boolean present = completed.contains(monster.getId());
                if (present && gap) throw new IllegalArgumentException("Campaign progress has a gap");
                if (present) expected.add(monster.getId());
                else gap = true;
            }
        }
        if (expected.size() != completed.size()) throw new IllegalArgumentException("Unknown campaign encounter");
        completedEncounterIds.clear();
        completedEncounterIds.addAll(expected);
    }

    public synchronized EncounterRun getActiveRun() { return activeRun; }

    /** 잠금 검사 후 첫 미완료 전투 시작, 완료된 레벨은 해당 전투 재시작 */
    public synchronized EncounterRun startStage(String stageId, String runId,
                                                String localParticipantId, String monsterParticipantId) {
        CampaignProgress progress = getCampaignProgress();
        if (!progress.isStageUnlocked(stageId)) {
            throw new IllegalStateException("Stage is locked: " + stageId);
        }
        return begin(stageId, progress.getNextEncounter(stageId), runId,
                localParticipantId, monsterParticipantId);
    }

    /** 지정 패턴 전투의 해금을 확인한 후 시작 (이전 3전투 UI와 호환). */
    public synchronized EncounterRun startEncounter(String stageId, MonsterTier tier, String runId,
                                                     String localParticipantId, String monsterParticipantId) {
        if (!getCampaignProgress().isEncounterUnlocked(stageId, tier)) {
            throw new IllegalStateException("Encounter is locked: " + stageId + "/" + tier);
        }
        for (MonsterSpec monster : catalog.getStage(stageId).getEncounters()) {
            if (monster.getTier() == tier) {
                return begin(stageId, monster, runId, localParticipantId, monsterParticipantId);
            }
        }
        throw new IllegalArgumentException("Unknown encounter tier: " + tier);
    }

    /** 3×3 캠페인의 특정 레벨을 ID로 선택한다. 같은 Elite 패턴도 독립적으로 해금된다. */
    public synchronized EncounterRun startEncounter(String stageId, String encounterId, String runId,
                                                     String localParticipantId, String monsterParticipantId) {
        if (!getCampaignProgress().isEncounterUnlocked(stageId, encounterId)) {
            throw new IllegalStateException("Encounter is locked: " + stageId + "/" + encounterId);
        }
        for (MonsterSpec monster : catalog.getStage(stageId).getEncounters()) {
            if (monster.getId().equals(encounterId)) {
                return begin(stageId, monster, runId, localParticipantId, monsterParticipantId);
            }
        }
        throw new IllegalArgumentException("Unknown encounter ID: " + encounterId);
    }

    /** 패배 또는 포기 뒤 같은 상대를 새 전투로 재시작 */
    public synchronized EncounterRun restartActive(String newRunId) {
        if (activeRun == null) throw new IllegalStateException("No active story encounter");
        return begin(activeRun.getStageId(), activeRun.getMonster(), newRunId,
                activeRun.getLocalParticipantId(), activeRun.getMonsterParticipantId());
    }

    /** 확정 승리 뒤 다음 전투나 다음 레벨의 첫 전투 시작 */
    public synchronized EncounterRun nextEncounter(String newRunId) {
        if (activeRun == null || !activeRun.isWon()) {
            throw new IllegalStateException("Confirmed win is required to advance");
        }
        String stageId = activeRun.getStageId();
        Stage stage = catalog.getStage(stageId);
        int stageIndex = catalog.getStages().indexOf(stage);
        int encounterIndex = stage.getEncounters().indexOf(activeRun.getMonster());
        if (encounterIndex < stage.getEncounters().size() - 1) {
            return begin(stageId, stage.getEncounters().get(encounterIndex + 1), newRunId,
                    activeRun.getLocalParticipantId(), activeRun.getMonsterParticipantId());
        }
        if (stageIndex == catalog.getStages().size() - 1) return null;
        Stage next = catalog.getStages().get(stageIndex + 1);
        return begin(next.getId(), next.getEncounters().get(0), newRunId,
                activeRun.getLocalParticipantId(), activeRun.getMonsterParticipantId());
    }

    /** 종료된 BattleResult의 승자와 참가자 검증 후 한 번만 진행 반영 */
    public synchronized ResultDisposition recordBattleResult(String runId, BattleResult result) {
        if (blank(runId) || result == null || result.getState() == null) {
            throw new IllegalArgumentException("Run ID and battle result are required");
        }
        if (activeRun == null || !activeRun.getRunId().equals(runId)) return ResultDisposition.STALE;
        BattleState state = result.getState();
        if (state.getStatus() != BattleState.Status.FINISHED) return ResultDisposition.IN_PROGRESS;
        if (state.getParticipants().size() != 2
                || !state.getParticipants().containsKey(activeRun.getLocalParticipantId())
                || !state.getParticipants().containsKey(activeRun.getMonsterParticipantId())) {
            throw new IllegalArgumentException("Battle participants do not match story run");
        }
        String winnerId = state.getWinnerId();
        if (winnerId != null && !winnerId.equals(activeRun.getLocalParticipantId())
                && !winnerId.equals(activeRun.getMonsterParticipantId())) {
            throw new IllegalArgumentException("Battle winner does not match story run");
        }
        boolean won = activeRun.getLocalParticipantId().equals(winnerId);
        if (activeRun.isFinished()) {
            return state.getVersion() == terminalVersion
                    && same(winnerId, terminalWinnerId)
                    && same(state.getReason(), terminalReason)
                    ? ResultDisposition.DUPLICATE : ResultDisposition.CONFLICT;
        }
        if (!result.isAccepted() && winnerId == null) {
            return ResultDisposition.IN_PROGRESS;
        }
        activeProgress.completeEncounter(won);
        activeRun = activeRun.resolved(won);
        terminalVersion = state.getVersion();
        terminalWinnerId = winnerId;
        terminalReason = state.getReason();
        if (won) completedEncounterIds.add(activeRun.getEncounterId());
        return won ? ResultDisposition.APPLIED_WIN : ResultDisposition.APPLIED_LOSS;
    }

    private EncounterRun begin(String stageId, MonsterSpec monster, String runId,
                               String localParticipantId, String monsterParticipantId) {
        if (blank(runId) || blank(localParticipantId) || blank(monsterParticipantId)
                || localParticipantId.equals(monsterParticipantId)) {
            throw new IllegalArgumentException("Distinct run and participant IDs are required");
        }
        if (!usedRunIds.add(runId)) throw new IllegalArgumentException("Run ID was already used: " + runId);
        Stage stage = catalog.getStage(stageId);
        int stageIndex = catalog.getStages().indexOf(stage);
        int encounterIndex = stage.getEncounters().indexOf(monster);
        activeProgress = new StageProgress(catalog, stageIndex, encounterIndex);
        activeRun = new EncounterRun(runId, stageId, monster, localParticipantId,
                monsterParticipantId, false, false);
        terminalVersion = 0;
        terminalWinnerId = null;
        terminalReason = null;
        return activeRun;
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
