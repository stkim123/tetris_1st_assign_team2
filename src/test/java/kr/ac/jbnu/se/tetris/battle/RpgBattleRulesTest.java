package kr.ac.jbnu.se.tetris.battle;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import kr.ac.jbnu.se.tetris.ai.AIContext;
import kr.ac.jbnu.se.tetris.ai.AIProfile;
import kr.ac.jbnu.se.tetris.ai.BossPhaseWeightPolicy;
import kr.ac.jbnu.se.tetris.ai.DifficultyProfile;
import kr.ac.jbnu.se.tetris.ai.DifficultyProfileCatalog;
import kr.ac.jbnu.se.tetris.ai.HeuristicWeights;
import kr.ac.jbnu.se.tetris.ai.PlayerProfile;
import kr.ac.jbnu.se.tetris.ai.PolicyDecision;
import kr.ac.jbnu.se.tetris.character.CharacterCatalog;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameEngine;
import kr.ac.jbnu.se.tetris.core.PieceGenerator;
import kr.ac.jbnu.se.tetris.core.PieceType;
import kr.ac.jbnu.se.tetris.core.SevenBagGenerator;
import kr.ac.jbnu.se.tetris.item.ItemSpec;
import kr.ac.jbnu.se.tetris.story.MonsterSpec;
import kr.ac.jbnu.se.tetris.story.MonsterTier;
import kr.ac.jbnu.se.tetris.story.StageCatalog;

/** 시간, 탱크, PvP 기본형, 난이도 정책의 게임 규칙 회귀 검증. */
public final class RpgBattleRulesTest {
    public static void main(String[] args) {
        garbageTankTimingAndFifo();
        pvpForcesBasicAndInventoryOwnership();
        encounterProfilesAndBossBoundaries();
        monsterItemLevelsLimitSpawnsAndUse();
        actualItemPickupAndUse();
        detailedItemChargeRules();
        perfectClearPreservesAutomaticItems();
        itemEventsTrackConsumptionAndReacquisition();
        feverAndTimeWarpExpire();
        characterPassives();
    }

    private static void garbageTankTimingAndFifo() {
        GarbageTank tank = new GarbageTank(71);
        tank.enqueue(3, 100);
        int firstHole = tank.getPreviousHole();
        tank.enqueue(4, 900);
        check(tank.getWaitRemainingMillis(1900) == 200, "later attack does not reset first wait");
        check(tank.drainForPlacement(2099).isEmpty(), "before two seconds no garbage rises");
        check(tank.cancel(2) == 0 && tank.getPendingLines() == 5, "FIFO cancellation");
        List<GameAction.Garbage> risen = tank.drainForPlacement(2100);
        check(risen.size() == 2 && risen.get(0).getLines() == 1 && risen.get(1).getLines() == 3,
                "oldest chunk first, four-line cap and chunk boundaries preserved");
        check(risen.get(0).getHoleColumn() == firstHole, "first chunk keeps its own hole");
        check(tank.getPendingLines() == 1, "one line remains for next placement");
        check(tank.drainForPlacement(2100).get(0).getLines() == 1, "next placement drains rest");
        check(tank.getPendingLines() == 0 && tank.getPreviousHole() >= 0,
                "empty tank remembers last hole");
        tank.enqueue(1, 2200);
        check(tank.getWaitRemainingMillis(2200) == 2000, "new empty-to-nonempty period restarts wait");
        check(tank.removeOldestLine() && tank.getPendingLines() == 0,
                "defender passive can reduce the oldest chunk to zero");
    }

