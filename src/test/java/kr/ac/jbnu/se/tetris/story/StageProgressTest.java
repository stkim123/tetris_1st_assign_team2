package kr.ac.jbnu.se.tetris.story;

/** 패배 재시도와 승리 뒤 이동을 전 스테이지에 걸쳐 검증 */
public final class StageProgressTest {
    public static void main(String[] args) {
        StageCatalog catalog = StageCatalog.loadDefault();
        StageProgress progress = new StageProgress(catalog);
        expectState(progress::advance);
        progress.completeEncounter(false);
        check(progress.getOutcome() == StageProgress.Outcome.LOST, "패배가 기록된다");
        expectState(() -> progress.completeEncounter(true));
        expectState(progress::advance);
        progress.retry();
        check(progress.getCurrentEncounter().getId().equals("level_1_monster"),
                "재시도는 같은 몬스터를 유지한다");
        expectState(progress::retry);
        expectState(progress::replay);

        progress.completeEncounter(true);
        progress.replay();
        check(progress.getCurrentEncounter().getId().equals("level_1_monster")
                && progress.getOutcome() == StageProgress.Outcome.IN_PROGRESS,
                "승리 후 재대결도 같은 몬스터를 유지한다");

        for (int stageIndex = 0; stageIndex < 3; stageIndex++) {
            for (int encounterIndex = 0; encounterIndex < 3; encounterIndex++) {
                check(progress.getStageIndex() == stageIndex
                        && progress.getEncounterIndex() == encounterIndex,
                        "진행 순서가 맞다");
                check(progress.isLastEncounter() == (stageIndex == 2 && encounterIndex == 2),
                        "마지막 보스를 식별한다");
                progress.completeEncounter(true);
                expectState(() -> progress.completeEncounter(true));
                progress.advance();
                expectState(progress::advance);
            }
        }
        check(progress.isCampaignComplete(), "최종 보스 승리 후 완료된다");
        check(progress.getCurrentStage() == null && progress.getCurrentEncounter() == null,
                "완료된 캠페인에는 다음 전투가 없다");
        expectState(() -> progress.completeEncounter(true));

        StageProgress selected = new StageProgress(catalog, 2);
        check(selected.getCurrentStage() == catalog.getStages().get(2)
                && selected.getCurrentEncounter().getTier() == MonsterTier.ELITE,
                "선택한 과정의 첫 패턴부터 시작한다");
        expectInvalid(() -> new StageProgress(catalog, 3));
    }

    private static void expectState(Runnable action) {
        try { action.run(); }
        catch (IllegalStateException expected) { return; }
        throw new AssertionError("잘못된 진행 전이를 거부해야 한다");
    }

    private static void expectInvalid(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("잘못된 시작 장을 거부해야 한다");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
