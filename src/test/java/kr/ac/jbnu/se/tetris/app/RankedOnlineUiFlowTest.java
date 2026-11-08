package kr.ac.jbnu.se.tetris.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Component;
import java.awt.Container;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import kr.ac.jbnu.se.tetris.auth.AuthException;
import kr.ac.jbnu.se.tetris.auth.AuthIdentity;
import kr.ac.jbnu.se.tetris.auth.TokenVerifier;
import kr.ac.jbnu.se.tetris.app.session.SessionPhase;
import kr.ac.jbnu.se.tetris.network.server.RenderGameServer;
import kr.ac.jbnu.se.tetris.ranking.MatchRecord;
import kr.ac.jbnu.se.tetris.ranking.RankedMatchStore;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.LeaderboardPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.LoginPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.MainLobbyPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.ResultPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.WaitingRoomPanel;

/** Headless Swing -> Auth -> WebSocket battle -> save gate -> leaderboard integration. */
public final class RankedOnlineUiFlowTest {
    private static final String ADMIN = "local-test-admin-key-1234567890";
    private static final String FIRST = "66bb5a13-f250-4aa5-aa75-913cc7430001";
    private static final String SECOND = "66bb5a13-f250-4aa5-aa75-913cc7430002";

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        DelayedStore store = new DelayedStore();
        TokenVerifier verifier = token -> {
            if (FIRST.equals(token) || SECOND.equals(token))
                return new AuthIdentity(token, "Player", System.currentTimeMillis() / 1000L + 3600);
            throw new AuthException("INVALID_TOKEN");
        };
        HttpServer auth = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        auth.createContext("/", request -> respondAuth(request, store));
        auth.start();
        RenderGameServer server = new RenderGameServer(verifier, store, ADMIN, 0);
        SeongeunApplication[] apps = new SeongeunApplication[2];
        String[] keys = {"tetris.server.url", "tetris.supabase.url", "tetris.supabase.publishableKey",
                "tetris.online.provider"};
        String[] previous = new String[keys.length];
        for (int i = 0; i < keys.length; i++) previous[i] = System.getProperty(keys[i]);
        try {
            server.start();
            int port = server.getPort();
            check(httpPost("http://127.0.0.1:" + port + "/admin/open", ADMIN) == 200, "admission open");
            System.setProperty(keys[0], "ws://127.0.0.1:" + port + "/ws");
            System.setProperty(keys[1], "http://127.0.0.1:" + auth.getAddress().getPort());
            System.setProperty(keys[2], "public-test-key");
            System.setProperty(keys[3], "supabase");
            edt(() -> {
                apps[0] = new SeongeunApplication(new Random(1), null);
                apps[1] = new SeongeunApplication(new Random(2), null);
                for (int i = 0; i < 2; i++) {
                    button(child(apps[i].getScreens(), MainLobbyPanel.class), "온라인 대전").doClick();
                    LoginPanel login = child(apps[i].getScreens(), LoginPanel.class);
                    child(login, JTextField.class).setText(i == 0 ? "first@example.test" : "second@example.test");
                    child(login, JPasswordField.class).setText("test-password");
                    button(login, "온라인 로그인").doClick();
                }
            });
            awaitEdt(() -> apps[0].isSignedIn() && apps[1].isSignedIn()
                    && apps[0].isOnlineConnected() && apps[1].isOnlineConnected(), "both authenticated and connected");
            edt(() -> apps[0].createRoomWithName("공식 대전"));
            awaitEdt(() -> apps[0].getRoomState() != null, "room created");
            String roomId = onEdt(() -> apps[0].getRoomState().getRoomId());
            edt(() -> apps[1].joinRoom(roomId));
            awaitEdt(() -> apps[0].getRoomState().getReadyByParticipantId().size() == 2
                    && apps[1].getRoomState() != null
                    && apps[1].getRoomState().getReadyByParticipantId().size() == 2, "joined room");
            edt(() -> {
                button(child(apps[0].getScreens(), WaitingRoomPanel.class), "waitingReady").doClick();
                button(child(apps[1].getScreens(), WaitingRoomPanel.class), "waitingReady").doClick();
            });
            awaitEdt(() -> running(apps[0]) && running(apps[1]), "ranked match started");
            edt(() -> {
                check(!apps[0].isStoryClockRunning(), "no local match tick");
                button(apps[0].getScreens(), "대전 포기 [ESC]").doClick();
            });
            check(store.finishEntered.await(5, TimeUnit.SECONDS), "persistence reached");
            awaitEdt(() -> finished(apps[1])
                    && labelContains(child(apps[1].getScreens(), ResultPanel.class), "저장 중"),
                    "result shows pending save");
            check(store.records.size() == 1, "exactly one match registered");
            check(store.records.values().iterator().next().getStatus() == MatchRecord.Status.RUNNING,
                    "record still pending while DB held");
            check(onEdt(() -> button(child(apps[1].getScreens(), ResultPanel.class), "로비로").isEnabled()),
                    "held database worker keeps EDT responsive");
            edt(() -> apps[0].close());
            store.releaseFinish.countDown();
            awaitEdt(() -> store.records.values().iterator().next().getStatus() == MatchRecord.Status.FINALIZED
                    && labelContains(child(apps[1].getScreens(), ResultPanel.class), "저장되었습니다"),
                    "saved result updates current result");
            edt(() -> {
                button(child(apps[1].getScreens(), ResultPanel.class), "로비로").doClick();
                button(child(apps[1].getScreens(), MainLobbyPanel.class), "PvP 랭킹").doClick();
            });
            awaitEdt(() -> "LEADERBOARD".equals(apps[1].getCurrentScreen())
                    && child(child(apps[1].getScreens(), LeaderboardPanel.class), JTable.class).getRowCount() == 1,
                    "leaderboard shows finalized winner");
            check(onEdt(() -> child(child(apps[1].getScreens(), LeaderboardPanel.class), JTable.class)
                    .getValueAt(0, 2)).equals(1016), "leaderboard rating");
        } finally {
            store.releaseFinish.countDown();
            edt(() -> { for (SeongeunApplication app : apps) if (app != null) app.close(); });
            server.close();
            auth.stop(0);
            for (int i = 0; i < keys.length; i++) {
                if (previous[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], previous[i]);
            }
        }
        System.out.println("PASS RankedOnlineUiFlowTest");
    }

    private static boolean running(SeongeunApplication app) {
        return "BATTLE".equals(app.getCurrentScreen()) && app.getMatchSnapshot() != null
                && app.getMatchSnapshot().getPhase() == SessionPhase.RUNNING;
    }
    private static boolean finished(SeongeunApplication app) {
        return "RESULT".equals(app.getCurrentScreen()) && app.getMatchSnapshot() != null
                && app.getMatchSnapshot().getPhase() == SessionPhase.FINISHED;
    }

    private static void respondAuth(HttpExchange request, DelayedStore store) throws IOException {
        String path = request.getRequestURI().getPath();
        String body = new String(read(request), StandardCharsets.UTF_8);
        check("public-test-key".equals(request.getRequestHeaders().getFirst("apikey")), "public Auth key");
        if (path.equals("/auth/v1/token")) {
            String user = body.contains("first@example.test") ? FIRST : SECOND;
            String response = "{\"access_token\":\"" + user + "\",\"refresh_token\":\"r-" + user
                    + "\",\"expires_in\":3600,\"user\":{\"id\":\"" + user + "\"}}";
            answer(request, 200, response);
        } else if (path.equals("/rest/v1/rpc/ranked_leaderboard")) {
            check(("Bearer " + FIRST).equals(request.getRequestHeaders().getFirst("Authorization"))
                    || ("Bearer " + SECOND).equals(request.getRequestHeaders().getFirst("Authorization")),
                    "signed-in leaderboard request");
            boolean saved = store.records.values().stream().anyMatch(record ->
                    record.getStatus() == MatchRecord.Status.FINALIZED);
            answer(request, 200, saved ? "[{\"rank\":1,\"user_id\":\"" + SECOND
                    + "\",\"display_name\":\"Player\",\"rating\":1016,\"wins\":1,"
                    + "\"losses\":0,\"games\":1}]" : "[]");
        } else answer(request, 404, "{}");
    }

    private static byte[] read(HttpExchange request) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[512]; int n;
            while ((n = request.getRequestBody().read(buffer)) != -1) output.write(buffer, 0, n);
            return output.toByteArray();
        }
    }
    private static void answer(HttpExchange request, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        request.sendResponseHeaders(status, bytes.length);
        request.getResponseBody().write(bytes); request.close();
    }
    private static int httpPost(String url, String token) throws IOException {
        HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();
        request.setRequestMethod("POST"); request.setRequestProperty("Authorization", "Bearer " + token);
        request.setConnectTimeout(2000); request.setReadTimeout(2000);
        try { return request.getResponseCode(); } finally { request.disconnect(); }
    }
    private static <T extends Component> T child(Component root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container) for (Component nested : ((Container) root).getComponents()) {
            T found = child(nested, type);
            if (found != null) return found;
        }
        return null;
    }
    private static AbstractButton button(Component root, String label) {
        if (root instanceof AbstractButton) {
            AbstractButton candidate = (AbstractButton) root;
            if (label.equals(candidate.getName()) || label.equals(candidate.getText())) return candidate;
        }
        if (root instanceof Container) for (Component nested : ((Container) root).getComponents()) {
            AbstractButton found = button(nested, label);
            if (found != null) return found;
        }
        return null;
    }
    private static boolean labelContains(Component root, String phrase) {
        if (root instanceof JLabel && ((JLabel) root).getText().contains(phrase)) return true;
        if (root instanceof Container) for (Component nested : ((Container) root).getComponents())
            if (labelContains(nested, phrase)) return true;
        return false;
    }
    private static void edt(Runnable runnable) throws Exception {
        onEdt(() -> { runnable.run(); return true; });
    }
    private static <T> T onEdt(java.util.concurrent.Callable<T> work) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeLater(() -> {
            try { value.set(work.call()); }
            catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        });
        check(done.await(20, TimeUnit.SECONDS), "EDT blocked");
        if (failure.get() != null) throw new AssertionError("EDT failure", failure.get());
        return value.get();
    }
    private static void awaitEdt(BooleanSupplier condition, String name) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!onEdt(() -> condition.getAsBoolean()) && System.nanoTime() < deadline)
            Thread.sleep(15);
        check(onEdt(() -> condition.getAsBoolean()), name);
    }
    private static void check(boolean valid, String name) {
        if (!valid) throw new AssertionError(name);
    }

    private static final class DelayedStore implements RankedMatchStore {
        final Map<String, MatchRecord> records = new ConcurrentHashMap<>();
        final CountDownLatch finishEntered = new CountDownLatch(1);
        final CountDownLatch releaseFinish = new CountDownLatch(1);
        private String ownerRunId;
        private boolean enabled;
        @Override public synchronized kr.ac.jbnu.se.tetris.ranking.RunLease claimRun(String runId) {
            if (ownerRunId == null && enabled) ownerRunId = runId;
            return new kr.ac.jbnu.se.tetris.ranking.RunLease(runId.equals(ownerRunId), enabled);
        }
        @Override public synchronized kr.ac.jbnu.se.tetris.ranking.RunLease renewRun(String runId) {
            return new kr.ac.jbnu.se.tetris.ranking.RunLease(runId.equals(ownerRunId), enabled);
        }
        @Override public synchronized kr.ac.jbnu.se.tetris.ranking.RunLease setAdmission(String runId,
                boolean open) {
            if (ownerRunId == null) ownerRunId = runId;
            if (!runId.equals(ownerRunId))
                return new kr.ac.jbnu.se.tetris.ranking.RunLease(false, enabled);
            enabled = open;
            return new kr.ac.jbnu.se.tetris.ranking.RunLease(true, enabled);
        }
        @Override public synchronized MatchRecord beginMatch(String matchId, String runId,
                String rulesVersion, String first, String second) throws IOException {
            MatchRecord existing = records.get(matchId);
            if (existing != null) return existing;
            MatchRecord running = new MatchRecord(matchId, runId, first, second,
                    MatchRecord.Status.RUNNING, null, null, null, null, null, null);
            records.put(matchId, running);
            return running;
        }
        @Override public MatchRecord finishMatch(String matchId, String runId,
                String winner, String reason) throws IOException {
            finishEntered.countDown();
            try { if (!releaseFinish.await(8, TimeUnit.SECONDS)) throw new IOException("Delayed save timeout"); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
            synchronized (this) {
                MatchRecord old = records.get(matchId);
                if (old == null) throw new IOException("Unknown match");
                if (old.getStatus() == MatchRecord.Status.FINALIZED) return old;
                MatchRecord saved = new MatchRecord(matchId, runId, old.getFirstUserId(), old.getSecondUserId(),
                        MatchRecord.Status.FINALIZED, winner, reason, 1000,
                        winner.equals(old.getFirstUserId()) ? 1016 : 984, 1000,
                        winner.equals(old.getSecondUserId()) ? 1016 : 984);
                records.put(matchId, saved); return saved;
            }
        }
        @Override public synchronized MatchRecord voidMatch(String matchId, String runId, String reason)
                throws IOException {
            MatchRecord old = records.get(matchId);
            if (old == null) throw new IOException("Unknown match");
            MatchRecord voided = new MatchRecord(matchId, runId, old.getFirstUserId(), old.getSecondUserId(),
                    MatchRecord.Status.VOID, null, reason, null, null, null, null);
            records.put(matchId, voided); return voided;
        }
        @Override public synchronized MatchRecord getMatch(String matchId) { return records.get(matchId); }
        @Override public int voidStoppedRun(String currentRunId, String stoppedRunId) { return 0; }
    }
}