    private static void pvpForcesBasicAndInventoryOwnership() {
        CharacterCatalog catalog = CharacterCatalog.loadDefault();
        BattleManager pvp = BattleManager.pvp(Arrays.asList(
                new ParticipantSpec("a", "A", catalog.attacker()),
                new ParticipantSpec("b", "B", catalog.defender())), 7);
        ParticipantState a = pvp.getState().getParticipant("a");
        ParticipantState b = pvp.getState().getParticipant("b");
        check("student".equals(a.getCharacterId()) && "student".equals(b.getCharacterId()),
                "PvP ignores selected specialty characters");
        check(a.getMaxHp() == 100 && b.getMaxHp() == 100, "PvP HP also fixed at 100");
        check(a.getGravityMillis() == 500, "PvP starts at 500ms");
        check(BattleManager.pvpGravityAt(59999) == 500
                && BattleManager.pvpGravityAt(60000) == 450
                && BattleManager.pvpGravityAt(90000) == 400
                && BattleManager.pvpGravityAt(240000) == 200,
                "first acceleration at 60s, then every 30s, floor 200ms");
        check(pvp.start().isAccepted(), "PvP starts");
        BattleState before = pvp.getState();
        BattleResult noItem = pvp.submitItem("a", new GameAction.ItemUse("garbage_bomb", "b"));
        check(!noItem.isAccepted() && "ITEM_NOT_OWNED".equals(noItem.getReason()),
                "unowned item cannot be used");
        check(pvp.getState().getVersion() == before.getVersion(), "rejected use leaves state unchanged");
        BattleResult advancing = pvp.advance(499);
        check(advancing.isAccepted() && advancing.getState().getParticipant("a").getGameState().getTick() == 0,
                "499ms does not drop a cell");
        check(pvp.advance(1).getState().getParticipant("a").getGameState().getTick() == 1,
                "500ms drops exactly once");
    }

    private static void encounterProfilesAndBossBoundaries() {
        int[] gravity = {450, 420, 400, 370, 350, 330, 310, 290, 270};
        int[] hpByLevel = {30, 38, 46, 54, 64, 75, 82, 92, 102};
        int[] delay = {3500, 3300, 3000, 2800, 2600, 2450, 2300, 2150, 2000};
        int[] states = {150, 150, 400, 400, 400, 800, 800, 800, 800};
        int[] budget = {12, 12, 24, 24, 24, 40, 40, 40, 40};
        int[] aiTiers = {1, 1, 2, 2, 2, 3, 3, 3, 3};
        int[] items = {0, 0, 0, 1, 2, 2, 3, 4, 5};
        MonsterTier[] pattern = {MonsterTier.NORMAL, MonsterTier.ELITE, MonsterTier.BOSS,
            MonsterTier.NORMAL, MonsterTier.ELITE, MonsterTier.BOSS,
            MonsterTier.ELITE, MonsterTier.ELITE, MonsterTier.BOSS};
        double[] attack = {1.00, 1.05, 1.10, 1.15, 1.20, 1.25, 1.30, 1.40, 1.50};
        int previousHp = 0, previousDelay = Integer.MAX_VALUE;
        for (int level = 1; level <= 9; level++) {
            DifficultyProfile profile = DifficultyProfileCatalog.level(level);
            check(profile.getLevel() == level
                    && profile.getPlayerGravityMillis() == gravity[level - 1]
                    && profile.getMonsterHp() == hpByLevel[level - 1]
                    && profile.getMonsterDelayMillis() == delay[level - 1]
                    && profile.getAiStrength().getMaxSearchStates() == states[level - 1]
                    && profile.getAiStrength().getBudgetMillis() == budget[level - 1]
                    && profile.getMonsterItemLevel() == items[level - 1]
                    && DifficultyProfileCatalog.getAiTier(level) == aiTiers[level - 1]
                    && DifficultyProfileCatalog.getPattern(level) == pattern[level - 1]
                    && Math.abs(1.0 + profile.getAttackStrength().getDamageBuff()
                            - attack[level - 1]) < .000001,
                    "Lv " + level + " 지정 난이도 축");
            check(profile.getMonsterHp() > previousHp && profile.getMonsterDelayMillis() < previousDelay
                    && profile.getAttackStrength().getExtraGarbageLines() == 0,
                    "Lv " + level + " HP/행동 간격 증가와 추가 가비지 없음");
            previousHp = profile.getMonsterHp(); previousDelay = profile.getMonsterDelayMillis();
        }
        MonsterSpec first = StageCatalog.loadDefault().getStages().get(0).getEncounters().get(0);
        DifficultyProfile encounter = DifficultyProfileCatalog.forEncounter(first);
        check(encounter.getMonsterHp() == first.getHp(), "story HP preserved");
        AIProfile base = encounter.toAiProfile();
        GameEngine game = new GameEngine("monster", new SevenBagGenerator(1));
        game.dispatch(new GameAction(GameAction.Type.START, "monster", 1));
        for (double limit : new double[] {0, .1, .25}) {
            AIProfile profile = new AIProfile("boss_check", "BOSS_PHASE", HeuristicWeights.TETRIS,
                    900, 700, 40, limit);
            for (int hp : new int[] {100, 67, 66, 34, 33, 1}) {
                AIContext context = new AIContext("match", game.getState(), hp, 100,
                        new PlayerProfile().snapshot(), 1, null, null);
                PolicyDecision chosen = new BossPhaseWeightPolicy().decide(context, profile);
                check(profile.allows(chosen.getWeights()), "boss weights remain within profile bound");
                String expected = hp >= 67 ? "PREPARE" : hp >= 34 ? "PRESSURE" : "SURVIVE";
                check(expected.equals(chosen.getNextPolicyState()), "boss HP phase boundary " + hp);
            }
        }
        check(base.getMaxSearchStates() == encounter.getAiStrength().getMaxSearchStates(),
                "difficulty search capacity reaches AI profile");
    }

