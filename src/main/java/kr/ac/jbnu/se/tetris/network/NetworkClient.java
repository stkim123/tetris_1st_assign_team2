package kr.ac.jbnu.se.tetris.network;

import kr.ac.jbnu.se.tetris.core.PlayerIntent;

/** 연결, 방 명령, 입력 송신, 확정 상태 구독의 비차단 계약 */
public interface NetworkClient extends AutoCloseable {
    long connect(ConnectionOptions options);
    long send(RoomCommand command);
    long send(PlayerIntent intent);
    long requestSnapshot();
    default long refreshAuthentication(String accessToken) {
        throw new UnsupportedOperationException("Authentication refresh is unavailable on this transport");
    }
    NetworkSubscription subscribe(NetworkListener listener);
    @Override void close();
}
