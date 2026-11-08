package kr.ac.jbnu.se.tetris.battle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import kr.ac.jbnu.se.tetris.character.CharacterSpec;
import kr.ac.jbnu.se.tetris.controller.Controller;
import kr.ac.jbnu.se.tetris.controller.PlayerController;
import kr.ac.jbnu.se.tetris.core.ActionResult;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameEngine;
import kr.ac.jbnu.se.tetris.core.GameEvent;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.PieceGenerator;
import kr.ac.jbnu.se.tetris.core.SevenBagGenerator;
import kr.ac.jbnu.se.tetris.item.ItemSpec;

/** 가상 시간, 참가자 상태, 공격과 가비지를 처리하는 전투 규칙 소유자. */
public final class BattleManager {
    private static final List<String> ITEM_IDS = Arrays.asList("damage_boost", "garbage_bomb",
            "heal", "shield", "line_cleaner", "fever_charge", "time_warp", "nullify");
    private final Map<String, Participant> participants = new LinkedHashMap<String, Participant>();
    private final DamageManager damageManager = new DamageManager();
    private final HPManager hpManager = new HPManager();
    private final Random itemRandom;
    private final boolean pvp;
    /** advance()/tick()에서 실제로 적용된 중력 칸 수. 서버가 스냅샷 전송 여부를 판단하는 데 쓴다. */
    private long gravityStepCount;
    private BattleState.Status status = BattleState.Status.READY;
    private long version;
    private long lastEventId;
    private long elapsedMillis;
    private String winnerId;
    private String reason;

    public BattleManager(List<ParticipantSpec> specs, long seed) {
        this(specs, seed, null, false, 400, 400, 0, 0, -1);
    }
    BattleManager(List<ParticipantSpec> specs, long seed, Map<String, PieceGenerator> generators) {
        this(specs, seed, generators, false, 400, 400, 0, 0, -1);
    }
    public static BattleManager pvp(List<ParticipantSpec> specs, long seed) {
        return new BattleManager(specs, seed, null, true, 500, 500, 0, 0, -1);
    }
    public static BattleManager pve(List<ParticipantSpec> specs, long seed, int playerGravityMillis,
            int monsterGravityMillis, double monsterAttackBuff) {
        return new BattleManager(specs, seed, null, false, playerGravityMillis,
                monsterGravityMillis, monsterAttackBuff, 0, -1);
    }
    public static BattleManager pve(List<ParticipantSpec> specs, long seed, int playerGravityMillis,
            int monsterGravityMillis, double monsterAttackBuff, int monsterExtraGarbage) {
        return new BattleManager(specs, seed, null, false, playerGravityMillis,
                monsterGravityMillis, monsterAttackBuff, monsterExtraGarbage, -1);
    }
    public static BattleManager pve(List<ParticipantSpec> specs, long seed, int playerGravityMillis,
            int monsterGravityMillis, double monsterAttackBuff, int monsterExtraGarbage,
            int monsterItemLevel) {
        return new BattleManager(specs, seed, null, false, playerGravityMillis,
                monsterGravityMillis, monsterAttackBuff, monsterExtraGarbage, monsterItemLevel);
    }
    private BattleManager(List<ParticipantSpec> specs, long seed,
            Map<String, PieceGenerator> generators, boolean pvp, int playerGravityMillis,
            int monsterGravityMillis, double monsterAttackBuff, int monsterExtraGarbage,
            int monsterItemLevel) {
        if (specs == null || specs.size() < 2 || specs.size() > 4
                || (generators != null && generators.size() != specs.size())
                || playerGravityMillis < 100 || monsterGravityMillis < 100
                || !Double.isFinite(monsterAttackBuff) || monsterAttackBuff < 0
                || monsterExtraGarbage < 0 || monsterExtraGarbage > 4
                || monsterItemLevel < -1 || monsterItemLevel > 5)
            throw new IllegalArgumentException("Invalid battle settings");
        this.pvp = pvp;
        int index = 0;
        for (ParticipantSpec supplied : specs) {
            if (supplied == null || participants.containsKey(supplied.getId()))
                throw new IllegalArgumentException("Participants require unique IDs");
            ParticipantSpec spec = pvp ? new ParticipantSpec(supplied.getId(), supplied.getName(),
                    CharacterSpec.DEFAULT) : supplied;
            PieceGenerator generator = generators == null ? new SevenBagGenerator(seed)
                    : generators.get(spec.getId());
            if (generator == null) throw new IllegalArgumentException("Missing generator: " + spec.getId());
            GameEngine engine = new GameEngine(spec.getId(), generator);
            engine.setBattleManaged(true);
            int itemLevel = !pvp && index > 0 ? monsterItemLevel : -1;
            if (itemLevel < 0) engine.configureItemSpawns(
                    !pvp && "utility".equals(spec.getCharacter().getId()) ? 3 : 5,
                    seed ^ (long) spec.getId().hashCode());
            else engine.configureItemSpawns(itemLevel == 0 ? 0 : 10 - itemLevel,
                    seed ^ (long) spec.getId().hashCode(), monsterItems(itemLevel));
            int gravity = pvp ? 500 : index == 0 ? playerGravityMillis : monsterGravityMillis;
            double attackBuff = !pvp && index > 0 ? monsterAttackBuff : 0;
            int extraGarbage = !pvp && index > 0 ? monsterExtraGarbage : 0;
            participants.put(spec.getId(), new Participant(spec, engine,
                    new PlayerController(engine, spec.getId()), seed ^ (index * 0x5DEECE66DL),
                    gravity, attackBuff, extraGarbage, itemLevel));
            index++;
        }
        itemRandom = new Random(seed ^ 0x2A6F19E5L);
    }

