package kr.ac.jbnu.se.tetris.app.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import kr.ac.jbnu.se.tetris.app.MonsterSession;
import kr.ac.jbnu.se.tetris.battle.BattleResult;
import kr.ac.jbnu.se.tetris.battle.BattleState;
import kr.ac.jbnu.se.tetris.core.PlayerIntent;
import kr.ac.jbnu.se.tetris.ui.ScreenRouter;

/** 로컬 전투의 시계·AI 실행을 소유하는 EDT 전용 대전 어댑터 */
public final class LocalMatchSession implements MatchSession {
    private final MonsterSession engine;
    private final LongSupplier clock;
    private final Timer gravity;
    private final Timer aiPulse;
    private final List<ListenerSlot> listeners = new ArrayList<ListenerSlot>();
    private long requestId;
    private long lastGravityNanos;
    private boolean closed;
    private String failure;

    public LocalMatchSession(MonsterSession engine) { this(engine, System::nanoTime); }

    public LocalMatchSession(MonsterSession engine, LongSupplier clock) {
        ScreenRouter.requireEdt();
        if (engine == null || clock == null) throw new IllegalArgumentException("Engine and clock required");
        this.engine = engine; this.clock = clock;
        gravity = new Timer(50, event -> advanceTimed()); gravity.setCoalesce(true);
        aiPulse = new Timer(50, event -> advanceAi()); aiPulse.setCoalesce(true);
    }

    @Override public SessionSnapshot getSnapshot() {
        ScreenRouter.requireEdt();
        BattleState state = engine.getBattleState();
        SessionPhase phase = closed ? SessionPhase.CLOSED : failure != null ? SessionPhase.FAILED
                : state.getStatus() == BattleState.Status.FINISHED ? SessionPhase.FINISHED
                : state.getStatus() == BattleState.Status.PAUSED ? SessionPhase.PAUSED : SessionPhase.RUNNING;
        boolean live = phase == SessionPhase.RUNNING || phase == SessionPhase.PAUSED;
        return new SessionSnapshot(engine.getMatchId(), SessionMode.STORY_PVE, phase,
                engine.getLocalParticipantId(), engine.getMatchId(), closed || failure != null ? null : state,
                new SessionCapabilities(phase == SessionPhase.RUNNING, live, false, live));
    }

    @Override public long submit(PlayerIntent intent) {
        requireOpen();
        if (intent == null) throw new IllegalArgumentException("Intent required");
        long id = ++requestId;
        if (!getSnapshot().getCapabilities().canSubmit()) return reject(id, "SESSION_NOT_RUNNING");
        if (intent.getItemUse() == null) engine.submit(intent.getType());
        else engine.submitItem(intent.getItemUse());
        publishResult(id, engine.getLastBattleResult());
        return id;
    }

    @Override public long requestPause(boolean paused) {
        requireOpen(); long id = ++requestId;
        if (engine.isFinished() || failure != null) return reject(id, "SESSION_NOT_RUNNING");
        if (paused == engine.isPaused()) {
            deferOutcome(new CommandOutcome(id, true, null, engine.getMatchId(),
                    engine.getBattleState().getVersion()));
            return id;
        }
        if (paused) { engine.pause(); stopClock(); }
        else { engine.resume(); startClock(); }
        publishResult(id, engine.getLastBattleResult()); return id;
    }

    @Override public long leave() {
        requireOpen(); long id = ++requestId;
        if (engine.isFinished()) return reject(id, "SESSION_NOT_RUNNING");
        publishResult(id, engine.leave()); stopClock(); return id;
    }

    @Override public Subscription subscribe(SessionListener listener) {
        requireOpen();
        if (listener == null) throw new IllegalArgumentException("Listener required");
        final ListenerSlot slot = new ListenerSlot(listener); listeners.add(slot);
        listener.onUpdate(new SessionUpdate(getSnapshot(), Collections.emptyList(), null));
        return () -> { ScreenRouter.requireEdt(); slot.active = false; listeners.remove(slot); };
    }

    /** 로컬 세션만 호출 가능한 중력 진행 및 테스트 경계 */
    public void advanceGravity() {
        ScreenRouter.requireEdt();
        if (closed || failure != null || engine.isPaused() || engine.isFinished()) return;
        engine.tick(); publish(null, engine.getLastBattleResult());
    }

