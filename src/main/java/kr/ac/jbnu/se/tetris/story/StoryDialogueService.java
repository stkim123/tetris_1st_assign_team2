package kr.ac.jbnu.se.tetris.story;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 전투 상태와 확정 진행 결과를 화면용 대사 이벤트로 바꾼다. */
public final class StoryDialogueService {
    private static final int MONSTER_HP_PERCENT = 50;
    private static final int MONSTER_CRISIS_PERCENT = 20;
    /** 전투 시작 뒤 이 시간이 지나면 플레이어와 몬스터가 한 번 대화한다. */
    public static final long INTERACTION_AFTER_MILLIS = 30000L;
    /** 한 번에 이만큼 이상 줄을 지우면 강공격(테트리스)으로 본다. */
    public static final int STRONG_ATTACK_LINES = 4;

    private final StageCatalog catalog;
    private final StoryDialogueLines lines;
    private final Set<String> hpTriggeredRuns = new HashSet<String>();
    private final Set<String> resultTriggeredRuns = new HashSet<String>();
    private final Set<String> startTriggeredRuns = new HashSet<String>();
    private final Set<String> interactionTriggeredRuns = new HashSet<String>();
    private final Set<String> strongTriggeredRuns = new HashSet<String>();
    private final Set<String> crisisTriggeredRuns = new HashSet<String>();

    public StoryDialogueService(StageCatalog catalog) {
        this(catalog, StoryDialogueLines.loadDefault());
    }

    StoryDialogueService(StageCatalog catalog, StoryDialogueLines lines) {
        if (catalog == null) throw new IllegalArgumentException("Stage catalog is required");
        if (lines == null) throw new IllegalArgumentException("Dialogue lines are required");
        this.catalog = catalog;
        this.lines = lines;
    }

    /** 전투가 시작될 때 몬스터의 조우 대사를 한 번만 발화한다. */
    public synchronized StoryDialogueCue onEncounterStart(EncounterRun run) {
        requireRun(run);
        if (run.isFinished() || !startTriggeredRuns.add(run.getRunId())) return null;
        String line = lines.get(run.getEncounterId(), "encounter");
        return line == null ? null : cue(StoryDialogueCue.Trigger.ENCOUNTER_START, run, MONSTER, line);
    }

    /** 전투 시간이 30초에 도달하면 플레이어의 말과 몬스터의 대답을 순서대로 한 번만 발화한다. */
    public synchronized List<StoryDialogueCue> onElapsed(EncounterRun run, long elapsedMillis) {
        requireRun(run);
        if (elapsedMillis < 0) throw new IllegalArgumentException("Invalid elapsed time");
        if (run.isFinished() || elapsedMillis < INTERACTION_AFTER_MILLIS
                || !interactionTriggeredRuns.add(run.getRunId())) return Collections.emptyList();
        List<StoryDialogueCue> cues = new ArrayList<StoryDialogueCue>();
        addLine(cues, StoryDialogueCue.Trigger.INTERACTION, run, PLAYER, run.getEncounterId(), "interaction.player");
        addLine(cues, StoryDialogueCue.Trigger.INTERACTION, run, MONSTER, run.getEncounterId(), "interaction.monster");
        return Collections.unmodifiableList(cues);
    }

    /** 플레이어가 한 번에 4줄 이상 지웠을 때 몬스터의 강공격 대사를 전투당 한 번만 발화한다. */
    public synchronized StoryDialogueCue onPlayerLineClear(EncounterRun run, int linesCleared) {
        requireRun(run);
        if (linesCleared < 0) throw new IllegalArgumentException("Invalid cleared line count");
        if (run.isFinished() || linesCleared < STRONG_ATTACK_LINES
                || !strongTriggeredRuns.add(run.getRunId())) return null;
        String line = lines.get(run.getEncounterId(), "strong");
        return line == null ? null : cue(StoryDialogueCue.Trigger.STRONG_ATTACK, run, MONSTER, line);
    }

    /** 몬스터 HP가 20% 이하로 처음 내려간 순간 위기 대사를 한 번만 발화한다. */
    public synchronized StoryDialogueCue onMonsterCrisis(EncounterRun run, int hp, int maxHp) {
        requireRun(run);
        if (maxHp <= 0 || hp < 0 || hp > maxHp) throw new IllegalArgumentException("Invalid monster HP");
        if (run.isFinished() || hp == 0 || (long) hp * 100 > (long) maxHp * MONSTER_CRISIS_PERCENT
                || !crisisTriggeredRuns.add(run.getRunId())) return null;
        String line = lines.get(run.getEncounterId(), "crisis");
        return line == null ? null : cue(StoryDialogueCue.Trigger.MONSTER_CRISIS, run, MONSTER, line);
    }

