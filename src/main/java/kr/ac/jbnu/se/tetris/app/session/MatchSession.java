package kr.ac.jbnu.se.tetris.app.session;

import kr.ac.jbnu.se.tetris.core.PlayerIntent;

/**
 * 두 대전 모드의 입력, 표시, 종료 계약
 * 직접 구독 시 요청 ID 반환 이전의 결과 콜백 가능성
 * UI 요청 ID 등록에는 SessionUiBinding의 EDT 전달 사용
 */
public interface MatchSession extends AutoCloseable {
    SessionSnapshot getSnapshot();
    long submit(PlayerIntent intent);
    long requestPause(boolean paused);
    long leave();
    Subscription subscribe(SessionListener listener);
    @Override void close();
}
