package kr.ac.jbnu.se.tetris.migration;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Offline export validation, including settlement and identity continuity checks. */
public final class FirebaseMigrationPlanTest {
    private static final String FIRST = "11111111-1111-4111-8111-111111111111";
    private static final String SECOND = "22222222-2222-4222-8222-222222222222";
    private static final String RUN = "33333333-3333-4333-8333-333333333333";
    private static final String FINAL = "44444444-4444-4444-8444-444444444444";
    private static final String VOID = "55555555-5555-4555-8555-555555555555";
    private FirebaseMigrationPlanTest() { }

    public static void main(String[] args) throws Exception {
        JsonObject export = fixture();
        FirebaseMigrationPlan plan = parse(export);
        check(plan.getUsers().size() == 2 && plan.getMatches().size() == 2
                && plan.getPlayers().size() == 2 && plan.getRuns().size() == 1, "record counts");
        check(plan.getSha256().matches("[0-9a-f]{64}"), "digest");
        check(plan.getIdentities().size() == 2 && plan.getUsernames().size() == 1
                && FIRST.equals(plan.getUsernames().get("player_01").get("userId")),
                "immutable username binding seeded");
        Map<String, Object> first = plan.getPlayers().get(FIRST);
        check("player_01".equals(first.get("displayName")) && Boolean.TRUE.equals(first.get("ranked"))
                && (Integer) first.get("rating") == 1016, "username and score preserved");
        check("STOPPED".equals(plan.getRuns().get(RUN).get("state")), "historical run fenced");
        check("FINALIZED".equals(plan.getMatches().get(FINAL).get("status"))
                && (Integer) plan.getMatches().get(FINAL).get("firstRatingAfter") == 1016,
                "final rating snapshot preserved");
        check(!plan.getUsers().get(0).isDisabled() && plan.getUsers().get(0).isEmailVerified(),
                "active account and email confirmation preserved");
        check(plan.getUsers().get(1).isDisabled(), "ban preserved");

        JsonObject active = copy(export);
        active.getAsJsonArray("player_stats").get(0).getAsJsonObject().addProperty("active_match_id", FINAL);
        rejects(active, "active match");
        JsonObject running = copy(export);
        running.getAsJsonArray("matches").get(0).getAsJsonObject().addProperty("status", "RUNNING");
        rejects(running, "running match");
        JsonObject duplicateEmail = copy(export);
        duplicateEmail.getAsJsonArray("users").get(1).getAsJsonObject()
                .addProperty("email", "player_01@players.campus-quest.invalid");
        rejects(duplicateEmail, "duplicate Auth email");
        JsonObject score = copy(export);
        score.getAsJsonArray("player_stats").get(0).getAsJsonObject().addProperty("rating", 2000);
        rejects(score, "ranking mismatch");
        JsonObject badHash = copy(export);
        badHash.getAsJsonArray("users").get(0).getAsJsonObject()
                .addProperty("encrypted_password", "not-bcrypt");
        rejects(badHash, "unsupported password format");
        JsonObject passwordless = copy(export);
        passwordless.getAsJsonArray("users").get(0).getAsJsonObject()
                .add("encrypted_password", JsonNull.INSTANCE);
        rejects(passwordless, "passwordless account cannot use this migration path");
        JsonObject finiteBan = copy(export);
        finiteBan.getAsJsonArray("users").get(1).getAsJsonObject()
                .addProperty("banned_until", "2999-01-01T00:00:00Z");
        rejects(finiteBan, "active finite ban without expiry mapping");
        JsonObject zeroDelta = copy(export);
        zeroDelta.getAsJsonArray("match_participants").get(0).getAsJsonObject()
                .addProperty("rating_before", 2000);
        zeroDelta.getAsJsonArray("match_participants").get(0).getAsJsonObject()
                .addProperty("rating_after", 2000);
        zeroDelta.getAsJsonArray("match_participants").get(1).getAsJsonObject()
                .addProperty("rating_before", 1000);
        zeroDelta.getAsJsonArray("match_participants").get(1).getAsJsonObject()
                .addProperty("rating_after", 1000);
        zeroDelta.getAsJsonArray("player_stats").get(0).getAsJsonObject().addProperty("rating", 2000);
        zeroDelta.getAsJsonArray("player_stats").get(1).getAsJsonObject().addProperty("rating", 1000);
        check(parse(zeroDelta).getMatches().size() == 2, "zero Elo delta remains valid");
        JsonObject negativeRating = copy(export);
        negativeRating.getAsJsonArray("match_participants").get(0).getAsJsonObject()
                .addProperty("rating_before", -100);
        negativeRating.getAsJsonArray("match_participants").get(0).getAsJsonObject()
                .addProperty("rating_after", -68);
        negativeRating.getAsJsonArray("match_participants").get(1).getAsJsonObject()
                .addProperty("rating_before", 1000);
        negativeRating.getAsJsonArray("match_participants").get(1).getAsJsonObject()
                .addProperty("rating_after", 968);
        negativeRating.getAsJsonArray("player_stats").get(0).getAsJsonObject().addProperty("rating", -68);
        negativeRating.getAsJsonArray("player_stats").get(1).getAsJsonObject().addProperty("rating", 968);
        check(parse(negativeRating).getPlayers().size() == 2, "legal negative rating preserved");
        JsonObject wrongElo = copy(export);
        wrongElo.getAsJsonArray("match_participants").get(0).getAsJsonObject()
                .addProperty("rating_after", 1017);
        wrongElo.getAsJsonArray("match_participants").get(1).getAsJsonObject()
                .addProperty("rating_after", 983);
        wrongElo.getAsJsonArray("player_stats").get(0).getAsJsonObject().addProperty("rating", 1017);
        wrongElo.getAsJsonArray("player_stats").get(1).getAsJsonObject().addProperty("rating", 983);
        rejects(wrongElo, "incorrect Elo settlement");
        System.out.println("PASS FirebaseMigrationPlanTest");
    }

