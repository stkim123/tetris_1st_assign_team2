package kr.ac.jbnu.se.tetris.network;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import kr.ac.jbnu.se.tetris.auth.AuthException;
import kr.ac.jbnu.se.tetris.auth.AuthSession;
import kr.ac.jbnu.se.tetris.auth.SupabaseAuthService;
import kr.ac.jbnu.se.tetris.ranking.LeaderboardEntry;
import kr.ac.jbnu.se.tetris.ranking.LeaderboardService;
import kr.ac.jbnu.se.tetris.supabase.SupabaseConfig;

/** 공개 인증 키, 세션 갱신, 인증된 읽기 전용 랭킹 요청을 로컬 HTTP 응답으로 검증한다. */
public final class RankedServicesTest {
    private static final String USER = "00000000-0000-4000-8000-000000000001";

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger refreshes = new AtomicInteger();
        server.createContext("/", exchange -> answer(exchange, refreshes));
        server.start();
        try {
            SupabaseConfig config = new SupabaseConfig("http://127.0.0.1:" + server.getAddress().getPort(),
                    "sb_publishable_localtest");
            try { new SupabaseConfig(config.getBaseUri().toString(), "sb_secret_forbidden");
                throw new AssertionError("Secret accepted as public key");
            } catch (IllegalArgumentException expected) { /* expected */ }
            SupabaseAuthService auth = new SupabaseAuthService(config);
            AuthSession first = auth.signIn("player@example.test", "  preserved password  ");
            check("first".equals(first.getAccessToken()) && !first.toString().contains("first"), "Login/redaction");
            AuthSession next = auth.refresh();
            check("second".equals(next.getAccessToken()) && refreshes.get() == 1, "Rotating refresh");
            List<LeaderboardEntry> entries = new LeaderboardService(config).top100(next.getAccessToken());
            check(entries.size() == 1 && entries.get(0).getRank() == 1
                    && entries.get(0).getRating() == 1016, "Official leaderboard");
            try { auth.refresh(); throw new AssertionError("Revoked refresh accepted"); }
            catch (AuthException expected) {
                check(expected.getHttpStatus() == 401 && auth.getSession() == null, "Revoked session cleared");
            }
        } finally { server.stop(0); }
    }

    private static void answer(HttpExchange exchange, AtomicInteger refreshes) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String body = new String(read(exchange), StandardCharsets.UTF_8);
            check("sb_publishable_localtest".equals(exchange.getRequestHeaders().getFirst("apikey")), "Public key only");
            if ("/auth/v1/token".equals(path)) {
                String grant = exchange.getRequestURI().getQuery();
                if ("grant_type=password".equals(grant)) {
                    check(body.contains("  preserved password  "), "Password whitespace preserved");
                    send(exchange, 200, session("first", "refresh-one"));
                } else if (refreshes.incrementAndGet() == 1) {
                    check(body.contains("refresh-one"), "Refresh uses rotating secret");
                    send(exchange, 200, session("second", "refresh-two"));
                } else send(exchange, 401, "{\"code\":\"refresh_token_already_used\"}");
            } else if ("/rest/v1/rpc/ranked_leaderboard".equals(path)) {
                check("Bearer second".equals(exchange.getRequestHeaders().getFirst("Authorization")),
                        "Leaderboard uses account token");
                send(exchange, 200, "[{\"rank\":1,\"user_id\":\"" + USER
                        + "\",\"display_name\":\"Player\",\"rating\":1016,\"wins\":1,\"losses\":0,\"games\":1}]");
            } else send(exchange, 404, "{\"code\":\"unknown\"}");
        } catch (AssertionError error) {
            send(exchange, 500, "{\"code\":\"test_failed\"}");
            throw error;
        }
    }

    private static String session(String access, String refresh) {
        return "{\"access_token\":\"" + access + "\",\"refresh_token\":\"" + refresh
                + "\",\"expires_at\":" + (Instant.now().getEpochSecond() + 3600)
                + ",\"user\":{\"id\":\"" + USER + "\"}}";
    }

    private static byte[] read(HttpExchange exchange) throws IOException {
        java.io.ByteArrayOutputStream result = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[1024]; int count;
        while ((count = exchange.getRequestBody().read(buffer)) != -1) result.write(buffer, 0, count);
        return result.toByteArray();
    }

    private static void send(HttpExchange exchange, int code, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }

    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
