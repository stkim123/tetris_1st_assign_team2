package kr.ac.jbnu.se.tetris.app.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import kr.ac.jbnu.se.tetris.battle.BattleSnapshots;
import kr.ac.jbnu.se.tetris.battle.BattleEvent;
import kr.ac.jbnu.se.tetris.battle.BattleState;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.PlayerIntent;
import kr.ac.jbnu.se.tetris.network.NetworkUpdate;
import kr.ac.jbnu.se.tetris.network.RoomCommand;
import kr.ac.jbnu.se.tetris.network.RoomState;
import kr.ac.jbnu.se.tetris.support.FakeNetworkClient;
import kr.ac.jbnu.se.tetris.support.SampleSnapshots;

/** 요청 수락 구분과 경기 교체 뒤 지연 메시지 차단 검증 */
public final class OnlineMatchSessionTest {
    public static void main(String[] args) {
        inputContract();
        onlineLifecycle();
        oreEventsFollowSnapshotAndDeduplicate();
        failedSessionIgnoresLateMessages();
        concurrentCallbacksDoNotDeadlock(false);
        concurrentCallbacksDoNotDeadlock(true);
    }

    private static void oreEventsFollowSnapshotAndDeduplicate() {
        FakeNetworkClient fake = new FakeNetworkClient();
        OnlineMatchSession session = new OnlineMatchSession("ore-session", fake);
        List<SessionUpdate> observed = new ArrayList<SessionUpdate>();
        session.subscribe(observed::add);
        fake.emit(NetworkUpdate.connected());
        fake.emit(NetworkUpdate.roomState(room(1)));
        fake.emit(NetworkUpdate.matchStarted("room", 2, "match-ore", "p"));
        fake.emit(NetworkUpdate.snapshot("room", "match-ore", runningVersionZero()));
        BattleState initial = runningVersionZero();
        BattleState afterMining = BattleSnapshots.battle(BattleState.Status.RUNNING, 1,
                initial.getParticipants(), null, null);
        BattleEvent pickup = BattleEvent.fromWire(BattleEvent.Type.ITEM_ACQUIRED, 18,
                "p", "p", 1, "heal", null, 3, 5, 0);
        fake.emit(NetworkUpdate.snapshot("room", "match-ore", afterMining));
        fake.emit(NetworkUpdate.events("room", "match-ore", Arrays.asList(pickup)));
        fake.emit(NetworkUpdate.events("room", "match-ore", Arrays.asList(pickup)));
        fake.emit(NetworkUpdate.events("room", "stale-match", Arrays.asList(
                BattleEvent.fromWire(BattleEvent.Type.ITEM_ACQUIRED, 19,
                        "p", "p", 1, "shield", null, 4, 6, 1))));
        fake.drain();
        int deliveries = 0;
        for (SessionUpdate update : observed) if (!update.getEvents().isEmpty()) {
            deliveries++;
            check(update.getSnapshot().getBattleState().getVersion() == 1
                    && update.getEvents().get(0).getItemSourceX() == 3,
                    "Pickup is delivered after its authoritative snapshot");
        }
        check(deliveries == 1, "Repeated or stale item event is ignored");
        session.close();
    }

    private static void inputContract() {
        expectInvalid(() -> new PlayerIntent(GameAction.Type.GRAVITY_TICK));
        expectInvalid(() -> new PlayerIntent(GameAction.Type.USE_ITEM));
        expectInvalid(() -> new PlayerIntent(GameAction.Type.MOVE_LEFT,
                new GameAction.ItemUse("item", "m")));
        check(new PlayerIntent(GameAction.Type.USE_ITEM,
                new GameAction.ItemUse("item", "m")).getItemUse() != null,
                "아이템 payload 보존");
    }

