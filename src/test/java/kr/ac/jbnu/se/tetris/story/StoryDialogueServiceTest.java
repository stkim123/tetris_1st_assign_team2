package kr.ac.jbnu.se.tetris.story;

import java.util.Arrays;
import java.util.List;
import kr.ac.jbnu.se.tetris.battle.BattleManager;
import kr.ac.jbnu.se.tetris.battle.BattleResult;
import kr.ac.jbnu.se.tetris.battle.ParticipantSpec;

/** 실제 9전투 진행과 재시도에서 대사 트리거의 시점·중복 여부 검증. */
public final class StoryDialogueServiceTest {
    public static void main(String[] args) {
        hpThresholdAndRetry();
        campaignMilestonesAndReplay();
        scriptedTriggers();
        scriptCoversAllEncounters();
    }

    private static void hpThresholdAndRetry() {
        StageCatalog catalog = StageCatalog.loadDefault();
        StoryProgressService progress = new StoryProgressService(catalog);
        StoryDialogueService dialogue = new StoryDialogueService(catalog);
        EncounterRun first = progress.startStage("university", "first", "p", "m");

        check(dialogue.onMonsterHp(first, 51, 100) == null, "HP 절반 전에는 발화하지 않음");
        StoryDialogueCue hpCue = dialogue.onMonsterHp(first, 50, 100);
        check(hpCue != null && hpCue.getTrigger() == StoryDialogueCue.Trigger.MONSTER_HP_THRESHOLD,
                "HP 절반 도달 시 한 번 발화");
        check("first".equals(hpCue.getRunId()) && first.getEncounterId().equals(hpCue.getEncounterId()),
                "대사는 실제 전투와 몬스터를 식별함");
        check("몬스터".equals(hpCue.getSpeaker()) && "어어?! 아직 1차도 안 끝났는데?!".equals(hpCue.getText()),
                "HP 대사는 대사집의 술 대사");
        check(dialogue.onMonsterHp(first, 30, 100) == null
                && dialogue.onMonsterHp(first, 50, 100) == null,
                "추가 피해와 회복 뒤 같은 전투에서는 반복하지 않음");

        CampaignProgress beforeLoss = progress.getCampaignProgress();
        StoryProgressService.ResultDisposition loss = progress.recordBattleResult("first", forfeit("p"));
        List<StoryDialogueCue> lossCues = dialogue.onResult(progress.getActiveRun(), loss,
                beforeLoss, progress.getCampaignProgress());
        check(lossCues.size() == 2 && lossCues.get(0).getTrigger() == StoryDialogueCue.Trigger.ENCOUNTER_LOSS
                        && lossCues.get(1).getTrigger() == StoryDialogueCue.Trigger.ENCOUNTER_LOSS,
                "패배 이벤트는 몬스터 대사와 플레이어 대사 2건이고 진행을 해금하지 않음");
        check("몬스터".equals(lossCues.get(0).getSpeaker())
                        && "으하하! 오늘은 내가 쏜다! 3차 가자!".equals(lossCues.get(0).getText())
                        && "플레이어".equals(lossCues.get(1).getSpeaker())
                        && "으윽… 속이 안 좋아…".equals(lossCues.get(1).getText()),
                "패배 대사는 몬스터(승리) 다음 플레이어(패배) 순서");
        check(progress.getCampaignProgress().getCompletedEncounterIds().isEmpty(), "패배는 미클리어");
        check(dialogue.onMonsterHp(progress.getActiveRun(), 20, 100) == null,
                "종료된 전투에서는 HP 대사를 발화하지 않음");
        check(dialogue.onResult(progress.getActiveRun(), loss, beforeLoss,
                progress.getCampaignProgress()).isEmpty(), "결과 콜백 재호출은 중복 대사 없음");

        EncounterRun retry = progress.restartActive("retry");
        check(dialogue.onMonsterHp(retry, 50, 100) != null, "새 전투에서는 HP 대사를 다시 허용");
        check(dialogue.onResult(retry, StoryProgressService.ResultDisposition.IN_PROGRESS,
                beforeLoss, beforeLoss).isEmpty(), "진행 중 결과는 대사 없음");
        expectInvalid(() -> dialogue.onMonsterHp(retry, 101, 100));
    }

