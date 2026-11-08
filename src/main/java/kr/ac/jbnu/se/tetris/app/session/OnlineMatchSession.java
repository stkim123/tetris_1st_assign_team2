package kr.ac.jbnu.se.tetris.app.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ac.jbnu.se.tetris.battle.BattleEvent;
import kr.ac.jbnu.se.tetris.battle.BattleState;
import kr.ac.jbnu.se.tetris.core.PlayerIntent;
import kr.ac.jbnu.se.tetris.network.NetworkClient;
import kr.ac.jbnu.se.tetris.network.NetworkListener;
import kr.ac.jbnu.se.tetris.network.NetworkSubscription;
import kr.ac.jbnu.se.tetris.network.NetworkUpdate;
import kr.ac.jbnu.se.tetris.network.RequestOutcome;
import kr.ac.jbnu.se.tetris.network.RoomCommand;
import kr.ac.jbnu.se.tetris.network.RoomState;
import kr.ac.jbnu.se.tetris.network.WebSocketNetworkClient;

/** 서버 확정 상태만 화면으로 전달하는 온라인 대전 어댑터 */
public final class OnlineMatchSession implements MatchSession {
    private final String sessionId;
    private final NetworkClient network;
    private final NetworkSubscription networkSubscription;
    private final List<Observer> observers = new ArrayList<Observer>();
    private final Map<Long, Long> localByNetworkRequest = new LinkedHashMap<Long, Long>();
    private final Map<Long, RequestOutcome> earlyOutcomes = new LinkedHashMap<Long, RequestOutcome>();
    private long nextLocalRequestId;
    private long adoptedStartVersion = -1;
    private long lastEventId;
    private boolean sending;
    private boolean closed;
    private SessionPhase phase = SessionPhase.CONNECTING;
    private RoomState roomState;
    private String matchId;
    private BattleState battleState;

    public OnlineMatchSession(String sessionId, NetworkClient network) {
        if (sessionId == null || sessionId.trim().isEmpty() || network == null) {
            throw new IllegalArgumentException("Session ID and network client are required");
        }
        this.sessionId = sessionId;
        this.network = network;
        this.networkSubscription = network.subscribe(new NetworkListener() {
            @Override public void onUpdate(NetworkUpdate update) { receive(update); }
        });
    }

    @Override public synchronized SessionSnapshot getSnapshot() { return snapshot(); }

    @Override public synchronized long submit(PlayerIntent intent) {
        requireOpen();
        if (intent == null) throw new IllegalArgumentException("Player intent is required");
        long localId = ++nextLocalRequestId;
        if (phase != SessionPhase.RUNNING) {
            reject(localId, "SESSION_NOT_RUNNING");
        } else if (network instanceof WebSocketNetworkClient && intent.getItemUse() != null) {
            // 배포된 공식 PvP 서버는 아직 아이템 입력을 받지 않는다.
            reject(localId, "ITEM_NOT_IMPLEMENTED");
        } else {
            send(localId, intent);
        }
        return localId;
    }

    @Override public synchronized long requestPause(boolean paused) {
        requireOpen();
        long localId = ++nextLocalRequestId;
        reject(localId, "PAUSE_NOT_ALLOWED");
        return localId;
    }

    @Override public synchronized long leave() {
        requireOpen();
        long localId = ++nextLocalRequestId;
        if (roomState == null || roomState.getPhase() == RoomState.Phase.CLOSED) {
            reject(localId, "ROOM_NOT_JOINED");
        } else {
            send(localId, RoomCommand.leaveRoom());
        }
        return localId;
    }

    @Override public synchronized Subscription subscribe(SessionListener listener) {
        requireOpen();
        if (listener == null) throw new IllegalArgumentException("Session listener is required");
        final Observer observer = new Observer(listener);
        observers.add(observer);
        observer.deliver(new SessionUpdate(snapshot(), Collections.<BattleEvent>emptyList(), null));
        return new Subscription() {
            @Override public void close() {
                synchronized (OnlineMatchSession.this) {
                    observer.active = false;
                    observers.remove(observer);
                }
            }
        };
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        phase = SessionPhase.CLOSED;
        battleState = null;
        failPending("CLOSED");
        emit(Collections.<BattleEvent>emptyList(), null);
        networkSubscription.close();
        network.close();
        for (Observer observer : observers) observer.active = false;
        observers.clear();
    }