    public synchronized BattleState getState() {
        Map<String, ParticipantState> snapshots = new LinkedHashMap<String, ParticipantState>();
        for (Participant p : participants.values()) {
            snapshots.put(p.spec.getId(), new ParticipantState(p.spec.getId(), p.spec.getName(),
                    p.hp, p.spec.getMaxHp(), p.controller.getState(), p.eliminated,
                    p.spec.getCharacter().getId(), p.spec.getCharacter().getItemSlots(), p.items,
                    p.itemCharges,
                    p.fever, Math.max(0, p.feverUntil - elapsedMillis),
                    Math.max(0, p.warpUntil - elapsedMillis), p.tank.getPendingLines(),
                    p.tank.getWaitRemainingMillis(elapsedMillis), gravityMillis(p),
                    p.maxCombo, p.totalDamage));
        }
        return new BattleState(status, version, snapshots, winnerId, reason, elapsedMillis);
    }

    public synchronized BattleResult start() {
        if (status != BattleState.Status.READY) return reject("START requires READY", null);
        List<BattleEvent> events = new ArrayList<BattleEvent>();
        for (Participant p : participants.values()) {
            ActionResult action = p.controller.submit(GameAction.Type.START);
            processCore(p, action, events);
            if (!action.isAccepted() || p.eliminated) {
                finish(!action.isAccepted() ? "START_FAILED: " + action.getReason() : "START_TOP_OUT", events);
                return result(false, reason, events);
            }
        }
        status = BattleState.Status.RUNNING;
        version++;
        events.add(event(BattleEvent.Type.MATCH_STARTED, null, null, 0, null, null));
        return result(true, null, events);
    }

    public synchronized BattleResult submit(String actorId, GameAction.Type type) {
        Participant p = participants.get(actorId);
        if (p == null) return reject("Unknown actor ID", actorId);
        if (status != BattleState.Status.RUNNING) return reject("Battle requires RUNNING", actorId);
        if (p.eliminated) return reject("Actor is eliminated", actorId);
        if (type == GameAction.Type.USE_ITEM) return reject("INVALID_PAYLOAD", actorId);
        if (type == null || type == GameAction.Type.START || type == GameAction.Type.PAUSE
                || type == GameAction.Type.RESUME || type == GameAction.Type.GRAVITY_TICK
                || type == GameAction.Type.RECEIVE_GARBAGE)
            return reject("Action is managed by BattleManager", actorId);
        List<BattleEvent> events = new ArrayList<BattleEvent>();
        ActionResult action = p.controller.submit(type);
        processCore(p, action, events);
        if (isInternalFailure()) return result(false, reason, events);
        if (!action.isAccepted()) return rejectWithEvents(action.getReason(), actorId, events);
        version++; finishIfOneRemains(events);
        return result(true, null, events);
    }

