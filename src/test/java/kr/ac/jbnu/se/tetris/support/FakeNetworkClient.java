package kr.ac.jbnu.se.tetris.support;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import kr.ac.jbnu.se.tetris.core.PlayerIntent;
import kr.ac.jbnu.se.tetris.network.ConnectionOptions;
import kr.ac.jbnu.se.tetris.network.NetworkClient;
import kr.ac.jbnu.se.tetris.network.NetworkListener;
import kr.ac.jbnu.se.tetris.network.NetworkSubscription;
import kr.ac.jbnu.se.tetris.network.NetworkUpdate;
import kr.ac.jbnu.se.tetris.network.RequestOutcome;
import kr.ac.jbnu.se.tetris.network.RoomCommand;
import kr.ac.jbnu.se.tetris.network.RoomState;

/** 서버 없이 수신 순서와 요청 결과를 제어하는 테스트 클라이언트 */
public final class FakeNetworkClient implements NetworkClient {
    private final List<Observer> observers = new ArrayList<Observer>();
    private final Queue<NetworkUpdate> queued = new ArrayDeque<NetworkUpdate>();
    private final Map<Long, String> pending = new LinkedHashMap<Long, String>();
    private final List<RoomCommand> sentRoomCommands = new ArrayList<RoomCommand>();
    private final List<PlayerIntent> sentIntents = new ArrayList<PlayerIntent>();
    private long nextRequestId;
    private boolean closed;
    private boolean draining;
    private boolean connected;
    private NetworkUpdate lastConnection;
    private RoomState lastRoomState;
    private String currentMatchId;

    @Override public synchronized long connect(ConnectionOptions options) {
        if (options == null) throw new IllegalArgumentException("Connection options are required");
        long id = request("CONNECT");
        if (connected) reject(id, "ALREADY_CONNECTED");
        return id;
    }

    @Override public synchronized long send(RoomCommand command) {
        if (command == null) throw new IllegalArgumentException("Room command is required");
        long id = request("ROOM");
        if (!connected) reject(id, "NOT_CONNECTED");
        else if (lastRoomState == null && command.getType() != RoomCommand.Type.CREATE_ROOM
                && command.getType() != RoomCommand.Type.JOIN_ROOM) reject(id, "ROOM_NOT_JOINED");
        else sentRoomCommands.add(command);
        return id;
    }

    @Override public synchronized long send(PlayerIntent intent) {
        if (intent == null) throw new IllegalArgumentException("Player intent is required");
        long id = request("ACTION");
        if (!connected) reject(id, "NOT_CONNECTED");
        else if (currentMatchId == null) reject(id, "MATCH_NOT_STARTED");
        else sentIntents.add(intent);
        return id;
    }

    @Override public synchronized long requestSnapshot() {
        long id = request("SNAPSHOT");
        if (!connected) reject(id, "NOT_CONNECTED");
        else if (currentMatchId == null) reject(id, "MATCH_NOT_STARTED");
        return id;
    }

    @Override public NetworkSubscription subscribe(NetworkListener listener) {
        if (listener == null) throw new IllegalArgumentException("Network listener is required");
        final Observer observer = new Observer(listener);
        synchronized (this) {
            requireOpen();
            observers.add(observer);
            if (lastConnection != null) observer.enqueue(lastConnection);
            if (lastRoomState != null) observer.enqueue(NetworkUpdate.roomState(lastRoomState));
        }
        observer.drain();
        return new NetworkSubscription() {
            @Override public void close() {
                synchronized (FakeNetworkClient.this) {
                    observers.remove(observer);
                }
                observer.close();
            }
        };
    }

    /** 다음 drain 때 하나의 서버 메시지 전달 */
    public synchronized void emit(NetworkUpdate update) {
        requireOpen();
        if (update == null) throw new IllegalArgumentException("Network update is required");
        queued.add(update);
    }