    private static void campaignMilestonesAndReplay() {
        StageCatalog catalog = StageCatalog.loadDefault();
        StoryProgressService progress = new StoryProgressService(catalog);
        StoryDialogueService dialogue = new StoryDialogueService(catalog);
        int encounter = 0;
        for (Stage stage : catalog.getStages()) {
            for (MonsterSpec monster : stage.getEncounters()) {
                encounter++;
                EncounterRun run = progress.startEncounter(stage.getId(), monster.getId(),
                        "win-" + encounter, "p", "m");
                CampaignProgress before = progress.getCampaignProgress();
                StoryProgressService.ResultDisposition disposition =
                        progress.recordBattleResult(run.getRunId(), forfeit("m"));
                CampaignProgress after = progress.getCampaignProgress();
                List<StoryDialogueCue> cues = dialogue.onResult(progress.getActiveRun(), disposition,
                        before, after);
                check(has(cues, StoryDialogueCue.Trigger.ENCOUNTER_WIN), "매 전투 승리 발화");
                check(has(cues, StoryDialogueCue.Trigger.STAGE_CLEAR) == (encounter % 3 == 0),
                        "3/6/9번째 전투에서 과정 완료 발화");
                check(has(cues, StoryDialogueCue.Trigger.HALFWAY) == (encounter == 5),
                        "전체 진행의 절반을 넘는 5번째 전투에서 한 번 발화");
                check(has(cues, StoryDialogueCue.Trigger.CAMPAIGN_CLEAR) == (encounter == 9),
                        "최종 전투 승리에서만 전체 완료 발화");
                check(dialogue.onResult(progress.getActiveRun(), disposition, before, after).isEmpty(),
                        "동일 결과 콜백은 재발화하지 않음");
            }
        }

        EncounterRun replay = progress.startEncounter("university", "level_3_monster",
                "replay", "p", "m");
        CampaignProgress beforeReplay = progress.getCampaignProgress();
        StoryProgressService.ResultDisposition disposition =
                progress.recordBattleResult(replay.getRunId(), forfeit("m"));
        List<StoryDialogueCue> replayCues = dialogue.onResult(progress.getActiveRun(), disposition,
                beforeReplay, progress.getCampaignProgress());
        check(replayCues.size() == 2
                && replayCues.get(0).getTrigger() == StoryDialogueCue.Trigger.ENCOUNTER_WIN
                && replayCues.get(1).getTrigger() == StoryDialogueCue.Trigger.ENCOUNTER_WIN,
                "클리어한 전투 재플레이는 승리 대사 2줄만 띄우고 신규 과정/전체 완료를 다시 띄우지 않음");
        check("몬스터".equals(replayCues.get(0).getSpeaker())
                        && "좋다… 자네는 학부에 남아도 좋다…".equals(replayCues.get(0).getText())
                        && "플레이어".equals(replayCues.get(1).getSpeaker())
                        && "교수님, 시험 이의 신청은 어디서 하나요?".equals(replayCues.get(1).getText()),
                "승리 대사는 몬스터(패배) 다음 플레이어(승리) 순서(교수)");
    }


    /** 조우·30초 대화·강공격·위기 트리거의 시점, 순서, 한 전투당 한 번 제한, 재도전 허용을 확인한다. */
    private static void scriptedTriggers() {
        StageCatalog catalog = StageCatalog.loadDefault();
        StoryProgressService progress = new StoryProgressService(catalog);
        StoryDialogueService dialogue = new StoryDialogueService(catalog);
        EncounterRun run = progress.startStage("university", "script-1", "p", "m");

        StoryDialogueCue start = dialogue.onEncounterStart(run);
        check(start != null && start.getTrigger() == StoryDialogueCue.Trigger.ENCOUNTER_START
                        && "몬스터".equals(start.getSpeaker())
                        && "신입생인가? 좋아! 오늘은 내가 네 간을 시험해 주마!".equals(start.getText()),
                "조우 대사는 몬스터가 전투 시작 때 한 번");
        check(dialogue.onEncounterStart(run) == null, "조우 대사는 같은 전투에서 반복하지 않음");

        check(dialogue.onElapsed(run, 29999).isEmpty(), "30초 전에는 대화하지 않음");
        List<StoryDialogueCue> talk = dialogue.onElapsed(run, 30000);
        check(talk.size() == 2 && talk.get(0).getTrigger() == StoryDialogueCue.Trigger.INTERACTION
                        && "플레이어".equals(talk.get(0).getSpeaker())
                        && "저 내일 9시 수업인데요?".equals(talk.get(0).getText())
                        && "몬스터".equals(talk.get(1).getSpeaker())
                        && "그런 건 2차에서 생각하는 거다!".equals(talk.get(1).getText()),
                "30초에 플레이어 말 다음 몬스터 대답");
        check(dialogue.onElapsed(run, 45000).isEmpty() && dialogue.onElapsed(run, 31000).isEmpty(),
                "30초 대화는 한 번만");

        check(dialogue.onPlayerLineClear(run, 3) == null, "3줄 이하는 강공격이 아님");
        StoryDialogueCue strong = dialogue.onPlayerLineClear(run, 4);
        check(strong != null && strong.getTrigger() == StoryDialogueCue.Trigger.STRONG_ATTACK
                        && "폭탄주 콤보오오오!".equals(strong.getText()), "4줄 제거는 강공격 대사");
        check(dialogue.onPlayerLineClear(run, 4) == null, "강공격 대사는 전투당 한 번");

        check(dialogue.onMonsterCrisis(run, 21, 100) == null, "HP 21%는 위기가 아님");
        StoryDialogueCue crisis = dialogue.onMonsterCrisis(run, 20, 100);
        check(crisis != null && crisis.getTrigger() == StoryDialogueCue.Trigger.MONSTER_CRISIS
                        && "아직 집에 보낼 순 없다…!".equals(crisis.getText()), "HP 20% 이하는 위기 대사");
        check(dialogue.onMonsterCrisis(run, 10, 100) == null, "위기 대사는 전투당 한 번");

        EncounterRun dead = progress.restartActive("script-dead");
        check(dialogue.onMonsterCrisis(dead, 0, 100) == null, "HP 0에서는 위기 대사 없음");
        // 재도전(새 run)에서는 모든 대사를 다시 허용한다.
        check(dialogue.onEncounterStart(dead) != null && dialogue.onElapsed(dead, 30000).size() == 2
                && dialogue.onPlayerLineClear(dead, 4) != null && dialogue.onMonsterCrisis(dead, 5, 100) != null,
                "새 전투에서는 조우·대화·강공격·위기 대사를 다시 허용");

        expectInvalidArgs(() -> dialogue.onElapsed(dead, -1));
        expectInvalidArgs(() -> dialogue.onPlayerLineClear(dead, -1));
        expectInvalidArgs(() -> dialogue.onMonsterCrisis(dead, 101, 100));
    }