    private static void monsterItemLevelsLimitSpawnsAndUse() {
        check(BattleManager.monsterItems(0).isEmpty(), "Lv 0은 아이템 사용 불가");
        check(BattleManager.monsterItems(1).equals(Arrays.asList("heal")),
                "Lv 1은 회복만 허용");
        check(BattleManager.monsterItems(2).contains("shield")
                && !BattleManager.monsterItems(2).contains("garbage_bomb"),
                "Lv 2는 방어만 추가");
        check(BattleManager.monsterItems(3).contains("line_cleaner")
                && BattleManager.monsterItems(4).contains("damage_boost")
                && BattleManager.monsterItems(5).contains("garbage_bomb"),
                "Lv 3~5의 아이템 범위 확장");

        GameEngine disabled = new GameEngine("m", () -> PieceType.O);
        disabled.configureItemSpawns(0, 1L, BattleManager.monsterItems(0));
        check(disabled.dispatch(new GameAction(GameAction.Type.START, "m", 1)).isAccepted()
                && disabled.getState().getActivePiece().getItemId() == null,
                "몬스터 레벨 0은 첫 블록부터 아이템 없음");
        GameEngine allowed = new GameEngine("m", () -> PieceType.O);
        allowed.configureItemSpawns(1, 1L, BattleManager.monsterItems(1));
        check(allowed.dispatch(new GameAction(GameAction.Type.START, "m", 1)).isAccepted()
                && "heal".equals(allowed.getState().getActivePiece().getItemId()),
                "허용 목록에서만 아이템 생성");
        BattleManager pve = BattleManager.pve(Arrays.asList(
                new ParticipantSpec("p", "P", 100), new ParticipantSpec("m", "M", 30)),
                42L, 450, 3500, 0, 0, 0);
        check(pve.start().isAccepted()
                && pve.getState().getParticipant("m").getGameState().getActivePiece().getItemId() == null,
                "스토리 전투에도 레벨 0 정책 연결");
        BattleResult forbidden = pve.submitItem("m", new GameAction.ItemUse("heal", "m"));
        check("ITEM_RESTRICTED".equals(forbidden.getReason())
                && count(forbidden, BattleEvent.Type.ITEM_USED) == 0,
                "획득 전이라도 사용 경로를 거부");
    }