    private synchronized void receive(NetworkUpdate update) {
        if (closed || update == null) return;
        // G0 재접속 미지원에 따른 FAILED 이후 수신 상태 고정
        if (phase == SessionPhase.FAILED) return;
        switch (update.getType()) {
            case CONNECTED:
                if (phase == SessionPhase.CONNECTING) phase = SessionPhase.WAITING;
                emit(Collections.<BattleEvent>emptyList(), null);
                break;
            case CONNECTION_FAILED:
            case ERROR:
            case CLOSED:
                phase = SessionPhase.FAILED;
                battleState = null;
                failPending(update.getReasonCode());
                emit(Collections.<BattleEvent>emptyList(), null);
                break;
            case ROOM_STATE:
                onRoomState(update.getRoomState());
                break;
            case MATCH_STARTED:
                onMatchStarted(update);
                break;
            case SNAPSHOT:
                onSnapshot(update);
                break;
            case EVENTS:
                onEvents(update);
                break;
            case REQUEST_OUTCOME:
                onRequestOutcome(update.getRequestOutcome());
                break;
            case RANKED_SAVE_STATUS:
                // 경기 상태와 별개인 저장 상태는 앱의 결과 표시 구독에서 처리
                break;
            default:
                throw new IllegalStateException("Unknown network update");
        }
    }

    private void onRoomState(RoomState incoming) {
        if (incoming == null) return;
        if (roomState != null) {
            if (!roomState.getRoomId().equals(incoming.getRoomId())) {
                if (matchId != null) return;
            } else if (incoming.getRoomVersion() < roomState.getRoomVersion()
                    || (matchId != null && incoming.getRoomVersion() < adoptedStartVersion)) return;
        }
        roomState = incoming;
        if (incoming.getPhase() == RoomState.Phase.CLOSED) {
            matchId = null;
            battleState = null;
            adoptedStartVersion = -1;
            phase = SessionPhase.WAITING;
        } else if (incoming.getPhase() == RoomState.Phase.WAITING && matchId != null
                && incoming.getRoomVersion() > adoptedStartVersion) {
            // 새 참가자 입장으로 시작한 준비 라운드의 이전 경기와 대기 요청 정리
            matchId = null;
            battleState = null;
            phase = SessionPhase.WAITING;
            lastEventId = 0;
            failPending("MATCH_REPLACED");
        } else if (phase == SessionPhase.CONNECTING) {
            phase = SessionPhase.WAITING;
        }
        emit(Collections.<BattleEvent>emptyList(), null);
    }

    private void onMatchStarted(NetworkUpdate update) {
        if (roomState == null || roomState.getPhase() == RoomState.Phase.CLOSED
                || !roomState.getRoomId().equals(update.getRoomId())
                || !roomState.getLocalParticipantId().equals(update.getLocalParticipantId())
                || update.getRoomVersion() < roomState.getRoomVersion()
                || update.getRoomVersion() <= adoptedStartVersion
                || update.getMatchId().equals(matchId)) return;
        adoptedStartVersion = update.getRoomVersion();
        matchId = update.getMatchId();
        battleState = null;
        lastEventId = 0;
        phase = SessionPhase.WAITING;
        failPending("MATCH_REPLACED");
        emit(Collections.<BattleEvent>emptyList(), null);
    }

    private void onSnapshot(NetworkUpdate update) {
        if (!matchesCurrent(update) || update.getBattleState() == null) return;
        BattleState incoming = update.getBattleState();
        if (incoming.getStatus() != BattleState.Status.RUNNING
                && incoming.getStatus() != BattleState.Status.FINISHED) return;
        if (phase == SessionPhase.FINISHED && incoming.getStatus() != BattleState.Status.FINISHED) return;
        if (incoming.getParticipant(roomState.getLocalParticipantId()) == null) return;
        if (battleState != null && incoming.getVersion() <= battleState.getVersion()) return;
        battleState = incoming;
        phase = incoming.getStatus() == BattleState.Status.FINISHED
                ? SessionPhase.FINISHED : SessionPhase.RUNNING;
        emit(Collections.<BattleEvent>emptyList(), null);
    }

