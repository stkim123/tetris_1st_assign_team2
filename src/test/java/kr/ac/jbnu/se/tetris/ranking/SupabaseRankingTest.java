package kr.ac.jbnu.se.tetris.ranking;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import kr.ac.jbnu.se.tetris.supabase.SupabaseConfig;

public final class SupabaseRankingTest {
    private static final String MATCH = "10000000-0000-4000-8000-000000000001";
    private static final String RUN = "20000000-0000-4000-8000-000000000001";
    private static final String FIRST = "00000000-0000-4000-8000-000000000001";
    private static final String SECOND = "00000000-0000-4000-8000-000000000002";

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/", exchange -> handle(exchange, calls));
        server.start();
        try {
            SupabaseConfig config = new SupabaseConfig("http://127.0.0.1:" + server.getAddress().getPort(),
                    "public-test-key", "secret-test-key");
            SupabaseRankedMatchStore store = new SupabaseRankedMatchStore(config);
            check(!store.claimRun(RUN).isOwned(), "Initial drained lease");
            check(store.setAdmission(RUN, true).isOwned(), "Operator opened lease");
            check(store.renewRun(RUN).isEnabled(), "Lease renewed");
            MatchRecord started = store.beginMatch(MATCH, RUN, "v1", FIRST, SECOND);
            check(started.getStatus() == MatchRecord.Status.RUNNING, "Begin result");
            MatchRecord ended = store.finishMatch(MATCH, RUN, FIRST, "WIN");
            check(ended.getStatus() == MatchRecord.Status.FINALIZED
                    && ended.getFirstRatingAfter() == 1016, "Finalized rating");
            check(store.getMatch(MATCH).getStatus() == MatchRecord.Status.FINALIZED, "Get match");
            check(store.voidStoppedRun(RUN, "20000000-0000-4000-8000-000000000002") == 2,
                    "Stopped run count");
            try { store.voidMatch(MATCH, RUN, "ERROR"); throw new AssertionError("Conflict missed"); }
            catch (IOException expected) { /* HTTP 409 remains an error. */ }
            LeaderboardEntry entry = new LeaderboardService(config).top100("user-access-token").get(0);
            check(entry.getRank() == 1 && entry.getRating() == 1016 && FIRST.equals(entry.getUserId()),
                    "Leaderboard DTO");
            check(calls.get() == 9, "All RPC paths exercised");
            try { new SupabaseRankedMatchStore(new SupabaseConfig(config.getBaseUri().toString(), "public"));
                throw new AssertionError("Server initialized without secret");
            } catch (IllegalStateException expected) { /* fail closed */ }
        } finally { server.stop(0); }
    }

    private static void handle(HttpExchange request, AtomicInteger calls) throws IOException {
        calls.incrementAndGet();
        String function = request.getRequestURI().getPath();
        String apikey = request.getRequestHeaders().getFirst("apikey");
        if (function.endsWith("ranked_leaderboard")) {
            check("public-test-key".equals(apikey), "Client uses public key");
            check("Bearer user-access-token".equals(request.getRequestHeaders().getFirst("Authorization")),
                    "Leaderboard uses user token");
            answer(request, 200, "[{\"rank\":1,\"user_id\":\"" + FIRST
                    + "\",\"display_name\":\"Player\",\"rating\":1016,\"wins\":1,\"losses\":0,\"games\":1}]");
            return;
        }
        check("secret-test-key".equals(apikey), "Server uses secret key");
        check(request.getRequestHeaders().getFirst("Authorization") == null,
                "Secret key is never used as a bearer JWT");
        if (function.endsWith("ranked_void_match")) { answer(request, 409, "{\"code\":\"MATCH_ALREADY_FINALIZED\"}"); return; }
        if (function.endsWith("ranked_void_stopped_run")) { answer(request, 200, "2"); return; }
        if (function.endsWith("ranked_run_lease")) {
            answer(request, 200, "{\"owned\":" + (calls.get() != 1)
                    + ",\"enabled\":" + (calls.get() != 1) + "}");
            return;
        }
        String status = function.endsWith("ranked_finish_match") || function.endsWith("ranked_get_match")
                ? "FINALIZED" : "RUNNING";
        answer(request, 200, "{\"match_id\":\"" + MATCH + "\",\"server_run_id\":\"" + RUN
                + "\",\"first_user_id\":\"" + FIRST + "\",\"second_user_id\":\"" + SECOND
                + "\",\"status\":\"" + status + "\",\"winner_user_id\":\"" + FIRST
                + "\",\"reason\":\"WIN\",\"first_rating_before\":1000,\"first_rating_after\":1016,"
                + "\"second_rating_before\":1000,\"second_rating_after\":984}");
    }

    private static void answer(HttpExchange request, int status, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        request.sendResponseHeaders(status, data.length);
        request.getResponseBody().write(data);
        request.close();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
