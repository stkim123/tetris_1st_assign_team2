package kr.ac.jbnu.se.tetris.ai;

import kr.ac.jbnu.se.tetris.app.MonsterStrategies;
import kr.ac.jbnu.se.tetris.core.ActionResult;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameEngine;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.PieceGenerator;
import kr.ac.jbnu.se.tetris.core.PieceType;
import kr.ac.jbnu.se.tetris.story.MonsterSpec;
import kr.ac.jbnu.se.tetris.story.MonsterTier;

/** 공개 관측·HP 경계·경기 격리·계획 확정 전 순수 계산 검증 */
public final class WeightPoliciesTest {
    public static void main(String[] args) {
        adaptiveObservationAndAcceptedWeightBoundary();
        bossHpPhasesAndMatchReset();
        policyRangeAtExtremeInputs();
    }

    private static void adaptiveObservationAndAcceptedWeightBoundary() {
        GameState state = startedState();
        AIProfile profile = AIProfileCatalog.loadDefault().get("elite_default");
        AdaptiveWeightPolicy policy = new AdaptiveWeightPolicy();
        PlayerProfile empty = new PlayerProfile();
        AIContext cold = context("first", state, 100, 100, empty.snapshot(), 1, null, null);
        PolicyDecision initial = policy.decide(cold, profile);
        check(initial.getWeights() == profile.getBaseWeights(), "Sparse observation retains base weights");
        PlayerProfile observed = sixRealPlacements();
        check(observed.snapshot().getPlacements() >= 6, "Enough accepted placements observed");
        AIContext learned = context("first", state, 100, 100, observed.snapshot(), 2,
                initial.getWeights(), initial.getNextPolicyState());
        PolicyDecision changed = policy.decide(learned, profile);
        check(profile.allows(changed.getWeights()) && !changed.isFallback(),
                "Adaptive weights remain in configured range");
        check(changed.getWeights().getLine() > profile.getBaseWeights().getLine(),
                "Observed hard drops increase line pressure");
        check(changed.getWeights().getLine() < profile.getBaseWeights().getLine()
                * (1.0 + profile.getMaxWeightDeltaRatio()), "Response remains bounded");
        AIPlan elitePlan = MonsterStrategies.create(new MonsterSpec("elite_test", "Elite",
                MonsterTier.ELITE, 100, "elite_default")).plan(learned);
        close(elitePlan.getPolicyDecision().getWeights().getLine(),
                changed.getWeights().getLine(), "Selected elite strategy uses adaptive weights");
        check(!elitePlan.getActions().isEmpty() && !elitePlan.getPolicyDecision().isFallback(),
                "Adaptive weights reach real placement search");
        PolicyDecision repeated = policy.decide(learned, profile);
        close(changed.getWeights().getLine(), repeated.getWeights().getLine(),
                "Repeated uncommitted request has no hidden policy mutation");

        AIContext accepted = context("first", state, 100, 100, observed.snapshot(), 3,
                changed.getWeights(), changed.getNextPolicyState());
        PolicyDecision next = policy.decide(accepted, profile);
        check(next.getWeights().getLine() >= changed.getWeights().getLine(),
                "Only passed accepted weights advance smoothing");
        AIContext nextMatch = context("second", state, 100, 100, observed.snapshot(), 4,
                changed.getWeights(), changed.getNextPolicyState());
        PolicyDecision reset = policy.decide(nextMatch, profile);
        check(!reset.getNextPolicyState().equals(changed.getNextPolicyState()),
                "New match has separate policy state");
        check(reset.getWeights().getLine() > changed.getWeights().getLine(),
                "Previous match weights do not smooth new match");
    }

