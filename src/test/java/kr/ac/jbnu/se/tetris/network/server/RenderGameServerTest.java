package kr.ac.jbnu.se.tetris.network.server;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kr.ac.jbnu.se.tetris.auth.AuthIdentity;
import kr.ac.jbnu.se.tetris.auth.AuthException;
import kr.ac.jbnu.se.tetris.auth.TokenVerifier;
import kr.ac.jbnu.se.tetris.network.ConnectionOptions;
import kr.ac.jbnu.se.tetris.network.NetworkUpdate;
import kr.ac.jbnu.se.tetris.network.RoomCommand;
import kr.ac.jbnu.se.tetris.network.WebSocketNetworkClient;
import kr.ac.jbnu.se.tetris.network.protocol.WireCodec;
import kr.ac.jbnu.se.tetris.network.protocol.WireRequest;
import kr.ac.jbnu.se.tetris.ranking.MatchRecord;
import kr.ac.jbnu.se.tetris.ranking.RankedMatchStore;
import kr.ac.jbnu.se.tetris.ranking.RunLease;

/** A live loopback WebSocket transport with multiple independent ranked 1v1 rooms. */
public final class RenderGameServerTest {
    private static final String ADMIN = "local-test-admin-key-1234567890";
    private static final String[] USERS = {
            "66bb5a13-f250-4aa5-aa75-913cc7430001", "66bb5a13-f250-4aa5-aa75-913cc7430002",
            "66bb5a13-f250-4aa5-aa75-913cc7430003", "66bb5a13-f250-4aa5-aa75-913cc7430004",
            "66bb5a13-f250-4aa5-aa75-913cc7430005", "66bb5a13-f250-4aa5-aa75-913cc7430006"
    };

    public static void main(String[] args) throws Exception {
        TestStore store = new TestStore();
        TokenVerifier verifier = token -> {
            for (String user : USERS) if (user.equals(token)) {
                return new AuthIdentity(user, "Player", System.currentTimeMillis() / 1000L + 3600);
            }
            throw new IOException("Bad token");
        };
        RenderGameServer server = new RenderGameServer(verifier, store, ADMIN, 0);
        server.start();
        Probe[] probes = new Probe[6];
        try {
            String base = "http://127.0.0.1:" + server.getPort();
            assertEquals(200, request(base + "/health", "GET", null));
            assertEquals(200, request(base + "/healthz", "GET", null));
            assertEquals(503, request(base + "/ready", "GET", null));
            assertEquals(503, request(base + "/readyz", "GET", null));
            if (!requestText(base + "/", null).contains("Match admission: closed"))
                throw new AssertionError("Public server page must explain closed admission");
            assertEquals(401, request(base + "/admin/status", "GET", null));
            assertEquals(503, request(base + "/ws", "GET", "Bearer " + USERS[0]));
            assertEquals(200, request(base + "/admin/open", "POST", "Bearer " + ADMIN));
            assertEquals(200, request(base + "/ready", "GET", null));
            assertEquals(200, request(base + "/readyz", "GET", null));
            if (!requestText(base + "/", null).contains("Match admission: open"))
                throw new AssertionError("Public server page must explain open admission");
            fragmentedAndMalformedBoundary(server.getPort());
            oversizeFrameBoundary(server.getPort());
            for (int i = 0; i < probes.length; i++) {
                probes[i] = new Probe(USERS[i]);
                probes[i].client.connect(new ConnectionOptions(new URI("ws://127.0.0.1:"
                        + server.getPort() + "/ws"), USERS[i]));
                probes[i].await(NetworkUpdate.Type.CONNECTED);
            }
            Probe duplicate = new Probe(USERS[0]);
            try {
                duplicate.client.connect(new ConnectionOptions(new URI("ws://127.0.0.1:"
                        + server.getPort() + "/ws"), USERS[0]));
                if (duplicate.awaitConnectionOutcome() != NetworkUpdate.Type.CONNECTION_FAILED) {
                    throw new AssertionError("Duplicate account connected");
                }
            } finally { duplicate.client.close(); }
            long refreshId = probes[0].client.refreshAuthentication(USERS[0]);
            if (!probes[0].awaitOutcome(refreshId).getRequestOutcome().isAccepted()) {
                throw new AssertionError("Same-account token refresh rejected");
            }
            long forgedId = probes[0].client.refreshAuthentication(USERS[1]);
            if (probes[0].awaitOutcome(forgedId).getRequestOutcome().isAccepted()) {
                throw new AssertionError("Cross-account token refresh accepted");
            }
            java.util.Set<String> roomCodes = new java.util.HashSet<String>();
            for (int pair = 0; pair < 3; pair++) {
                Probe first = probes[pair * 2], second = probes[pair * 2 + 1];
                first.client.send(RoomCommand.createRoom(2));
                NetworkUpdate created = first.await(NetworkUpdate.Type.ROOM_STATE);
                // 방 코드는 헷갈리는 문자(0/O/1/I/L)를 뺀 6자리 영숫자이고 방마다 달라야 한다.
                if (!created.getRoomId().matches("[A-HJ-NP-Z2-9]{6}")) {
                    throw new AssertionError("Room code must be 6 chars: " + created.getRoomId());
                }
                if (!roomCodes.add(created.getRoomId())) {
                    throw new AssertionError("Duplicate room code: " + created.getRoomId());
                }
                // 존재하지 않는 코드로 입장하면 ROOM_NOT_FOUND여야 한다(이미 방에 있는 사람 말고 다른 참가자가 시도).
                if (pair == 0) {
                    long missing = probes[5].client.send(RoomCommand.joinRoom("ZZZZZZ"));
                    NetworkUpdate rejected = probes[5].awaitOutcome(missing);
                    if (rejected.getRequestOutcome().isAccepted()
                            || !"ROOM_NOT_FOUND".equals(rejected.getRequestOutcome().getReasonCode())) {
                        throw new AssertionError("Unknown room code must be ROOM_NOT_FOUND");
                    }
                }
                // 소문자로 입력해도 같은 방에 입장해야 한다.
                second.client.send(RoomCommand.joinRoom(created.getRoomId().toLowerCase()));
                second.await(NetworkUpdate.Type.ROOM_STATE);
                first.client.send(RoomCommand.setReady(true));
                second.client.send(RoomCommand.setReady(true));
                first.await(NetworkUpdate.Type.MATCH_STARTED);
                second.await(NetworkUpdate.Type.MATCH_STARTED);
            }
            if (store.records.size() != 3) throw new AssertionError("Three ranked matches not registered");
            String liveStatus = requestText(base + "/admin/status", "Bearer " + ADMIN);
            if (!liveStatus.contains("users=6") || !liveStatus.contains("rooms=3")
                    || !liveStatus.contains("active=3")) {
                throw new AssertionError("Three concurrent 1v1 matches not active: " + liveStatus);
            }
            for (int pair = 0; pair < 3; pair++) {
                Probe first = probes[pair * 2], second = probes[pair * 2 + 1];
                first.client.send(RoomCommand.leaveRoom());
                NetworkUpdate saved = second.await(NetworkUpdate.Type.RANKED_SAVE_STATUS, "SAVED");
                MatchRecord match = store.records.get(saved.getMatchId());
                if (match == null || match.getStatus() != MatchRecord.Status.FINALIZED
                        || !second.user.equals(match.getWinnerUserId())) {
                    throw new AssertionError("Forfeit did not persist winner once");
                }
                if (pair == 0) assertStatusContains(base, "active=2", "unresolved=2");
            }
            assertEquals(200, request(base + "/admin/drain", "POST", "Bearer " + ADMIN));
            assertEquals(503, request(base + "/ready", "GET", null));
            assertEquals(503, request(base + "/readyz", "GET", null));
            Probe first = probes[0];
            long rejectedId = first.client.send(RoomCommand.createRoom(2));
            NetworkUpdate rejected = first.awaitOutcome(rejectedId);
            if (rejected.getRequestOutcome().isAccepted()) throw new AssertionError("Draining accepted room");
        } finally {
            for (Probe probe : probes) if (probe != null) probe.client.close();
            server.close();
        }
        staleRegistrationMustNotPoisonReplacementRoom(verifier);
        parallelAdmissionAndAuthErrors();
        configuredSocketLimitRejectsExcess(verifier);
        unauthenticatedTimeoutReleasesCapacity();
        invalidatedMatchIsTerminal(verifier);
        pvpGravityFollowsRealTime(verifier);
        timeWarpSlowsOnlyItsUserOnServer(verifier);
        pvpSpeedsUpAfterOneMinuteOnServer(verifier);
        roomCodesAreShortAndAvoidExistingRooms(verifier);
        lostFinishResponseKeepsOrder(verifier);
        orphanPendingVisibleInDrainStatus(verifier);
        slowAuthRefreshMustNotBlockRanking();
        leaseRestartAndFencing(verifier);
        System.out.println("RenderGameServerTest: PASS");
    }

