package kr.ac.jbnu.se.tetris.core;

/** 시드 기반 재현 가능한 순서를 지원하는 엔진 세션의 블록 공급 계약 */
public interface PieceGenerator {
    PieceType nextPiece();
}
