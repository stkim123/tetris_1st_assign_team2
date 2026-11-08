package kr.ac.jbnu.se.tetris.story;

/** 한 전투의 식별자와 확정 결과를 묶은 불변 사본 */
public final class EncounterRun {
    private final String runId;
    private final String stageId;
    private final MonsterSpec monster;
    private final String localParticipantId;
    private final String monsterParticipantId;
    private final boolean finished;
    private final boolean won;

    EncounterRun(String runId, String stageId, MonsterSpec monster,
                 String localParticipantId, String monsterParticipantId,
                 boolean finished, boolean won) {
        this.runId = runId;
        this.stageId = stageId;
        this.monster = monster;
        this.localParticipantId = localParticipantId;
        this.monsterParticipantId = monsterParticipantId;
        this.finished = finished;
        this.won = won;
    }

    EncounterRun resolved(boolean won) {
        return new EncounterRun(runId, stageId, monster, localParticipantId,
                monsterParticipantId, true, won);
    }

    public String getRunId() { return runId; }
    public String getStageId() { return stageId; }
    public String getEncounterId() { return monster.getId(); }
    public MonsterSpec getMonster() { return monster; }
    public String getLocalParticipantId() { return localParticipantId; }
    public String getMonsterParticipantId() { return monsterParticipantId; }
    public boolean isFinished() { return finished; }
    public boolean isWon() { return finished && won; }
}