    public synchronized BattleResult submitItem(String actorId, GameAction.ItemUse use) {
        Participant source = participants.get(actorId);
        if (source == null) return reject("UNKNOWN_ACTOR", actorId);
        if (status != BattleState.Status.RUNNING) return reject("BATTLE_NOT_RUNNING", actorId);
        if (source.eliminated) return reject("ACTOR_ELIMINATED", actorId);
        if (use == null) return reject("INVALID_PAYLOAD", actorId);
        Participant target = participants.get(use.getTargetActorId());
        if (target == null || target.eliminated) return reject("INVALID_TARGET", actorId);
        GameAction.TargetCell cell = use.getTargetCell();
        if (cell != null && (cell.getX() >= 10 || cell.getY() >= 22))
            return reject("INVALID_TARGET_CELL", actorId);
        String id = use.getItemId();
        if (source.itemLevel >= 0 && !monsterItems(source.itemLevel).contains(id))
            return reject("ITEM_RESTRICTED", actorId);
        if (!ITEM_IDS.contains(id) || !source.items.contains(id)) return reject("ITEM_NOT_OWNED", actorId);
        if ("damage_boost".equals(id) || "shield".equals(id)) return reject("AUTO_ITEM", actorId);
        boolean self = source == target;
        if (("garbage_bomb".equals(id) || "nullify".equals(id)) == self)
            return reject("INVALID_TARGET", actorId);
        if (!("garbage_bomb".equals(id) || "nullify".equals(id)) && !self)
            return reject("INVALID_TARGET", actorId);
        if ("heal".equals(id) && source.hp == source.spec.getMaxHp()) return reject("NO_EFFECT", actorId);
        if ("time_warp".equals(id) && source.warpUntil > elapsedMillis) return reject("NO_EFFECT", actorId);
        List<BattleEvent> events = new ArrayList<BattleEvent>();
        String removedByNullify = null;
        if ("heal".equals(id)) {
            int amount = Math.max(1, (int) Math.ceil(source.spec.getMaxHp() * .1));
            source.hp = hpManager.applyHealing(source.hp, source.spec.getMaxHp(), amount);
            events.add(event(BattleEvent.Type.HP_CHANGED, actorId, actorId, source.hp, "HEAL", null));
        } else if ("garbage_bomb".equals(id))
            sendGarbage(source, target, source.tank.cancel(1), null, events);
        else if ("line_cleaner".equals(id)) {
            ActionResult clean = source.engine.clearBottomGarbageLine();
            if (clean.isAccepted()) processCore(source, clean, events);
            else if (!"No garbage line to clean".equals(clean.getReason()))
                return rejectWithEvents(clean.getReason(), actorId, events);
        } else if ("fever_charge".equals(id)) chargeFever(source, 20);
        else if ("time_warp".equals(id)) {
            source.warpUntil = elapsedMillis + 5000;
            source.warpSlotIndex = source.items.indexOf(id);
        }
        else if ("nullify".equals(id) && !target.items.isEmpty()) {
            int removedIndex = itemRandom.nextInt(target.items.size());
            removedByNullify = removeItemAt(target, removedIndex);
        }
        if (!"time_warp".equals(id)) consumeItem(source, id);
        events.add(event(BattleEvent.Type.ITEM_USED, actorId, target.spec.getId(), 1, id, null));
        if (removedByNullify != null)
            events.add(event(BattleEvent.Type.ITEM_REMOVED, actorId,
                    target.spec.getId(), 1, removedByNullify, null));
        version++;
        return result(true, null, events);
    }

