package kr.ac.jbnu.se.tetris.network;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.PlayerIntent;
import kr.ac.jbnu.se.tetris.support.FakeNetworkClient;

/** 접속 전 요청과 확정 결과 및 종료 시 대기 요청 검증 */
public final class NetworkContractTest {
    public static void main(String[] args) {
        FakeNetworkClient fake = new FakeNetworkClient();
        final List<NetworkUpdate> seen = new ArrayList<NetworkUpdate>();
        NetworkSubscription subscription = fake.subscribe(new NetworkListener() {
            @Override public void onUpdate(NetworkUpdate update) { seen.add(update); }
        });
        long early = fake.send(RoomCommand.createRoom(2));
        fake.drain();
        check(outcome(seen, early).getReasonCode().equals("NOT_CONNECTED")
                && fake.getSentRoomCommands().isEmpty(), "접속 전 방 생성 거절");

        long connect = fake.connect(new ConnectionOptions(URI.create("ws://127.0.0.1:8080/ws"),
                "fixture-token"));
        fake.emit(NetworkUpdate.connected());
        fake.complete(connect, true, null, null, null, null);
        fake.drain();
        check(outcome(seen, connect).isAccepted(), "접속 요청 결과 확인");
        long beforeMatch = fake.send(new PlayerIntent(GameAction.Type.MOVE_LEFT));
        fake.drain();
        check(outcome(seen, beforeMatch).getReasonCode().equals("MATCH_NOT_STARTED"),
                "경기 전 입력 거절");

        long create = fake.send(RoomCommand.createRoom(2));
        check(fake.getSentRoomCommands().size() == 1, "접속 후 방 생성 요청 전달");
        fake.complete(create, true, null, "room", null, null);
        fake.emit(NetworkUpdate.roomState(room(1)));
        fake.drain();
        final List<NetworkUpdate> replay = new ArrayList<NetworkUpdate>();
        NetworkSubscription late = fake.subscribe(new NetworkListener() {
            @Override public void onUpdate(NetworkUpdate update) { replay.add(update); }
        });
        check(replay.size() == 2 && replay.get(0).getType() == NetworkUpdate.Type.CONNECTED
                && replay.get(1).getRoomState().getRoomVersion() == 1,
                "늦은 구독자의 접속 및 현재 방 상태 재생");

        fake.emit(NetworkUpdate.matchStarted("room", 2, "match", "p"));
        fake.drain();
        long action = fake.send(new PlayerIntent(GameAction.Type.HARD_DROP));
        long snapshot = fake.requestSnapshot();
        check(fake.getSentIntents().size() == 1 && outcome(seen, action) == null,
                "송신과 확정 결과 분리");
        fake.close();
        check(outcome(seen, action).getReasonCode().equals("CLOSED")
                && outcome(seen, snapshot).getReasonCode().equals("CLOSED"),
                "종료 시 미완료 요청 거절");
        int count = replay.size();
        late.close();
        subscription.close();
        fake.close();
        check(replay.size() == count, "구독 해제 뒤 추가 콜백 없음");
        expectState(() -> fake.send(RoomCommand.getRoomState()));
    }

    private static RoomState room(long version) {
        Map<String, Boolean> ready = new LinkedHashMap<String, Boolean>();
        ready.put("p", true);
        return new RoomState("room", version, RoomState.Phase.WAITING, "p", ready);
    }

    private static RequestOutcome outcome(List<NetworkUpdate> updates, long requestId) {
        for (NetworkUpdate update : updates) {
            RequestOutcome outcome = update.getRequestOutcome();
            if (outcome != null && outcome.getRequestId() == requestId) return outcome;
        }
        return null;
    }

    private static void expectState(Runnable action) {
        try { action.run(); }
        catch (IllegalStateException expected) { return; }
        throw new AssertionError("종료된 클라이언트의 요청 거절 필요");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