    private void addLine(List<StoryDialogueCue> cues, StoryDialogueCue.Trigger trigger, EncounterRun run,
            String speaker, String encounterId, String situation) {
        String line = lines.get(encounterId, situation);
        if (line != null) cues.add(cue(trigger, run, speaker, line));
    }

    /** 진행 중인 몬스터 HP가 기준 이하로 처음 내려간 순간 한 번만 발화한다. */
    public synchronized StoryDialogueCue onMonsterHp(EncounterRun run, int hp, int maxHp) {
        requireRun(run);
        if (maxHp <= 0 || hp < 0 || hp > maxHp) throw new IllegalArgumentException("Invalid monster HP");
        if (run.isFinished() || hp == 0 || (long) hp * 100 > (long) maxHp * MONSTER_HP_PERCENT
                || !hpTriggeredRuns.add(run.getRunId())) return null;
        String scripted = lines.get(run.getEncounterId(), "hp");
        return scripted != null
                ? cue(StoryDialogueCue.Trigger.MONSTER_HP_THRESHOLD, run, MONSTER, scripted)
                : cue(StoryDialogueCue.Trigger.MONSTER_HP_THRESHOLD, run, hpSpeaker(run), hpLine(run));
    }

    /** recordBattleResult 직전/직후의 진행 스냅샷으로 최초 해금 이벤트를 구별한다. */
    public synchronized List<StoryDialogueCue> onResult(EncounterRun run,
            StoryProgressService.ResultDisposition disposition,
            CampaignProgress before, CampaignProgress after) {
        requireRun(run);
        if (disposition == null || before == null || after == null) {
            throw new IllegalArgumentException("Disposition and campaign progress are required");
        }
        if (disposition != StoryProgressService.ResultDisposition.APPLIED_WIN
                && disposition != StoryProgressService.ResultDisposition.APPLIED_LOSS) {
            return Collections.emptyList();
        }
        if (!run.isFinished()
                || run.isWon() != (disposition == StoryProgressService.ResultDisposition.APPLIED_WIN)
                || !resultTriggeredRuns.add(run.getRunId())) {
            return Collections.emptyList();
        }

        List<StoryDialogueCue> cues = new ArrayList<StoryDialogueCue>();
        if (disposition == StoryProgressService.ResultDisposition.APPLIED_LOSS) {
            // 플레이어가 진 결과: 몬스터(승리)와 플레이어(패배) 대사
            addLine(cues, StoryDialogueCue.Trigger.ENCOUNTER_LOSS, run, MONSTER, run.getEncounterId(), "playerLoss.monster");
            addLine(cues, StoryDialogueCue.Trigger.ENCOUNTER_LOSS, run, PLAYER, run.getEncounterId(), "playerLoss.player");
            if (cues.isEmpty()) {
                cues.add(cue(StoryDialogueCue.Trigger.ENCOUNTER_LOSS, run, lossSpeaker(run), lossLine(run)));
            }
            return Collections.unmodifiableList(cues);
        }

        // 플레이어가 이긴 결과: 몬스터(패배)와 플레이어(승리) 대사
        addLine(cues, StoryDialogueCue.Trigger.ENCOUNTER_WIN, run, MONSTER, run.getEncounterId(), "playerWin.monster");
        addLine(cues, StoryDialogueCue.Trigger.ENCOUNTER_WIN, run, PLAYER, run.getEncounterId(), "playerWin.player");
        if (cues.isEmpty()) {
            cues.add(cue(StoryDialogueCue.Trigger.ENCOUNTER_WIN, run, winSpeaker(run), winLine(run)));
        }
        boolean firstClear = !before.isEncounterCleared(run.getEncounterId())
                && after.isEncounterCleared(run.getEncounterId());
        if (firstClear) {
            int midpoint = (encounterCount() + 1) / 2;
            if (before.getCompletedEncounterIds().size() < midpoint
                    && after.getCompletedEncounterIds().size() >= midpoint) {
                cues.add(cue(StoryDialogueCue.Trigger.HALFWAY, run,
                        "내레이션", "여기까지는 준비 운동이었다. 이제부터 시작이다!"));
            }
            if (!before.isStageCleared(run.getStageId()) && after.isStageCleared(run.getStageId())) {
                cues.add(cue(StoryDialogueCue.Trigger.STAGE_CLEAR, run,
                        "내레이션", stageClearLine(run)));
            }
            if (after.getCompletedEncounterIds().size() == encounterCount()) {
                cues.add(cue(StoryDialogueCue.Trigger.CAMPAIGN_CLEAR, run,
                        "내레이션", "마침내 취업에 성공했다! 끝없이 쌓인 과제와 면접을 넘어, 첫 출근의 문이 열렸다!"));
            }
        }
        return Collections.unmodifiableList(cues);
    }