    public synchronized BattleResult forfeit(String actorId) {
        Participant p = participants.get(actorId);
        if (p == null) return reject("UNKNOWN_ACTOR", actorId);
        if (status != BattleState.Status.RUNNING && status != BattleState.Status.PAUSED)
            return reject("BATTLE_NOT_RUNNING", actorId);
        if (p.eliminated) return reject("ACTOR_ELIMINATED", actorId);
        p.eliminated = true; p.eliminationReason = "FORFEIT";
        List<BattleEvent> events = new ArrayList<BattleEvent>();
        version++;
        BattleState.Status prior = status; status = BattleState.Status.RUNNING;
        finishIfOneRemains(events);
        if (status != BattleState.Status.FINISHED) status = prior;
        return result(true, null, events);
    }

    /** 지금까지 적용된 중력 칸 수(참가자 합계). 값이 변했으면 보드가 움직인 것이다. */
    public synchronized long getGravityStepCount() { return gravityStepCount; }

    public synchronized BattleResult advance(long deltaMillis) {
        if (status != BattleState.Status.RUNNING) return reject("TICK requires RUNNING", null);
        if (deltaMillis < 0 || deltaMillis > 600000) return reject("INVALID_ELAPSED_MILLIS", null);
        List<BattleEvent> events = new ArrayList<BattleEvent>();
        long remaining = deltaMillis;
        while (remaining > 0 && status == BattleState.Status.RUNNING) {
            long step = Math.min(50, remaining);
            elapsedMillis += step; remaining -= step;
            expireTimeWarpItems();
            for (Participant p : participants.values()) {
                if (p.eliminated) continue;
                p.gravityAccumulated += step;
                int interval = gravityMillis(p);
                while (p.gravityAccumulated >= interval && !p.eliminated) {
                    p.gravityAccumulated -= interval;
                    gravityStepCount++;
                    ActionResult action = p.controller.submit(GameAction.Type.GRAVITY_TICK);
                    processCore(p, action, events);
                    if (isInternalFailure()) return result(false, reason, events);
                    if (!action.isAccepted()) {
                        finish("TICK_FAILED: " + p.spec.getId() + ": " + action.getReason(), events);
                        return result(false, reason, events);
                    }
                }
            }
            finishIfOneRemains(events);
        }
        version++;
        return result(true, null, events);
    }

    /** 이전 테스트와 호출부가 사용하는 직접 중력 틱. 시간 기반 호출부는 advance를 사용한다. */
    public synchronized BattleResult tick() {
        if (status != BattleState.Status.RUNNING) return reject("TICK requires RUNNING", null);
        elapsedMillis += 400;
        expireTimeWarpItems();
        List<BattleEvent> events = new ArrayList<BattleEvent>();
        for (Participant p : participants.values()) {
            if (p.eliminated) continue;
            gravityStepCount++;
            ActionResult action = p.controller.submit(GameAction.Type.GRAVITY_TICK);
            processCore(p, action, events);
            if (isInternalFailure()) return result(false, reason, events);
            if (!action.isAccepted()) {
                finish("TICK_FAILED: " + p.spec.getId() + ": " + action.getReason(), events);
                return result(false, reason, events);
            }
        }
        finishIfOneRemains(events); version++;
        return result(true, null, events);
    }

    public synchronized BattleResult pause() {
        return changePause(GameAction.Type.PAUSE, BattleState.Status.RUNNING,
                BattleState.Status.PAUSED, BattleEvent.Type.PAUSED);
    }
    public synchronized BattleResult resume() {
        return changePause(GameAction.Type.RESUME, BattleState.Status.PAUSED,
                BattleState.Status.RUNNING, BattleEvent.Type.RESUMED);
    }
    private BattleResult changePause(GameAction.Type type, BattleState.Status required,
            BattleState.Status next, BattleEvent.Type eventType) {
        if (status != required) return reject(type + " requires " + required, null);
        List<BattleEvent> events = new ArrayList<BattleEvent>();
        for (Participant p : participants.values()) {
            if (p.controller.getState().getStatus() == GameState.Status.GAME_OVER) continue;
            ActionResult action = p.controller.submit(type);
            processCore(p, action, events);
            if (!action.isAccepted()) {
                finish(type + "_FAILED: " + action.getReason(), events);
                return result(false, reason, events);
            }
        }
        status = next; version++;
        events.add(event(eventType, null, null, 0, null, null));
        return result(true, null, events);
    }

