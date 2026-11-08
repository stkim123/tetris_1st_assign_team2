package kr.ac.jbnu.se.tetris.story;

/** 화면이 원하는 방식으로 표시할 수 있는 스토리 대사 이벤트 한 건. */
public final class StoryDialogueCue {
    public enum Trigger {
        MONSTER_HP_THRESHOLD, ENCOUNTER_WIN, ENCOUNTER_LOSS,
        STAGE_CLEAR, HALFWAY, CAMPAIGN_CLEAR,
        /** 전투 시작(조우), 시작 30초 뒤 플레이어와 몬스터의 대화, 플레이어의 강공격, 몬스터 HP 위기 */
        ENCOUNTER_START, INTERACTION, STRONG_ATTACK, MONSTER_CRISIS
    }

    private final Trigger trigger;
    private final String runId;
    private final String stageId;
    private final String encounterId;
    private final String speaker;
    private final String text;

    StoryDialogueCue(Trigger trigger, EncounterRun run, String speaker, String text) {
        this.trigger = trigger;
        this.runId = run.getRunId();
        this.stageId = run.getStageId();
        this.encounterId = run.getEncounterId();
        this.speaker = speaker;
        this.text = text;
    }

    public Trigger getTrigger() { return trigger; }
    public String getRunId() { return runId; }
    public String getStageId() { return stageId; }
    public String getEncounterId() { return encounterId; }
    public String getSpeaker() { return speaker; }
    public String getText() { return text; }
}