    private static void actualItemPickupAndUse() {
        BattleManager heal = squares(seedFor("heal", null), 10000);
        fillRows(heal, "a", false);
        check(heal.getState().getParticipant("a").getItems().contains("heal"), "item mino acquired by line clear");
        fillRows(heal, "b", false);
        check(heal.getState().getParticipant("a").getHp() == 60, "counterattack damaged player");
        BattleResult healed = heal.submitItem("a", new GameAction.ItemUse("heal", "a"));
        check(healed.isAccepted(), "heal can be used");
        check(itemEvent(healed, BattleEvent.Type.ITEM_USED, "a", "a", "heal") != null,
                "accepted heal emits actual item use");
        check(heal.getState().getParticipant("a").getHp() == 70
                && heal.getState().getParticipant("a").getItemCharges().equals(Arrays.asList(1)),
                "first heal restores ten percent and leaves one charge in its slot");

        BattleManager bomb = squares(seedFor("garbage_bomb", null), 10000);
        fillRows(bomb, "a", false);
        fillRows(bomb, "b", false);
        fillRows(bomb, "b", false);
        check(bomb.getState().getParticipant("a").getPendingGarbageLines() == 8, "inbound queue exists");
        check(bomb.submitItem("a", new GameAction.ItemUse("garbage_bomb", "b")).isAccepted(),
                "bomb use accepted");
        check(bomb.getState().getParticipant("a").getPendingGarbageLines() == 7
                && bomb.getState().getParticipant("b").getPendingGarbageLines() == 0,
                "bomb cancels own oldest garbage before any remainder is sent");

        BattleManager fever = squares(seedFor("fever_charge", null), 10000);
        fillRows(fever, "a", false);
        check(fever.getState().getParticipant("a").getFever() == 5, "double clear charges five");
        check(fever.submitItem("a", new GameAction.ItemUse("fever_charge", "a")).isAccepted(),
                "fever charger can be used");
        check(fever.getState().getParticipant("a").getFever() == 25,
                "charger adds twenty after clear gain");

        BattleManager warp = squares(seedFor("time_warp", null), 10000);
        fillRows(warp, "a", false);
        check(warp.submitItem("a", new GameAction.ItemUse("time_warp", "a")).isAccepted(),
                "warp can be used");
        check(warp.getState().getParticipant("a").getGravityMillis() == 500
                && warp.getState().getParticipant("a").getTimeWarpRemainingMillis() == 5000
                && warp.getState().getParticipant("a").getItems().contains("time_warp"),
                "warp slows gravity and occupies its slot for five seconds");
        check(!warp.submitItem("a", new GameAction.ItemUse("time_warp", "a")).isAccepted(),
                "active warp cannot be used twice");

        BattleManager nullifyEmpty = squares(seedFor("nullify", null), 10000);
        fillRows(nullifyEmpty, "a", false);
        BattleResult wasted = nullifyEmpty.submitItem("a", new GameAction.ItemUse("nullify", "b"));
        check(wasted.isAccepted() && nullifyEmpty.getState().getParticipant("a").getItems().isEmpty(),
                "nullify with empty opposing inventory consumes item");
        BattleManager nullify = squares(seedFor("nullify", null), 10000);
        fillRows(nullify, "a", false);
        fillRows(nullify, "b", false);
        String victimItem = nullify.getState().getParticipant("b").getItems().get(0);
        BattleResult nullified = nullify.submitItem("a", new GameAction.ItemUse("nullify", "b"));
        check(nullified.isAccepted(),
                "nullify removes one actual opposing item");
        BattleEvent nullifyUse = itemEvent(nullified, BattleEvent.Type.ITEM_USED,
                "a", "b", "nullify");
        BattleEvent nullifyRemoval = itemEvent(nullified, BattleEvent.Type.ITEM_REMOVED,
                "a", "b", victimItem);
        check(nullifyUse != null && nullifyRemoval != null
                && nullifyUse.getEventId() < nullifyRemoval.getEventId(),
                "nullify distinguishes own use from opponent item removal");
        check(nullify.getState().getParticipant("b").getItems().isEmpty(), "opposing slot emptied");

        BattleManager cleaner = squares(seedFor("line_cleaner", null), 10000);
        fillRows(cleaner, "a", false);
        BattleResult noClean = cleaner.submitItem("a", new GameAction.ItemUse("line_cleaner", "a"));
        check(noClean.isAccepted() && count(noClean, BattleEvent.Type.ITEM_USED) == 1,
                "cleaner on empty board spends one use");
        check(cleaner.getState().getParticipant("a").getItemCharges().equals(Arrays.asList(1)),
                "one cleaner charge remains");
        fillRows(cleaner, "b", false);
        fillRows(cleaner, "b", false);
        cleaner.advance(2000);
        BattleResult eruption = cleaner.submit("a", GameAction.Type.HARD_DROP);
        check(eruption.isAccepted() && count(eruption, BattleEvent.Type.GARBAGE_RECEIVED) == 1,
                "mature tank erupts at next uncleared placement");
        BattleResult clean = cleaner.submitItem("a", new GameAction.ItemUse("line_cleaner", "a"));
        check(clean.isAccepted() && !cleaner.getState().getParticipant("a").getItems().contains("line_cleaner"),
                "cleaner removes one landed garbage row and consumes one use");
    }