    private void processCore(Participant p, ActionResult action, List<BattleEvent> events) {
        boolean placed = false;
        boolean cleared = false;
        List<GameEvent.ItemExtraction> acquired = new ArrayList<GameEvent.ItemExtraction>();
        for (GameEvent core : action.getEvents()) {
            events.add(event(BattleEvent.Type.CORE_EVENT, core.getActorId(), null,
                    core.getLineCount(), core.getReason(), core));
            if (core.getType() == GameEvent.Type.PIECE_PLACED) placed = true;
            if (core.getType() == GameEvent.Type.LINE_CLEAR) {
                cleared = true;
                attack(p, core, events);
                p.maxCombo = Math.max(p.maxCombo, Math.max(0, core.getCombo()));
                acquired.addAll(core.getItemExtractions());
            }
            if (core.getType() == GameEvent.Type.TOP_OUT && !p.eliminated) {
                p.eliminated = true; p.eliminationReason = "TOP_OUT";
            }
            if (core.getType() == GameEvent.Type.GARBAGE_RECEIVED)
                events.add(event(BattleEvent.Type.GARBAGE_RECEIVED, p.spec.getId(), null,
                        core.getLineCount(), core.getReason(), core));
        }
        for (GameEvent.ItemExtraction ore : acquired) acquire(p, ore, events);
        if (placed && !p.eliminated && p.controller.getState().isAwaitingSpawn()) {
            if (!cleared) {
                List<GameAction.Garbage> due = p.tank.drainForPlacement(elapsedMillis);
                if (!due.isEmpty()) {
                    ActionResult applied = p.engine.applyGarbage(due);
                    processCore(p, applied, events);
                    if (!applied.isAccepted()) {
                        finish("GARBAGE_DELIVERY_FAILED: " + applied.getReason(), events);
                        return;
                    }
                }
            }
            if (!p.eliminated) {
                ActionResult spawned = p.controller.submit(GameAction.Type.GRAVITY_TICK);
                if (spawned.isAccepted()) processCore(p, spawned, events);
                else finish("SPAWN_FAILED: " + spawned.getReason(), events);
            }
        }
    }

    /** 기존 자동 아이템과 Fever로 공격을 처리한 뒤 획득 아이템과 충전을 적용한다. */
    private void attack(Participant source, GameEvent clear, List<BattleEvent> events) {
        if (source.eliminated) return;
        Participant target = nextLivingTarget(source.spec.getId());
        if (target == null) return;
        boolean perfect = clear.isPerfectClear();
        boolean boost = !perfect && source.items.contains("damage_boost");
        boolean shield = !perfect && target.items.contains("shield");
        double buff = perfect ? 0 : source.spec.getCharacter().getDamageBuff()
                + source.extraAttackBuff + (source.feverUntil > elapsedMillis ? .2 : 0)
                + (boost ? .5 : 0);
        DamageManager.Attack computed = damageManager.forLineClear(clear, buff, shield ? .3 : 0);
        int damage = computed.getDamage();
        if (boost && consumeItem(source, "damage_boost"))
            events.add(event(BattleEvent.Type.ITEM_USED, source.spec.getId(),
                    source.spec.getId(), 1, "damage_boost", null));
        if (shield && damage > 0 && consumeItem(target, "shield"))
            events.add(event(BattleEvent.Type.ITEM_USED, target.spec.getId(),
                    target.spec.getId(), 1, "shield", null));
        int before = target.hp;
        target.hp = hpManager.applyDamage(before, target.spec.getMaxHp(), damage);
        source.totalDamage = (int) Math.min(Integer.MAX_VALUE,
                (long) source.totalDamage + before - target.hp);
        events.add(event(BattleEvent.Type.DAMAGE, source.spec.getId(), target.spec.getId(),
                before - target.hp, null, clear));
        events.add(event(BattleEvent.Type.HP_CHANGED, source.spec.getId(), target.spec.getId(),
                target.hp, null, clear));
        int garbage = computed.getGarbageLines();
        if (!perfect) {
            garbage += source.extraGarbage;
            if (source.feverUntil > elapsedMillis && clear.getLineCount() == 1) garbage++;
            if ("attacker".equals(source.spec.getCharacter().getId()) && clear.getLineCount() >= 2) garbage++;
            if ("defender".equals(source.spec.getCharacter().getId()) && clear.getLineCount() >= 2)
                source.tank.removeOldestLine();
        }
        garbage = source.tank.cancel(garbage);
        if (target.hp == 0) {
            target.eliminated = true; target.eliminationReason = "HP_DEPLETED";
        } else sendGarbage(source, target, garbage, clear, events);
        chargeFever(source, feverGain(clear.getLineCount()));
    }

