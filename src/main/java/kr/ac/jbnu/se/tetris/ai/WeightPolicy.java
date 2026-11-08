package kr.ac.jbnu.se.tetris.ai;

/** 공통 휴리스틱에 사용할 가중치 선정 계약 */
public interface WeightPolicy {
    PolicyDecision decide(AIContext context, AIProfile profile);
}
