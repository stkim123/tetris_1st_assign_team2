package kr.ac.jbnu.se.tetris.ranking;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import kr.ac.jbnu.se.tetris.supabase.SupabaseConfig;
import kr.ac.jbnu.se.tetris.supabase.SupabaseHttp;
import kr.ac.jbnu.se.tetris.network.ConnectionOptions;

/** Authenticated read-only leaderboard access from the desktop client. */
public final class LeaderboardService {
    private final SupabaseHttp http;
    private final URI serverUri;
    private final long startupWaitMillis;

    public LeaderboardService(SupabaseConfig config) { http = new SupabaseHttp(config); serverUri = null; startupWaitMillis = 0; }
    public LeaderboardService(URI serverUri) {
        this(serverUri, 75000);
    }

    LeaderboardService(URI serverUri, long startupWaitMillis) {
        new ConnectionOptions(serverUri, "configuration-validation");
        if (startupWaitMillis < 1) throw new IllegalArgumentException("startupWaitMillis");
        this.http = null;
        this.serverUri = serverUri;
        this.startupWaitMillis = startupWaitMillis;
    }

    public List<LeaderboardEntry> top100(String accessToken) throws IOException {
        if (accessToken == null || accessToken.isEmpty()) throw new IllegalArgumentException("accessToken");
        JsonElement response = serverUri == null
                ? http.request("POST", "/rest/v1/rpc/ranked_leaderboard", new JsonObject(), accessToken, false)
                : cloudRunLeaderboard(accessToken);
        try {
            JsonArray array = response.getAsJsonArray();
            List<LeaderboardEntry> entries = new ArrayList<>();
            for (JsonElement element : array) {
                JsonObject row = element.getAsJsonObject();
                entries.add(new LeaderboardEntry(row.get("rank").getAsInt(), row.get("user_id").getAsString(),
                        row.get("display_name").getAsString(), row.get("rating").getAsInt(),
                        row.get("wins").getAsInt(), row.get("losses").getAsInt(),
                        row.get("games").getAsInt()));
            }
            if (entries.size() > 100) throw new IOException("Leaderboard limit exceeded");
            return Collections.unmodifiableList(entries);
        } catch (RuntimeException e) { throw new IOException("Invalid leaderboard response", e); }
    }

    private JsonElement cloudRunLeaderboard(String accessToken) throws IOException {
        new ConnectionOptions(serverUri, accessToken);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(startupWaitMillis);
        int attempts = 0;
        while (true) {
            long remaining = Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
            try { return cloudRunLeaderboardAttempt(accessToken, remaining); }
            catch (ColdStartUnavailable | SocketException | SocketTimeoutException transientFailure) {
                remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remaining <= 0) throw transientFailure;
                try { Thread.sleep(Math.min(remaining, Math.min(2000L, 250L << Math.min(attempts++, 3)))); }
                catch (InterruptedException cancelled) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Leaderboard request interrupted", cancelled);
                }
            }
        }
    }

    private JsonElement cloudRunLeaderboardAttempt(String accessToken, long remainingMillis) throws IOException {
        URI endpoint;
        try {
            endpoint = new URI("wss".equalsIgnoreCase(serverUri.getScheme()) ? "https" : "http",
                    null, serverUri.getHost(), serverUri.getPort(), "/api/leaderboard", null, null);
        } catch (URISyntaxException invalid) { throw new IOException("Invalid leaderboard endpoint", invalid); }
        HttpURLConnection connection = (HttpURLConnection) endpoint.toURL().openConnection();
        connection.setConnectTimeout((int) Math.min(5000, remainingMillis));
        connection.setReadTimeout((int) Math.min(20000, remainingMillis));
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        try {
            int status = connection.getResponseCode();
            if (status == 502 || status == 503 || status == 504) throw new ColdStartUnavailable(status);
            if (status != 200) throw new IOException("Leaderboard HTTP " + status);
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 1024 * 1024) throw new IOException("Leaderboard response too large");
                    output.write(buffer, 0, count);
                }
                try { return JsonParser.parseString(new String(output.toByteArray(), StandardCharsets.UTF_8)); }
                catch (RuntimeException invalid) { throw new IOException("Invalid leaderboard JSON", invalid); }
            }
        } finally { connection.disconnect(); }
    }

    private static final class ColdStartUnavailable extends IOException {
        private static final long serialVersionUID = 1L;
        private ColdStartUnavailable(int status) { super("Leaderboard HTTP " + status); }
    }
}