    private void sendGarbage(Participant source, Participant target, int lines,
            GameEvent core, List<BattleEvent> events) {
        if (lines <= 0) return;
        target.tank.enqueue(lines, elapsedMillis);
        events.add(event(BattleEvent.Type.GARBAGE_SENT, source.spec.getId(), target.spec.getId(),
                lines, null, core));
    }
    private void acquire(Participant p, GameEvent.ItemExtraction ore, List<BattleEvent> events) {
        String id = ore.getItemId();
        if (ITEM_IDS.contains(id) && p.items.size() < p.spec.getCharacter().getItemSlots()) {
            int slot = p.items.size();
            p.items.add(id);
            p.itemCharges.add(ItemSpec.initialChargesOf(id));
            events.add(new BattleEvent(BattleEvent.Type.ITEM_ACQUIRED, ++lastEventId,
                    p.spec.getId(), p.spec.getId(), 1, id, null,
                    ore.getX(), ore.getY(), slot));
        }
    }
    private static boolean consumeItem(Participant p, String id) {
        int index = p.items.indexOf(id);
        if (index < 0) return false;
        int left = p.itemCharges.get(index) - 1;
        if (left > 0) p.itemCharges.set(index, left);
        else removeItemAt(p, index);
        return true;
    }
    private static String removeItemAt(Participant p, int index) {
        String removed = p.items.remove(index);
        p.itemCharges.remove(index);
        if (index == p.warpSlotIndex) p.warpSlotIndex = -1;
        else if (index < p.warpSlotIndex) p.warpSlotIndex--;
        return removed;
    }
    private void expireTimeWarpItems() {
        for (Participant p : participants.values()) {
            if (p.warpUntil > 0 && p.warpUntil <= elapsedMillis) {
                p.warpUntil = 0;
                if (p.warpSlotIndex >= 0) removeItemAt(p, p.warpSlotIndex);
            }
        }
    }
    static List<String> monsterItems(int level) {
        switch (level) {
            case 0: return java.util.Collections.emptyList();
            case 1: return Arrays.asList("heal");
            case 2: return Arrays.asList("heal", "shield");
            case 3: return Arrays.asList("heal", "shield", "fever_charge", "line_cleaner");
            case 4: return Arrays.asList("heal", "shield", "fever_charge", "line_cleaner",
                    "damage_boost", "time_warp");
            case 5: return ITEM_IDS;
            default: throw new IllegalArgumentException("Invalid monster item level");
        }
    }
    private void chargeFever(Participant p, int amount) {
        if (p.feverUntil > elapsedMillis) return;
        p.fever = Math.min(100, p.fever + amount);
        if (p.fever == 100) { p.fever = 0; p.feverUntil = elapsedMillis + 10000; }
    }
    private static int feverGain(int lines) {
        return lines == 1 ? 2 : lines == 2 ? 5 : lines == 3 ? 10 : 20;
    }
    private int gravityMillis(Participant p) {
        int interval = p.baseGravityMillis;
        if (pvp) interval = pvpGravityAt(elapsedMillis);
        if (p.warpUntil > elapsedMillis) interval += 100;
        return interval;
    }
    public static int pvpGravityAt(long elapsedMillis) {
        if (elapsedMillis < 0) throw new IllegalArgumentException("Negative elapsed time");
        if (elapsedMillis < 60000) return 500;
        long changes = 1 + (elapsedMillis - 60000) / 30000;
        return (int) Math.max(200, 500 - Math.min(300, changes * 50));
    }

