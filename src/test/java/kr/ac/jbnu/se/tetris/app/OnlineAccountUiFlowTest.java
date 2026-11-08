package kr.ac.jbnu.se.tetris.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Component;
import java.awt.Container;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.AbstractButton;
import javax.swing.JPasswordField;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.LeaderboardPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.LoginPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.MainLobbyPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.SettingsPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.SignUpPanel;

/** 현재 앱의 비동기 인증, 만료 전 갱신, 계정 실패, 종료 후 응답을 검증한다. */
public final class OnlineAccountUiFlowTest {
    private static final String USER = "00000000-0000-4000-8000-000000000001";
    private OnlineAccountUiFlowTest() { }

    public static void main(String[] args) throws Exception {
        CountDownLatch firstEntered = new CountDownLatch(1), releaseFirst = new CountDownLatch(1);
        CountDownLatch lateEntered = new CountDownLatch(1), releaseLate = new CountDownLatch(1);
        AtomicInteger logins = new AtomicInteger(), refreshes = new AtomicInteger(), signups = new AtomicInteger();
        AtomicReference<String> rankingBearer = new AtomicReference<String>();
        HttpServer api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext("/", exchange -> handle(exchange, firstEntered, releaseFirst, lateEntered,
                releaseLate, logins, refreshes, signups, rankingBearer));
        api.start();
        String[] keys = {"tetris.server.url", "tetris.supabase.url", "tetris.supabase.publishableKey",
                "tetris.online.provider"};
        String[] previous = new String[keys.length];
        for (int i = 0; i < keys.length; i++) previous[i] = System.getProperty(keys[i]);
        System.setProperty(keys[0], "ws://127.0.0.1:12345/ws");
        System.setProperty(keys[1], "http://127.0.0.1:" + api.getAddress().getPort());
        System.setProperty(keys[2], "sb_publishable_fixture");
        System.setProperty(keys[3], "supabase");
        SeongeunApplication[] app = new SeongeunApplication[1];
        try {
            onEdt(() -> {
                app[0] = new SeongeunApplication(new Random(441), null);
                button(panel(app[0], MainLobbyPanel.class), "PvP 랭킹").doClick();
                login(app[0], "Player_01", "password123");
            });
            check(firstEntered.await(3, TimeUnit.SECONDS), "login HTTP started off EDT");
            onEdt(() -> {
                LoginPanel panel = panel(app[0], LoginPanel.class);
                check(!button(panel, "온라인 로그인").isEnabled(), "busy prevents duplicate login");
                check(child(panel, JPasswordField.class).getPassword().length == 0, "password input cleared");
                button(panel, "온라인 로그인").doClick();
            });
            check(logins.get() == 1, "busy login was not submitted twice");
            releaseFirst.countDown();
            await(() -> "LEADERBOARD".equals(app[0].getCurrentScreen())
                    && table(app[0]).getRowCount() == 1, "leaderboard after login and refresh");
            check(refreshes.get() == 1, "near-expiry token refreshed once before leaderboard");
            check("Bearer fresh".equals(rankingBearer.get()), "leaderboard used fresh token");

            onEdt(() -> {
                button(panel(app[0], LeaderboardPanel.class), "돌아가기").doClick();
                button(panel(app[0], MainLobbyPanel.class), "설정").doClick();
                button(panel(app[0], SettingsPanel.class), "로그아웃").doClick();
                check(!app[0].isSignedIn(), "logout clears identity immediately");
                check(table(app[0]).getRowCount() == 0, "old account ranking rows cleared");
            });
            await(() -> button(panel(app[0], SettingsPanel.class), "로그인 / 가입").isEnabled(),
                    "logout completed");
            onEdt(() -> {
                button(panel(app[0], SettingsPanel.class), "로그인 / 가입").doClick();
                button(panel(app[0], LoginPanel.class), "회원가입").doClick();
                SignUpPanel signUp = panel(app[0], SignUpPanel.class);
                child(signUp, JTextField.class).setText("New_01");
                JPasswordField[] secrets = passwordFields(signUp);
                secrets[0].setText("password123"); secrets[1].setText("password123");
                button(signUp, "가입").doClick();
                check(secrets[0].getPassword().length == 0 && secrets[1].getPassword().length == 0,
                        "signup secrets cleared");
            });
            await(() -> signups.get() == 1 && app[0].getLastMessage() != null
                    && app[0].getLastMessage().contains("가입 실패"), "failed signup completed");
            onEdt(() -> {
                check(!app[0].isSignedIn(), "failed signup did not restore stale identity");
                check(table(app[0]).getRowCount() == 0, "failed signup kept ranking empty");
                button(panel(app[0], SignUpPanel.class), "돌아가기").doClick();
                login(app[0], "Slow_02", "password123");
            });
            check(lateEntered.await(3, TimeUnit.SECONDS), "second login started");
            int completedBeforeClose = onEdtInt(() -> app[0].getCompletedAuthRequests());
            onEdt(() -> app[0].close());
            releaseLate.countDown();
            await(() -> app[0].getCompletedAuthRequests() > completedBeforeClose,
                    "late login completion callback");
            onEdt(() -> check(!app[0].isSignedIn(), "late completion after close ignored"));
        } finally {
            releaseFirst.countDown(); releaseLate.countDown();
            onEdt(() -> { if (app[0] != null) app[0].close(); });
            for (int i = 0; i < keys.length; i++) {
                if (previous[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], previous[i]);
            }
            api.stop(0);
        }
        System.out.println("PASS OnlineAccountUiFlowTest: current app busy auth, refresh, stale identity, close");
    }

