package kr.ac.jbnu.se.tetris.app;

import kr.ac.jbnu.se.tetris.core.GameAction;

/** 목표 순서, 수락된 이벤트, 종료 후 입력 차단을 실제 엔진으로 확인 */
public final class TutorialSessionTest {
    public static void main(String[] args) {
        TutorialSession session = new TutorialSession(42);
        try {
            session.submit(GameAction.Type.ROTATE_LEFT);
            check(session.getStep() == 0, "순서 밖 조작은 다음 목표를 생략하지 않음");
            session.pause();
            session.submit(GameAction.Type.MOVE_LEFT);
            check(session.getStep() == 0, "일시정지 중 조작은 달성으로 계산하지 않음");
            session.resume();
            session.submit(GameAction.Type.MOVE_LEFT);
            session.submit(GameAction.Type.ROTATE_RIGHT);
            session.submit(GameAction.Type.SOFT_DROP);
            session.submit(GameAction.Type.HOLD);
            check(session.getStep() == 4, "이동 회전 낙하 HOLD 네 목표 완료");
            session.submit(GameAction.Type.HARD_DROP);
            check(session.isCompleted() && session.isFinished(), "실제 고정 후 튜토리얼 완료");
            long version = session.getPlayerState().getVersion();
            session.tick(); session.resume(); session.submit(GameAction.Type.HARD_DROP);
            check(session.getPlayerState().getVersion() == version, "완료 결과 이후 상태 고정");
        } finally { session.close(); }
        TutorialSession closed = new TutorialSession(9);
        closed.close();
        long version = closed.getPlayerState().getVersion();
        closed.submit(GameAction.Type.MOVE_LEFT); closed.resume(); closed.tick();
        check(version == closed.getPlayerState().getVersion(), "종료된 세션 입력 무시");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
