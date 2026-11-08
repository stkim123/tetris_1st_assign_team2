package kr.ac.jbnu.se.tetris.network.server;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import kr.ac.jbnu.se.tetris.auth.AuthSession;
import kr.ac.jbnu.se.tetris.auth.FirebaseAuthService;
import kr.ac.jbnu.se.tetris.firebase.FirebaseConfig;
import kr.ac.jbnu.se.tetris.firebase.server.FirebaseServerContext;
import kr.ac.jbnu.se.tetris.firebase.server.FirebaseTokenVerifier;
import kr.ac.jbnu.se.tetris.firebase.server.FirestoreRankedMatchStore;
import kr.ac.jbnu.se.tetris.network.ConnectionOptions;
import kr.ac.jbnu.se.tetris.network.NetworkUpdate;
import kr.ac.jbnu.se.tetris.network.RoomCommand;
import kr.ac.jbnu.se.tetris.network.WebSocketNetworkClient;
import kr.ac.jbnu.se.tetris.ranking.LeaderboardEntry;
import kr.ac.jbnu.se.tetris.ranking.LeaderboardService;
import kr.ac.jbnu.se.tetris.ranking.MatchRecord;

/** Explicit local emulator check for Auth -> PvP -> Firestore -> authenticated ranking. */
public final class FirebaseCloudServerEmulatorCheck {
    private static final String ADMIN = "emulator-admin-token-123456789";

    private FirebaseCloudServerEmulatorCheck() { }

    public static void main(String[] args) throws Exception {
        String project = System.getenv("FIREBASE_PROJECT_ID");
        String authHost = System.getenv("FIREBASE_AUTH_EMULATOR_HOST");
        String dbHost = System.getenv("FIRESTORE_EMULATOR_HOST");
        require("demo-cloud-server".equals(project) && loopback(authHost) && loopback(dbHost),
                "Use separate localhost emulators for demo-cloud-server");
        FirebaseConfig config = FirebaseConfig.forLocalEmulator(project, "demo-api-key",
                URI.create("http://" + authHost + "/identitytoolkit.googleapis.com/v1/"),
                URI.create("http://" + authHost + "/securetoken.googleapis.com/v1/"));
        FirebaseAuthService firstAuth = new FirebaseAuthService(config);
        FirebaseAuthService secondAuth = new FirebaseAuthService(config);
        String firstName = "p" + shortId(), secondName = "q" + shortId();
        AuthSession firstSession = firstAuth.signUpUsername(firstName, "IntegrationPassword123!").getSession();
        AuthSession secondSession = secondAuth.signUpUsername(secondName, "IntegrationPassword123!").getSession();
        require(firstSession != null && secondSession != null, "Firebase accounts signed up");
        try (FirebaseServerContext context = FirebaseServerContext.fromEnvironment()) {
            FirestoreRankedMatchStore store = new FirestoreRankedMatchStore(
                    context.getFirestore(), context.getAuth());
            require(!context.getFirestore().collection("ranked_control").document("singleton")
                    .get().get().exists(), "Use a fresh demo-cloud-server database");
            RenderGameServer server = new RenderGameServer(new FirebaseTokenVerifier(
                    context.getAuth(), context.getFirestore()),
                    store, ADMIN, 0);
            Probe first = null, second = null;
            try {
                server.start();
                String base = "http://127.0.0.1:" + server.getPort();
                require(status(base + "/health", null) == 200, "Container liveness");
                require(status(base + "/healthz", null) == 200, "Legacy liveness path");
                require(status(base + "/ready", null) == 503, "No admission before lease open");
                require(status(base + "/readyz", null) == 503, "Legacy readiness path");
                require(status(base + "/api/leaderboard", null) == 503,
                        "Leaderboard closed until admission opens");
                require(post(base + "/admin/open", "Bearer " + ADMIN) == 200, "Open admission");
                require(status(base + "/ready", null) == 200, "Lease-backed readiness");
                require(status(base + "/readyz", null) == 200, "Legacy readiness after open");
                require(status(base + "/api/leaderboard", null) == 401,
                        "Leaderboard requires a user token");
                require(status(base + "/api/leaderboard", "Bearer invalid-token") == 401,
                        "Leaderboard rejects an invalid token");
                first = connect(server.getPort(), firstSession.getAccessToken());
                second = connect(server.getPort(), secondSession.getAccessToken());
                first.client.send(RoomCommand.createRoom(2));
                String roomId = first.await(NetworkUpdate.Type.ROOM_STATE).getRoomId();
                second.client.send(RoomCommand.joinRoom(roomId));
                second.await(NetworkUpdate.Type.ROOM_STATE);
                first.client.send(RoomCommand.setReady(true));
                second.client.send(RoomCommand.setReady(true));
                String matchId = first.await(NetworkUpdate.Type.MATCH_STARTED).getMatchId();
                second.await(NetworkUpdate.Type.MATCH_STARTED);
                MatchRecord running = store.getMatch(matchId);
                require(running != null && running.getStatus() == MatchRecord.Status.RUNNING,
                        "Server registered the authoritative match");
                first.client.send(RoomCommand.leaveRoom());
                NetworkUpdate saved = second.await(NetworkUpdate.Type.RANKED_SAVE_STATUS, "SAVED");
                require(matchId.equals(saved.getMatchId()), "Saved result belongs to the game");
                MatchRecord result = store.getMatch(matchId);
                require(result.getStatus() == MatchRecord.Status.FINALIZED
                        && secondSession.getUserId().equals(result.getWinnerUserId()),
                        "Forfeit stored the correct winner exactly once");
                List<LeaderboardEntry> board = new LeaderboardService(
                        URI.create("ws://127.0.0.1:" + server.getPort() + "/ws"))
                        .top100(secondSession.getAccessToken());
                require(board.size() == 2 && board.get(0).getUserId().equals(secondSession.getUserId())
                        && board.get(0).getRating() == 1016 && board.get(0).getWins() == 1
                        && board.get(0).getDisplayName().equals(secondName),
                        "Authenticated HTTP ranking reflects persisted result");
                require(board.get(1).getUserId().equals(firstSession.getUserId())
                        && board.get(1).getRating() == 984 && board.get(1).getLosses() == 1,
                        "Losing player appears once with the correct rating");
                require(post(base + "/admin/drain", "Bearer " + ADMIN) == 200, "Persist drain");
                require(status(base + "/ready", null) == 503, "Drain removes readiness");
                require(status(base + "/readyz", null) == 503, "Legacy readiness after drain");
                require(status(base + "/api/leaderboard", "Bearer " + secondSession.getAccessToken()) == 503,
                        "Drained server rejects new leaderboard work");
                System.out.println("Firebase Cloud server emulator end-to-end PASS");
            } finally {
                if (first != null) first.client.close();
                if (second != null) second.client.close();
                server.close();
            }
        }
    }

