package kr.ac.jbnu.se.tetris.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import kr.ac.jbnu.se.tetris.firebase.FirebaseConfig;
import kr.ac.jbnu.se.tetris.network.RankedOnlineConfig;
import kr.ac.jbnu.se.tetris.ranking.LeaderboardEntry;

/** Exercises the real desktop REST flow against a bounded local Identity Toolkit fixture. */
public final class FirebaseAuthTest {
    private FirebaseAuthTest() { }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> signupEmail = new AtomicReference<>();
        AtomicReference<String> refreshUid = new AtomicReference<>("uid-a");
        AtomicReference<String> leaderboardBearer = new AtomicReference<>();
        server.createContext("/identitytoolkit.googleapis.com/v1/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            JsonObject body = JsonParser.parseString(read(exchange)).getAsJsonObject();
            if (path.endsWith("accounts:signUp")) {
                signupEmail.set(body.get("email").getAsString());
                respond(exchange, 200, session("uid-a", "access-a", "refresh-a"));
            } else if (path.endsWith("accounts:signInWithPassword")) {
                if (body.get("email").getAsString().startsWith("missing@"))
                    respond(exchange, 400, "{\"error\":{\"message\":\"INVALID_PASSWORD\"}}");
                else respond(exchange, 200, session("uid-a", "access-a", "refresh-a"));
            } else if (path.endsWith("accounts:update")) {
                check("access-a".equals(body.get("idToken").getAsString()), "password update uses ID token");
                respond(exchange, 200, session("uid-a", "access-b", "refresh-b"));
            } else if (path.endsWith("accounts:sendOobCode")) {
                respond(exchange, 200, "{\"email\":\"real@example.test\"}");
            } else if (path.endsWith("accounts:resetPassword")) {
                respond(exchange, 200, "{\"email\":\"real@example.test\",\"requestType\":\"PASSWORD_RESET\"}");
            } else respond(exchange, 404, "{\"error\":{\"message\":\"NOT_FOUND\"}}");
        });
        server.createContext("/securetoken.googleapis.com/v1/", exchange -> {
            String request = read(exchange);
            check(request.contains("grant_type=refresh_token"), "refresh uses form body");
            if ("error".equals(refreshUid.get()))
                respond(exchange, 400, "{\"error\":{\"message\":\"INVALID_REFRESH_TOKEN\"}}");
            else respond(exchange, 200, "{\"id_token\":\"access-c\",\"refresh_token\":\"refresh-c\","
                    + "\"user_id\":\"" + refreshUid.get() + "\",\"expires_in\":\"3600\"}");
        });
        server.createContext("/api/leaderboard", exchange -> {
            leaderboardBearer.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "[{\"rank\":1,\"user_id\":\"uid-a\",\"display_name\":\"player_01\","
                    + "\"rating\":1016,\"wins\":1,\"losses\":0,\"games\":1}]");
        });
        server.start();
        Path configFile = Files.createTempFile("tetris-firebase-client-", ".properties");
        String oldConfig = System.getProperty("tetris.client.config");
        try {
            int port = server.getAddress().getPort();
            URI auth = URI.create("http://127.0.0.1:" + port + "/identitytoolkit.googleapis.com/v1/");
            URI token = URI.create("http://127.0.0.1:" + port + "/securetoken.googleapis.com/v1/");
            FirebaseConfig firebase = FirebaseConfig.forLocalEmulator("demo-test-project", "test-api-key", auth, token);
            FirebaseAuthService service = new FirebaseAuthService(firebase);
            SignUpResult signup = service.signUpUsername("Player_01", "password123");
            check("player_01@players.campus-quest.invalid".equals(signupEmail.get()), "canonical username email");
            check(!signup.isPendingEmailVerification() && signup.getSession() != null, "signup session");
            service.signOut();
            check(service.getSession() == null, "local logout clears token");
            service.signInUsername("PLAYER_01", "password123");
            check("uid-a".equals(service.getSession().getUserId()), "sign in identity");
            service.changePassword("new-password123");
            check("access-b".equals(service.getSession().getAccessToken()), "password change rotates token");
            service.signInUsername("player_01", "password123");
            refreshUid.set("uid-b");
            try { service.refresh(); throw new AssertionError("cross-account refresh accepted"); }
            catch (AuthException expected) {
                check("IDENTITY_MISMATCH".equals(expected.getCode()) && service.getSession() == null,
                        "cross-account refresh clears session");
            }
            service.signInUsername("player_01", "password123");
            refreshUid.set("error");
            try { service.refresh(); throw new AssertionError("revoked refresh accepted"); }
            catch (AuthException expected) {
                check("INVALID_REFRESH_TOKEN".equals(expected.getCode()) && service.getSession() == null,
                        "revoked refresh clears session");
            }
            try { service.signIn("missing@example.test", "password123"); throw new AssertionError("bad password accepted"); }
            catch (AuthException expected) { check(expected.getHttpStatus() == 400, "Auth error mapped"); }
            try { service.recoverPassword("player_01@players.campus-quest.invalid");
                throw new AssertionError("undeliverable recovery accepted"); }
            catch (AuthException expected) { check("RECOVERY_UNAVAILABLE_FOR_USERNAME".equals(expected.getCode()),
                    "username recovery rejected clearly"); }
            service.recoverPassword("real@example.test");
            check("uid-a".equals(service.completePasswordRecovery("real@example.test", "code", "new-password123")
                    .getUserId()), "email recovery signs in");

            String properties = "online.provider=firebase\nserver.url=ws://127.0.0.1:" + port + "/ws\n"
                    + "firebase.projectId=demo-test-project\nfirebase.apiKey=test-api-key\n"
                    + "firebase.authEmulatorUrl=" + auth + "\nfirebase.tokenEmulatorUrl=" + token + "\n";
            Files.write(configFile, properties.getBytes(StandardCharsets.UTF_8));
            System.setProperty("tetris.client.config", configFile.toString());
            RankedOnlineConfig selected = RankedOnlineConfig.load();
            check(selected.isFirebase() && selected.createAuthService() instanceof FirebaseAuthService,
                    "provider selects Firebase Auth");
            LeaderboardEntry entry = selected.createLeaderboardService().top100("access-a").get(0);
            check(entry.getRating() == 1016 && "Bearer access-a".equals(leaderboardBearer.get()),
                    "Cloud Run leaderboard uses bearer token");
            try { FirebaseConfig.forLocalEmulator("demo-test-project", "test-api-key",
                    URI.create("http://example.com/v1/"), token);
                throw new AssertionError("remote Auth emulator accepted"); }
            catch (IllegalArgumentException expected) { /* Loopback only. */ }
            System.out.println("PASS FirebaseAuthTest");
        } finally {
            if (oldConfig == null) System.clearProperty("tetris.client.config");
            else System.setProperty("tetris.client.config", oldConfig);
            Files.deleteIfExists(configFile);
            server.stop(0);
        }
    }

    private static String session(String user, String token, String refresh) {
        return "{\"localId\":\"" + user + "\",\"idToken\":\"" + token
                + "\",\"refreshToken\":\"" + refresh + "\",\"expiresIn\":\"3600\"}";
    }

    private static String read(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody();
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 65536) throw new IOException("Fixture request too large");
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().put("Content-Type", Collections.singletonList("application/json"));
        exchange.sendResponseHeaders(status, bytes.length);
        try (java.io.OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