    private static void detailedItemChargeRules() {
        check(ItemSpec.categoryOf("damage_boost") == ItemSpec.Category.H
                && ItemSpec.categoryOf("garbage_bomb") == ItemSpec.Category.L
                && ItemSpec.categoryOf("heal") == ItemSpec.Category.H
                && ItemSpec.categoryOf("shield") == ItemSpec.Category.H
                && ItemSpec.categoryOf("line_cleaner") == ItemSpec.Category.L
                && ItemSpec.categoryOf("fever_charge") == ItemSpec.Category.G
                && ItemSpec.categoryOf("time_warp") == ItemSpec.Category.G
                && ItemSpec.categoryOf("nullify") == ItemSpec.Category.G,
                "all eight item categories follow H/L/G specification");
        BattleManager heal = squares(seedFor("heal", null), 10000);
        fillRows(heal, "a", false);
        check(heal.getState().getParticipant("a").getItemCharges().equals(Arrays.asList(2)),
                "healing item begins with two uses in one slot");
        fillRows(heal, "b", false);
        check(heal.submitItem("a", new GameAction.ItemUse("heal", "a")).isAccepted(),
                "first heal use");
        check(heal.submitItem("a", new GameAction.ItemUse("heal", "a")).isAccepted()
                && heal.getState().getParticipant("a").getHp() == 80
                && heal.getState().getParticipant("a").getItems().isEmpty(),
                "second heal use frees slot");

        BattleManager bomb = squares(seedFor("garbage_bomb", null), 10000);
        fillRows(bomb, "a", false);
        check(bomb.getState().getParticipant("a").getItemCharges().equals(Arrays.asList(2)),
                "bomb has two charges");
        check(bomb.submitItem("a", new GameAction.ItemUse("garbage_bomb", "b")).isAccepted()
                && bomb.getState().getParticipant("a").getItemCharges().equals(Arrays.asList(1)),
                "bomb first use retains slot");
        check(bomb.submitItem("a", new GameAction.ItemUse("garbage_bomb", "b")).isAccepted()
                && bomb.getState().getParticipant("a").getItems().isEmpty(),
                "bomb second use empties slot");

        BattleManager cleaner = squares(seedFor("line_cleaner", null), 10000);
        fillRows(cleaner, "a", false);
        check(cleaner.submitItem("a", new GameAction.ItemUse("line_cleaner", "a")).isAccepted()
                && cleaner.getState().getParticipant("a").getItemCharges().equals(Arrays.asList(1)),
                "empty board burns first cleaner charge");
        check(cleaner.submitItem("a", new GameAction.ItemUse("line_cleaner", "a")).isAccepted()
                && cleaner.getState().getParticipant("a").getItems().isEmpty(),
                "empty board burns second cleaner charge");

        BattleManager nullify = squares(seedForTwoChargeNullifyTarget(), 10000);
        fillRows(nullify, "a", false);
        fillRows(nullify, "b", false);
        fillRows(nullify, "b", false);
        check(nullify.getState().getParticipant("b").getItemCharges().get(1) == 2,
                "target owns two-charge slot");
        check(nullify.submitItem("a", new GameAction.ItemUse("nullify", "b")).isAccepted()
                && nullify.getState().getParticipant("b").getItems().size() == 1,
                "nullify discards entire two-charge slot");

        BattleManager warp = squares(seedFor("time_warp", null), 10000);
        fillRows(warp, "a", false);
        check(warp.submitItem("a", new GameAction.ItemUse("time_warp", "a")).isAccepted()
                && warp.getState().getParticipant("a").getItems().size() == 1,
                "active warp occupies slot");
        check(warp.advance(5000).isAccepted()
                && warp.getState().getParticipant("a").getItems().isEmpty(),
                "warp slot expires at five seconds");

        BattleManager fever = squares(seedFor("fever_charge", null), 10000);
        for (int i = 0; i < 20; i++) fillRows(fever, "a", false);
        check(fever.getState().getParticipant("a").isFeverActive(),
                "twenty double clears activate Fever");
        int feverChargesBefore = java.util.Collections.frequency(
                fever.getState().getParticipant("a").getItems(), "fever_charge");
        check(fever.submitItem("a", new GameAction.ItemUse("fever_charge", "a")).isAccepted()
                && java.util.Collections.frequency(fever.getState().getParticipant("a").getItems(),
                        "fever_charge") == feverChargesBefore - 1
                && fever.getState().getParticipant("a").getFever() == 0,
                "Fever charge is spent without effect during active Fever");
    }