    private static void onlineLifecycle() {
        FakeNetworkClient fake = new FakeNetworkClient();
        OnlineMatchSession session = new OnlineMatchSession("session-1", fake);
        final List<SessionUpdate> updates = new ArrayList<SessionUpdate>();
        Subscription subscription = session.subscribe(new SessionListener() {
            @Override public void onUpdate(SessionUpdate update) { updates.add(update); }
        });
        check(updates.get(0).getSnapshot().getPhase() == SessionPhase.CONNECTING,
                "구독 시 초기 상태 전달");
        long earlyInput = session.submit(new PlayerIntent(GameAction.Type.MOVE_LEFT));
        check(findOutcome(updates, earlyInput).getReasonCode().equals("SESSION_NOT_RUNNING")
                && fake.getSentIntents().isEmpty(), "경기 전 입력은 송신 없이 거절");

        fake.emit(NetworkUpdate.connected());
        fake.emit(NetworkUpdate.roomState(room(1)));
        fake.emit(NetworkUpdate.matchStarted("room", 2, "match-a", "p"));
        fake.emit(NetworkUpdate.snapshot("room", "match-a", runningVersionZero()));
        fake.drain();
        check(session.getSnapshot().getPhase() == SessionPhase.RUNNING
                && session.getSnapshot().getBattleState().getVersion() == 0,
                "최초 version 0 Snapshot 채택");

        long move = session.submit(new PlayerIntent(GameAction.Type.MOVE_RIGHT));
        check(findOutcome(updates, move) == null && fake.getSentIntents().size() == 1,
                "송신만으로 입력 성공 처리 금지");
        fake.complete(fake.getLastRequestId(), true, null, "room", "match-a", 0L);
        fake.drain();
        check(findOutcome(updates, move).isAccepted(), "서버 확정 결과와 로컬 요청 연결");
        long item = session.submit(new PlayerIntent(GameAction.Type.USE_ITEM,
                new GameAction.ItemUse("heal", "p")));
        check(findOutcome(updates, item) == null && fake.getSentIntents().size() == 2,
                "아이템은 보유 여부를 서버에서 검증하도록 실제 송신");
        fake.complete(fake.getLastRequestId(), false, "ITEM_NOT_OWNED", "room", "match-a", 0L);
        fake.drain();
        check(!findOutcome(updates, item).isAccepted()
                && "ITEM_NOT_OWNED".equals(findOutcome(updates, item).getReasonCode()),
                "아이템도 서버 확정 거절과 로컬 요청 연결");
        long pause = session.requestPause(true);
        check(findOutcome(updates, pause).getReasonCode().equals("PAUSE_NOT_ALLOWED"),
                "온라인 일시정지 거절");

        fake.emit(NetworkUpdate.roomState(room(3)));
        fake.emit(NetworkUpdate.matchStarted("room", 4, "match-b", "p"));
        fake.emit(NetworkUpdate.snapshot("room", "match-a", finished("p")));
        fake.emit(NetworkUpdate.matchStarted("room", 2, "match-a", "p"));
        fake.emit(NetworkUpdate.snapshot("room", "match-b", runningVersionZero()));
        fake.drain();
        check(session.getSnapshot().getMatchId().equals("match-b")
                && session.getSnapshot().getBattleState().getVersion() == 0,
                "새 경기 v0 채택과 지연된 이전 경기 메시지 폐기");

        long leave = session.leave();
        List<RoomCommand> roomCommands = fake.getSentRoomCommands();
        check(roomCommands.size() == 1
                && roomCommands.get(0).getType() == RoomCommand.Type.LEAVE_ROOM,
                "방 나가기 요청 연결");
        check(findOutcome(updates, leave) == null, "방 나가기도 확정 결과 대기");
        session.close();
        check(session.getSnapshot().getPhase() == SessionPhase.CLOSED
                && findOutcome(updates, leave).getReasonCode().equals("CLOSED"),
                "종료 시 대기 요청 정리");
        int count = updates.size();
        subscription.close();
        check(updates.size() == count, "구독 해제 이후 콜백 없음");
        expectState(() -> session.submit(new PlayerIntent(GameAction.Type.MOVE_LEFT)));
        session.close();
    }