    private void onEvents(NetworkUpdate update) {
        if (!matchesCurrent(update) || update.getEvents().isEmpty()) return;
        List<BattleEvent> fresh = new ArrayList<BattleEvent>();
        for (BattleEvent event : update.getEvents()) {
            if (event.getEventId() > lastEventId) {
                fresh.add(event);
                lastEventId = event.getEventId();
            }
        }
        if (!fresh.isEmpty()) emit(fresh, null);
    }

    private void onRequestOutcome(RequestOutcome incoming) {
        if (incoming == null) return;
        Long localId = localByNetworkRequest.remove(incoming.getRequestId());
        if (localId == null) {
            if (sending) earlyOutcomes.put(incoming.getRequestId(), incoming);
            return;
        }
        String reason = incoming.getMatchId() != null && matchId != null
                && !matchId.equals(incoming.getMatchId()) ? "MATCH_REPLACED" : incoming.getReasonCode();
        boolean accepted = reason == null && incoming.isAccepted();
        if (!accepted && reason == null) reason = "REQUEST_REJECTED";
        CommandOutcome outcome = new CommandOutcome(localId.longValue(), accepted, reason,
                incoming.getMatchId(), incoming.getBattleVersion());
        emit(Collections.<BattleEvent>emptyList(), outcome);
    }

    private boolean matchesCurrent(NetworkUpdate update) {
        return roomState != null && matchId != null
                && roomState.getRoomId().equals(update.getRoomId())
                && matchId.equals(update.getMatchId());
    }

    private void send(long localId, PlayerIntent intent) {
        try {
            sending = true;
            long networkId = network.send(intent);
            track(localId, networkId);
        } catch (RuntimeException error) {
            reject(localId, "NETWORK_SEND_FAILED");
        } finally { sending = false; }
    }

    private void send(long localId, RoomCommand command) {
        try {
            sending = true;
            long networkId = network.send(command);
            track(localId, networkId);
        } catch (RuntimeException error) {
            reject(localId, "NETWORK_SEND_FAILED");
        } finally { sending = false; }
    }

    private void track(long localId, long networkId) {
        if (networkId <= 0) throw new IllegalStateException("Network request ID must be positive");
        localByNetworkRequest.put(networkId, localId);
        RequestOutcome early = earlyOutcomes.remove(networkId);
        if (early != null) onRequestOutcome(early);
    }

    private void reject(long localId, String reason) {
        emit(Collections.<BattleEvent>emptyList(), new CommandOutcome(localId, false, reason,
                matchId, battleState == null ? null : battleState.getVersion()));
    }

    private void failPending(String reason) {
        List<Long> pending = new ArrayList<Long>(localByNetworkRequest.values());
        localByNetworkRequest.clear();
        earlyOutcomes.clear();
        for (Long localId : pending) reject(localId.longValue(), reason);
    }

    private SessionSnapshot snapshot() {
        boolean running = phase == SessionPhase.RUNNING;
        boolean canLeave = !closed && phase != SessionPhase.FAILED && roomState != null
                && roomState.getPhase() != RoomState.Phase.CLOSED;
        SessionCapabilities capabilities = new SessionCapabilities(running, false, false, canLeave);
        return new SessionSnapshot(sessionId, SessionMode.ONLINE_PVP, phase,
                roomState == null ? null : roomState.getLocalParticipantId(), matchId,
                battleState, capabilities);
    }

    private void emit(List<BattleEvent> events, CommandOutcome outcome) {
        SessionUpdate update = new SessionUpdate(snapshot(), events, outcome);
        for (Observer observer : new ArrayList<Observer>(observers)) observer.deliver(update);
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("CLOSED");
    }

    private static final class Observer {
        private final SessionListener listener;
        private boolean active = true;

        private Observer(SessionListener listener) { this.listener = listener; }
        private void deliver(SessionUpdate update) { if (active) listener.onUpdate(update); }
    }
}