    private void advanceTimed() {
        ScreenRouter.requireEdt();
        if (closed || failure != null || engine.isPaused() || engine.isFinished()) return;
        long now = clock.getAsLong();
        long millis = Math.max(0, (now - lastGravityNanos) / 1_000_000L);
        if (millis == 0) return;
        lastGravityNanos += millis * 1_000_000L;
        advanceMillis(millis);
    }

    /** 검증과 서버 재생에서 같은 가상 시간을 전달하는 경계. */
    public void advanceMillis(long elapsedMillis) {
        ScreenRouter.requireEdt();
        if (elapsedMillis < 0) throw new IllegalArgumentException("Elapsed time must be non-negative");
        if (closed || failure != null || engine.isPaused() || engine.isFinished()) return;
        long remaining = elapsedMillis;
        while (remaining > 0 && !engine.isFinished()) {
            long step = Math.min(600000, remaining);
            engine.advance(step);
            publish(null, engine.getLastBattleResult());
            remaining -= step;
        }
    }

    public void advanceAi() {
        ScreenRouter.requireEdt();
        if (closed || failure != null || engine.isPaused() || engine.isFinished()) return;
        BattleResult previous = engine.getLastBattleResult();
        try {
            engine.pulse(clock.getAsLong());
            if (engine.getLastBattleResult() != previous) publish(null, engine.getLastBattleResult());
        } catch (RuntimeException error) {
            failure = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            engine.pause(); stopClock(); publish(null, null);
        }
    }

    public void startClock() {
        ScreenRouter.requireEdt();
        if (!closed && failure == null && !engine.isPaused() && !engine.isFinished()) {
            if (!gravity.isRunning()) lastGravityNanos = clock.getAsLong();
            gravity.start(); aiPulse.start();
        }
    }
    public void stopClock() { ScreenRouter.requireEdt(); gravity.stop(); aiPulse.stop(); }
    public boolean isGravityRunning() { return gravity.isRunning(); }
    public boolean isAiTimerRunning() { return aiPulse.isRunning(); }
    public boolean isThinking() { return engine.isThinking(); }
    public String getFailure() { return failure; }
    public BattleResult getLastBattleResult() { return engine.getLastBattleResult(); }

    private long reject(long id, String reason) {
        deferOutcome(new CommandOutcome(id, false, reason, engine.getMatchId(), engine.getBattleState().getVersion()));
        return id;
    }
    private void publishResult(long id, BattleResult result) {
        String reason = result.getReason();
        if (!result.isAccepted() && (reason == null || !reason.matches("[A-Z_]+"))) reason = "ACTION_REJECTED";
        publish(null, result);
        deferOutcome(new CommandOutcome(id, result.isAccepted(), result.isAccepted() ? null : reason,
                engine.getMatchId(), result.getState().getVersion()));
    }
    /** 요청 ID 반환 후 결과 통지 및 늦은 결과에 최신 표시 상태 동봉 */
    private void deferOutcome(CommandOutcome outcome) {
        SwingUtilities.invokeLater(() -> { if (!closed) publish(outcome, null); });
    }
    private void publish(CommandOutcome outcome, BattleResult result) {
        if (engine.isFinished()) stopClock();
        SessionUpdate update = new SessionUpdate(getSnapshot(), result == null
                ? Collections.emptyList() : result.getEvents(), outcome);
        for (ListenerSlot slot : new ArrayList<ListenerSlot>(listeners)) {
            if (slot.active && !closed) slot.listener.onUpdate(update);
        }
    }
    private void requireOpen() {
        ScreenRouter.requireEdt();
        if (closed) throw new IllegalStateException("CLOSED");
    }
    @Override public void close() {
        ScreenRouter.requireEdt();
        if (closed) return;
        closed = true; stopClock(); engine.close();
        for (ListenerSlot slot : listeners) slot.active = false;
        listeners.clear();
    }
    private static final class ListenerSlot {
        private final SessionListener listener;
        private boolean active = true;
        private ListenerSlot(SessionListener listener) { this.listener = listener; }
    }
}