    /** 대사집의 9전투 x 10줄이 모두 들어 있고 키 철자 오류가 없는지, 몇 줄은 원문 그대로인지 확인한다. */
    private static void scriptCoversAllEncounters() {
        StageCatalog catalog = StageCatalog.loadDefault();
        StoryDialogueService dialogue = new StoryDialogueService(catalog);
        String[] situations = { "encounter", "interaction.player", "interaction.monster", "hp", "strong",
                "crisis", "playerWin.monster", "playerWin.player", "playerLoss.monster", "playerLoss.player" };
        java.util.Set<String> expected = new java.util.HashSet<String>();
        StoryDialogueLines lines = StoryDialogueLines.loadDefault();
        int encounters = 0;
        for (Stage stage : catalog.getStages()) {
            for (MonsterSpec monster : stage.getEncounters()) {
                encounters++;
                for (String situation : situations) {
                    check(lines.get(monster.getId(), situation) != null,
                            monster.getId() + "." + situation + " 대사가 있어야 함");
                    expected.add(monster.getId() + "." + situation);
                }
            }
        }
        check(encounters == 9 && expected.size() == 90, "9전투 x 10줄");
        check(dialogue.lineKeys().equals(expected), "대사 파일에 모르는 키(오타)나 빠진 키가 없음");
        check("엉터리".equals(lines.get("level_1_monster", "unknown")) == false
                && lines.get("level_1_monster", "unknown") == null, "없는 키는 null");

        check("교양은 쉽다고? 그 말, 시험 끝나고도 할 수 있을까?".equals(lines.get("level_2_monster", "encounter")),
                "교양 조우 원문");
        check("안녕하세요! 무엇이든 도와드릴 수 있습니다!".equals(lines.get("level_5_monster", "encounter")),
                "GPT 조우 원문");
        check("아직 최종_진짜최종_수정본을 제출하지 못했다!".equals(lines.get("level_6_monster", "crisis")),
                "캡스톤 위기 원문(밑줄 포함)");
        check("불합격입니다.".equals(lines.get("level_8_monster", "playerLoss.monster")), "면접관 패배 원문");
        check("합격이다! 드디어 취업 성공이다!!!".equals(lines.get("level_9_monster", "playerWin.player")),
                "기업 승리 원문(느낌표 3개)");
    }

    private static void expectInvalidArgs(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("잘못된 인자를 거부해야 한다");
    }

    private static boolean has(List<StoryDialogueCue> cues, StoryDialogueCue.Trigger trigger) {
        for (StoryDialogueCue cue : cues) if (cue.getTrigger() == trigger) return true;
        return false;
    }

    private static BattleResult forfeit(String actorId) {
        BattleManager battle = new BattleManager(Arrays.asList(
                new ParticipantSpec("p", "PLAYER", 100),
                new ParticipantSpec("m", "MONSTER", 100)), 17L);
        check(battle.start().isAccepted(), "전투 시작");
        BattleResult result = battle.forfeit(actorId);
        check(result.isAccepted(), "전투 종료");
        return result;
    }

    private static void expectInvalid(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("잘못된 HP를 거부해야 한다");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