    /** 대사 파일의 키 목록(철자 검증용). */
    java.util.Set<String> lineKeys() { return lines.keys(); }

    private void requireRun(EncounterRun run) {
        if (run == null || catalog.getStage(run.getStageId()) == null) {
            throw new IllegalArgumentException("Known encounter run is required");
        }
    }

    private int encounterCount() {
        int count = 0;
        for (Stage stage : catalog.getStages()) count += stage.getEncounters().size();
        return count;
    }

    private int stageNumber(EncounterRun run) {
        for (int i = 0; i < catalog.getStages().size(); i++) {
            if (catalog.getStages().get(i).getId().equals(run.getStageId())) return i + 1;
        }
        throw new IllegalArgumentException("Unknown story stage");
    }

    private static StoryDialogueCue cue(StoryDialogueCue.Trigger trigger,
            EncounterRun run, String speaker, String line) {
        return new StoryDialogueCue(trigger, run, speaker, line);
    }

    private static final String MONSTER = "몬스터";
    private static final String PLAYER = "플레이어";

    // 기획·프론트엔드 담당자는 트리거 횟수, HP 기준과 타이밍, 대사 문구·화자,
    // 화면 배치·디자인을 모두 자유롭게 바꾸어도 된다. 아래 문구는 연결용 예시다.
    private static String hpSpeaker(EncounterRun run) {
        return "level_4_monster".equals(run.getEncounterId()) ? "플레이어" : "몬스터";
    }

    private static String hpLine(EncounterRun run) {
        String id = run.getEncounterId();
        if ("level_3_monster".equals(id)) return "오늘은 오픈북이 아니다 으하하!";
        if ("level_4_monster".equals(id)) return "벼락치기의 힘!~으어어!";
        if ("level_8_monster".equals(id)) return "지원동기를 말해라 이녀석!!!!!";
        return "이 정도면 제법이군. 하지만 아직 끝나지 않았다!";
    }

    private static String winSpeaker(EncounterRun run) {
        return "level_3_monster".equals(run.getEncounterId()) ? "몬스터" : "플레이어";
    }

    private static String winLine(EncounterRun run) {
        if ("level_3_monster".equals(run.getEncounterId())) {
            return "이녀석 제법이구만… 대학원에 들어오지 않겠나?";
        }
        return "후….이번 학기는 살았다…";
    }

    private static String lossSpeaker(EncounterRun run) {
        return "level_4_monster".equals(run.getEncounterId())
                || "level_8_monster".equals(run.getEncounterId()) ? "몬스터" : "내레이션";
    }

    private String lossLine(EncounterRun run) {
        if ("level_4_monster".equals(run.getEncounterId())) return "재수강을 하려고 이려냐!!!!";
        if ("level_8_monster".equals(run.getEncounterId())) return "넌 면접 점수 0점이다. ㅋ";
        if (stageNumber(run) == 2) return "졸업에 실패했다. 취업의 문은 아직 멀고 높았다…";
        if (stageNumber(run) == 3) return "취업의 문은 높았다. 다음 면접에서 다시 도전하자…";
        return "이번 학기는 여기까지인가… 다시 도전하자!";
    }

    private String stageClearLine(EncounterRun run) {
        if (stageNumber(run) == 1) return "대학의 첫 관문을 넘었다! 다음 학기가 기다린다.";
        if (stageNumber(run) == 2) return "마침내 졸업이다! 이제 취업의 문을 두드릴 차례다.";
        return "취업 과정의 마지막 관문까지 돌파했다!";
    }
}
