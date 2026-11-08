package kr.ac.jbnu.se.tetris.app.session;

/** 갱신 구독의 반복 호출 가능한 해제 경계 */
public interface Subscription extends AutoCloseable {
    @Override void close();
}