    private Participant nextLivingTarget(String actorId) {
        List<Participant> ordered = new ArrayList<Participant>(participants.values());
        int index = 0;
        for (int i = 0; i < ordered.size(); i++)
            if (ordered.get(i).spec.getId().equals(actorId)) { index = i; break; }
        for (int i = 1; i < ordered.size(); i++) {
            Participant p = ordered.get((index + i) % ordered.size());
            if (!p.eliminated) return p;
        }
        return null;
    }
    private void finishIfOneRemains(List<BattleEvent> events) {
        if (status != BattleState.Status.RUNNING) return;
        String survivor = null, lastReason = null;
        int alive = 0;
        for (Participant p : participants.values()) {
            if (p.eliminated) lastReason = p.eliminationReason;
            else { survivor = p.spec.getId(); alive++; }
        }
        if (alive <= 1) {
            winnerId = alive == 1 ? survivor : null;
            finish(alive == 0 ? "NO_SURVIVOR" : lastReason, events);
        }
    }
    private void finish(String why, List<BattleEvent> events) {
        if (status == BattleState.Status.FINISHED) return;
        status = BattleState.Status.FINISHED;
        reason = why; version++;
        events.add(event(BattleEvent.Type.MATCH_FINISHED, null, winnerId, 0, why, null));
    }
    private boolean isInternalFailure() {
        return status == BattleState.Status.FINISHED && reason != null
                && (reason.startsWith("SPAWN_FAILED") || reason.startsWith("GARBAGE_DELIVERY_FAILED"));
    }
    private BattleResult reject(String why, String actorId) {
        return rejectWithEvents(why, actorId, new ArrayList<BattleEvent>());
    }
    private BattleResult rejectWithEvents(String why, String actorId, List<BattleEvent> events) {
        events.add(event(BattleEvent.Type.ACTION_REJECTED, actorId, null, 0, why, null));
        return result(false, why, events);
    }
    private BattleResult result(boolean accepted, String why, List<BattleEvent> events) {
        return new BattleResult(accepted, why, getState(), events);
    }
    private BattleEvent event(BattleEvent.Type type, String actorId, String targetId,
            int amount, String why, GameEvent core) {
        return new BattleEvent(type, ++lastEventId, actorId, targetId, amount, why, core);
    }

    private static final class Participant {
        private final ParticipantSpec spec;
        private final GameEngine engine;
        private final Controller controller;
        private final GarbageTank tank;
        private final List<String> items = new ArrayList<String>();
        private final List<Integer> itemCharges = new ArrayList<Integer>();
        private final int baseGravityMillis;
        private final double extraAttackBuff;
        private final int extraGarbage;
        private final int itemLevel;
        private int hp;
        private int fever;
        private long feverUntil;
        private long warpUntil;
        private int warpSlotIndex = -1;
        private long gravityAccumulated;
        private int maxCombo;
        private int totalDamage;
        private boolean eliminated;
        private String eliminationReason;
        private Participant(ParticipantSpec spec, GameEngine engine, Controller controller,
                long tankSeed, int gravityMillis, double extraAttackBuff, int extraGarbage,
                int itemLevel) {
            this.spec = spec; this.engine = engine; this.controller = controller;
            this.tank = new GarbageTank(tankSeed); this.hp = spec.getMaxHp();
            this.baseGravityMillis = gravityMillis; this.extraAttackBuff = extraAttackBuff;
            this.extraGarbage = extraGarbage;
            this.itemLevel = itemLevel;
        }
    }
}