    private static Probe connect(int port, String token) throws Exception {
        Probe probe = new Probe();
        probe.client.connect(new ConnectionOptions(URI.create("ws://127.0.0.1:" + port + "/ws"), token));
        probe.await(NetworkUpdate.Type.CONNECTED);
        return probe;
    }

    private static int status(String url, String authorization) throws IOException {
        return http(url, "GET", authorization);
    }

    private static int post(String url, String authorization) throws IOException {
        return http(url, "POST", authorization);
    }

    private static int http(String url, String method, String authorization) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        if (authorization != null) connection.setRequestProperty("Authorization", authorization);
        try { return connection.getResponseCode(); }
        finally { connection.disconnect(); }
    }

    private static boolean loopback(String host) {
        return host != null && (host.matches("127\\.0\\.0\\.1:[0-9]{1,5}")
                || host.matches("localhost:[0-9]{1,5}"));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Probe {
        private final WebSocketNetworkClient client = new WebSocketNetworkClient();
        private final ArrayBlockingQueue<NetworkUpdate> updates = new ArrayBlockingQueue<NetworkUpdate>(128);
        private Probe() { client.subscribe(updates::offer); }
        private NetworkUpdate await(NetworkUpdate.Type type) throws Exception { return await(type, null); }
        private NetworkUpdate await(NetworkUpdate.Type type, String reason) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
            while (System.nanoTime() < deadline) {
                NetworkUpdate update = updates.poll(100, TimeUnit.MILLISECONDS);
                if (update == null) continue;
                if (update.getType() == NetworkUpdate.Type.CONNECTION_FAILED
                        || update.getType() == NetworkUpdate.Type.CLOSED)
                    throw new AssertionError("Unexpected connection loss: " + update.getReasonCode());
                if (update.getType() == type && (reason == null || reason.equals(update.getReasonCode())))
                    return update;
            }
            throw new AssertionError("Timed out waiting for " + type + " " + reason);
        }
    }
}