    private static void perfectClearPreservesAutomaticItems() {
        BattleManager boost = squares(seedFor("damage_boost", null), 10000);
        fillRows(boost, "a", false);
        fillRows(boost, "b", false);
        check(boost.getState().getParticipant("a").getItems().contains("damage_boost"),
                "boost held before next attack");
        BattleResult perfect = fillRows(boost, "a", false);
        check(boost.getState().getParticipant("a").getItems().contains("damage_boost"),
                "perfect clear leaves boost untouched");
        check(count(perfect, BattleEvent.Type.ITEM_USED) == 0,
                "perfect clear does not falsely report boost consumption");
        int before = boost.getState().getParticipant("b").getHp();
        BattleResult boosted = fillRows(boost, "a", true);
        check(boost.getState().getParticipant("b").getHp() == before - 12,
                "next ordinary double gets +50% damage boost");
        check(itemEvent(boosted, BattleEvent.Type.ITEM_USED,
                "a", "a", "damage_boost") != null,
                "automatic boost consumption emits item use");

        BattleManager shield = squares(seedFor("shield", null), 10000);
        // Same deterministic item belongs to actor a; place b into defending role by switching attack order.
        fillRows(shield, "a", false);
        check(shield.getState().getParticipant("a").getItems().contains("shield"),
                "shield held before ordinary hit");
        fillRows(shield, "b", false);
        check(shield.getState().getParticipant("a").getItems().contains("shield"),
                "perfect clear leaves shield untouched");
        int hp = shield.getState().getParticipant("a").getHp();
        BattleResult blocked = fillRows(shield, "b", true);
        check(shield.getState().getParticipant("a").getHp() == hp - 6,
                "next ordinary double applies thirty percent shield reduction");
        check(!shield.getState().getParticipant("a").getItems().contains("shield"),
                "shield consumed by ordinary HP hit");
        check(itemEvent(blocked, BattleEvent.Type.ITEM_USED,
                "a", "a", "shield") != null,
                "automatic shield consumption emits item use");
    }

    private static void itemEventsTrackConsumptionAndReacquisition() {
        BattleManager boost = squares(seedForRepeats("a", "damage_boost", 2), 10000);
        BattleResult first = fillRows(boost, "a", false);
        BattleEvent firstPickup = itemEvent(first, BattleEvent.Type.ITEM_ACQUIRED,
                "a", "a", "damage_boost");
        check(firstPickup != null,
                "actual mino pickup emits acquisition");
        check(firstPickup.getItemSourceX() >= 0 && firstPickup.getItemSourceY() >= 0
                && firstPickup.getItemSlotIndex() == 0,
                "pickup carries its mined cell and destination slot");
        BattleResult next = fillRows(boost, "a", true);
        BattleEvent used = itemEvent(next, BattleEvent.Type.ITEM_USED,
                "a", "a", "damage_boost");
        BattleEvent acquired = itemEvent(next, BattleEvent.Type.ITEM_ACQUIRED,
                "a", "a", "damage_boost");
        check(used != null && acquired != null && used.getEventId() < acquired.getEventId()
                && boost.getState().getParticipant("a").getItems().contains("damage_boost"),
                "same-ID automatic use and reacquisition both survive one clear");
        assertSequentialIds(next);

        BattleManager full = squares(seedForRepeats("a", "heal", 4), 10000);
        for (int i = 0; i < 3; i++) {
            check(count(fillRows(full, "a", false), BattleEvent.Type.ITEM_ACQUIRED) == 1,
                    "free inventory slot emits one acquisition");
        }
        BattleResult fourth = fillRows(full, "a", false);
        check(full.getState().getParticipant("a").getItems().size() == 3
                && count(fourth, BattleEvent.Type.ITEM_ACQUIRED) == 0,
                "full inventory rejects pickup without false event");

        BattleManager utility = squares(15, CharacterCatalog.loadDefault().utility(), 10000);
        BattleResult utilityClear = fillRows(utility, "a", true);
        int mined = 0;
        for (BattleEvent event : utilityClear.getEvents()) {
            if (event.getType() == BattleEvent.Type.CORE_EVENT && event.getCoreEvent() != null)
                mined += event.getCoreEvent().getItemExtractions().size();
            if (event.getType() == BattleEvent.Type.ITEM_ACQUIRED)
                check(event.getItemSourceX() >= 0 && event.getItemSourceY() >= 0,
                        "utility pickup must come from a visible ore cell");
        }
        check(mined > 0 && count(utilityClear, BattleEvent.Type.ITEM_ACQUIRED) == mined,
                "utility can only acquire actually mined ore");
    }

