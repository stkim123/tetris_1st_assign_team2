package kr.ac.jbnu.se.tetris.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import kr.ac.jbnu.se.tetris.supabase.SupabaseConfig;

public final class SupabaseAuthTest {
    private static final String USER = "00000000-0000-4000-8000-000000000001";

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger userCalls = new AtomicInteger();
        server.createContext("/", exchange -> handle(exchange, userCalls));
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            SupabaseConfig config = new SupabaseConfig(base, "public-test-key");
            try { new SupabaseConfig(base, "sb_secret_accidental");
                throw new AssertionError("Secret key accepted as client key");
            } catch (IllegalArgumentException expected) { /* expected */ }
            try { new SupabaseConfig(base, jwt(base, USER, "service_role",
                    Instant.now().getEpochSecond() + 3600));
                throw new AssertionError("Legacy service_role key accepted as client key");
            } catch (IllegalArgumentException expected) { /* expected */ }
            SupabaseAuthService auth = new SupabaseAuthService(config);
            SignUpResult signup = auth.signUp("a@example.test", "password123");
            check(signup.isPendingEmailVerification() && signup.getSession() == null, "Signup awaits email");
            check("player_01".equals(UsernameIdentity.normalize(" Player_01 ")), "ID canonicalization");
            check("player_01@players.campus-quest.invalid".equals(UsernameIdentity.emailFor("PLAYER_01")),
                    "Stable internal Auth email");
            check("player_01".equals(UsernameIdentity.fromEmail("player_01@players.campus-quest.invalid")),
                    "Canonical ID from Auth email");
            check(UsernameIdentity.fromEmail("player_01@example.com") == null,
                    "Unreserved email has no canonical ID");
            for (String invalid : new String[] {"ab", "1player", "player-name", "한글아이디", "player@example.com"}) {
                try { UsernameIdentity.emailFor(invalid); throw new AssertionError("Invalid ID accepted: " + invalid); }
                catch (IllegalArgumentException expected) { /* expected */ }
            }
            SignUpResult usernameSignup = auth.signUpUsername("Player_01", "password123");
            check(!usernameSignup.isPendingEmailVerification() && usernameSignup.getSession() != null,
                    "Username signup with autoconfirm response");
            AuthSession usernameSession = auth.signInUsername("PLAYER_01", "password123");
            check(USER.equals(usernameSession.getUserId()), "Username password login");
            AuthSession signedIn = auth.signIn("a@example.test", "  surrounding spaces  ");
            check(USER.equals(signedIn.getUserId()) && signedIn.getAccessToken().equals("first"), "Password login");
            check(!signedIn.toString().contains("first"), "Token redaction");
            AuthSession refreshed = auth.refresh();
            check(refreshed.getAccessToken().equals("second"), "Refresh rotates token");
            auth.changePassword("new-password123");
            auth.recoverPassword("a@example.test");
            try {
                auth.completePasswordRecovery("a@example.test", "wrong", "next-password123");
                throw new AssertionError("Wrong OTP accepted");
            } catch (AuthException expected) { check("invalid_otp".equals(expected.getCode()), "OTP code"); }
            AuthSession recovered = auth.completePasswordRecovery("a@example.test", "123456", "next-password123");
            check("recovered".equals(recovered.getAccessToken()), "Recovery session");
            auth.signOut();
            check(auth.getSession() == null, "Logout clears memory");
            auth.signIn("a@example.test", "  surrounding spaces  ");
            auth.refresh();
            try { auth.refresh(); throw new AssertionError("Revoked refresh accepted"); }
            catch (AuthException expected) {
                check(auth.getSession() == null && expected.getHttpStatus() == 401,
                        "Terminal refresh failure clears session");
            }
            auth.signIn("rate@example.test", "password123");
            try { auth.refresh(); throw new AssertionError("Rate limit ignored"); }
            catch (AuthException expected) {
                check(auth.getSession() != null && expected.getHttpStatus() == 429,
                        "Transient refresh failure retains session");
            }