    private static void bossHpPhasesAndMatchReset() {
        GameState game = startedState();
        AIProfile profile = AIProfileCatalog.loadDefault().get("boss_default");
        BossWeightPolicy policy = new BossWeightPolicy();
        PlayerProfile.Snapshot empty = new PlayerProfile().snapshot();
        PolicyDecision full = policy.decide(context("boss-a", game, 100, 100, empty,
                1, null, null), profile);
        PolicyDecision beforeMiddle = policy.decide(context("boss-a", game, 67, 100, empty,
                2, full.getWeights(), full.getNextPolicyState()), profile);
        PolicyDecision middle = policy.decide(context("boss-a", game, 66, 100, empty,
                3, beforeMiddle.getWeights(), beforeMiddle.getNextPolicyState()), profile);
        PolicyDecision beforeLow = policy.decide(context("boss-a", game, 34, 100, empty,
                4, middle.getWeights(), middle.getNextPolicyState()), profile);
        PolicyDecision low = policy.decide(context("boss-a", game, 33, 100, empty,
                5, beforeLow.getWeights(), beforeLow.getNextPolicyState()), profile);
        check(full.getWeights().getFourLineBonus() == profile.getBaseWeights().getFourLineBonus(),
                "Boss prepares four-line clears at full HP");
        check(full.getNextPolicyState().equals(beforeMiddle.getNextPolicyState())
                && middle.getNextPolicyState().equals(beforeLow.getNextPolicyState())
                && !middle.getNextPolicyState().equals(full.getNextPolicyState())
                && !low.getNextPolicyState().equals(middle.getNextPolicyState()),
                "HP phase changes exactly at two-thirds and one-third boundaries");
        check(low.getWeights().getHoles() < middle.getWeights().getHoles()
                && middle.getWeights().getLine() > full.getWeights().getLine(),
                "Pressure and survival phases change board priorities");
        AIContext lowContext = context("boss-a", game, 33, 100, empty,
                5, beforeLow.getWeights(), beforeLow.getNextPolicyState());
        AIPlan bossPlan = MonsterStrategies.create(new MonsterSpec("boss_test", "Boss",
                MonsterTier.BOSS, 100, "boss_default")).plan(lowContext);
        close(bossPlan.getPolicyDecision().getWeights().getHoles(), low.getWeights().getHoles(),
                "Selected boss strategy uses survival weights");
        check(!bossPlan.getActions().isEmpty() && !bossPlan.getPolicyDecision().isFallback(),
                "Boss weights reach real placement search");
        PolicyDecision healed = policy.decide(context("boss-a", game, 99, 100, empty,
                6, low.getWeights(), low.getNextPolicyState()), profile);
        check(healed.getNextPolicyState().equals(low.getNextPolicyState()),
                "Healing does not reverse committed boss phase");
        PolicyDecision newMatch = policy.decide(context("boss-b", game, 100, 100, empty,
                7, low.getWeights(), low.getNextPolicyState()), profile);
        check(!newMatch.getNextPolicyState().equals(low.getNextPolicyState())
                && newMatch.getWeights().getLine() == full.getWeights().getLine(),
                "New match resets boss phase even if old state is supplied");
    }

    private static void policyRangeAtExtremeInputs() {
        GameState game = startedState();
        PlayerProfile.Snapshot observed = sixRealPlacements().snapshot();
        for (String id : new String[] {"elite_default", "boss_default"}) {
            AIProfile profile = AIProfileCatalog.loadDefault().get(id);
            WeightPolicy policy = "elite_default".equals(id)
                    ? new AdaptiveWeightPolicy() : new BossWeightPolicy();
            for (int hp : new int[] {0, 1, 33, 34, 66, 67, 100}) {
                AIContext context = context(id, game, hp, 100, observed, hp, null, null);
                PolicyDecision decision = policy.decide(context, profile);
                check(profile.allows(decision.getWeights()), "Policy weights bounded at HP " + hp);
                check(decision.getDecisionId() == hp
                        && decision.getSourceVersion() == game.getVersion(),
                        "Decision identity follows snapshot");
            }
        }
    }

    private static PlayerProfile sixRealPlacements() {
        GameEngine engine = fixedEngine();
        PlayerProfile profile = new PlayerProfile();
        apply(engine, profile, GameAction.Type.START, 0);
        long sequence = 1;
        for (int placed = 0; placed < 6; placed++) {
            if (engine.getState().isAwaitingSpawn()) {
                apply(engine, profile, GameAction.Type.GRAVITY_TICK, sequence++);
            }
            GameState before = engine.getState();
            check(before.getStatus() == GameState.Status.RUNNING,
                    "Observation fixture remains in a real running game");
            AIPlan plan = new HeuristicStrategy(HeuristicWeights.SAFE).plan(before);
            for (GameAction.Type action : plan.getActions()) {
                apply(engine, profile, action, sequence++);
            }
        }
        return profile;
    }

    private static GameState startedState() {
        GameEngine engine = fixedEngine();
        check(engine.dispatch(new GameAction(GameAction.Type.START, "local", 0)).isAccepted(),
                "Start fixture game");
        return engine.getState();
    }

    private static GameEngine fixedEngine() {
        return new GameEngine(new PieceGenerator() {
            public PieceType nextPiece() { return PieceType.O; }
        });
    }

    private static void apply(GameEngine engine, PlayerProfile profile,
                              GameAction.Type action, long sequence) {
        GameState before = engine.getState();
        ActionResult result = engine.dispatch(new GameAction(action, "local", sequence));
        check(result.isAccepted(), "Real game accepts fixture action " + action);
        profile.observe(before, action, result.getEvents());
    }

    private static AIContext context(String matchId, GameState state, int hp, int maxHp,
                                     PlayerProfile.Snapshot observation, long decisionId,
                                     HeuristicWeights previous, String policyState) {
        return new AIContext(matchId, state, hp, maxHp, observation,
                decisionId, previous, policyState);
    }

    private static void close(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-9, message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
