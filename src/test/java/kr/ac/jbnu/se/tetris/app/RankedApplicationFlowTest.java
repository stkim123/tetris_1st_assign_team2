package kr.ac.jbnu.se.tetris.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Component;
import java.awt.Container;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPasswordField;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import kr.ac.jbnu.se.tetris.network.ConnectionOptions;
import kr.ac.jbnu.se.tetris.network.NetworkUpdate;
import kr.ac.jbnu.se.tetris.network.protocol.WireCodec;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.LeaderboardPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.LoginPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.MainLobbyPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.RoomListPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.SettingsPanel;

/** 계정 전환 때 이전 PvP 소켓을 닫고 새 계정 토큰으로 랭킹을 읽는 실제 Swing 흐름. */
public final class RankedApplicationFlowTest {
    private static final String USER_A = "00000000-0000-4000-8000-000000000001";
    private static final String USER_B = "00000000-0000-4000-8000-000000000002";

    public static void main(String[] args) throws Exception {
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> rankBearer = new AtomicReference<String>();
        CountDownLatch canceledLoginStarted = new CountDownLatch(1);
        CountDownLatch releaseCanceledLogin = new CountDownLatch(1);
        CountDownLatch canceledLoginAnswered = new CountDownLatch(1);
        http.createContext("/", exchange -> answer(exchange, rankBearer,
                canceledLoginStarted, releaseCanceledLogin, canceledLoginAnswered));
        http.start();
        CountDownLatch socketClosed = new CountDownLatch(1);
        AtomicReference<Throwable> peerFailure = new AtomicReference<Throwable>();
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            Thread peer = new Thread(() -> {
                try {
                    try (Socket accepted = socket.accept()) { serveWebSocket(accepted, socketClosed); }
                    try (Socket rejected = socket.accept()) { serveUnauthorized(rejected); }
                }
                catch (Throwable failure) { peerFailure.set(failure); }
            }, "ranked-app-ws-fixture");
            peer.setDaemon(true); peer.start();
            String[] keys = {"tetris.server.url", "tetris.supabase.url", "tetris.supabase.publishableKey",
                    "tetris.online.provider"};
            String[] previous = new String[keys.length];
            for (int index = 0; index < keys.length; index++) previous[index] = System.getProperty(keys[index]);
            System.setProperty(keys[0], "ws://127.0.0.1:" + socket.getLocalPort() + "/ws");
            System.setProperty(keys[1], "http://127.0.0.1:" + http.getAddress().getPort());
            System.setProperty(keys[2], "sb_publishable_fixture");
            System.setProperty(keys[3], "supabase");
            SeongeunApplication[] app = new SeongeunApplication[1];
            try {
                onEdt(() -> app[0] = new SeongeunApplication(new Random(7), null));
                check("LOBBY".equals(app[0].getCurrentScreen()), "App must start in the lobby");
                onEdt(() -> {
                    button(child(app[0].getScreens(), MainLobbyPanel.class), "설정").doClick();
                    button(child(app[0].getScreens(), SettingsPanel.class), "로그인 / 가입").doClick();
                });
                check("LOGIN".equals(app[0].getCurrentScreen()), "Account login must open from settings");
                onEdt(() -> button(child(app[0].getScreens(), LoginPanel.class), "설정으로").doClick());
                check("SETTINGS".equals(app[0].getCurrentScreen()), "Account cancel must return to settings");
                onEdt(() -> button(child(app[0].getScreens(), SettingsPanel.class), "돌아가기").doClick());
                onEdt(() -> button(child(app[0].getScreens(), MainLobbyPanel.class), "온라인 대전").doClick());
                check("LOGIN".equals(app[0].getCurrentScreen()), "Online entry must request login directly");
                onEdt(() -> app[0].getScreens().getActionMap().get("navigate-back")
                        .actionPerformed(new java.awt.event.ActionEvent(app[0].getScreens(), 0, "escape")));
                check("LOBBY".equals(app[0].getCurrentScreen()), "Login ESC must return to lobby");
                onEdt(() -> button(child(app[0].getScreens(), MainLobbyPanel.class), "온라인 대전").doClick());
                int completedBeforeCancel = app[0].getCompletedAuthRequests();
                login(app[0], "cancel_user", "password-a");
                check(canceledLoginStarted.await(5, TimeUnit.SECONDS), "Canceled login did not start");
                onEdt(() -> {
                    button(child(app[0].getScreens(), LoginPanel.class), "취소").doClick();
                    button(child(app[0].getScreens(), MainLobbyPanel.class), "PvP 랭킹").doClick();
                });
                releaseCanceledLogin.countDown();
                check(canceledLoginAnswered.await(5, TimeUnit.SECONDS), "Canceled login did not finish");
                await(() -> app[0].getCompletedAuthRequests() > completedBeforeCancel,
                        "Canceled login completion callback");
                check("LOGIN".equals(app[0].getCurrentScreen()) && !app[0].isSignedIn(),
                        "Canceled login must not sign in or follow a later destination");
                onEdt(() -> button(child(app[0].getScreens(), LoginPanel.class), "취소").doClick());
                onEdt(() -> {
                    button(child(app[0].getScreens(), MainLobbyPanel.class), "온라인 대전").doClick();
                    button(child(app[0].getScreens(), LoginPanel.class), "회원가입").doClick();
                });
                check("SIGN_UP".equals(app[0].getCurrentScreen()), "Signup must preserve online destination");
                onEdt(() -> button(child(app[0].getScreens(), kr.ac.jbnu.se.tetris.ui.seongeun.panels.SignUpPanel.class),
                        "돌아가기").doClick());
                login(app[0], "Player_01", "password-a");
                await(() -> "ROOM_LIST".equals(app[0].getCurrentScreen()) && app[0].isOnlineConnected(),
                        "Login must resume online room entry");
                onEdt(() -> button(child(app[0].getScreens(), RoomListPanel.class), "돌아가기").doClick());
                check(socketClosed.await(5, TimeUnit.SECONDS), "Old account socket remained open");
                await(() -> !app[0].isOnlineConnected() && "LOBBY".equals(app[0].getCurrentScreen()),
                        "Online room exit");
                onEdt(() -> {
                    button(child(app[0].getScreens(), MainLobbyPanel.class), "설정").doClick();
                    button(child(app[0].getScreens(), SettingsPanel.class), "로그아웃").doClick();
                });
                await(() -> button(child(app[0].getScreens(), SettingsPanel.class), "로그인 / 가입").isEnabled(),
                        "Settings account logout");
                onEdt(() -> button(child(app[0].getScreens(), SettingsPanel.class), "돌아가기").doClick());
                onEdt(() -> button(child(app[0].getScreens(), MainLobbyPanel.class), "PvP 랭킹").doClick());
                check("LOGIN".equals(app[0].getCurrentScreen()), "Ranking entry must request login directly");
                login(app[0], "b@example.test", "password-b");
                await(() -> {
                    LeaderboardPanel panel = child(app[0].getScreens(), LeaderboardPanel.class);
                    JTable table = child(panel, JTable.class);
                    return "LEADERBOARD".equals(app[0].getCurrentScreen())
                            && table.getRowCount() == 1 && "Player B".equals(table.getValueAt(0, 1));
                }, "Login must resume ranking");
                check("Bearer token-B".equals(rankBearer.get()), "Leaderboard used old account token");
                onEdt(() -> {
                    button(child(app[0].getScreens(), LeaderboardPanel.class), "돌아가기").doClick();
                    button(child(app[0].getScreens(), MainLobbyPanel.class), "온라인 대전").doClick();
                });
                await(() -> "ROOM_LIST".equals(app[0].getCurrentScreen())
                        && button(child(app[0].getScreens(), RoomListPanel.class), "다시 로그인") != null,
                        "Rejected ranked token must request login instead of repeating the same token");
                onEdt(() -> button(child(app[0].getScreens(), RoomListPanel.class), "다시 로그인").doClick());
                check("LOGIN".equals(app[0].getCurrentScreen()), "Rejected token retry must open login");
                if (peerFailure.get() != null) throw new AssertionError("WebSocket fixture failure", peerFailure.get());
                System.out.println("PASS RankedApplicationFlowTest");
            } finally {
                if (app[0] != null) onEdt(() -> app[0].close());
                for (int index = 0; index < keys.length; index++) {
                    if (previous[index] == null) System.clearProperty(keys[index]);
                    else System.setProperty(keys[index], previous[index]);
                }
            }
        } finally { http.stop(0); }
    }

    private static void login(SeongeunApplication app, String email, String password) throws Exception {
        onEdt(() -> {
            LoginPanel panel = child(app.getScreens(), LoginPanel.class);
            JTextField field = emailField(panel);
            JPasswordField secret = child(panel, JPasswordField.class);
            field.setText(email); secret.setText(password);
            button(panel, "온라인 로그인").doClick();
            check(secret.getPassword().length == 0, "Password field was not cleared");
        });
    }

    private static void answer(HttpExchange exchange, AtomicReference<String> rankBearer,
            CountDownLatch canceledLoginStarted, CountDownLatch releaseCanceledLogin,
            CountDownLatch canceledLoginAnswered) throws IOException {
        byte[] request = readAll(exchange.getRequestBody());
        String body = new String(request, StandardCharsets.UTF_8);
        String path = exchange.getRequestURI().getPath();
        String response;
        if ("/auth/v1/token".equals(path)) {
            boolean canceled = body.contains("cancel_user@players.campus-quest.invalid");
            if (canceled) {
                canceledLoginStarted.countDown();
                try {
                    if (!releaseCanceledLogin.await(5, TimeUnit.SECONDS))
                        throw new IOException("Canceled login response was not released");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); throw new IOException(interrupted);
                }
            }
            if (!body.contains("b@example.test"))
                check(body.contains("player_01@players.campus-quest.invalid") || canceled,
                        "ID login must use stable internal Auth address");
            boolean second = body.contains("b@example.test");
            String id = second ? USER_B : USER_A;
            String name = second ? "B" : "A";
            response = "{\"access_token\":\"token-" + name + "\",\"refresh_token\":\"refresh-" + name
                    + "\",\"expires_at\":" + (Instant.now().getEpochSecond() + 3600)
                    + ",\"user\":{\"id\":\"" + id + "\"}}";
        } else if ("/rest/v1/rpc/ranked_leaderboard".equals(path)) {
            rankBearer.set(exchange.getRequestHeaders().getFirst("Authorization"));
            response = "[{\"rank\":1,\"user_id\":\"" + USER_B
                    + "\",\"display_name\":\"Player B\",\"rating\":1016,\"wins\":1,\"losses\":0,\"games\":1}]";
        } else {
            send(exchange, 404, "{\"code\":\"not_found\"}"); return;
        }
        send(exchange, 200, response);
        if (body.contains("cancel_user@players.campus-quest.invalid")) canceledLoginAnswered.countDown();
    }

    private static void serveUnauthorized(Socket socket) throws Exception {
        socket.setSoTimeout(15000);
        String request = new String(readHeaders(socket.getInputStream()), StandardCharsets.US_ASCII);
        check(request.contains("Authorization: Bearer token-B\r\n"), "Rejected account bearer header");
        OutputStream output = socket.getOutputStream();
        output.write("HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                .getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    private static void serveWebSocket(Socket socket, CountDownLatch closed) throws Exception {
        socket.setSoTimeout(15000);
        InputStream input = socket.getInputStream();
        OutputStream output = socket.getOutputStream();
        String request = new String(readHeaders(input), StandardCharsets.US_ASCII);
        check(request.contains("Authorization: Bearer token-A\r\n"), "First account bearer header");
        String key = null;
        for (String line : request.split("\r\n"))
            if (line.toLowerCase(java.util.Locale.ROOT).startsWith("sec-websocket-key:"))
                key = line.substring(line.indexOf(':') + 1).trim();
        check(key != null, "Missing WebSocket key");
        String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
        output.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        byte[] message = WireCodec.encodeUpdatePayload(NetworkUpdate.connected());
        check(message.length < 126, "Fixture frame too large");
        output.write(0x81); output.write(message.length); output.write(message); output.flush();
        while (input.read() != -1) { /* Client may send its close frame before closing TCP. */ }
        closed.countDown();
    }

    private static byte[] readHeaders(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (bytes.size() < 8192) {
            int value = input.read();
            if (value < 0) throw new IOException("Handshake ended early");
            bytes.write(value);
            byte[] current = bytes.toByteArray(); int size = current.length;
            if (size >= 4 && current[size - 4] == '\r' && current[size - 3] == '\n'
                    && current[size - 2] == '\r' && current[size - 1] == '\n') return current;
        }
        throw new IOException("Handshake exceeded fixture limit");
    }

    private static byte[] readAll(InputStream input) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return bytes.toByteArray();
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes); exchange.close();
    }

    private static void await(BooleanSupplier condition, String label) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < until) {
            boolean[] ready = new boolean[1]; onEdt(() -> ready[0] = condition.getAsBoolean());
            if (ready[0]) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out: " + label);
    }

    private static void onEdt(Runnable task) throws Exception { SwingUtilities.invokeAndWait(task); }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }

    private static JTextField emailField(Component root) {
        if (root instanceof JTextField && !(root instanceof JPasswordField)) return (JTextField) root;
        if (root instanceof Container) for (Component child : ((Container) root).getComponents()) {
            JTextField found = emailField(child); if (found != null) return found;
        }
        return null;
    }

    private static JButton button(Component root, String text) {
        if (root instanceof JButton && text.equals(((JButton) root).getText())) return (JButton) root;
        if (root instanceof Container) for (Component child : ((Container) root).getComponents()) {
            JButton found = button(child, text); if (found != null) return found;
        }
        return null;
    }

    private static JMenuItem menuItem(JMenuBar bar, String text) {
        for (int menu = 0; menu < bar.getMenuCount(); menu++) {
            JMenu group = bar.getMenu(menu);
            for (int item = 0; item < group.getItemCount(); item++) {
                JMenuItem found = group.getItem(item);
                if (found != null && text.equals(found.getText())) return found;
            }
        }
        throw new AssertionError("Missing menu: " + text);
    }

    private static <T extends Component> T child(Component root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container) for (Component descendant : ((Container) root).getComponents()) {
            T found = child(descendant, type); if (found != null) return found;
        }
        return null;
    }
}
