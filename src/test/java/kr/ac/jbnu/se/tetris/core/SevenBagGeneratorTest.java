package kr.ac.jbnu.se.tetris.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import kr.ac.jbnu.se.tetris.battle.BattleManager;
import kr.ac.jbnu.se.tetris.battle.BattleState;
import kr.ac.jbnu.se.tetris.battle.ParticipantSpec;
import kr.ac.jbnu.se.tetris.battle.ParticipantState;

/** 7-bag의 가방 규칙, 시드 재현, 엔진·전투 연결을 확인 */
public final class SevenBagGeneratorTest {
    public static void main(String[] args) {
        everyBagHasEachPieceOnce();
        spacingBetweenSamePiecesIsBounded();
        sameSeedRepeatsAndDifferentSeedDiffers();
        engineStartsWithFourDistinctPieces();
        battleParticipantsShareTheSameBags();
    }

    /** 일곱 개씩 끊은 모든 구간에 일곱 종류가 정확히 한 번씩 들어 있는지 확인 */
    private static void everyBagHasEachPieceOnce() {
        SevenBagGenerator generator = new SevenBagGenerator(7);
        for (int bag = 0; bag < 200; bag++) {
            Set<PieceType> seen = EnumSet.noneOf(PieceType.class);
            for (int i = 0; i < 7; i++) {
                PieceType type = generator.nextPiece();
                check(type != PieceType.EMPTY && type != PieceType.GARBAGE, "playable piece only");
                check(seen.add(type), "no repeat inside a bag: bag " + bag + " " + type);
            }
            check(seen.size() == 7, "bag has all seven pieces: bag " + bag);
        }
    }

    /** 같은 종류 사이의 간격이 최대 13, 연속 세 번 같은 블록이 나오지 않는지 확인 */
    private static void spacingBetweenSamePiecesIsBounded() {
        SevenBagGenerator generator = new SevenBagGenerator(2026);
        List<PieceType> sequence = take(generator, 7 * 300);
        for (PieceType type : PieceType.values()) {
            if (type == PieceType.EMPTY || type == PieceType.GARBAGE) continue;
            int previous = -1;
            for (int i = 0; i < sequence.size(); i++) {
                if (sequence.get(i) != type) continue;
                // 한 가방의 맨 앞과 다음 가방의 맨 뒤가 만나는 경우가 최대 간격 13
                if (previous >= 0) check(i - previous <= 13, "spacing at most 13: " + type);
                previous = i;
            }
        }
        for (int i = 2; i < sequence.size(); i++) {
            check(!(sequence.get(i) == sequence.get(i - 1) && sequence.get(i) == sequence.get(i - 2)),
                    "no triple repeat at " + i);
        }
    }

    /** 같은 시드는 같은 순서를 내고 다른 시드는 다른 순서를 내는지 확인 */
    private static void sameSeedRepeatsAndDifferentSeedDiffers() {
        List<PieceType> first = take(new SevenBagGenerator(42), 140);
        List<PieceType> second = take(new SevenBagGenerator(42), 140);
        List<PieceType> other = take(new SevenBagGenerator(43), 140);
        check(first.equals(second), "same seed repeats");
        check(!first.equals(other), "different seed differs");
    }

    /** 엔진은 시작할 때 현재 블록과 NEXT 3개를 한 번에 뽑으므로 서로 다른 네 종류여야 함 */
    private static void engineStartsWithFourDistinctPieces() {
        for (long seed = 1; seed <= 20; seed++) {
            GameEngine engine = new GameEngine(new SevenBagGenerator(seed));
            check(engine.dispatch(new GameAction(GameAction.Type.START, "local", 0)).isAccepted(), "start");
            GameState state = engine.getState();
            List<PieceType> opening = new ArrayList<PieceType>();
            opening.add(state.getActivePiece().getType());
            opening.addAll(state.getNextPieces());
            check(opening.size() == 4, "active plus three next");
            check(EnumSet.copyOf(opening).size() == 4, "opening four are distinct: seed " + seed);
        }
    }

    /** 실제 대전 경로는 7-bag을 쓰고 모든 참가자가 같은 블록 순서를 받는지 확인 */
    private static void battleParticipantsShareTheSameBags() {
        // 랜덤 생성기라면 네 개가 모두 다를 확률이 약 35%이므로 여러 시드로 반복해 우연한 통과를 방지
        for (long seed = 1; seed <= 20; seed++) {
            BattleManager battle = new BattleManager(Arrays.asList(
                    new ParticipantSpec("a", "A", 100), new ParticipantSpec("b", "B", 100)), seed);
            check(battle.start().isAccepted(), "battle starts");
            BattleState state = battle.getState();
            ParticipantState a = state.getParticipant("a");
            ParticipantState b = state.getParticipant("b");
            List<PieceType> openingA = opening(a.getGameState());
            List<PieceType> openingB = opening(b.getGameState());
            check(openingA.equals(openingB), "participants share the same sequence: seed " + seed);
            check(EnumSet.copyOf(openingA).size() == 4, "battle opening comes from one bag: seed " + seed);
        }
    }

    private static List<PieceType> opening(GameState state) {
        List<PieceType> result = new ArrayList<PieceType>();
        result.add(state.getActivePiece().getType());
        result.addAll(state.getNextPieces());
        return result;
    }

    private static List<PieceType> take(PieceGenerator generator, int count) {
        List<PieceType> result = new ArrayList<PieceType>();
        for (int i = 0; i < count; i++) result.add(generator.nextPiece());
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
