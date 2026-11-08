package kr.ac.jbnu.se.tetris.ranking;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.UUID;
import kr.ac.jbnu.se.tetris.supabase.SupabaseConfig;
import kr.ac.jbnu.se.tetris.supabase.SupabaseHttp;

/** Server-only Data API client. The secret key must never be packaged with the desktop. */
public final class SupabaseRankedMatchStore implements RankedMatchStore {
    private final SupabaseHttp http;

    public SupabaseRankedMatchStore(SupabaseConfig config) {
        config.requireServerSecretKey();
        this.http = new SupabaseHttp(config);
    }

    @Override public RunLease claimRun(String serverRunId) throws IOException {
        return lease(serverRunId, "CLAIM");
    }

    @Override public RunLease renewRun(String serverRunId) throws IOException {
        return lease(serverRunId, "RENEW");
    }

    @Override public RunLease setAdmission(String serverRunId, boolean enabled) throws IOException {
        return lease(serverRunId, enabled ? "OPEN" : "DRAIN");
    }

    private RunLease lease(String serverRunId, String action) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("p_server_run", uuid(serverRunId));
        body.addProperty("p_action", action);
        try {
            JsonObject result = rpc("ranked_run_lease", body).getAsJsonObject();
            return new RunLease(result.get("owned").getAsBoolean(), result.get("enabled").getAsBoolean());
        } catch (RuntimeException invalid) { throw new IOException("Invalid lease response", invalid); }
    }

    @Override public MatchRecord beginMatch(String matchId, String serverRunId, String rulesVersion,
            String firstUserId, String secondUserId) throws IOException {
        JsonObject body = matchAndRun(matchId, serverRunId);
        body.addProperty("p_rules_version", required(rulesVersion));
        body.addProperty("p_first_user", uuid(firstUserId));
        body.addProperty("p_second_user", uuid(secondUserId));
        if (firstUserId.equals(secondUserId)) throw new StoreException("SELF_MATCH");
        return decode(rpc("ranked_begin_match", body));
    }

    @Override public MatchRecord finishMatch(String matchId, String serverRunId,
            String winnerUserId, String reason) throws IOException {
        JsonObject body = matchAndRun(matchId, serverRunId);
        body.addProperty("p_winner_user", uuid(winnerUserId));
        body.addProperty("p_reason", required(reason));
        return decode(rpc("ranked_finish_match", body));
    }

    @Override public MatchRecord voidMatch(String matchId, String serverRunId, String reason)
            throws IOException {
        JsonObject body = matchAndRun(matchId, serverRunId);
        body.addProperty("p_reason", required(reason));
        return decode(rpc("ranked_void_match", body));
    }

    @Override public MatchRecord getMatch(String matchId) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("p_match_id", uuid(matchId));
        JsonElement value = rpc("ranked_get_match", body);
        return value == null || value.isJsonNull() ? null : decode(value);
    }

    @Override public int voidStoppedRun(String currentRunId, String stoppedRunId) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("p_current_run", uuid(currentRunId));
        body.addProperty("p_stopped_run", uuid(stoppedRunId));
        return rpc("ranked_void_stopped_run", body).getAsInt();
    }

    private JsonElement rpc(String function, JsonObject body) throws IOException {
        return http.request("POST", "/rest/v1/rpc/" + function, body, null, true);
    }

    private static JsonObject matchAndRun(String matchId, String serverRunId) {
        JsonObject body = new JsonObject();
        body.addProperty("p_match_id", uuid(matchId));
        body.addProperty("p_server_run", uuid(serverRunId));
        return body;
    }

    private static String uuid(String value) {
        try { return UUID.fromString(value).toString(); }
        catch (RuntimeException e) { throw new StoreException("INVALID_UUID"); }
    }

    private static String required(String value) {
        if (value == null || value.trim().isEmpty() || value.length() > 100)
            throw new StoreException("INVALID_VALUE");
        return value.trim();
    }

    static MatchRecord decode(JsonElement element) throws IOException {
        try {
            JsonObject json = element.getAsJsonObject();
            return new MatchRecord(value(json, "match_id"), value(json, "server_run_id"),
                    value(json, "first_user_id"), value(json, "second_user_id"),
                    MatchRecord.Status.valueOf(value(json, "status")), value(json, "winner_user_id"),
                    value(json, "reason"), integer(json, "first_rating_before"),
                    integer(json, "first_rating_after"), integer(json, "second_rating_before"),
                    integer(json, "second_rating_after"));
        } catch (RuntimeException e) { throw new IOException("Invalid match response", e); }
    }

    private static String value(JsonObject json, String name) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsString() : null;
    }

    private static Integer integer(JsonObject json, String name) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsInt() : null;
    }
}
