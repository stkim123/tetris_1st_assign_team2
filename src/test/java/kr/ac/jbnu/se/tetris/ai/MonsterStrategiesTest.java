package kr.ac.jbnu.se.tetris.ai;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kr.ac.jbnu.se.tetris.controller.AIController;
import kr.ac.jbnu.se.tetris.core.ActionResult;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameEngine;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.PieceGenerator;
import kr.ac.jbnu.se.tetris.core.PieceType;
import kr.ac.jbnu.se.tetris.app.MonsterStrategies;
import kr.ac.jbnu.se.tetris.story.MonsterSpec;
import kr.ac.jbnu.se.tetris.story.MonsterTier;

/** 공통 휴리스틱 프로필·정책 교체·관측 고정의 실행 계약 검증 */
public final class MonsterStrategiesTest {
    public static void main(String[] args) throws Exception {
        profileCatalogValidation();
        fixedPolicyAndExecutablePlan();
        policyRuntimeFallback();
        frozenObservationOnWorker();
    }

    private static void profileCatalogValidation() {
        AIProfileCatalog catalog = AIProfileCatalog.loadDefault();
        check(catalog.all().size() == 3, "Three baseline profiles");
        AIProfile normal = catalog.get("normal_default");
        AIProfile elite = catalog.get("elite_default");
        AIProfile boss = catalog.get("boss_default");
        check("FIXED".equals(normal.getPolicyId()) && "ADAPTIVE".equals(elite.getPolicyId())
                && "BOSS".equals(boss.getPolicyId()), "Story profiles select three implemented policies");
        check(normal.getDelayMillis() > elite.getDelayMillis()
                && elite.getDelayMillis() > boss.getDelayMillis(), "Configured action intervals");
        reject(new Runnable() { public void run() { AIProfileCatalog.loadDefault().get("missing"); } });

        String valid = "profiles=test\n"
                + "test.policy=FIXED\ntest.delayMillis=1000\ntest.maxSearchStates=700\n"
                + "test.budgetMillis=40\ntest.maxWeightDeltaRatio=0.25\n"
                + "test.line=6\ntest.fourLineBonus=15\ntest.aggregateHeight=-0.45\n"
                + "test.maximumHeight=-0.8\ntest.holes=-7\ntest.bumpiness=-0.35\n"
                + "test.wells=-0.25\n";
        check(load(valid).get("test").getMaxSearchStates() == 700, "Independent profile fixture");
        rejectLoad(valid.replace("profiles=test", "profiles=test,test"));
        check("ADAPTIVE".equals(load(valid.replace("test.policy=FIXED", "test.policy=ADAPTIVE"))
                .get("test").getPolicyId()), "Adaptive policy fixture accepted");
        check("BOSS".equals(load(valid.replace("test.policy=FIXED", "test.policy=BOSS"))
                .get("test").getPolicyId()), "Boss policy fixture accepted");
        rejectLoad(valid.replace("test.policy=FIXED", "test.policy=UNKNOWN"));
        rejectLoad(valid.replace("test.delayMillis=1000", "test.delayMillis=99"));
        rejectLoad(valid.replace("test.budgetMillis=40", "test.budgetMillis=oops"));
        rejectLoad(valid.replace("test.maxWeightDeltaRatio=0.25", "test.maxWeightDeltaRatio=1.1"));
        rejectLoad(valid.replace("test.holes=-7\n", ""));
        rejectLoad(valid + "test.holePenalty=-9\n");
    }