    private static void feverAndTimeWarpExpire() {
        Map<String, PieceGenerator> generators = new LinkedHashMap<String, PieceGenerator>();
        generators.put("a", new PieceGenerator() {
            private int issued;
            @Override public PieceType nextPiece() {
                return ++issued <= 100 ? PieceType.O : issued <= 102 ? PieceType.I : PieceType.O;
            }
        });
        generators.put("b", () -> PieceType.O);
        BattleManager fever = start(new BattleManager(Arrays.asList(
                new ParticipantSpec("a", "A", 100), new ParticipantSpec("b", "B", 10000)),
                7, generators));
        for (int i = 0; i < 20; i++) fillRows(fever, "a", false);
        check(fever.getState().getParticipant("a").isFeverActive()
                && fever.getState().getParticipant("a").getFeverRemainingMillis() == 10000,
                "twenty double clears auto-activate ten-second Fever");
        check(fever.submit("a", GameAction.Type.ROTATE_RIGHT).isAccepted(), "first I rotates flat");
        dropAt(fever, "a", 2);
        check(fever.submit("a", GameAction.Type.ROTATE_RIGHT).isAccepted(), "second I rotates flat");
        dropAt(fever, "a", 6);
        int beforeHp = fever.getState().getParticipant("b").getHp();
        int beforeGarbage = fever.getState().getParticipant("b").getPendingGarbageLines();
        dropAt(fever, "a", 8);
        check(fever.getState().getParticipant("b").getHp() == beforeHp - 5
                && fever.getState().getParticipant("b").getPendingGarbageLines() == beforeGarbage + 1,
                "active Fever adds twenty percent damage and one garbage on a single clear");
        check(fever.getState().getParticipant("a").getFever() == 0,
                "active Fever does not recharge from clears");
        check(fever.advance(9999).isAccepted(), "Fever time advances");
        check(fever.getState().getParticipant("a").getFeverRemainingMillis() == 1,
                "Fever remains through 9999ms");
        check(fever.advance(1).isAccepted()
                && !fever.getState().getParticipant("a").isFeverActive(),
                "Fever expires at exactly ten seconds");

        BattleManager warp = squares(seedFor("time_warp", null), 10000);
        fillRows(warp, "a", false);
        check(warp.submitItem("a", new GameAction.ItemUse("time_warp", "a")).isAccepted(),
                "warp activation");
        check(warp.advance(5000).isAccepted(), "warp time advances");
        check(warp.getState().getParticipant("a").getGravityMillis() == 400
                && warp.getState().getParticipant("a").getTimeWarpRemainingMillis() == 0,
                "warp expires at exactly five seconds");
    }

    private static void characterPassives() {
        CharacterCatalog catalog = CharacterCatalog.loadDefault();
        BattleManager defense = squares(9, catalog.defender(), 10000);
        fillRows(defense, "b", true);
        check(defense.getState().getParticipant("a").getPendingGarbageLines() == 1,
                "incoming one-line chunk for defender");
        fillRows(defense, "a", true);
        check(defense.getState().getParticipant("a").getPendingGarbageLines() == 0
                && defense.getState().getParticipant("b").getPendingGarbageLines() == 1,
                "defense passive removes last queued line before own attack cancellation");
        check(defense.getState().getParticipant("a").getMaxHp() == 150,
                "defender maximum HP is 150");

        BattleManager utility = squares(15, catalog.utility(), 10000);
        BattleResult utilityClear = fillRows(utility, "a", true);
        check(utility.getState().getParticipant("a").getItemSlots() == 4
                && utility.getState().getParticipant("a").getItems().size()
                        == count(utilityClear, BattleEvent.Type.ITEM_ACQUIRED),
                "utility gets four slots but no unmined bonus item");
    }

