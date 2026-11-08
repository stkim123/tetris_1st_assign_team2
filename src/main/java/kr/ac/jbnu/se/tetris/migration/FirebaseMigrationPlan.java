package kr.ac.jbnu.se.tetris.migration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import kr.ac.jbnu.se.tetris.auth.UsernameIdentity;

/** Validated immutable mapping of the read-only Supabase export to Firestore documents. */
public final class FirebaseMigrationPlan {
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}");
    private final String sha256;
    private final long exportedAtMillis;
    private final List<User> users;
    private final Map<String, Map<String, Object>> players;
    private final Map<String, Map<String, Object>> identities;
    private final Map<String, Map<String, Object>> usernames;
    private final Map<String, Map<String, Object>> matches;
    private final Map<String, Map<String, Object>> runs;

    public static final class User {
        private final String uid;
        private final String email;
        private final String displayName;
        private final String bcryptHash;
        private final boolean emailVerified;
        private final boolean disabled;
        private User(String uid, String email, String displayName, String bcryptHash,
                     boolean emailVerified, boolean disabled) {
            this.uid = uid; this.email = email; this.displayName = displayName;
            this.bcryptHash = bcryptHash; this.emailVerified = emailVerified; this.disabled = disabled;
        }
        public String getUid() { return uid; }
        public String getEmail() { return email; }
        public String getDisplayName() { return displayName; }
        public String getBcryptHash() { return bcryptHash; }
        public boolean isEmailVerified() { return emailVerified; }
        public boolean isDisabled() { return disabled; }
    }

    private FirebaseMigrationPlan(String sha256, long exportedAtMillis, List<User> users,
                                  Map<String, Map<String, Object>> players,
                                  Map<String, Map<String, Object>> identities,
                                  Map<String, Map<String, Object>> usernames,
                                  Map<String, Map<String, Object>> matches,
                                  Map<String, Map<String, Object>> runs) {
        this.sha256 = sha256;
        this.exportedAtMillis = exportedAtMillis;
        this.users = Collections.unmodifiableList(users);
        this.players = Collections.unmodifiableMap(players);
        this.identities = Collections.unmodifiableMap(identities);
        this.usernames = Collections.unmodifiableMap(usernames);
        this.matches = Collections.unmodifiableMap(matches);
        this.runs = Collections.unmodifiableMap(runs);
    }

    public String getSha256() { return sha256; }
    public long getExportedAtMillis() { return exportedAtMillis; }
    public List<User> getUsers() { return users; }
    public Map<String, Map<String, Object>> getPlayers() { return players; }
    public Map<String, Map<String, Object>> getIdentities() { return identities; }
    public Map<String, Map<String, Object>> getUsernames() { return usernames; }
    public Map<String, Map<String, Object>> getMatches() { return matches; }
    public Map<String, Map<String, Object>> getRuns() { return runs; }

    public static FirebaseMigrationPlan load(Path path) throws IOException {
        long size = Files.size(path);
        if (size <= 0 || size > 50L * 1024 * 1024) throw new IOException("Export size is invalid");
        byte[] bytes = Files.readAllBytes(path);
        String digest = sha256(bytes);
        JsonObject root;
        try { root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException invalid) { throw new IOException("Invalid export JSON", invalid); }
        if (integer(root, "schemaVersion") != 1) throw new IOException("Unsupported export schema");
        long exported = timestamp(root, "exportedAt");
        if (exported > System.currentTimeMillis() + 60_000) throw new IOException("Export time is in the future");
        List<User> users = new ArrayList<>();
        Map<String, User> byUid = new LinkedHashMap<>();
        Set<String> emails = new HashSet<>();
        for (JsonElement element : array(root, "users")) {
            JsonObject row = object(element, "user");
            String uid = id(row, "id"), email = required(row, "email").toLowerCase(Locale.ROOT);
            if (email.length() > 254 || email.indexOf('@') < 1 || email.indexOf(' ') >= 0
                    || !emails.add(email)) throw new IOException("Invalid or duplicate user email");
            String hash = optional(row, "encrypted_password");
            if (hash == null || !BCRYPT.matcher(hash).matches())
                throw new IOException("Unsupported password hash; bcrypt required");
            boolean confirmed = optional(row, "email_confirmed_at") != null;
            String ban = optional(row, "banned_until");
            boolean disabled = "infinity".equals(ban);
            if (ban != null && !disabled && !"-infinity".equals(ban) && parseTimestamp(ban) > exported)
                throw new IOException("Active finite ban requires an expiry-aware migration path");
            String name = UsernameIdentity.fromEmail(email);
            if (name == null) {
                JsonObject metadata = nullableObject(row, "raw_user_meta_data");
                name = metadata == null ? null : optional(metadata, "display_name");
            }
            User user = new User(uid, email, name, hash, confirmed, disabled);
            if (byUid.put(uid, user) != null) throw new IOException("Duplicate user ID");
            users.add(user);
        }
        Map<String, String> names = new HashMap<>();
        for (JsonElement element : array(root, "profiles")) {
            JsonObject row = object(element, "profile");
            String uid = id(row, "user_id"), name = required(row, "display_name");
            if (!byUid.containsKey(uid) || name.length() > 32 || names.put(uid, name) != null)
                throw new IOException("Invalid or duplicate profile");
        }
        if (!names.keySet().equals(byUid.keySet())) throw new IOException("Missing user profile");
        Map<String, Map<String, Object>> players = new LinkedHashMap<>();
        for (JsonElement element : array(root, "player_stats")) {
            JsonObject row = object(element, "player stats");
            String uid = id(row, "user_id");
            User user = byUid.get(uid);
            if (user == null || players.containsKey(uid)) throw new IOException("Invalid or duplicate player stats");
            int rating = integer(row, "rating"), wins = integer(row, "wins");
            int losses = integer(row, "losses"), games = integer(row, "games");
            if (wins < 0 || losses < 0 || games < 0
                    || games != (long) wins + losses || optional(row, "active_match_id") != null)
                throw new IOException("Inconsistent player stats or active match");
            String name = UsernameIdentity.fromEmail(user.email);
            if (name == null) name = names.get(uid);
            Map<String, Object> document = fields("userId", uid, "displayName", name,
                    "rating", rating, "wins", wins, "losses", losses, "games", games,
                    "ranked", games > 0, "activeMatchId", null, "migrationDigest", digest);
            players.put(uid, document);
        }
        if (!players.keySet().equals(byUid.keySet())) throw new IOException("Missing player stats");
        Map<String, Map<String, Object>> identities = new LinkedHashMap<>();
        Map<String, Map<String, Object>> usernames = new LinkedHashMap<>();
        for (User user : users) {
            String displayName = (String) players.get(user.uid).get("displayName");
            identities.put(playerDocumentId(user.uid), fields("userId", user.uid,
                    "email", user.email, "displayName", displayName));
            String canonical = UsernameIdentity.fromEmail(user.email);
            if (canonical != null) {
                if (usernames.put(canonical, fields("userId", user.uid)) != null)
                    throw new IOException("Duplicate canonical username");
            }
        }

        Map<String, Map<String, Object>> runs = new LinkedHashMap<>();
        for (JsonElement element : array(root, "server_runs")) {
            JsonObject row = object(element, "run");
            String id = id(row, "run_id"), state = required(row, "state");
            if (!("ACTIVE".equals(state) || "STOPPED".equals(state)) || runs.containsKey(id))
                throw new IOException("Invalid or duplicate server run");
            long created = timestamp(row, "created_at");
            Long stopped = optional(row, "stopped_at") == null ? null : timestamp(row, "stopped_at");
            if ("STOPPED".equals(state) && stopped == null) throw new IOException("Stopped run has no timestamp");
            runs.put(id, fields("state", "STOPPED", "createdAtMillis", created,
                    "stoppedAtMillis", stopped == null ? exported : stopped, "migrationDigest", digest));
        }

        Map<String, JsonObject[]> participants = new HashMap<>();
        for (JsonElement element : array(root, "match_participants")) {
            JsonObject row = object(element, "participant");
            String matchId = id(row, "match_id");
            int slot = integer(row, "slot");
            if (slot < 1 || slot > 2) throw new IOException("Invalid participant slot");
            JsonObject[] pair = participants.computeIfAbsent(matchId, ignored -> new JsonObject[2]);
            if (pair[slot - 1] != null) throw new IOException("Duplicate participant slot");
            pair[slot - 1] = row;
        }
        Map<String, Map<String, Object>> matches = new LinkedHashMap<>();
        Map<String, int[]> totals = new HashMap<>();
        Map<String, Long> latestTime = new HashMap<>();
        Map<String, Integer> latestRating = new HashMap<>();
        for (JsonElement element : array(root, "matches")) {
            JsonObject row = object(element, "match");
            String matchId = id(row, "match_id"), runId = id(row, "server_run_id");
            String first = id(row, "first_user_id"), second = id(row, "second_user_id");
            String status = required(row, "status"), rules = required(row, "rules_version");
            if (!runs.containsKey(runId) || !byUid.containsKey(first) || !byUid.containsKey(second)
                    || first.equals(second) || rules.length() > 100 || matches.containsKey(matchId)
                    || !("FINALIZED".equals(status) || "VOID".equals(status)))
                throw new IOException("Invalid, running, or duplicate match");
            JsonObject[] pair = participants.remove(matchId);
            if (pair == null || pair[0] == null || pair[1] == null
                    || !first.equals(id(pair[0], "user_id")) || !second.equals(id(pair[1], "user_id")))
                throw new IOException("Match participants do not match slots");
            String winner = optional(row, "winner_user_id"), reason = required(row, "reason");
            if (reason.length() > 100) throw new IOException("Invalid match reason");
            long started = timestamp(row, "started_at"), ended = timestamp(row, "ended_at");
            if (ended < started || ended > exported) throw new IOException("Invalid match timestamps");
            Integer firstBefore = nullableInteger(pair[0], "rating_before");
            Integer firstAfter = nullableInteger(pair[0], "rating_after");
            Integer secondBefore = nullableInteger(pair[1], "rating_before");
            Integer secondAfter = nullableInteger(pair[1], "rating_after");
            if ("FINALIZED".equals(status)) {
                long firstDelta = firstBefore == null || firstAfter == null ? Long.MIN_VALUE
                        : (long) firstAfter - firstBefore;
                long secondDelta = secondBefore == null || secondAfter == null ? Long.MIN_VALUE
                        : (long) secondAfter - secondBefore;
                long winnerBefore = winner != null && winner.equals(first) && firstBefore != null
                        ? firstBefore : secondBefore == null ? 0 : secondBefore;
                long loserBefore = winner != null && winner.equals(first) && secondBefore != null
                        ? secondBefore : firstBefore == null ? 0 : firstBefore;
                double expectedWin = 1.0 / (1.0 + Math.pow(10.0, (loserBefore - winnerBefore) / 400.0));
                long eloDelta = (long) Math.floor(32.0 * (1.0 - expectedWin) + 0.5);
                if (!(first.equals(winner) || second.equals(winner)) || firstBefore == null
                        || firstAfter == null || secondBefore == null || secondAfter == null
                        || firstDelta != -secondDelta
                        || firstDelta != (winner.equals(first) ? eloDelta : -eloDelta)
                        || !required(pair[0], "outcome").equals(winner.equals(first) ? "WIN" : "LOSS")
                        || !required(pair[1], "outcome").equals(winner.equals(second) ? "WIN" : "LOSS"))
                    throw new IOException("Inconsistent finalized match result");
                tally(totals, first, winner.equals(first));
                tally(totals, second, winner.equals(second));
                latest(latestTime, latestRating, first, ended, firstAfter);
                latest(latestTime, latestRating, second, ended, secondAfter);
            } else if (winner != null || firstBefore != null || firstAfter != null
                    || secondBefore != null || secondAfter != null
                    || !"VOID".equals(required(pair[0], "outcome"))
                    || !"VOID".equals(required(pair[1], "outcome")))
                throw new IOException("Inconsistent void match result");
            matches.put(matchId, fields("matchId", matchId, "serverRunId", runId,
                    "rulesVersion", rules, "firstUserId", first, "secondUserId", second,
                    "status", status, "winnerUserId", winner, "reason", reason,
                    "startedAtMillis", started, "endedAtMillis", ended,
                    "firstRatingBefore", firstBefore, "firstRatingAfter", firstAfter,
                    "secondRatingBefore", secondBefore, "secondRatingAfter", secondAfter,
                    "migrationDigest", digest));
        }
        if (!participants.isEmpty()) throw new IOException("Orphan match participant");
        for (Map.Entry<String, Map<String, Object>> entry : players.entrySet()) {
            String uid = entry.getKey();
            Map<String, Object> player = entry.getValue();
            int[] score = totals.getOrDefault(uid, new int[2]);
            if (score[0] != (Integer) player.get("wins") || score[1] != (Integer) player.get("losses"))
                throw new IOException("Player totals do not match finalized matches");
            if (latestRating.containsKey(uid) && !latestRating.get(uid).equals(player.get("rating")))
                throw new IOException("Player rating does not match latest finalized match");
        }
        return new FirebaseMigrationPlan(digest, exported, users, players, identities, usernames, matches, runs);
    }

    private static void tally(Map<String, int[]> totals, String uid, boolean won) {
        totals.computeIfAbsent(uid, ignored -> new int[2])[won ? 0 : 1]++;
    }

    private static void latest(Map<String, Long> times, Map<String, Integer> ratings,
                               String uid, long ended, int after) {
        if (ended >= times.getOrDefault(uid, Long.MIN_VALUE)) {
            times.put(uid, ended); ratings.put(uid, after);
        }
    }

    public static String playerDocumentId(String uid) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(uid.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> fields(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return Collections.unmodifiableMap(result);
    }

    private static JsonArray array(JsonObject root, String name) throws IOException {
        if (!root.has(name) || !root.get(name).isJsonArray()) throw new IOException("Missing export section: " + name);
        return root.getAsJsonArray(name);
    }
    private static JsonObject object(JsonElement element, String name) throws IOException {
        if (!element.isJsonObject()) throw new IOException("Invalid export " + name);
        return element.getAsJsonObject();
    }
    private static JsonObject nullableObject(JsonObject row, String name) throws IOException {
        JsonElement element = row.get(name);
        if (element == null || element.isJsonNull()) return null;
        return object(element, name);
    }
    private static String optional(JsonObject row, String name) throws IOException {
        JsonElement element = row.get(name);
        if (element == null || element.isJsonNull()) return null;
        if (!element.isJsonPrimitive()) throw new IOException("Invalid export field: " + name);
        return element.getAsString();
    }
    private static String required(JsonObject row, String name) throws IOException {
        String value = optional(row, name);
        if (value == null || value.trim().isEmpty()) throw new IOException("Missing export field: " + name);
        return value;
    }
    private static String id(JsonObject row, String name) throws IOException {
        String value = required(row, name);
        if (!UUID.matcher(value).matches()) throw new IOException("Invalid UUID: " + name);
        return value;
    }
    private static int integer(JsonObject row, String name) throws IOException {
        String value = required(row, name);
        try { return Integer.parseInt(value); }
        catch (NumberFormatException invalid) { throw new IOException("Invalid integer: " + name, invalid); }
    }
    private static Integer nullableInteger(JsonObject row, String name) throws IOException {
        String value = optional(row, name);
        if (value == null) return null;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException invalid) { throw new IOException("Invalid integer: " + name, invalid); }
    }
    private static long timestamp(JsonObject row, String name) throws IOException {
        return parseTimestamp(required(row, name));
    }
    private static long parseTimestamp(String value) throws IOException {
        try { return OffsetDateTime.parse(value).toInstant().toEpochMilli(); }
        catch (RuntimeException invalid) { throw new IOException("Invalid export timestamp", invalid); }
    }
    private static String sha256(byte[] bytes) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
    }
}
