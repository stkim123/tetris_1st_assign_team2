package kr.ac.jbnu.se.tetris.core;

import java.util.Random;

/** 시드 기반 기존 균등 분포 재현 및 Board와 난수 상태 소유권 분리 */
public final class SeededPieceGenerator implements PieceGenerator {
    private static final PieceType[] PLAYABLE = {
        PieceType.Z, PieceType.S, PieceType.I, PieceType.T,
        PieceType.O, PieceType.L, PieceType.J
    };
    private final Random random;

    public SeededPieceGenerator(long seed) {
        this.random = new Random(seed);
    }

    public PieceType nextPiece() {
        return PLAYABLE[random.nextInt(PLAYABLE.length)];
    }
}
