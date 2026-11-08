package kr.ac.jbnu.se.tetris.ai;

import kr.ac.jbnu.se.tetris.core.GameState;

/** 불변 상태 사본 기반 입력 계획 및 실제 엔진 상태 유지 */
public interface AIStrategy {
    AIPlan plan(GameState state);

    /** 요청 시점에 고정한 관측과 정책 상태 기반 판단 */
    default AIPlan plan(AIContext context) {
        if (context == null) throw new IllegalArgumentException("AI context is required");
        return plan(context.getGameState());
    }
}