    private static void leaseRestartAndFencing(TokenVerifier verifier) throws Exception {
        TestStore store = new TestStore();
        String firstRun;
        RenderGameServer first = new RenderGameServer(verifier, store, ADMIN, 0);
        first.start();
        try {
            String base = "http://127.0.0.1:" + first.getPort();
            assertEquals(503, request(base + "/ws", "GET", "Bearer " + USERS[0]));
            assertEquals(200, request(base + "/admin/open", "POST", "Bearer " + ADMIN));
            firstRun = first.getRunId();
            String match = UUID.randomUUID().toString();
            store.beginMatch(match, firstRun, "pvp-v1", USERS[0], USERS[1]);
            String other = UUID.randomUUID().toString();
            if (store.claimRun(other).isOwned()) throw new AssertionError("Overlapping run claimed lease");
            store.expired = true;
            if (!store.claimRun(other).isOwned()) throw new AssertionError("Expired run was not replaced");
            if (store.getMatch(match).getStatus() != MatchRecord.Status.VOID)
                throw new AssertionError("Takeover did not reconcile running match");
            try { store.beginMatch(UUID.randomUUID().toString(), firstRun, "pvp-v1", USERS[0], USERS[1]);
                throw new AssertionError("Late begin accepted"); }
            catch (IOException expected) { /* fenced */ }
            try { store.finishMatch(match, firstRun, USERS[0], "WIN");
                throw new AssertionError("Late result accepted"); }
            catch (IOException expected) { /* fenced */ }
        } finally { first.close(); }

        // A fresh process resumes the persistent OPEN policy after the old lease expires.
        store.expired = true;
        RenderGameServer second = new RenderGameServer(verifier, store, ADMIN, 0);
        second.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!second.isAdmissionOpen() && System.nanoTime() < deadline) Thread.sleep(20);
            if (!second.isAdmissionOpen()) throw new AssertionError("Restart did not auto-open");
            String base = "http://127.0.0.1:" + second.getPort();
            store.failNextRenew = true;
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (second.isAdmissionOpen() && System.nanoTime() < deadline) Thread.sleep(20);
            if (second.isAdmissionOpen()) throw new AssertionError("Uncertain lease kept admission open");
            assertEquals(200, request(base + "/healthz", "GET", null));
            assertEquals(503, request(base + "/readyz", "GET", null));
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (!second.isAdmissionOpen() && System.nanoTime() < deadline) Thread.sleep(20);
            if (!second.isAdmissionOpen()) throw new AssertionError("Transient lease error did not recover");
            store.leaseUnavailable = true;
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (second.isAdmissionOpen() && System.nanoTime() < deadline) Thread.sleep(20);
            if (second.isAdmissionOpen()) throw new AssertionError("DB outage kept admission open");
            assertEquals(200, request(base + "/healthz", "GET", null));
        } finally { second.close(); }

        store.leaseUnavailable = false;
        store.expired = true;
        RenderGameServer third = new RenderGameServer(verifier, store, ADMIN, 0);
        third.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!third.isAdmissionOpen() && System.nanoTime() < deadline) Thread.sleep(20);
            if (!third.isAdmissionOpen()) throw new AssertionError("Second restart did not auto-open");
            assertEquals(200, request("http://127.0.0.1:" + third.getPort()
                    + "/admin/drain", "POST", "Bearer " + ADMIN));
        } finally { third.close(); }

        store.expired = true;
        RenderGameServer fourth = new RenderGameServer(verifier, store, ADMIN, 0);
        fourth.start();
        try {
            Thread.sleep(200);
            if (fourth.isAdmissionOpen()) throw new AssertionError("Maintenance drain lost on restart");
            assertEquals(200, request("http://127.0.0.1:" + fourth.getPort()
                    + "/admin/open", "POST", "Bearer " + ADMIN));
        } finally { fourth.close(); }
    }

    private static void slowAuthRefreshMustNotBlockRanking() throws Exception {
        CountDownLatch refreshEntered = new CountDownLatch(2);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        TokenVerifier verifier = token -> {
            boolean refresh = token.startsWith("refresh-");
            String user = refresh ? token.substring("refresh-".length()) : token;
            if (!USERS[0].equals(user) && !USERS[1].equals(user)) throw new AuthException("INVALID_TOKEN");
            if (refresh) {
                refreshEntered.countDown();
                try { releaseRefresh.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
            return new AuthIdentity(user, "Player", System.currentTimeMillis() / 1000L + 3600);
        };
        RenderGameServer server = new RenderGameServer(verifier, new TestStore(), ADMIN, 0);
        server.start();
        Probe a = null, b = null;
        try {
            assertEquals(200, request("http://127.0.0.1:" + server.getPort() + "/admin/open",
                    "POST", "Bearer " + ADMIN));
            a = connectProbe(USERS[0], server.getPort());
            b = connectProbe(USERS[1], server.getPort());
            a.client.send(RoomCommand.createRoom(2));
            String roomId = a.await(NetworkUpdate.Type.ROOM_STATE).getRoomId();
            b.client.send(RoomCommand.joinRoom(roomId));
            b.await(NetworkUpdate.Type.ROOM_STATE);
            a.client.send(RoomCommand.setReady(true));
            b.client.send(RoomCommand.setReady(true));
            a.await(NetworkUpdate.Type.MATCH_STARTED);
            b.await(NetworkUpdate.Type.MATCH_STARTED);
            a.client.refreshAuthentication("refresh-" + USERS[0]);
            b.client.refreshAuthentication("refresh-" + USERS[1]);
            if (!refreshEntered.await(3, TimeUnit.SECONDS)) throw new AssertionError("Refreshes not in flight");
            long duplicateRefresh = b.client.refreshAuthentication("refresh-" + USERS[1]);
            NetworkUpdate denied = b.awaitOutcome(duplicateRefresh);
            if (denied.getRequestOutcome().isAccepted()
                    || !"AUTH_REFRESH_BUSY".equals(denied.getRequestOutcome().getReasonCode())) {
                throw new AssertionError("Per-peer refresh was not bounded");
            }
            a.client.send(RoomCommand.leaveRoom());
            b.await(NetworkUpdate.Type.RANKED_SAVE_STATUS, "SAVED");
        } finally {
            releaseRefresh.countDown();
            if (a != null) a.client.close();
            if (b != null) b.client.close();
            server.close();
        }
    }

    private static void lostFinishResponseKeepsOrder(TokenVerifier verifier) throws Exception {
        DelayedFinishStore store = new DelayedFinishStore();
        RenderGameServer server = new RenderGameServer(verifier, store, ADMIN, 0);
        server.start();
        Probe a = null, b = null;
        try {
            assertEquals(200, request("http://127.0.0.1:" + server.getPort() + "/admin/open",
                    "POST", "Bearer " + ADMIN));
            a = connectProbe(USERS[0], server.getPort());
            b = connectProbe(USERS[1], server.getPort());
            a.client.send(RoomCommand.createRoom(2));
            String roomId = a.await(NetworkUpdate.Type.ROOM_STATE).getRoomId();
            b.client.send(RoomCommand.joinRoom(roomId));
            b.await(NetworkUpdate.Type.ROOM_STATE);
            a.client.send(RoomCommand.setReady(true));
            b.client.send(RoomCommand.setReady(true));
            a.await(NetworkUpdate.Type.MATCH_STARTED);
            b.await(NetworkUpdate.Type.MATCH_STARTED);
            a.client.send(RoomCommand.leaveRoom());
            b.await(NetworkUpdate.Type.RANKED_SAVE_STATUS, "SAVE_PENDING");
            if (!store.firstFinished.await(5, TimeUnit.SECONDS)) throw new AssertionError("Finish was not sent");
            long rematchId = b.client.send(RoomCommand.requestRematch());
            NetworkUpdate denied = b.awaitOutcome(rematchId);
            if (denied.getRequestOutcome().isAccepted()
                    || !"RESULT_UNRESOLVED".equals(denied.getRequestOutcome().getReasonCode())) {
                throw new AssertionError("Rematch started before saved result was confirmed");
            }
            store.releaseResponse.countDown();
            NetworkUpdate saved = b.await(NetworkUpdate.Type.RANKED_SAVE_STATUS, "SAVED");
            if (store.getMatch(saved.getMatchId()).getStatus() != MatchRecord.Status.FINALIZED
                    || store.finishCalls.get() != 1) {
                throw new AssertionError("Lost DB response duplicated result or blocked recovery");
            }
        } finally {
            store.releaseResponse.countDown();
            if (a != null) a.client.close();
            if (b != null) b.client.close();
            server.close();
        }
    }

    private static void orphanPendingVisibleInDrainStatus(TokenVerifier verifier) throws Exception {
        DelayedFinishStore store = new DelayedFinishStore();
        RenderGameServer server = new RenderGameServer(verifier, store, ADMIN, 0);
        server.start();
        Probe a = null, b = null;
        try {
            String base = "http://127.0.0.1:" + server.getPort();
            assertEquals(200, request(base + "/admin/open", "POST", "Bearer " + ADMIN));
            a = connectProbe(USERS[0], server.getPort());
            b = connectProbe(USERS[1], server.getPort());
            a.client.send(RoomCommand.createRoom(2));
            String roomId = a.await(NetworkUpdate.Type.ROOM_STATE).getRoomId();
            b.client.send(RoomCommand.joinRoom(roomId));
            b.await(NetworkUpdate.Type.ROOM_STATE);
            a.client.send(RoomCommand.setReady(true));
            b.client.send(RoomCommand.setReady(true));
            a.await(NetworkUpdate.Type.MATCH_STARTED);
            b.await(NetworkUpdate.Type.MATCH_STARTED);
            a.client.send(RoomCommand.leaveRoom());
            b.await(NetworkUpdate.Type.RANKED_SAVE_STATUS, "SAVE_PENDING");
            if (!store.firstFinished.await(5, TimeUnit.SECONDS)) throw new AssertionError("Finish not pending");
            long lastLeave = b.client.send(RoomCommand.leaveRoom());
            b.awaitOutcome(lastLeave);
            assertStatusContains(base, "rooms=0", "unresolved=1");
            store.releaseResponse.countDown();
            assertStatusContains(base, "rooms=0", "unresolved=0");
        } finally {
            store.releaseResponse.countDown();
            if (a != null) a.client.close();
            if (b != null) b.client.close();
            server.close();
        }
    }

    private static void assertStatusContains(String base, String first, String second) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        String status = "";
        while (System.nanoTime() < until) {
            status = requestText(base + "/admin/status", "Bearer " + ADMIN);
            if (status.contains(first) && status.contains(second)) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Status missed " + first + "/" + second + ": " + status);
    }

    private static void invalidatedMatchIsTerminal(TokenVerifier verifier) throws Exception {
        TestStore store = new TestStore();
        RenderGameServer server = new RenderGameServer(verifier, store, ADMIN, 0);
        server.start();
        Probe a = null, b = null;
        try {
            assertEquals(200, request("http://127.0.0.1:" + server.getPort() + "/admin/open",
                    "POST", "Bearer " + ADMIN));
            a = connectProbe(USERS[0], server.getPort());
            b = connectProbe(USERS[1], server.getPort());
            a.client.send(RoomCommand.createRoom(2));
            String roomId = a.await(NetworkUpdate.Type.ROOM_STATE).getRoomId();
            b.client.send(RoomCommand.joinRoom(roomId));
            b.await(NetworkUpdate.Type.ROOM_STATE);
            a.client.send(RoomCommand.setReady(true));
            b.client.send(RoomCommand.setReady(true));
            a.await(NetworkUpdate.Type.MATCH_STARTED);
            b.await(NetworkUpdate.Type.MATCH_STARTED);

            // Force the same private failure transition used by a tick exception.
            java.lang.reflect.Field roomsField = RenderGameServer.class.getDeclaredField("rooms");
            java.lang.reflect.Field queueField = RenderGameServer.class.getDeclaredField("roomsExecutor");
            roomsField.setAccessible(true);
            queueField.setAccessible(true);
            java.util.concurrent.ScheduledThreadPoolExecutor queue =
                    (java.util.concurrent.ScheduledThreadPoolExecutor) queueField.get(server);
            Class<?> roomClass = Class.forName(RenderGameServer.class.getName() + "$Room");
            java.lang.reflect.Method invalidate = RenderGameServer.class.getDeclaredMethod("invalidate",
                    roomClass, String.class);
            invalidate.setAccessible(true);
            queue.submit(() -> {
                try {
                    Map<?, ?> current = (Map<?, ?>) roomsField.get(server);
                    invalidate.invoke(server, current.get(roomId), "SERVER_TICK_FAILED");
                } catch (Exception failure) { throw new RuntimeException(failure); }
            }).get(3, TimeUnit.SECONDS);
            for (Probe probe : new Probe[] { a, b }) {
                probe.await(NetworkUpdate.Type.RANKED_SAVE_STATUS, "VOIDED");
                NetworkUpdate terminal = probe.await(NetworkUpdate.Type.ERROR);
                if (!"MATCH_VOIDED".equals(terminal.getReasonCode())) {
                    throw new AssertionError("Missing terminal void error");
                }
            }
        } finally {
            if (a != null) a.client.close();
            if (b != null) b.client.close();
            server.close();
        }
    }

    private static void parallelAdmissionAndAuthErrors() throws Exception {
        CountDownLatch verifying = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        TokenVerifier verifier = token -> {
            if ("bad-token".equals(token)) throw new AuthException("INVALID_TOKEN");
            if ("down-token".equals(token)) throw new IOException("Auth backend unavailable");
            for (String user : USERS) if (user.equals(token)) {
                verifying.countDown();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                return new AuthIdentity(user, "Player", System.currentTimeMillis() / 1000L + 3600);
            }
            throw new AuthException("INVALID_TOKEN");
        };
        RenderGameServer server = new RenderGameServer(verifier, new TestStore(), ADMIN, 0, 8, 2);
        server.start();
        Probe[] probes = new Probe[6];
        Probe recovered = null;
        try {
            String base = "http://127.0.0.1:" + server.getPort();
            assertEquals(200, request(base + "/admin/open", "POST", "Bearer " + ADMIN));
            assertEquals(401, request(base + "/ws", "GET", "Bearer bad-token"));
            assertEquals(503, request(base + "/ws", "GET", "Bearer down-token"));
            for (String[] caseData : new String[][] { { "bad-token", "AUTH_INVALID" },
                    { "down-token", "SERVICE_UNAVAILABLE" } }) {
                Probe failed = new Probe(caseData[0]);
                try {
                    failed.client.connect(new ConnectionOptions(new URI("ws://127.0.0.1:"
                            + server.getPort() + "/ws"), caseData[0]));
                    NetworkUpdate update = failed.awaitConnectionUpdate();
                    if (update.getType() != NetworkUpdate.Type.CONNECTION_FAILED
                            || !caseData[1].equals(update.getReasonCode())) {
                        throw new AssertionError("Wrong handshake error for " + caseData[0]);
                    }
                } finally { failed.client.close(); }
            }
            for (int i = 0; i < 2; i++) {
                probes[i] = new Probe(USERS[i]);
                probes[i].client.connect(new ConnectionOptions(new URI("ws://127.0.0.1:"
                        + server.getPort() + "/ws"), USERS[i]));
            }
            if (!verifying.await(5, TimeUnit.SECONDS)) throw new AssertionError("No parallel auth requests");
            for (int i = 2; i < probes.length; i++) {
                probes[i] = new Probe(USERS[i]);
                probes[i].client.connect(new ConnectionOptions(new URI("ws://127.0.0.1:"
                        + server.getPort() + "/ws"), USERS[i]));
                if (probes[i].awaitConnectionOutcome() != NetworkUpdate.Type.CONNECTION_FAILED) {
                    throw new AssertionError("Pending-auth overload was accepted");
                }
            }
            release.countDown();
            int connected = 0;
            for (int i = 0; i < 2; i++) {
                NetworkUpdate.Type result = probes[i].awaitConnectionOutcome();
                if (result == NetworkUpdate.Type.CONNECTED) connected++;
            }
            if (connected != 2) throw new AssertionError("Expected two initial authentications");
            recovered = connectProbe(USERS[2], server.getPort());
        } finally {
            release.countDown();
            if (recovered != null) recovered.client.close();
            for (Probe probe : probes) if (probe != null) probe.client.close();
            server.close();
        }
    }

    private static void configuredSocketLimitRejectsExcess(TokenVerifier verifier) throws Exception {
        RenderGameServer server = new RenderGameServer(verifier, new TestStore(), ADMIN, 0, 2, 1);
        server.start();
        Probe first = null, second = null, excess = null, recovered = null;
        try {
            String base = "http://127.0.0.1:" + server.getPort();
            assertEquals(200, request(base + "/admin/open",
                    "POST", "Bearer " + ADMIN));
            first = connectProbe(USERS[0], server.getPort());
            second = connectProbe(USERS[1], server.getPort());
            assertEquals(200, request(base + "/healthz", "GET", null));
            if (!requestText(base + "/admin/status", "Bearer " + ADMIN).contains("users=2")) {
                throw new AssertionError("Admin status unavailable at WebSocket limit");
            }
            excess = new Probe(USERS[2]);
            excess.client.connect(new ConnectionOptions(new URI("ws://127.0.0.1:"
                    + server.getPort() + "/ws"), USERS[2]));
            if (excess.awaitConnectionOutcome() != NetworkUpdate.Type.CONNECTION_FAILED) {
                throw new AssertionError("Configured socket bound was not applied");
            }
            assertEquals(200, request(base + "/admin/drain", "POST", "Bearer " + ADMIN));
            first.client.close();
            assertStatusContains(base, "users=1", "admission=false");
            assertEquals(200, request(base + "/admin/open", "POST", "Bearer " + ADMIN));
            recovered = connectProbe(USERS[2], server.getPort());
        } finally {
            if (first != null) first.client.close();
            if (second != null) second.client.close();
            if (excess != null) excess.client.close();
            if (recovered != null) recovered.client.close();
            server.close();
        }
    }

    private static void unauthenticatedTimeoutReleasesCapacity() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TokenVerifier slow = token -> {
            entered.countDown();
            try { release.await(22, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return new AuthIdentity(USERS[0], "Player", System.currentTimeMillis() / 1000L + 3600);
        };
        RenderGameServer server = new RenderGameServer(slow, new TestStore(), ADMIN, 0, 8, 1);
        server.start();
        Probe recovered = null;
        try {
            assertEquals(200, request("http://127.0.0.1:" + server.getPort() + "/admin/open",
                    "POST", "Bearer " + ADMIN));
            try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
                socket.setSoTimeout(22000);
                sendUpgrade(socket, server.getPort(), USERS[0]);
                if (!entered.await(3, TimeUnit.SECONDS)) throw new AssertionError("Auth verification not entered");
                if (socket.getInputStream().read() != -1) {
                    throw new AssertionError("Unauthenticated connection not closed by timeout");
                }
            }
            release.countDown();
            recovered = connectProbe(USERS[0], server.getPort());
        } finally {
            release.countDown();
            if (recovered != null) recovered.client.close();
            server.close();
        }
    }

    private static void staleRegistrationMustNotPoisonReplacementRoom(TokenVerifier verifier) throws Exception {
        FlakyStore store = new FlakyStore();
        RenderGameServer server = new RenderGameServer(verifier, store, ADMIN, 0);
        server.start();
        Probe a = null, b = null, c = null;
        try {
            String base = "http://127.0.0.1:" + server.getPort();
            assertEquals(200, request(base + "/admin/open", "POST", "Bearer " + ADMIN));
            a = connectProbe(USERS[0], server.getPort());
            b = connectProbe(USERS[1], server.getPort());
            c = connectProbe(USERS[2], server.getPort());
            a.client.send(RoomCommand.createRoom(2));
            String roomId = a.await(NetworkUpdate.Type.ROOM_STATE).getRoomId();
            b.client.send(RoomCommand.joinRoom(roomId));
            b.await(NetworkUpdate.Type.ROOM_STATE);
            a.client.send(RoomCommand.setReady(true));
            b.client.send(RoomCommand.setReady(true));
            if (!store.firstEntered.await(5, TimeUnit.SECONDS)) throw new AssertionError("First begin not entered");
            long leaveId = b.client.send(RoomCommand.leaveRoom());
            if (!b.awaitOutcome(leaveId).getRequestOutcome().isAccepted()) {
                throw new AssertionError("Departure during registration rejected");
            }
            c.client.send(RoomCommand.joinRoom(roomId));
            c.await(NetworkUpdate.Type.ROOM_STATE);
            a.client.send(RoomCommand.setReady(true));
            c.client.send(RoomCommand.setReady(true));
            a.await(NetworkUpdate.Type.MATCH_STARTED);
            c.await(NetworkUpdate.Type.MATCH_STARTED);
            store.releaseFirst.countDown();
            if (!store.firstReturned.await(5, TimeUnit.SECONDS)) throw new AssertionError("Old begin still pending");
            Thread.sleep(150);
            assertStatusContains(base, "rooms=1", "unresolved=2");
            store.commitLate();
            a.client.send(RoomCommand.leaveRoom());
            c.await(NetworkUpdate.Type.RANKED_SAVE_STATUS, "SAVED");
            assertStatusContains(base, "rooms=1", "unresolved=0");
        } finally {
            store.releaseFirst.countDown();
            if (a != null) a.client.close();
            if (b != null) b.client.close();
            if (c != null) c.client.close();
            server.close();
        }
    }

    /**
     * 서버가 400ms 고정 틱이 아니라 실제 경과 시간으로 PvP 낙하를 처리하는지 클라이언트가 받는 스냅샷으로 확인한다.
     * PvP 시작 낙하 간격은 500ms이므로 4초 동안 참가자 2명이 합쳐 약 16칸 내려가야 한다(400ms 고정이면 약 20칸).
     */
    private static void pvpGravityFollowsRealTime(TokenVerifier verifier) throws Exception {
        TestStore store = new TestStore();
        RenderGameServer server = new RenderGameServer(verifier, store, ADMIN, 0);
        server.start();
        Probe a = null, b = null;
        try {
            assertEquals(200, request("http://127.0.0.1:" + server.getPort() + "/admin/open",
                    "POST", "Bearer " + ADMIN));
            a = connectProbe(USERS[0], server.getPort());
            b = connectProbe(USERS[1], server.getPort());
            a.client.send(RoomCommand.createRoom(2));
            String roomId = a.await(NetworkUpdate.Type.ROOM_STATE).getRoomId();
            b.client.send(RoomCommand.joinRoom(roomId));
            b.await(NetworkUpdate.Type.ROOM_STATE);
            a.client.send(RoomCommand.setReady(true));
            b.client.send(RoomCommand.setReady(true));
            a.await(NetworkUpdate.Type.MATCH_STARTED);
            b.await(NetworkUpdate.Type.MATCH_STARTED);

            // 시작 직후 첫 스냅샷을 기준점으로 삼는다.
            NetworkUpdate first = a.await(NetworkUpdate.Type.SNAPSHOT);
            long wallStart = System.nanoTime();
            long tickStart = totalTicks(first);
            long elapsedStart = first.getBattleState().getElapsedMillis();
            long lastVersion = first.getBattleState().getVersion();
            int snapshots = 0;
            NetworkUpdate last = first;
            long lastWall = wallStart; // 마지막 스냅샷을 받은 시각: 게임 시계는 이 시각과 비교해야 한다.
            long until = wallStart + TimeUnit.SECONDS.toNanos(4);
            while (System.nanoTime() < until) {
                NetworkUpdate update = a.updates.poll(50, TimeUnit.MILLISECONDS);
                if (update == null || update.getType() != NetworkUpdate.Type.SNAPSHOT) continue;
                snapshots++;
                if (update.getBattleState().getVersion() < lastVersion)
                    throw new AssertionError("Snapshot version went backwards");
                lastVersion = update.getBattleState().getVersion();
                last = update;
                lastWall = System.nanoTime();
            }
            long wallMillis = (lastWall - wallStart) / 1_000_000L;
            long steps = totalTicks(last) - tickStart;
            long gameMillis = last.getBattleState().getElapsedMillis() - elapsedStart;

            // 1) 게임 시계는 실제 시간과 같이 흘러야 한다(빨리 감기거나 느려지지 않는다).
            if (Math.abs(gameMillis - wallMillis) > 250)
                throw new AssertionError("Game clock drifted: game=" + gameMillis + "ms wall=" + wallMillis + "ms");
            // 2) PvP 시작 간격 500ms: 두 참가자 합쳐 (게임 시간/500)*2칸. 400ms 고정이면 이보다 25% 많다.
            long expected = gameMillis * 2 / 500;
            if (steps < expected - 2 || steps > expected + 2)
                throw new AssertionError("PvP gravity steps=" + steps + " expected~" + expected
                        + " (500ms interval) over " + gameMillis + "ms");
            // 3) 스냅샷이 끊기지 않으면서(하트비트 400ms 이하) 과하게 쏟아지지도 않는다.
            if (snapshots < wallMillis / 500 || snapshots > wallMillis / 50)
                throw new AssertionError("Snapshot cadence off: " + snapshots + " in " + wallMillis + "ms");
            System.out.println("pvpGravityFollowsRealTime: wall=" + wallMillis + "ms game=" + gameMillis
                    + "ms steps=" + steps + " (expected~" + expected + ") snapshots=" + snapshots);
        } finally {
            if (a != null) a.client.close();
            if (b != null) b.client.close();
            server.close();
        }
    }

    /**
     * 서버 전체 경로로 시간 왜곡을 검증한다: 아이템을 쓴 참가자의 낙하만 +100ms(500→600ms) 느려져야 한다.
     * 아이템 획득은 별도 규칙이라 서버 방의 참가자 슬롯에 직접 넣고, 사용은 실제 클라이언트 메시지로 보낸다.
     */
    @SuppressWarnings("unchecked")
    private static void timeWarpSlowsOnlyItsUserOnServer(TokenVerifier verifier) throws Exception {
        TestStore store = new TestStore();
        RenderGameServer server = new RenderGameServer(verifier, store, ADMIN, 0);
        server.start();
        Probe a = null, b = null;
        try {
            assertEquals(200, request("http://127.0.0.1:" + server.getPort() + "/admin/open",
                    "POST", "Bearer " + ADMIN));
            a = connectProbe(USERS[0], server.getPort());
            b = connectProbe(USERS[1], server.getPort());
            a.client.send(RoomCommand.createRoom(2));
            String roomId = a.await(NetworkUpdate.Type.ROOM_STATE).getRoomId();
            b.client.send(RoomCommand.joinRoom(roomId));
            b.await(NetworkUpdate.Type.ROOM_STATE);
            a.client.send(RoomCommand.setReady(true));
            b.client.send(RoomCommand.setReady(true));
            a.await(NetworkUpdate.Type.MATCH_STARTED);
            b.await(NetworkUpdate.Type.MATCH_STARTED);
            a.await(NetworkUpdate.Type.SNAPSHOT);

            // 참가자 A의 슬롯에 시간 왜곡 1개를 넣는다(테스트 전용 접근).
            java.lang.reflect.Field roomsField = RenderGameServer.class.getDeclaredField("rooms");
            roomsField.setAccessible(true);
            Object room = ((Map<?, ?>) roomsField.get(server)).get(roomId);
            java.lang.reflect.Field battleField = room.getClass().getDeclaredField("battle");
            battleField.setAccessible(true);
            kr.ac.jbnu.se.tetris.battle.BattleManager battle =
                    (kr.ac.jbnu.se.tetris.battle.BattleManager) battleField.get(room);
            java.lang.reflect.Field partsField = kr.ac.jbnu.se.tetris.battle.BattleManager.class
                    .getDeclaredField("participants");
            partsField.setAccessible(true);
            Object participant = ((Map<?, ?>) partsField.get(battle)).get(USERS[0]);
            java.lang.reflect.Field itemsField = participant.getClass().getDeclaredField("items");
            java.lang.reflect.Field chargesField = participant.getClass().getDeclaredField("itemCharges");
            itemsField.setAccessible(true);
            chargesField.setAccessible(true);
            synchronized (battle) {
                ((java.util.List<String>) itemsField.get(participant)).add("time_warp");
                ((java.util.List<Integer>) chargesField.get(participant)).add(1);
            }

            long requestId = a.client.send(new kr.ac.jbnu.se.tetris.core.PlayerIntent(
                    kr.ac.jbnu.se.tetris.core.GameAction.Type.USE_ITEM,
                    new kr.ac.jbnu.se.tetris.core.GameAction.ItemUse("time_warp", USERS[0])));
            NetworkUpdate outcome = a.awaitOutcome(requestId);
            if (!outcome.getRequestOutcome().isAccepted())
                throw new AssertionError("Time warp rejected: " + outcome.getRequestOutcome().getReasonCode());

            NetworkUpdate first = a.await(NetworkUpdate.Type.SNAPSHOT);
            long tickA0 = first.getBattleState().getParticipant(USERS[0]).getGameState().getTick();
            long tickB0 = first.getBattleState().getParticipant(USERS[1]).getGameState().getTick();
            long elapsed0 = first.getBattleState().getElapsedMillis();
            NetworkUpdate last = first;
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
            while (System.nanoTime() < until) {
                NetworkUpdate update = a.updates.poll(50, TimeUnit.MILLISECONDS);
                if (update != null && update.getType() == NetworkUpdate.Type.SNAPSHOT) last = update;
            }
            long stepsA = last.getBattleState().getParticipant(USERS[0]).getGameState().getTick() - tickA0;
            long stepsB = last.getBattleState().getParticipant(USERS[1]).getGameState().getTick() - tickB0;
            long gameMillis = last.getBattleState().getElapsedMillis() - elapsed0;
            // 워프 중 A는 600ms 간격, B는 500ms 간격이어야 한다(기대값 gameMillis/600, gameMillis/500).
            long expectA = gameMillis / 600, expectB = gameMillis / 500;
            if (Math.abs(stepsA - expectA) > 1 || Math.abs(stepsB - expectB) > 1 || stepsA >= stepsB)
                throw new AssertionError("Time warp not applied per participant: A=" + stepsA + " (expected~" + expectA
                        + ") B=" + stepsB + " (expected~" + expectB + ") over " + gameMillis + "ms");
            System.out.println("timeWarpSlowsOnlyItsUserOnServer: A(warp)=" + stepsA + " B=" + stepsB
                    + " over " + gameMillis + "ms");
        } finally {
            if (a != null) a.client.close();
            if (b != null) b.client.close();
            server.close();
        }
    }

    /**
     * 60초 이후 낙하 간격이 줄어드는지 서버 전체 경로로 검증한다. 실제로 몇 분을 기다릴 수 없으므로
     * 경기 경과 시간만 150초로 앞당긴 뒤(테스트 전용 접근) 낙하 속도를 잰다: 150초 시점의 간격은 300ms이다.
     */
    private static void pvpSpeedsUpAfterOneMinuteOnServer(TokenVerifier verifier) throws Exception {
        TestStore store = new TestStore();
        RenderGameServer server = new RenderGameServer(verifier, store, ADMIN, 0);
        server.start();
        Probe a = null, b = null;
        try {
            assertEquals(200, request("http://127.0.0.1:" + server.getPort() + "/admin/open",
                    "POST", "Bearer " + ADMIN));
            a = connectProbe(USERS[0], server.getPort());
            b = connectProbe(USERS[1], server.getPort());
            a.client.send(RoomCommand.createRoom(2));
            String roomId = a.await(NetworkUpdate.Type.ROOM_STATE).getRoomId();
            b.client.send(RoomCommand.joinRoom(roomId));
            b.await(NetworkUpdate.Type.ROOM_STATE);
            a.client.send(RoomCommand.setReady(true));
            b.client.send(RoomCommand.setReady(true));
            a.await(NetworkUpdate.Type.MATCH_STARTED);
            b.await(NetworkUpdate.Type.MATCH_STARTED);
            a.await(NetworkUpdate.Type.SNAPSHOT);

            java.lang.reflect.Field roomsField = RenderGameServer.class.getDeclaredField("rooms");
            roomsField.setAccessible(true);
            Object room = ((Map<?, ?>) roomsField.get(server)).get(roomId);
            java.lang.reflect.Field battleField = room.getClass().getDeclaredField("battle");
            battleField.setAccessible(true);
            kr.ac.jbnu.se.tetris.battle.BattleManager battle =
                    (kr.ac.jbnu.se.tetris.battle.BattleManager) battleField.get(room);
            java.lang.reflect.Field elapsedField = kr.ac.jbnu.se.tetris.battle.BattleManager.class
                    .getDeclaredField("elapsedMillis");
            elapsedField.setAccessible(true);
            synchronized (battle) { elapsedField.setLong(battle, 150000L); }

            NetworkUpdate first = a.await(NetworkUpdate.Type.SNAPSHOT);
            long tick0 = totalTicks(first);
            long elapsed0 = first.getBattleState().getElapsedMillis();
            NetworkUpdate last = first;
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (System.nanoTime() < until) {
                NetworkUpdate update = a.updates.poll(50, TimeUnit.MILLISECONDS);
                if (update != null && update.getType() == NetworkUpdate.Type.SNAPSHOT) last = update;
            }
            long steps = totalTicks(last) - tick0;
            long gameMillis = last.getBattleState().getElapsedMillis() - elapsed0;
            // 150초 시점 간격은 300ms: 두 참가자 합쳐 gameMillis/300*2칸. 500ms면 이보다 40% 적고, 400ms 고정이면 25% 적다.
            long expected = gameMillis / 300 * 2;
            if (Math.abs(steps - expected) > 2)
                throw new AssertionError("PvP speed-up not applied: steps=" + steps + " expected~" + expected
                        + " (300ms interval) over " + gameMillis + "ms");
            System.out.println("pvpSpeedsUpAfterOneMinuteOnServer: steps=" + steps + " expected~" + expected
                    + " over " + gameMillis + "ms");
        } finally {
            if (a != null) a.client.close();
            if (b != null) b.client.close();
            server.close();
        }
    }

    private static long totalTicks(NetworkUpdate update) {
        long total = 0;
        for (kr.ac.jbnu.se.tetris.battle.ParticipantState p : update.getBattleState().getParticipants().values())
            total += p.getGameState().getTick();
        return total;
    }

    /**
     * 방 코드 생성기 검증: 항상 6자리이고, 헷갈리는 문자(0/O/1/I/L)를 쓰지 않으며, 31개 문자를 고르게 쓰고,
     * 이미 열려 있는 방의 코드와는 절대 겹치지 않는다(2만 개 방을 채워 놓고 계속 뽑아 확인).
     */
    @SuppressWarnings("unchecked")
    private static void roomCodesAreShortAndAvoidExistingRooms(TokenVerifier verifier) throws Exception {
        RenderGameServer server = new RenderGameServer(verifier, new TestStore(), ADMIN, 0);
        try {
            java.lang.reflect.Method generator = RenderGameServer.class.getDeclaredMethod("newRoomCode");
            generator.setAccessible(true);
            java.lang.reflect.Field roomsField = RenderGameServer.class.getDeclaredField("rooms");
            roomsField.setAccessible(true);
            Map<String, Object> rooms = (Map<String, Object>) roomsField.get(server);
            Class<?> roomClass = Class.forName(RenderGameServer.class.getName() + "$Room");
            java.lang.reflect.Constructor<?> roomConstructor = roomClass.getDeclaredConstructor(String.class);
            roomConstructor.setAccessible(true);
            java.util.Set<Character> usedChars = new java.util.HashSet<Character>();
            for (int i = 0; i < 20000; i++) {
                String code = (String) generator.invoke(server);
                if (code == null || !code.matches("[A-HJ-NP-Z2-9]{6}"))
                    throw new AssertionError("Bad room code: " + code);
                if (rooms.containsKey(code)) throw new AssertionError("Room code collided with open room: " + code);
                for (char c : code.toCharArray()) usedChars.add(c);
                rooms.put(code, roomConstructor.newInstance(code));
            }
            if (usedChars.size() != 31)
                throw new AssertionError("Room code alphabet not fully used: " + usedChars.size());
            System.out.println("roomCodesAreShortAndAvoidExistingRooms: 20000 unique codes, 31 characters used");
        } finally {
            server.close();
        }
    }

    private static Probe connectProbe(String user, int port) throws Exception {
        Probe probe = new Probe(user);
        probe.client.connect(new ConnectionOptions(new URI("ws://127.0.0.1:" + port + "/ws"), user));
        probe.await(NetworkUpdate.Type.CONNECTED);
        return probe;
    }

    private static int request(String url, String method, String auth) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        if (auth != null) connection.setRequestProperty("Authorization", auth);
        int result = connection.getResponseCode();
        connection.disconnect();
        return result;
    }

    private static String requestText(String url, String auth) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        connection.setRequestProperty("Authorization", auth);
        if (connection.getResponseCode() != 200) throw new IOException("Status request rejected");
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[512];
            int count;
            while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally { connection.disconnect(); }
    }

    private static void fragmentedAndMalformedBoundary(int port) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            OutputStream output = socket.getOutputStream();
            InputStream input = socket.getInputStream();
            sendUpgrade(socket, port, USERS[0]);
            String response = readHeaders(input);
            if (!response.startsWith("HTTP/1.1 101")) throw new AssertionError("WS upgrade failed: " + response);
            if (readRawUpdate(input).getType() != NetworkUpdate.Type.CONNECTED) {
                throw new AssertionError("Expected authenticated connected update");
            }
            byte[] room = WireCodec.encodeRequestPayload(WireRequest.room(1, RoomCommand.createRoom(2)));
            int midpoint = room.length / 2;
            sendMaskedFrame(output, 0x01, false, java.util.Arrays.copyOfRange(room, 0, midpoint));
            sendMaskedFrame(output, 0x00, true, java.util.Arrays.copyOfRange(room, midpoint, room.length));
            NetworkUpdate update;
            do { update = readRawUpdate(input); }
            while (update.getType() != NetworkUpdate.Type.ROOM_STATE);
            sendMaskedFrame(output, 0x01, true, "{bad".getBytes(StandardCharsets.UTF_8));
            if (input.read() != -1) throw new AssertionError("Malformed WS request was not closed");
        }
    }

    private static void oversizeFrameBoundary(int port) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            sendUpgrade(socket, port, USERS[0]);
            if (!readHeaders(socket.getInputStream()).startsWith("HTTP/1.1 101")) {
                throw new AssertionError("WS upgrade failed");
            }
            readRawUpdate(socket.getInputStream());
            OutputStream output = socket.getOutputStream();
            output.write(0x81);
            output.write(0xff); // mask bit + 64-bit length
            long length = WireCodec.MAX_FRAME_BYTES + 1L;
            for (int shift = 56; shift >= 0; shift -= 8) output.write((int) (length >>> shift));
            output.write(new byte[] { 1, 2, 3, 4 });
            output.flush();
            int response = socket.getInputStream().read();
            if (response != -1 && (response & 0x0f) != 8) {
                throw new AssertionError("Oversize frame was not rejected");
            }
        }
    }

    private static void sendUpgrade(Socket socket, int port, String token) throws IOException {
        String key = Base64.getEncoder().encodeToString(new byte[16]);
        String upgrade = "GET /ws HTTP/1.1\r\nHost: 127.0.0.1:" + port
                + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: " + key
                + "\r\nSec-WebSocket-Version: 13\r\nAuthorization: Bearer " + token + "\r\n\r\n";
        socket.getOutputStream().write(upgrade.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

    private static String readHeaders(InputStream input) throws IOException {
        StringBuilder text = new StringBuilder();
        while (text.length() < 8192) {
            int next = input.read();
            if (next == -1) throw new IOException("Closed before headers");
            text.append((char) next);
            if (text.toString().endsWith("\r\n\r\n")) return text.toString();
        }
        throw new IOException("Header too large");
    }

    private static NetworkUpdate readRawUpdate(InputStream input) throws IOException {
        int first = input.read(), second = input.read();
        if (first == -1 || second == -1) throw new IOException("Closed before update");
        int length = second & 0x7f;
        if (length == 126) length = (input.read() << 8) | input.read();
        else if (length == 127) throw new IOException("Unexpected long update");
        byte[] payload = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = input.read(payload, offset, length - offset);
            if (count < 0) throw new IOException("Truncated update");
            offset += count;
        }
        return WireCodec.decodeUpdatePayload(payload);
    }

    private static void sendMaskedFrame(OutputStream output, int opcode, boolean fin, byte[] payload)
            throws IOException {
        output.write((fin ? 0x80 : 0) | opcode);
        if (payload.length < 126) output.write(0x80 | payload.length);
        else if (payload.length <= 65535) {
            output.write(0x80 | 126);
            output.write(payload.length >>> 8);
            output.write(payload.length);
        } else throw new IllegalArgumentException("Test frame too large");
        byte[] mask = { 1, 2, 3, 4 };
        output.write(mask);
        for (int i = 0; i < payload.length; i++) output.write(payload[i] ^ mask[i & 3]);
        output.flush();
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }

    private static final class Probe {
        private final String user;
        private final WebSocketNetworkClient client = new WebSocketNetworkClient();
        private final ArrayBlockingQueue<NetworkUpdate> updates = new ArrayBlockingQueue<NetworkUpdate>(512);
        private Probe(String user) {
            this.user = user;
            client.subscribe(update -> updates.offer(update));
        }
        private NetworkUpdate await(NetworkUpdate.Type type) throws Exception { return await(type, null); }
        private NetworkUpdate.Type awaitConnectionOutcome() throws Exception {
            return awaitConnectionUpdate().getType();
        }
        private NetworkUpdate awaitConnectionUpdate() throws Exception {
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < until) {
                NetworkUpdate update = updates.poll(100, TimeUnit.MILLISECONDS);
                if (update == null) continue;
                if (update.getType() == NetworkUpdate.Type.CONNECTED
                        || update.getType() == NetworkUpdate.Type.CONNECTION_FAILED) return update;
            }
            throw new AssertionError("Timed out waiting for connection outcome");
        }
        private NetworkUpdate awaitOutcome(long requestId) throws Exception {
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < until) {
                NetworkUpdate update = updates.poll(100, TimeUnit.MILLISECONDS);
                if (update == null) continue;
                if (update.getType() == NetworkUpdate.Type.REQUEST_OUTCOME
                        && update.getRequestOutcome().getRequestId() == requestId) return update;
            }
            throw new AssertionError("Timed out waiting for request " + requestId);
        }
        private NetworkUpdate await(NetworkUpdate.Type type, String reason) throws Exception {
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < until) {
                NetworkUpdate update = updates.poll(100, TimeUnit.MILLISECONDS);
                if (update == null) continue;
                if (update.getType() == NetworkUpdate.Type.CONNECTION_FAILED
                        || update.getType() == NetworkUpdate.Type.CLOSED) {
                    throw new AssertionError("Unexpected disconnect: " + update.getReasonCode());
                }
                if (update.getType() == type && (reason == null || reason.equals(update.getReasonCode()))) return update;
            }
            throw new AssertionError("Timed out waiting for " + type + " " + reason);
        }
    }

    private static class TestStore implements RankedMatchStore {
        protected final Map<String, MatchRecord> records = new ConcurrentHashMap<String, MatchRecord>();
        private String ownerRunId;
        private boolean enabled;
        private volatile boolean expired;
        private volatile boolean leaseUnavailable;
        private volatile boolean failNextRenew;

        @Override public synchronized RunLease claimRun(String runId) throws IOException {
            if (leaseUnavailable) throw new IOException("DB unavailable");
            if (ownerRunId != null && ownerRunId.equals(runId)) return new RunLease(!expired, enabled);
            if (ownerRunId != null && !expired || ownerRunId == null && !enabled)
                return new RunLease(false, enabled);
            if (ownerRunId != null) recoverOldRun(ownerRunId);
            ownerRunId = runId;
            expired = false;
            return new RunLease(true, enabled);
        }
        @Override public synchronized RunLease renewRun(String runId) throws IOException {
            if (leaseUnavailable) throw new IOException("DB unavailable");
            if (failNextRenew) { failNextRenew = false; throw new IOException("Transient DB error"); }
            return new RunLease(runId.equals(ownerRunId) && !expired, enabled);
        }
        @Override public synchronized RunLease setAdmission(String runId, boolean open) throws IOException {
            if (leaseUnavailable) throw new IOException("DB unavailable");
            if (ownerRunId != null && !ownerRunId.equals(runId) && !expired)
                return new RunLease(false, enabled);
            if (ownerRunId != null && !ownerRunId.equals(runId)) recoverOldRun(ownerRunId);
            if (ownerRunId != null && ownerRunId.equals(runId) && expired)
                return new RunLease(false, enabled);
            ownerRunId = runId;
            expired = false;
            enabled = open;
            return new RunLease(true, enabled);
        }
        private void recoverOldRun(String oldRunId) {
            for (Map.Entry<String, MatchRecord> entry : records.entrySet()) {
                MatchRecord old = entry.getValue();
                if (oldRunId.equals(old.getServerRunId()) && old.getStatus() == MatchRecord.Status.RUNNING) {
                    records.put(entry.getKey(), new MatchRecord(old.getMatchId(), oldRunId,
                            old.getFirstUserId(), old.getSecondUserId(), MatchRecord.Status.VOID,
                            null, "SERVER_STOPPED", null, null, null, null));
                }
            }
        }
        @Override public synchronized MatchRecord beginMatch(String matchId, String runId, String rulesVersion,
                String first, String second) throws IOException {
            if (!runId.equals(ownerRunId) || expired || !enabled) throw new IOException("RUN_NOT_OWNER");
            if (first.equals(second)) throw new IOException("self match");
            MatchRecord existing = records.get(matchId);
            if (existing != null) return existing;
            for (MatchRecord record : records.values()) {
                if (record.getStatus() == MatchRecord.Status.RUNNING
                        && (first.equals(record.getFirstUserId()) || second.equals(record.getFirstUserId())
                        || first.equals(record.getSecondUserId()) || second.equals(record.getSecondUserId()))) {
                    throw new IOException("active match");
                }
            }
            MatchRecord record = new MatchRecord(matchId, runId, first, second,
                    MatchRecord.Status.RUNNING, null, null, null, null, null, null);
            records.put(matchId, record);
            return record;
        }
        @Override public synchronized MatchRecord finishMatch(String matchId, String runId,
                String winner, String reason) throws IOException {
            if (!runId.equals(ownerRunId) || expired) throw new IOException("RUN_NOT_OWNER");
            MatchRecord old = records.get(matchId);
            if (old == null || !old.getServerRunId().equals(runId)) throw new IOException("Unknown match");
            if (old.getStatus() == MatchRecord.Status.FINALIZED) return old;
            if (old.getStatus() == MatchRecord.Status.VOID) throw new IOException("Voided match");
            MatchRecord next = new MatchRecord(matchId, runId, old.getFirstUserId(), old.getSecondUserId(),
                    MatchRecord.Status.FINALIZED, winner, reason, 1000, 1016, 1000, 984);
            records.put(matchId, next);
            return next;
        }
        @Override public synchronized MatchRecord voidMatch(String matchId, String runId,
                String reason) throws IOException {
            if (!runId.equals(ownerRunId) || expired) throw new IOException("RUN_NOT_OWNER");
            MatchRecord old = records.get(matchId);
            if (old == null || !old.getServerRunId().equals(runId)) throw new IOException("Unknown match");
            if (old.getStatus() == MatchRecord.Status.FINALIZED) throw new IOException("Finalized match");
            MatchRecord next = new MatchRecord(matchId, runId, old.getFirstUserId(), old.getSecondUserId(),
                    MatchRecord.Status.VOID, null, reason, null, null, null, null);
            records.put(matchId, next);
            return next;
        }
        @Override public synchronized MatchRecord getMatch(String matchId) throws IOException {
            return records.get(matchId);
        }
        @Override public int voidStoppedRun(String currentRunId, String stoppedRunId) { return 0; }
    }

    private static final class FlakyStore extends TestStore {
        private final AtomicInteger beginCalls = new AtomicInteger();
        private final CountDownLatch firstEntered = new CountDownLatch(1);
        private final CountDownLatch releaseFirst = new CountDownLatch(1);
        private final CountDownLatch firstReturned = new CountDownLatch(1);
        private volatile String firstMatchId;
        private volatile String firstRunId;
        private volatile String firstUserId;
        private volatile String secondUserId;
        private final AtomicInteger firstReads = new AtomicInteger();

        @Override public MatchRecord beginMatch(String matchId, String runId, String rulesVersion,
                String first, String second) throws IOException {
            if (beginCalls.incrementAndGet() == 1) {
                firstMatchId = matchId;
                firstRunId = runId;
                firstUserId = first;
                secondUserId = second;
                firstEntered.countDown();
                try { releaseFirst.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                firstReturned.countDown();
                throw new IOException("Lost first registration response");
            }
            return super.beginMatch(matchId, runId, rulesVersion, first, second);
        }

        @Override public synchronized MatchRecord getMatch(String matchId) throws IOException {
            if (matchId.equals(firstMatchId) && firstReads.getAndIncrement() == 0) {
                throw new IOException("Temporary lookup failure");
            }
            return super.getMatch(matchId);
        }

        private void commitLate() {
            records.put(firstMatchId, new MatchRecord(firstMatchId, firstRunId, firstUserId,
                    secondUserId, MatchRecord.Status.RUNNING, null, null, null, null, null, null));
        }
    }

    private static final class DelayedFinishStore extends TestStore {
        private final AtomicInteger finishCalls = new AtomicInteger();
        private final CountDownLatch firstFinished = new CountDownLatch(1);
        private final CountDownLatch releaseResponse = new CountDownLatch(1);

        @Override public MatchRecord finishMatch(String matchId, String runId, String winner,
                String reason) throws IOException {
            MatchRecord saved = super.finishMatch(matchId, runId, winner, reason);
            if (finishCalls.incrementAndGet() == 1) {
                firstFinished.countDown();
                try { releaseResponse.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                throw new IOException("Response lost after commit");
            }
            return saved;
        }
    }
}
