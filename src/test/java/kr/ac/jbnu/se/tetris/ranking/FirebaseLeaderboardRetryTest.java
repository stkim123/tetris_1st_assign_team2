package kr.ac.jbnu.se.tetris.ranking;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** An idle Cloud Run instance can answer 503 before its first lease transaction completes. */
public final class FirebaseLeaderboardRetryTest {
    public static void main(String[] args) throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger mode = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/leaderboard", exchange -> {
            int count = requests.incrementAndGet();
            int status = mode.get() == 1 ? 401 : mode.get() == 2 || count < 3 ? 503 : 200;
            byte[] body = (status == 200 ? "[]" : "unavailable").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (java.io.OutputStream output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            URI uri = URI.create("ws://127.0.0.1:" + server.getAddress().getPort() + "/ws");
            if (!new LeaderboardService(uri, 3000).top100("valid-test-token").isEmpty() || requests.get() != 3)
                throw new AssertionError("Cold-start 503 did not retry to success");
            mode.set(1); requests.set(0);
            try { new LeaderboardService(uri, 3000).top100("invalid-test-token"); throw new AssertionError("401 accepted"); }
            catch (java.io.IOException expected) {
                if (requests.get() != 1) throw new AssertionError("Authentication error retried");
            }
            mode.set(2); requests.set(0);
            long started = System.nanoTime();
            try { new LeaderboardService(uri, 300).top100("valid-test-token"); throw new AssertionError("503 accepted"); }
            catch (java.io.IOException expected) {
                if (java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) > 2000)
                    throw new AssertionError("Retry deadline was not bounded");
            }
        } finally { server.stop(0); }
    }
}