    private static void fixedPolicyAndExecutablePlan() {
        AIProfile selected = AIProfileCatalog.loadDefault().get("normal_default");
        GameEngine engine = fixedEngine();
        check(engine.dispatch(new GameAction(GameAction.Type.START, "local", 0)).isAccepted(), "Start");
        GameState state = engine.getState();
        AIContext context = new AIContext("match-1", state, 80, new PlayerProfile().snapshot(),
                7, null, "phase-1");
        AIPlan plan = new PolicyDrivenStrategy(selected, new FixedWeightPolicy()).plan(context);
        PolicyDecision decision = plan.getPolicyDecision();
        check(decision != null && decision.getDecisionId() == 7
                && decision.getSourceVersion() == state.getVersion()
                && "phase-1".equals(decision.getNextPolicyState())
                && !decision.isFallback() && decision.getFallbackReason() == null,
                "Normal policy metadata is distinct from fallback");
        check(decision.getWeights() == selected.getBaseWeights(), "Configured weights selected");
        check(!selected.allows(new HeuristicWeights(1000, 48, -.3, -.4, -6, -.25, -.05)),
                "Out-of-range policy weights rejected");
        check(!plan.getActions().isEmpty(), "Plan contains executable actions");
        long sequence = 1;
        for (GameAction.Type action : plan.getActions()) {
            ActionResult applied = engine.dispatch(new GameAction(action, "local", sequence++));
            check(applied.isAccepted(), "Engine accepts every planned action: " + action);
        }
        reject(new Runnable() {
            public void run() {
                new AIPlan(2, java.util.Collections.singletonList(GameAction.Type.HARD_DROP),
                        1, 0, 0, false, new PolicyDecision("FIXED", HeuristicWeights.SAFE,
                                null, 1, 3));
            }
        });
        for (MonsterTier tier : MonsterTier.values()) {
            String id = tier == MonsterTier.NORMAL ? "normal_default"
                    : tier == MonsterTier.ELITE ? "elite_default" : "boss_default";
            MonsterSpec spec = new MonsterSpec("policy_test", "Policy test", tier, 100, id);
            AIPlan assembled = MonsterStrategies.create(spec).plan(context);
            check(id.equals(AIProfileCatalog.loadDefault().get(id).getProfileId())
                    && assembled.getPolicyDecision() != null
                    && AIProfileCatalog.loadDefault().get(id).getPolicyId()
                    .equals(assembled.getPolicyDecision().getPolicyId())
                    && !assembled.getPolicyDecision().isFallback(),
                    "Monster strategy assembles policy for " + tier);
        }
        reject(new Runnable() {
            public void run() {
                new AIContext("match-1", state, 101, 100,
                        new PlayerProfile().snapshot(), 8, null, null);
            }
        });
    }

    private static void policyRuntimeFallback() {
        final AIProfile selected = AIProfileCatalog.loadDefault().get("normal_default");
        GameEngine engine = fixedEngine();
        check(engine.dispatch(new GameAction(GameAction.Type.START, "local", 0)).isAccepted(), "Start");
        final AIContext context = new AIContext("fallback-match", engine.getState(), 100, 100,
                new PlayerProfile().snapshot(), 31, selected.getBaseWeights(), "stable-phase");
        fallbackPlan(selected, context, new WeightPolicy() {
            public PolicyDecision decide(AIContext ignored, AIProfile profile) {
                throw new IllegalStateException("broken adaptive policy");
            }
        }, "POLICY_EXCEPTION");
        fallbackPlan(selected, context, new WeightPolicy() {
            public PolicyDecision decide(AIContext ignored, AIProfile profile) { return null; }
        }, "NO_POLICY_DECISION");
        fallbackPlan(selected, context, new WeightPolicy() {
            public PolicyDecision decide(AIContext ignored, AIProfile profile) {
                return new PolicyDecision("OTHER", profile.getBaseWeights(), "wrong",
                        context.getDecisionId(), context.getGameState().getVersion());
            }
        }, "POLICY_ID_MISMATCH");
        fallbackPlan(selected, context, new WeightPolicy() {
            public PolicyDecision decide(AIContext ignored, AIProfile profile) {
                return new PolicyDecision(profile.getPolicyId(), profile.getBaseWeights(), "wrong",
                        context.getDecisionId() + 1, context.getGameState().getVersion());
            }
        }, "DECISION_ID_MISMATCH");
        fallbackPlan(selected, context, new WeightPolicy() {
            public PolicyDecision decide(AIContext ignored, AIProfile profile) {
                return new PolicyDecision(profile.getPolicyId(), profile.getBaseWeights(), "wrong",
                        context.getDecisionId(), context.getGameState().getVersion() + 1);
            }
        }, "SOURCE_VERSION_MISMATCH");
        fallbackPlan(selected, context, new WeightPolicy() {
            public PolicyDecision decide(AIContext ignored, AIProfile profile) {
                return new PolicyDecision(profile.getPolicyId(),
                        new HeuristicWeights(1000, 15, -.45, -.8, -7, -.35, -.25),
                        "wrong", context.getDecisionId(), context.getGameState().getVersion());
            }
        }, "WEIGHTS_OUT_OF_RANGE");
    }