    private static void handle(HttpExchange exchange, CountDownLatch firstEntered, CountDownLatch releaseFirst,
            CountDownLatch lateEntered, CountDownLatch releaseLate, AtomicInteger logins,
            AtomicInteger refreshes, AtomicInteger signups, AtomicReference<String> rankingBearer) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        String body = new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8);
        if ("/auth/v1/token".equals(path)) {
            if (query != null && query.contains("refresh_token")) {
                refreshes.incrementAndGet(); reply(exchange, 200, session("fresh", 3600)); return;
            }
            int attempt = logins.incrementAndGet();
            if (attempt == 1) { firstEntered.countDown(); waitFor(releaseFirst); }
            else { lateEntered.countDown(); waitFor(releaseLate); }
            reply(exchange, 200, session(attempt == 1 ? "initial" : "late", attempt == 1 ? 60 : 3600));
            return;
        }
        if ("/auth/v1/signup".equals(path)) {
            signups.incrementAndGet(); reply(exchange, 503, "{}"); return;
        }
        if ("/auth/v1/logout".equals(path)) { reply(exchange, 200, "{}"); return; }
        if ("/rest/v1/rpc/ranked_leaderboard".equals(path)) {
            rankingBearer.set(exchange.getRequestHeaders().getFirst("Authorization"));
            reply(exchange, 200, "[{\"rank\":1,\"user_id\":\"" + USER
                    + "\",\"display_name\":\"Player\",\"rating\":1016,\"wins\":1,\"losses\":0,\"games\":1}]");
            return;
        }
        throw new IOException("Unexpected fixture request: " + path + " " + body);
    }
    private static String session(String token, int ttl) {
        return "{\"access_token\":\"" + token + "\",\"refresh_token\":\"refresh-test\",\"expires_in\":" + ttl
                + ",\"user\":{\"id\":\"" + USER + "\"}}";
    }
    private static byte[] readAll(InputStream source) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[1024]; int count;
        while ((count = source.read(buffer)) != -1) bytes.write(buffer, 0, count);
        return bytes.toByteArray();
    }
    private static void waitFor(CountDownLatch release) throws IOException {
        try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("fixture release timeout"); }
        catch (InterruptedException stopped) { Thread.currentThread().interrupt(); throw new IOException(stopped); }
    }
    private static void reply(HttpExchange request, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        request.sendResponseHeaders(status, bytes.length);
        request.getResponseBody().write(bytes); request.close();
    }
    private static void login(SeongeunApplication app, String username, String password) {
        LoginPanel panel = panel(app, LoginPanel.class);
        child(panel, JTextField.class).setText(username);
        child(panel, JPasswordField.class).setText(password);
        button(panel, "온라인 로그인").doClick();
    }
    private static JTable table(SeongeunApplication app) {
        return child(panel(app, LeaderboardPanel.class), JTable.class);
    }
    private static JPasswordField[] passwordFields(Component root) {
        java.util.ArrayList<JPasswordField> found = new java.util.ArrayList<JPasswordField>();
        collectPasswords(root, found);
        check(found.size() == 2, "two signup password fields");
        return found.toArray(new JPasswordField[0]);
    }
    private static void collectPasswords(Component root, java.util.List<JPasswordField> result) {
        if (root instanceof JPasswordField) result.add((JPasswordField) root);
        if (root instanceof Container) for (Component child : ((Container) root).getComponents())
            collectPasswords(child, result);
    }
    private static <T extends Component> T panel(SeongeunApplication app, Class<T> type) {
        T result = child(app.getScreens(), type);
        check(result != null, "panel " + type.getSimpleName()); return result;
    }
    private static <T extends Component> T child(Component root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container) for (Component element : ((Container) root).getComponents()) {
            T found = child(element, type); if (found != null) return found;
        }
        return null;
    }
    private static AbstractButton button(Component root, String label) {
        AbstractButton result = findButton(root, label);
        check(result != null, "button " + label); return result;
    }
    private static AbstractButton findButton(Component root, String label) {
        if (root instanceof AbstractButton && label.equals(((AbstractButton) root).getText()))
            return (AbstractButton) root;
        if (root instanceof Container) for (Component element : ((Container) root).getComponents()) {
            AbstractButton found = findButton(element, label); if (found != null) return found;
        }
        return null;
    }
    private static void await(BooleanSupplier predicate, String label) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            boolean[] result = new boolean[1]; onEdt(() -> result[0] = predicate.getAsBoolean());
            if (result[0]) return;
            Thread.sleep(10);
        }
        throw new AssertionError(label);
    }
    private static int onEdtInt(java.util.function.IntSupplier supplier) throws Exception {
        int[] value = new int[1]; onEdt(() -> value[0] = supplier.getAsInt()); return value[0];
    }
    private static void onEdt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}