    private static void concurrentCallbacksDoNotDeadlock(boolean closing) {
        final FakeNetworkClient fake = new FakeNetworkClient();
        final OnlineMatchSession session = new OnlineMatchSession("concurrent", fake);
        fake.emit(NetworkUpdate.connected());
        fake.emit(NetworkUpdate.roomState(room(1)));
        fake.emit(NetworkUpdate.matchStarted("room", 2, "concurrent-match", "p"));
        fake.emit(NetworkUpdate.snapshot("room", "concurrent-match", runningVersionZero()));
        fake.drain();
        final CountDownLatch callbackEntered = new CountDownLatch(1);
        final CountDownLatch callbackRelease = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        fake.subscribe(new kr.ac.jbnu.se.tetris.network.NetworkListener() {
            @Override public void onUpdate(NetworkUpdate update) {
                if (update.getType() != NetworkUpdate.Type.ROOM_STATE
                        || update.getRoomVersion() != 3) return;
                callbackEntered.countDown();
                try {
                    if (!callbackRelease.await(3, TimeUnit.SECONDS)) {
                        throw new AssertionError("Blocked network callback timed out");
                    }
                    session.getSnapshot();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    failure.set(error);
                }
            }
        });
        fake.emit(NetworkUpdate.roomState(room(3)));
        Thread receiver = new Thread(new Runnable() {
            @Override public void run() {
                try { fake.drain(); }
                catch (Throwable error) { failure.set(error); }
            }
        }, "test-network-receiver");
        receiver.setDaemon(true);
        receiver.start();
        try {
            check(callbackEntered.await(2, TimeUnit.SECONDS), "수신 콜백 진입");
            Thread requester = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        if (closing) session.close();
                        else session.submit(new PlayerIntent(GameAction.Type.MOVE_LEFT));
                    } catch (Throwable error) { failure.set(error); }
                }
            }, "test-session-requester");
            requester.setDaemon(true);
            requester.start();
            requester.join(2000);
            check(!requester.isAlive(), "수신 콜백과 요청 사이 교착 없음");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        } finally {
            callbackRelease.countDown();
        }
        try {
            receiver.join(2000);
            check(!receiver.isAlive(), "수신 콜백 완료");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
        if (failure.get() != null) throw new AssertionError(failure.get());
        session.close();
    }

    private static void failedSessionIgnoresLateMessages() {
        FakeNetworkClient fake = new FakeNetworkClient();
        OnlineMatchSession session = new OnlineMatchSession("failed-session", fake);
        final List<SessionUpdate> updates = new ArrayList<SessionUpdate>();
        session.subscribe(new SessionListener() {
            @Override public void onUpdate(SessionUpdate update) { updates.add(update); }
        });
        fake.emit(NetworkUpdate.connected());
        fake.emit(NetworkUpdate.roomState(room(1)));
        fake.emit(NetworkUpdate.matchStarted("room", 2, "old-match", "p"));
        fake.emit(NetworkUpdate.snapshot("room", "old-match", runningVersionZero()));
        fake.drain();
        long pending = session.submit(new PlayerIntent(GameAction.Type.MOVE_RIGHT));
        fake.emit(NetworkUpdate.closed("CONNECTION_LOST"));
        fake.drain();
        check(session.getSnapshot().getPhase() == SessionPhase.FAILED
                && session.getSnapshot().getBattleState() == null
                && findOutcome(updates, pending).getReasonCode().equals("CONNECTION_LOST"),
                "연결 종료 뒤 미완료 요청 실패와 FAILED 유지");
        int count = updates.size();
        fake.emit(NetworkUpdate.snapshot("room", "old-match", runningVersionZero()));
        fake.emit(NetworkUpdate.connected());
        fake.emit(NetworkUpdate.roomState(room(3)));
        fake.emit(NetworkUpdate.matchStarted("room", 4, "new-match", "p"));
        fake.emit(NetworkUpdate.snapshot("room", "new-match", runningVersionZero()));
        fake.drain();
        check(updates.size() == count && session.getSnapshot().getPhase() == SessionPhase.FAILED
                && session.getSnapshot().getBattleState() == null
                && session.getSnapshot().getMatchId().equals("old-match"),
                "FAILED 뒤 이전 및 새 경기 지연 수신 폐기");
        session.close();
    }

    private static BattleState runningVersionZero() {
        BattleState started = SampleSnapshots.running("p", "m");
        return BattleSnapshots.battle(BattleState.Status.RUNNING, 0,
                started.getParticipants(), null, null);
    }

    private static BattleState finished(String winnerId) {
        return SampleSnapshots.finished("p", "m", winnerId);
    }

    private static RoomState room(long version) {
        Map<String, Boolean> ready = new LinkedHashMap<String, Boolean>();
        ready.put("p", true);
        ready.put("m", true);
        return new RoomState("room", version, RoomState.Phase.IN_MATCH, "p", ready);
    }

    private static CommandOutcome findOutcome(List<SessionUpdate> updates, long id) {
        for (SessionUpdate update : updates) {
            if (update.getOutcome() != null && update.getOutcome().getRequestId() == id) {
                return update.getOutcome();
            }
        }
        return null;
    }

    private static void expectInvalid(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("잘못된 입력을 거부해야 한다");
    }

    private static void expectState(Runnable action) {
        try { action.run(); }
        catch (IllegalStateException expected) { return; }
        throw new AssertionError("종료된 세션의 신규 요청을 거부해야 한다");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
