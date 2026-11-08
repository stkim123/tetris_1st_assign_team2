package kr.ac.jbnu.se.tetris.controller;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import kr.ac.jbnu.se.tetris.ai.AIPlan;
import kr.ac.jbnu.se.tetris.ai.AIContext;
import kr.ac.jbnu.se.tetris.ai.AIStrategy;
import kr.ac.jbnu.se.tetris.ai.PlayerProfile;
import kr.ac.jbnu.se.tetris.core.GameState;

/** 단일 데몬 작업자에서 AI 결정을 계산하고 호출자가 게임 스레드에서 채택한 명령을 실행
 * 컨트롤러 하나는 게임 세션 하나에 속하며 세션이 끝나면 종료
 */
public final class AIController implements AutoCloseable {
    private final AIStrategy strategy;
    private final ExecutorService worker;
    private Future<AIPlan> pending;
    private String pendingActor;
    private long pendingVersion;
    private long pendingDecisionId;
    private String deliveredActor;
    private long deliveredVersion = Long.MIN_VALUE;
    private boolean closed;

    public AIController(AIStrategy strategy) {
        if (strategy == null) throw new IllegalArgumentException("Strategy is required");
        this.strategy = strategy;
        this.worker = Executors.newSingleThreadExecutor(new ThreadFactory() {
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "tetris-ai-planner");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    /** 비활성·중복·종료 상태이거나 이미 계산 중인 요청은 false를 반환 */
    public synchronized boolean request(final GameState state) {
        if (state == null) throw new IllegalArgumentException("Game state is required");
        return request(new AIContext("legacy", state, 100,
                new PlayerProfile().snapshot(), state.getVersion(), null, null));
    }

    /** 관측과 정책 상태를 요청 시점에 고정한 단일 작업 등록 */
    public synchronized boolean request(final AIContext context) {
        if (context == null) throw new IllegalArgumentException("AI context is required");
        final GameState state = context.getGameState();
        if (state == null) throw new IllegalArgumentException("Game state is required");
        if (closed || pending != null || state.getStatus() != GameState.Status.RUNNING
                || state.getActivePiece() == null || state.isAwaitingSpawn()
                || (state.getVersion() == deliveredVersion
                    && state.getActorId().equals(deliveredActor))) return false;
        pendingActor = state.getActorId();
        pendingVersion = state.getVersion();
        pendingDecisionId = context.getDecisionId();
        pending = worker.submit(new Callable<AIPlan>() {
            public AIPlan call() { return strategy.plan(context); }
        });
        return true;
    }

    /** 미완료·오래된 계획의 null 반환 및 작업자 오류의 호출자 전달 */
    public synchronized AIPlan poll(GameState current) {
        if (current == null) throw new IllegalArgumentException("Current state is required");
        if (pending == null || !pending.isDone()) return null;
        Future<AIPlan> finished = pending;
        String actor = pendingActor;
        long version = pendingVersion;
        long decisionId = pendingDecisionId;
        pending = null;
        pendingActor = null;
        try {
            AIPlan plan = finished.get();
            if (plan == null || plan.getSourceVersion() != version) {
                throw new IllegalStateException("AI strategy returned a plan for the wrong state");
            }
            if (plan.getPolicyDecision() != null
                    && plan.getPolicyDecision().getDecisionId() != decisionId) {
                throw new IllegalStateException("AI strategy returned the wrong decision ID");
            }
            if (!actor.equals(current.getActorId()) || version != current.getVersion()
                    || current.getStatus() != GameState.Status.RUNNING || current.getActivePiece() == null
                    || current.isAwaitingSpawn()) return null;
            deliveredActor = actor;
            deliveredVersion = version;
            return plan;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading AI plan", interrupted);
        } catch (ExecutionException failed) {
            throw new IllegalStateException("AI strategy failed", failed.getCause());
        }
    }

    public synchronized boolean isThinking() { return pending != null && !pending.isDone(); }

    public synchronized void cancelPending() {
        if (pending != null) pending.cancel(true);
        pending = null;
        pendingActor = null;
    }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        cancelPending();
        worker.shutdownNow();
    }
}
