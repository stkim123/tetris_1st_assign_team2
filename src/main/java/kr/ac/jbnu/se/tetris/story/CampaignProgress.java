package kr.ac.jbnu.se.tetris.story;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** 전투 완료와 스테이지 해금을 보여 주는 불변 진행 기록 */
public final class CampaignProgress {
    private final StageCatalog catalog;
    private final Set<String> completedEncounterIds;

    CampaignProgress(StageCatalog catalog, Set<String> completedEncounterIds) {
        this.catalog = catalog;
        this.completedEncounterIds = Collections.unmodifiableSet(
                new LinkedHashSet<String>(completedEncounterIds));
    }

    public Set<String> getCompletedEncounterIds() { return completedEncounterIds; }

    public boolean isEncounterCleared(String encounterId) {
        return completedEncounterIds.contains(encounterId);
    }

    public boolean isStageUnlocked(String stageId) {
        int index = indexOf(stageId);
        if (index == 0) return true;
        Stage previous = catalog.getStages().get(index - 1);
        return isStageCleared(previous.getId());
    }

    public boolean isStageCleared(String stageId) {
        Stage stage = catalog.getStages().get(indexOf(stageId));
        for (MonsterSpec encounter : stage.getEncounters()) {
            if (!completedEncounterIds.contains(encounter.getId())) return false;
        }
        return true;
    }

    /** ID로 특정 전투의 해금을 판정한다. 같은 패턴이 연속으로 나와도 구분된다. */
    public boolean isEncounterUnlocked(String stageId, String encounterId) {
        if (encounterId == null) throw new IllegalArgumentException("Encounter ID is required");
        Stage stage = catalog.getStages().get(indexOf(stageId));
        if (!isStageUnlocked(stageId)) return false;
        for (int index = 0; index < stage.getEncounters().size(); index++) {
            if (stage.getEncounters().get(index).getId().equals(encounterId)) {
                return index == 0 || completedEncounterIds.contains(
                        stage.getEncounters().get(index - 1).getId());
            }
        }
        throw new IllegalArgumentException("Unknown encounter ID: " + encounterId);
    }

    /** 이전 패턴별 선택 UI와 호환한다. 중복 패턴이면 첫 전투를 가리킨다. */
    public boolean isEncounterUnlocked(String stageId, MonsterTier tier) {
        if (tier == null) throw new IllegalArgumentException("Monster tier is required");
        Stage stage = catalog.getStages().get(indexOf(stageId));
        for (int index = 0; index < stage.getEncounters().size(); index++) {
            if (stage.getEncounters().get(index).getTier() == tier) {
                return isEncounterUnlocked(stageId, stage.getEncounters().get(index).getId());
            }
        }
        throw new IllegalArgumentException("Unknown encounter tier: " + tier);
    }

    /** 완료된 레벨의 재진입은 그 레벨의 첫 전투 */
    public MonsterSpec getNextEncounter(String stageId) {
        Stage stage = catalog.getStages().get(indexOf(stageId));
        for (MonsterSpec encounter : stage.getEncounters()) {
            if (!completedEncounterIds.contains(encounter.getId())) return encounter;
        }
        return stage.getEncounters().get(0);
    }

    private int indexOf(String stageId) {
        if (stageId == null) throw new IllegalArgumentException("Stage ID is required");
        for (int index = 0; index < catalog.getStages().size(); index++) {
            if (catalog.getStages().get(index).getId().equals(stageId)) return index;
        }
        throw new IllegalArgumentException("Unknown stage ID: " + stageId);
    }
}