    /** FIFO 메시지 처리 및 구독자 콜백의 결정적 실행 */
    public void drain() {
        synchronized (this) {
            if (draining) return;
            draining = true;
        }
        try {
            while (true) {
                NetworkUpdate update;
                List<Observer> current;
                synchronized (this) {
                    update = queued.poll();
                    if (update == null) {
                        draining = false;
                        return;
                    }
                    if (update.getType() == NetworkUpdate.Type.CONNECTED) connected = true;
                    if (update.getType() == NetworkUpdate.Type.CONNECTION_FAILED
                            || update.getType() == NetworkUpdate.Type.CLOSED) connected = false;
                    if (update.getType() == NetworkUpdate.Type.CONNECTED
                            || update.getType() == NetworkUpdate.Type.CONNECTION_FAILED
                            || update.getType() == NetworkUpdate.Type.CLOSED) lastConnection = update;
                    if (update.getType() == NetworkUpdate.Type.ROOM_STATE) {
                        lastRoomState = update.getRoomState();
                        if (lastRoomState.getPhase() == RoomState.Phase.CLOSED) currentMatchId = null;
                    }
                    if (update.getType() == NetworkUpdate.Type.MATCH_STARTED) {
                        currentMatchId = update.getMatchId();
                    }
                    if (update.getType() == NetworkUpdate.Type.REQUEST_OUTCOME) {
                        pending.remove(update.getRequestOutcome().getRequestId());
                    }
                    current = new ArrayList<Observer>(observers);
                    for (Observer observer : current) observer.enqueue(update);
                }
                for (Observer observer : current) observer.drain();
                if (closed && update.getType() == NetworkUpdate.Type.CLOSED) {
                    synchronized (this) { observers.clear(); }
                    for (Observer observer : current) observer.close();
                }
            }
        } finally {
            synchronized (this) { draining = false; }
        }
    }

    public synchronized void complete(long requestId, boolean accepted, String reasonCode,
                                      String roomId, String matchId, Long battleVersion) {
        requireOpen();
        if (!pending.containsKey(requestId)) throw new IllegalArgumentException("Unknown request ID");
        emit(NetworkUpdate.requestOutcome(new RequestOutcome(requestId, accepted, reasonCode,
                roomId, matchId, battleVersion)));
    }

    public synchronized List<RoomCommand> getSentRoomCommands() {
        return Collections.unmodifiableList(new ArrayList<RoomCommand>(sentRoomCommands));
    }

    public synchronized List<PlayerIntent> getSentIntents() {
        return Collections.unmodifiableList(new ArrayList<PlayerIntent>(sentIntents));
    }

    public synchronized long getLastRequestId() { return nextRequestId; }

    @Override public void close() {
        synchronized (this) {
            if (closed) return;
            queued.clear();
            for (Long id : new ArrayList<Long>(pending.keySet())) {
                queued.add(NetworkUpdate.requestOutcome(new RequestOutcome(id, false, "CLOSED",
                        lastRoomState == null ? null : lastRoomState.getRoomId(), null, null)));
            }
            queued.add(NetworkUpdate.closed("CLOSED"));
            closed = true;
        }
        drain();
    }

    private long request(String kind) {
        requireOpen();
        long id = ++nextRequestId;
        pending.put(id, kind);
        return id;
    }

    private void reject(long requestId, String reason) {
        queued.add(NetworkUpdate.requestOutcome(new RequestOutcome(requestId, false, reason,
                lastRoomState == null ? null : lastRoomState.getRoomId(), currentMatchId, null)));
    }

    private void requireOpen() { if (closed) throw new IllegalStateException("CLOSED"); }

    private static final class Observer {
        private final NetworkListener listener;
        private final Queue<NetworkUpdate> messages = new ArrayDeque<NetworkUpdate>();
        private boolean active = true;
        private boolean draining;
        private Observer(NetworkListener listener) { this.listener = listener; }
        private synchronized void enqueue(NetworkUpdate update) {
            if (active) messages.add(update);
        }
        private void drain() {
            synchronized (this) {
                if (!active || draining) return;
                draining = true;
            }
            try {
                while (true) {
                    NetworkUpdate update;
                    synchronized (this) {
                        if (!active) { messages.clear(); draining = false; return; }
                        update = messages.poll();
                        if (update == null) { draining = false; return; }
                    }
                    listener.onUpdate(update);
                }
            } finally {
                synchronized (this) { draining = false; }
            }
        }
        private synchronized void close() {
            active = false;
            messages.clear();
        }
    }
}