    private static void fallbackPlan(AIProfile profile, AIContext context, WeightPolicy policy,
                                     String expectedReason) {
        AIPlan plan = new PolicyDrivenStrategy(profile, policy).plan(context);
        PolicyDecision diagnostic = plan.getPolicyDecision();
        check(diagnostic != null && diagnostic.isFallback()
                && expectedReason.equals(diagnostic.getFallbackReason()), "Fallback diagnostic");
        check(diagnostic.getWeights() == profile.getBaseWeights()
                && diagnostic.getDecisionId() == context.getDecisionId()
                && diagnostic.getSourceVersion() == context.getGameState().getVersion()
                && "stable-phase".equals(diagnostic.getNextPolicyState()),
                "Fallback preserves prior policy state and request identity");
        check(!plan.getActions().isEmpty()
                && plan.getActions().get(plan.getActions().size() - 1) == GameAction.Type.HARD_DROP,
                "Fallback produces a legal heuristic placement");
    }

    private static void frozenObservationOnWorker() throws Exception {
        final GameEngine engine = fixedEngine();
        check(engine.dispatch(new GameAction(GameAction.Type.START, "local", 0)).isAccepted(), "Start");
        final PlayerProfile profile = new PlayerProfile();
        final PlayerProfile.Snapshot captured = profile.snapshot();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final long[] seenPlacements = {-1};
        WeightPolicy policy = new WeightPolicy() {
            public PolicyDecision decide(AIContext context, AIProfile selected) {
                entered.countDown();
                try { release.await(2, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                seenPlacements[0] = context.getObservation().getPlacements();
                return new PolicyDecision(selected.getPolicyId(), selected.getBaseWeights(),
                        "next", context.getDecisionId(), context.getGameState().getVersion());
            }
        };
        AIProfile selected = AIProfileCatalog.loadDefault().get("normal_default");
        AIController controller = new AIController(new PolicyDrivenStrategy(selected, policy));
        GameState original = engine.getState();
        try {
            check(controller.request(new AIContext("match-2", original, 100, captured,
                    11, null, "previous")), "Request accepted");
            check(entered.await(2, TimeUnit.SECONDS), "Worker entered policy");
            ActionResult placed = engine.dispatch(new GameAction(GameAction.Type.HARD_DROP, "local", 1));
            check(placed.isAccepted(), "Player placement for observation update");
            profile.observe(original, GameAction.Type.HARD_DROP, placed.getEvents());
            check(profile.snapshot().getPlacements() == 1, "Live observation changed");
            release.countDown();
            waitFinished(controller);
            AIPlan delivered = controller.poll(original);
            check(delivered != null && seenPlacements[0] == 0, "Worker read frozen observation");
            check("next".equals(delivered.getPolicyDecision().getNextPolicyState()),
                    "Next policy state returned for session commit");
        } finally {
            release.countDown();
            controller.close();
        }
    }

    private static GameEngine fixedEngine() {
        return new GameEngine(new PieceGenerator() {
            public PieceType nextPiece() { return PieceType.O; }
        });
    }

    private static AIProfileCatalog load(String content) {
        InputStream input = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        return AIProfileCatalog.load(input);
    }

    private static void rejectLoad(final String content) {
        reject(new Runnable() { public void run() { load(content); } });
    }

    private static void reject(Runnable action) {
        boolean rejected = false;
        try { action.run(); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Invalid input rejected");
    }

    private static void waitFinished(AIController controller) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (controller.isThinking() && System.nanoTime() < end) Thread.sleep(1);
        check(!controller.isThinking(), "Worker completed");
    }

    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
}