    private static BattleManager squares(long seed, int targetHp) {
        return squares(seed, CharacterCatalog.loadDefault().basic(), targetHp);
    }
    private static BattleManager squares(long seed, kr.ac.jbnu.se.tetris.character.CharacterSpec actor,
            int targetHp) {
        Map<String, PieceGenerator> generators = new LinkedHashMap<String, PieceGenerator>();
        generators.put("a", () -> PieceType.O);
        generators.put("b", () -> PieceType.O);
        return start(new BattleManager(Arrays.asList(new ParticipantSpec("a", "A", actor),
                new ParticipantSpec("b", "B", targetHp)), seed, generators));
    }
    private static BattleManager start(BattleManager battle) {
        check(battle.start().isAccepted(), "fixture battle starts");
        return battle;
    }
    private static BattleResult fillRows(BattleManager battle, String actor, boolean nonPerfect) {
        if (nonPerfect) dropAt(battle, actor, 0);
        BattleResult last = null;
        for (int x : new int[] {0, 2, 4, 6, 8}) last = dropAt(battle, actor, x);
        return last;
    }
    private static BattleResult dropAt(BattleManager battle, String actor, int x) {
        while (battle.getState().getParticipant(actor).getGameState().getPieceX() > x)
            check(battle.submit(actor, GameAction.Type.MOVE_LEFT).isAccepted(), "move left");
        while (battle.getState().getParticipant(actor).getGameState().getPieceX() < x)
            check(battle.submit(actor, GameAction.Type.MOVE_RIGHT).isAccepted(), "move right");
        BattleResult drop = battle.submit(actor, GameAction.Type.HARD_DROP);
        check(drop.isAccepted(), "drop O");
        return drop;
    }

    private static long seedForRepeats(String actor, String itemId, int repeats) {
        List<String> items = Arrays.asList("damage_boost", "garbage_bomb", "heal", "shield",
                "line_cleaner", "fever_charge", "time_warp", "nullify");
        for (long seed = 0; seed < 1000000; seed++) {
            Random random = new Random(seed ^ (long) actor.hashCode());
            boolean all = true;
            for (int index = 0; index < repeats; index++) {
                if (!itemId.equals(items.get(random.nextInt(items.size())))) { all = false; break; }
                random.nextInt(4); // 광석 위치도 같은 난수열에서 고정된다.
            }
            if (all) return seed;
        }
        throw new AssertionError("Repeated item seed not found");
    }

    private static long seedForTwoChargeNullifyTarget() {
        List<String> items = Arrays.asList("damage_boost", "garbage_bomb", "heal", "shield",
                "line_cleaner", "fever_charge", "time_warp", "nullify");
        for (long seed = 0; seed < 1000000; seed++) {
            int a = new Random(seed ^ (long) "a".hashCode()).nextInt(8);
            Random b = new Random(seed ^ (long) "b".hashCode());
            b.nextInt(8);
            b.nextInt(4);
            String second = items.get(b.nextInt(8));
            if ("nullify".equals(items.get(a)) && ("garbage_bomb".equals(second)
                    || "heal".equals(second) || "line_cleaner".equals(second))
                    && new Random(seed ^ 0x2A6F19E5L).nextInt(2) == 1) return seed;
        }
        throw new AssertionError("Two-charge nullify target seed not found");
    }

    private static BattleEvent itemEvent(BattleResult result, BattleEvent.Type type,
            String actorId, String targetId, String itemId) {
        for (BattleEvent event : result.getEvents()) {
            if (event.getType() == type && actorId.equals(event.getActorId())
                    && targetId.equals(event.getTargetId()) && itemId.equals(event.getReason())
                    && event.getAmount() == 1) return event;
        }
        return null;
    }
    private static void assertSequentialIds(BattleResult result) {
        long previous = 0;
        for (BattleEvent event : result.getEvents()) {
            check(event.getEventId() > previous, "battle event IDs increase in emission order");
            previous = event.getEventId();
        }
    }
    private static long seedFor(String aItem, String bItem) {
        List<String> items = Arrays.asList("damage_boost", "garbage_bomb", "heal", "shield",
                "line_cleaner", "fever_charge", "time_warp", "nullify");
        Random candidates = new Random(991);
        for (int attempt = 0; attempt < 100000; attempt++) {
            long seed = attempt < 1000 ? attempt : candidates.nextLong();
            Random a = new Random(seed ^ (long) "a".hashCode());
            Random b = new Random(seed ^ (long) "b".hashCode());
            if (!items.get(a.nextInt(8)).equals(aItem)) continue;
            if (bItem != null && !items.get(b.nextInt(8)).equals(bItem)) continue;
            return seed;
        }
        throw new AssertionError("Item seed not found");
    }
    private static int count(BattleResult result, BattleEvent.Type type) {
        int count = 0;
        for (BattleEvent event : result.getEvents()) if (event.getType() == type) count++;
        return count;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