            SupabaseTokenVerifier verifier = new SupabaseTokenVerifier(config);
            String token = jwt(base, USER, "authenticated", Instant.now().getEpochSecond() + 3600);
            AuthIdentity verified = verifier.verify(token);
            check(USER.equals(verified.getUserId()) && "player_01".equals(verified.getDisplayName()),
                    "Auth server validated identity and ignored mutable display metadata");
            int verifiedCalls = userCalls.get();
            rejected(verifier, jwt(base + "/other", USER, "authenticated", Instant.now().getEpochSecond() + 3600));
            rejected(verifier, jwt(base, USER, "anon", Instant.now().getEpochSecond() + 3600));
            rejected(verifier, jwt(base, USER, "authenticated", Instant.now().getEpochSecond() - 1));
            check(userCalls.get() == verifiedCalls, "Bad claims do not reach Auth server");
            rejected(verifier, jwt(base, "00000000-0000-4000-8000-000000000002",
                    "authenticated", Instant.now().getEpochSecond() + 3600));
            check(userCalls.get() == verifiedCalls + 1, "Remote account mismatch checked");
        } finally { server.stop(0); }
    }

    private static void rejected(SupabaseTokenVerifier verifier, String token) throws Exception {
        try { verifier.verify(token); throw new AssertionError("Token accepted"); }
        catch (AuthException expected) { /* expected */ }
    }

    private static String jwt(String base, String user, String role, long exp) {
        String claims = "{\"iss\":\"" + base + "/auth/v1\",\"sub\":\"" + user
                + "\",\"aud\":\"authenticated\",\"role\":\"" + role + "\",\"exp\":" + exp + "}";
        return encode("{\"alg\":\"HS256\"}") + "." + encode(claims) + "." + encode("signature");
    }

    private static String encode(String data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }

    private static void handle(HttpExchange request, AtomicInteger userCalls) throws IOException {
        String path = request.getRequestURI().getPath();
        String method = request.getRequestMethod();
        String body = new String(read(request), StandardCharsets.UTF_8);
        check("public-test-key".equals(request.getRequestHeaders().getFirst("apikey")), "Public key header");
        if (path.equals("/auth/v1/user") && method.equals("GET")) {
            userCalls.incrementAndGet();
            answer(request, 200, "{\"id\":\"" + USER + "\",\"email\":\"player_01@players.campus-quest.invalid\",\"user_metadata\":{\"display_name\":\"forged\"}}");
        } else if (path.equals("/auth/v1/user") && method.equals("PUT")) {
            check(body.contains("password"), "Password update body");
            answer(request, 200, "{\"id\":\"" + USER + "\"}");
        } else if (path.equals("/auth/v1/signup")) {
            if (body.contains("players.campus-quest.invalid")) {
                check(body.contains("\"email\":\"player_01@players.campus-quest.invalid\""),
                        "Username signup internal email");
                check(body.contains("\"data\":{\"display_name\":\"player_01\"}"),
                        "Username signup display metadata");
                answer(request, 200, session("username"));
            } else answer(request, 200, "{\"user\":{\"id\":\"" + USER + "\"}}");
        } else if (path.equals("/auth/v1/token") && request.getRequestURI().getQuery().contains("password")) {
            if (body.contains("players.campus-quest.invalid"))
                check(body.contains("\"email\":\"player_01@players.campus-quest.invalid\""),
                        "Username login internal email");
            if (body.contains("a@example.test"))
                check(body.contains("\"password\":\"  surrounding spaces  \""),
                        "Password bytes preserve spaces");
            answer(request, 200, session(body.contains("rate@example.test") ? "rate" : "first"));
        } else if (path.equals("/auth/v1/token")) {
            check(body.contains("refresh_token"), "Refresh body");
            if (body.contains("r-second")) answer(request, 401, "{\"error_code\":\"invalid_credentials\"}");
            else if (body.contains("r-rate")) answer(request, 429, "{\"error_code\":\"over_request_rate_limit\"}");
            else answer(request, 200, session("second"));
        } else if (path.equals("/auth/v1/recover")) {
            answer(request, 200, "{}");
        } else if (path.equals("/auth/v1/verify")) {
            if (body.contains("123456")) answer(request, 200, session("recovered"));
            else answer(request, 400, "{\"error_code\":\"invalid_otp\"}");
        } else if (path.equals("/auth/v1/logout")) {
            answer(request, 204, "");
        } else answer(request, 404, "{}");
    }

    private static String session(String token) {
        return "{\"access_token\":\"" + token + "\",\"refresh_token\":\"r-" + token
                + "\",\"expires_in\":3600,\"user\":{\"id\":\"" + USER + "\"}}";
    }

    private static byte[] read(HttpExchange request) throws IOException {
        byte[] buffer = new byte[1024];
        int count = request.getRequestBody().read(buffer);
        return count < 0 ? new byte[0] : java.util.Arrays.copyOf(buffer, count);
    }

    private static void answer(HttpExchange request, int status, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        request.sendResponseHeaders(status, status == 204 ? -1 : data.length);
        if (status != 204) request.getResponseBody().write(data);
        request.close();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
