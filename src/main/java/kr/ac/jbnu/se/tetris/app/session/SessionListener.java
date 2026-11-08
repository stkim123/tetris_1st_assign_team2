package kr.ac.jbnu.se.tetris.app.session;

/** 요청 ID 반환 이전에도 호출 가능한 직접 구독 콜백 */
public interface SessionListener {
    void onUpdate(SessionUpdate update);
}
