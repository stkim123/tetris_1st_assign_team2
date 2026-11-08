package kr.ac.jbnu.se.tetris.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import kr.ac.jbnu.se.tetris.core.GameAction;

/** 단일 게임 상태 버전용 불변 AI 입력 계획 */
public final class AIPlan {
    private final long sourceVersion;
    private final List<GameAction.Type> actions;
    private final int candidateCount;
    private final long elapsedNanos;
    private final double score;
    private final boolean timedOut;
    private final PolicyDecision policyDecision;

    public AIPlan(long sourceVersion, List<GameAction.Type> actions, int candidateCount,
                  long elapsedNanos, double score, boolean timedOut) {
        this(sourceVersion, actions, candidateCount, elapsedNanos, score, timedOut, null);
    }

    public AIPlan(long sourceVersion, List<GameAction.Type> actions, int candidateCount,
                  long elapsedNanos, double score, boolean timedOut, PolicyDecision policyDecision) {
        if (actions == null || candidateCount < 0 || elapsedNanos < 0 || Double.isNaN(score)) {
            throw new IllegalArgumentException("Invalid AI plan");
        }
        if (policyDecision != null && policyDecision.getSourceVersion() != sourceVersion) {
            throw new IllegalArgumentException("Policy decision version mismatch");
        }
        for (GameAction.Type action : actions) if (action == null) {
            throw new IllegalArgumentException("AI plan has null action");
        }
        this.sourceVersion = sourceVersion;
        this.actions = Collections.unmodifiableList(new ArrayList<GameAction.Type>(actions));
        this.candidateCount = candidateCount;
        this.elapsedNanos = elapsedNanos;
        this.score = score;
        this.timedOut = timedOut;
        this.policyDecision = policyDecision;
    }

    public long getSourceVersion() { return sourceVersion; }
    public List<GameAction.Type> getActions() { return actions; }
    public int getCandidateCount() { return candidateCount; }
    public long getElapsedNanos() { return elapsedNanos; }
    public double getScore() { return score; }
    public boolean isTimedOut() { return timedOut; }
    public PolicyDecision getPolicyDecision() { return policyDecision; }
}
