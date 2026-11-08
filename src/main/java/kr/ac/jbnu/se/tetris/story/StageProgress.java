package kr.ac.jbnu.se.tetris.story;

/** 승패 기록과 다음 대전 이동의 분리 및 중복 결과 입력 방지 */
public final class StageProgress {
    public enum Outcome { IN_PROGRESS, WON, LOST, COMPLETE }

    private final StageCatalog catalog;
    private int stageIndex;
    private int encounterIndex;
    private Outcome outcome = Outcome.IN_PROGRESS;

    public StageProgress(StageCatalog catalog) { this(catalog, 0); }

    /** 스테이지 선택 화면에서 시작할 0부터 세는 인덱스 입력 */
    public StageProgress(StageCatalog catalog, int stageIndex) { this(catalog, stageIndex, 0); }

    StageProgress(StageCatalog catalog, int stageIndex, int encounterIndex) {
        if (catalog == null || stageIndex < 0 || stageIndex >= catalog.getStages().size()
                || encounterIndex < 0
                || encounterIndex >= catalog.getStages().get(stageIndex).getEncounters().size()) {
            throw new IllegalArgumentException("Invalid starting stage");
        }
        this.catalog = catalog;
        this.stageIndex = stageIndex;
        this.encounterIndex = encounterIndex;
    }

    public Stage getCurrentStage() {
        return isCampaignComplete() ? null : catalog.getStages().get(stageIndex);
    }

    public MonsterSpec getCurrentEncounter() {
        return isCampaignComplete() ? null : getCurrentStage().getEncounters().get(encounterIndex);
    }

    public int getStageIndex() { return stageIndex; }
    public int getEncounterIndex() { return encounterIndex; }
    public Outcome getOutcome() { return outcome; }
    public boolean isCampaignComplete() { return outcome == Outcome.COMPLETE; }
    public boolean isLastEncounter() {
        return !isCampaignComplete() && stageIndex == catalog.getStages().size() - 1
                && encounterIndex == getCurrentStage().getEncounters().size() - 1;
    }

    /** 현재 전투 결과의 단일 기록 */
    public void completeEncounter(boolean won) {
        if (outcome != Outcome.IN_PROGRESS) {
            throw new IllegalStateException("Encounter outcome already recorded");
        }
        outcome = won ? Outcome.WON : Outcome.LOST;
    }

    /** 승리 후 다음 몬스터 이동 및 최종 보스 승리 후 캠페인 완료 */
    public void advance() {
        if (outcome != Outcome.WON) throw new IllegalStateException("Win is required to advance");
        encounterIndex++;
        if (encounterIndex == catalog.getStages().get(stageIndex).getEncounters().size()) {
            encounterIndex = 0;
            stageIndex++;
        }
        outcome = stageIndex == catalog.getStages().size() ? Outcome.COMPLETE : Outcome.IN_PROGRESS;
    }

    /** 패배한 몬스터와의 전투 재시작 */
    public void retry() {
        if (outcome != Outcome.LOST) throw new IllegalStateException("Loss is required to retry");
        outcome = Outcome.IN_PROGRESS;
    }

    /** 결과 화면에서 승패와 관계없는 현재 몬스터 재도전 */
    public void replay() {
        if (outcome != Outcome.WON && outcome != Outcome.LOST) {
            throw new IllegalStateException("Finished encounter is required to replay");
        }
        outcome = Outcome.IN_PROGRESS;
    }
}