    private static FirebaseMigrationPlan parse(JsonObject export) throws Exception {
        Path file = Files.createTempFile("firebase-migration-fixture-", ".json");
        try {
            Files.write(file, export.toString().getBytes(StandardCharsets.UTF_8));
            return FirebaseMigrationPlan.load(file);
        } finally { Files.deleteIfExists(file); }
    }

    private static void rejects(JsonObject export, String what) throws Exception {
        try { parse(export); throw new AssertionError("Accepted " + what); }
        catch (IOException expected) { /* Validation failed before any cloud access. */ }
    }

    private static JsonObject copy(JsonObject source) {
        return JsonParser.parseString(source.toString()).getAsJsonObject();
    }

    static JsonObject fixture() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("exportedAt", "2025-01-01T00:00:00Z");
        JsonArray users = new JsonArray();
        users.add(user(FIRST, "player_01@players.campus-quest.invalid", true, false));
        users.add(user(SECOND, "real@example.test", false, true));
        root.add("users", users);
        JsonArray profiles = new JsonArray();
        profiles.add(fields("user_id", FIRST, "display_name", "Wrong saved profile"));
        profiles.add(fields("user_id", SECOND, "display_name", "Real Player"));
        root.add("profiles", profiles);
        JsonArray stats = new JsonArray();
        stats.add(fields("user_id", FIRST, "rating", 1016, "wins", 1, "losses", 0,
                "games", 1, "active_match_id", null));
        stats.add(fields("user_id", SECOND, "rating", 984, "wins", 0, "losses", 1,
                "games", 1, "active_match_id", null));
        root.add("player_stats", stats);
        JsonArray runs = new JsonArray();
        runs.add(fields("run_id", RUN, "state", "ACTIVE", "created_at", "2024-12-30T00:00:00Z",
                "stopped_at", null));
        root.add("server_runs", runs);
        JsonArray matches = new JsonArray();
        matches.add(match(FINAL, "FINALIZED", FIRST, "RESULT", "2024-12-31T00:01:00Z"));
        matches.add(match(VOID, "VOID", null, "SERVER_STOPPED", "2024-12-31T00:03:00Z"));
        root.add("matches", matches);
        JsonArray participants = new JsonArray();
        participants.add(participant(FINAL, FIRST, 1, "WIN", 1000, 1016));
        participants.add(participant(FINAL, SECOND, 2, "LOSS", 1000, 984));
        participants.add(participant(VOID, FIRST, 1, "VOID", null, null));
        participants.add(participant(VOID, SECOND, 2, "VOID", null, null));
        root.add("match_participants", participants);
        return root;
    }

    private static JsonObject user(String uid, String email, boolean verified, boolean banned) {
        return fields("id", uid, "email", email, "encrypted_password",
                "$2a$10$EkPgLHmp4/LFP7DVD4Sj..5vVnYl9yyP0VCZaq7spwbOfRcYMaUtC",
                "email_confirmed_at", verified ? "2024-12-30T00:00:00Z" : null,
                "banned_until", banned ? "infinity" : null,
                "raw_user_meta_data", new JsonObject());
    }
    private static JsonObject match(String id, String status, String winner, String reason, String end) {
        return fields("match_id", id, "server_run_id", RUN, "rules_version", "v1",
                "first_user_id", FIRST, "second_user_id", SECOND, "status", status,
                "winner_user_id", winner, "reason", reason,
                "started_at", "2024-12-31T00:00:00Z", "ended_at", end);
    }
    private static JsonObject participant(String match, String uid, int slot, String outcome,
                                          Integer before, Integer after) {
        return fields("match_id", match, "user_id", uid, "slot", slot,
                "outcome", outcome, "rating_before", before, "rating_after", after);
    }
    private static JsonObject fields(Object... pairs) {
        JsonObject result = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) {
            Object value = pairs[i + 1];
            if (value == null) result.add((String) pairs[i], JsonNull.INSTANCE);
            else if (value instanceof Number) result.addProperty((String) pairs[i], (Number) value);
            else if (value instanceof Boolean) result.addProperty((String) pairs[i], (Boolean) value);
            else if (value instanceof JsonObject) result.add((String) pairs[i], (JsonObject) value);
            else result.addProperty((String) pairs[i], value.toString());
        }
        return result;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
