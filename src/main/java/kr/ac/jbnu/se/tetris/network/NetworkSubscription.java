package kr.ac.jbnu.se.tetris.network;

/** 클라이언트 이벤트 구독 해제 */
public interface NetworkSubscription extends AutoCloseable {
    @Override void close();
}
