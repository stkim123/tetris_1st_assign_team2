package kr.ac.jbnu.se.tetris.core;

import java.util.Random;

/** 일곱 종류를 한 번씩 섞은 가방을 순서대로 비우는 7-bag 블록 공급, 시드가 같으면 같은 순서 재현 */
public final class SevenBagGenerator implements PieceGenerator {
    private static final PieceType[] PLAYABLE = {
        PieceType.Z, PieceType.S, PieceType.I, PieceType.T,
        PieceType.O, PieceType.L, PieceType.J
    };
    private final Random random;
    private final PieceType[] bag = new PieceType[PLAYABLE.length];
    // 다음에 꺼낼 가방 위치, 처음에는 가방이 비어 있는 상태로 시작해 첫 호출에서 채움
    private int next = PLAYABLE.length;

    public SevenBagGenerator(long seed) {
        this.random = new Random(seed);
    }

    /** 한 가방의 일곱 개를 모두 꺼내기 전에는 같은 종류가 다시 나오지 않음 */
    public synchronized PieceType nextPiece() {
        if (next == bag.length) refill();
        return bag[next++];
    }

    private void refill() {
        System.arraycopy(PLAYABLE, 0, bag, 0, PLAYABLE.length);
        // Fisher-Yates 섞기, 모든 순열이 같은 확률
        for (int i = bag.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            PieceType swapped = bag[i];
            bag[i] = bag[j];
            bag[j] = swapped;
        }
        next = 0;
    }
}
