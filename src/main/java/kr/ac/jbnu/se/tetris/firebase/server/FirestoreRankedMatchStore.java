package kr.ac.jbnu.se.tetris.firebase.server;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.Transaction;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import kr.ac.jbnu.se.tetris.auth.UsernameIdentity;
import kr.ac.jbnu.se.tetris.ranking.LeaderboardEntry;
import kr.ac.jbnu.se.tetris.ranking.MatchRecord;
import kr.ac.jbnu.se.tetris.ranking.RankedMatchStore;
import kr.ac.jbnu.se.tetris.ranking.RunLease;

/** Transactional ranked storage. Only the Cloud Run service identity may write these collections. */
public final class FirestoreRankedMatchStore implements RankedMatchStore {
    private static final long LEASE_MILLIS = 30_000;
    // Recovery must be atomic with fencing. Refuse oversized recovery rather than leave half a run active.
    private static final int MAX_RECOVERY_MATCHES = 100;
    private final Firestore firestore;
    private final FirebaseAuth auth;
    private final Clock clock;
    private final DocumentReference control;
    private final CollectionReference runs;
    private final CollectionReference matches;
    private final CollectionReference players;

    public FirestoreRankedMatchStore(Firestore firestore) { this(firestore, null, Clock.systemUTC()); }
    public FirestoreRankedMatchStore(Firestore firestore, FirebaseAuth auth) {
        this(firestore, auth, Clock.systemUTC());
    }

    /** Clock injection is used by the emulator integration test; production uses Cloud Run's synced clock. */
    public FirestoreRankedMatchStore(Firestore firestore, FirebaseAuth auth, Clock clock) {
        if (firestore == null || clock == null) throw new IllegalArgumentException("Firestore and clock required");
        this.firestore = firestore;
        this.auth = auth;
        this.clock = clock;
        this.control = firestore.collection("ranked_control").document("singleton");
        this.runs = firestore.collection("ranked_runs");
        this.matches = firestore.collection("ranked_matches");
        this.players = firestore.collection("ranked_players");
    }

    @Override public RunLease claimRun(String serverRunId) throws IOException {
        return lease(serverRunId, Action.CLAIM);
    }

    @Override public RunLease renewRun(String serverRunId) throws IOException {
        return lease(serverRunId, Action.RENEW);
    }

    @Override public RunLease setAdmission(String serverRunId, boolean enabled) throws IOException {
        return lease(serverRunId, enabled ? Action.OPEN : Action.DRAIN);
    }

    private enum Action { CLAIM, RENEW, OPEN, DRAIN }

    private RunLease lease(String runId, Action action) throws IOException {
        required(runId, "INVALID_RUN");
        return transaction(tx -> {
            long now = clock.millis();
            DocumentSnapshot gate = await(tx.get(control));
            if (action == Action.OPEN || action == Action.CLAIM) {
                DocumentSnapshot migration = await(tx.get(firestore.collection("migration_state").document("supabase_v1")));
                if (migration.exists() && !"COMPLETE".equals(migration.getString("status")))
                    throw invalid("MIGRATION_IN_PROGRESS");
            }
            String owner = string(gate, "ownerRunId");
            long until = number(gate, "leaseUntilMillis", 0);
            boolean enabled = bool(gate, "enabled");
            if (action == Action.RENEW || action == Action.DRAIN) {
                if (!runId.equals(owner) || until <= now) return new RunLease(false, enabled);
                boolean nextEnabled = action == Action.DRAIN ? false : enabled;
                tx.set(control, controlFields(runId, now + LEASE_MILLIS, nextEnabled));
                return new RunLease(true, nextEnabled);
            }
            if (runId.equals(owner)) {
                if (until <= now) return new RunLease(false, enabled);
                boolean nextEnabled = action == Action.OPEN || enabled;
                tx.set(control, controlFields(runId, now + LEASE_MILLIS, nextEnabled));
                return new RunLease(true, nextEnabled);
            }
            if (owner != null && until > now) return new RunLease(false, enabled);
            if (action == Action.CLAIM && !enabled) return new RunLease(false, false);

            DocumentReference runRef = runs.document(runId);
            DocumentSnapshot run = await(tx.get(runRef));
            if ("STOPPED".equals(string(run, "state"))) throw invalid("RUN_STOPPED");
            QuerySnapshot active = await(tx.get(runs.whereEqualTo("state", "ACTIVE")));
            Set<String> stale = new LinkedHashSet<>();
            for (QueryDocumentSnapshot candidate : active.getDocuments())
                if (!runId.equals(candidate.getId())) stale.add(candidate.getId());
            if (owner != null && !runId.equals(owner)) stale.add(owner);
            Recovery recovery = readRecovery(tx, stale);

            writeRecovery(tx, recovery, now);
            if (!run.exists()) tx.set(runRef, runFields("ACTIVE", now, null));
            boolean nextEnabled = action == Action.OPEN || enabled;
            tx.set(control, controlFields(runId, now + LEASE_MILLIS, nextEnabled));
            return new RunLease(true, nextEnabled);
        });
    }

    @Override public MatchRecord beginMatch(String matchId, String serverRunId, String rulesVersion,
            String firstUserId, String secondUserId) throws IOException {
        required(matchId, "INVALID_MATCH");
        required(serverRunId, "INVALID_MATCH");
        required(firstUserId, "INVALID_MATCH");
        required(secondUserId, "INVALID_MATCH");
        bounded(rulesVersion, "INVALID_MATCH");
        if (firstUserId.equals(secondUserId)) throw new IOException("INVALID_MATCH");
        // Auth lookups happen outside retryable Firestore callbacks; the transaction still checks active slots.
        String firstName = profileName(firstUserId);
        String secondName = profileName(secondUserId);
        DocumentReference matchRef = matches.document(matchId);
        DocumentReference firstRef = player(firstUserId);
        DocumentReference secondRef = player(secondUserId);
        return transaction(tx -> {
            long now = clock.millis();
            DocumentSnapshot gate = await(tx.get(control));
            assertLease(gate, serverRunId, true, now);
            DocumentSnapshot run = await(tx.get(runs.document(serverRunId)));
            if (!"ACTIVE".equals(string(run, "state"))) throw invalid("RUN_STOPPED");
            DocumentSnapshot existing = await(tx.get(matchRef));
            if (existing.exists()) {
                if (!serverRunId.equals(string(existing, "serverRunId"))
                        || !rulesVersion.equals(string(existing, "rulesVersion"))
                        || !firstUserId.equals(string(existing, "firstUserId"))
                        || !secondUserId.equals(string(existing, "secondUserId")))
                    throw invalid("MATCH_ID_CONFLICT");
                return record(existing);
            }
            DocumentSnapshot first = await(tx.get(firstRef));
            DocumentSnapshot second = await(tx.get(secondRef));
            if (string(first, "activeMatchId") != null || string(second, "activeMatchId") != null)
                throw invalid("ACCOUNT_IN_ACTIVE_MATCH");
            Map<String, Object> firstData = playerData(first, firstUserId, firstName);
            Map<String, Object> secondData = playerData(second, secondUserId, secondName);
            firstData.put("activeMatchId", matchId);
            secondData.put("activeMatchId", matchId);
            Map<String, Object> game = new HashMap<>();
            game.put("matchId", matchId);
            game.put("serverRunId", serverRunId);
            game.put("rulesVersion", rulesVersion);
            game.put("firstUserId", firstUserId);
            game.put("secondUserId", secondUserId);
            game.put("status", "RUNNING");
            game.put("winnerUserId", null);
            game.put("reason", null);
            game.put("startedAtMillis", now);
            game.put("endedAtMillis", null);
            game.put("firstRatingBefore", null);
            game.put("firstRatingAfter", null);
            game.put("secondRatingBefore", null);
            game.put("secondRatingAfter", null);
            tx.set(matchRef, game);
            tx.set(firstRef, firstData);
            tx.set(secondRef, secondData);
            return record(game);
        });
    }

    @Override public MatchRecord finishMatch(String matchId, String serverRunId,
            String winnerUserId, String reason) throws IOException {
        required(matchId, "UNKNOWN_MATCH");
        required(serverRunId, "RUN_NOT_OWNER");
        bounded(reason, "INVALID_REASON");
        DocumentReference matchRef = matches.document(matchId);
        return transaction(tx -> {
            long now = clock.millis();
            DocumentSnapshot gate = await(tx.get(control));
            assertLease(gate, serverRunId, false, now);
            DocumentSnapshot match = await(tx.get(matchRef));
            if (!match.exists()) throw invalid("UNKNOWN_MATCH");
            if (!serverRunId.equals(string(match, "serverRunId"))) throw invalid("RUN_MISMATCH");
            String status = string(match, "status");
            if ("FINALIZED".equals(status)) {
                if (winnerUserId != null && winnerUserId.equals(string(match, "winnerUserId"))
                        && reason.equals(string(match, "reason"))) return record(match);
                throw invalid("RESULT_CONFLICT");
            }
            if (!"RUNNING".equals(status)) throw invalid("MATCH_NOT_RUNNING");
            String firstId = string(match, "firstUserId"), secondId = string(match, "secondUserId");
            if (winnerUserId == null || !(winnerUserId.equals(firstId) || winnerUserId.equals(secondId)))
                throw invalid("INVALID_WINNER");
            DocumentReference firstRef = player(firstId), secondRef = player(secondId);
            DocumentSnapshot first = await(tx.get(firstRef));
            DocumentSnapshot second = await(tx.get(secondRef));
            if (!first.exists() || !second.exists()) throw invalid("MISSING_STATS");
            if (!matchId.equals(string(first, "activeMatchId"))
                    || !matchId.equals(string(second, "activeMatchId")))
                throw invalid("ACTIVE_MATCH_CONFLICT");
            int firstRating = intValue(first, "rating", 1000);
            int secondRating = intValue(second, "rating", 1000);
            int winnerRating = winnerUserId.equals(firstId) ? firstRating : secondRating;
            int loserRating = winnerUserId.equals(firstId) ? secondRating : firstRating;
            int delta = eloDelta(winnerRating, loserRating);
            int firstAfter = firstRating + (winnerUserId.equals(firstId) ? delta : -delta);
            int secondAfter = secondRating + (winnerUserId.equals(secondId) ? delta : -delta);
            Map<String, Object> game = copy(match);
            game.put("status", "FINALIZED");
            game.put("winnerUserId", winnerUserId);
            game.put("reason", reason);
            game.put("endedAtMillis", now);
            game.put("firstRatingBefore", firstRating);
            game.put("firstRatingAfter", firstAfter);
            game.put("secondRatingBefore", secondRating);
            game.put("secondRatingAfter", secondAfter);
            tx.set(matchRef, game);
            tx.set(firstRef, settledPlayer(first, firstAfter, winnerUserId.equals(firstId)));
            tx.set(secondRef, settledPlayer(second, secondAfter, winnerUserId.equals(secondId)));
            return record(game);
        });
    }

    @Override public MatchRecord voidMatch(String matchId, String serverRunId, String reason)
            throws IOException {
        required(matchId, "UNKNOWN_MATCH");
        required(serverRunId, "RUN_NOT_OWNER");
        bounded(reason, "INVALID_REASON");
        DocumentReference matchRef = matches.document(matchId);
        return transaction(tx -> {
            long now = clock.millis();
            DocumentSnapshot gate = await(tx.get(control));
            assertLease(gate, serverRunId, false, now);
            DocumentSnapshot match = await(tx.get(matchRef));
            if (!match.exists()) throw invalid("UNKNOWN_MATCH");
            if (!serverRunId.equals(string(match, "serverRunId"))) throw invalid("RUN_MISMATCH");
            String status = string(match, "status");
            if ("VOID".equals(status)) {
                if (reason.equals(string(match, "reason"))) return record(match);
                throw invalid("VOID_CONFLICT");
            }
            if (!"RUNNING".equals(status)) throw invalid("MATCH_ALREADY_FINALIZED");
            DocumentReference firstRef = player(string(match, "firstUserId"));
            DocumentReference secondRef = player(string(match, "secondUserId"));
            DocumentSnapshot first = await(tx.get(firstRef)), second = await(tx.get(secondRef));
            if (!matchId.equals(string(first, "activeMatchId"))
                    || !matchId.equals(string(second, "activeMatchId")))
                throw invalid("ACTIVE_MATCH_CONFLICT");
            Map<String, Object> game = copy(match);
            game.put("status", "VOID");
            game.put("reason", reason);
            game.put("endedAtMillis", now);
            Map<String, Object> firstData = copy(first), secondData = copy(second);
            firstData.put("activeMatchId", null);
            secondData.put("activeMatchId", null);
            tx.set(matchRef, game);
            tx.set(firstRef, firstData);
            tx.set(secondRef, secondData);
            return record(game);
        });
    }

    @Override public MatchRecord getMatch(String matchId) throws IOException {
        required(matchId, "UNKNOWN_MATCH");
        DocumentSnapshot snapshot = await(matches.document(matchId).get());
        return snapshot.exists() ? record(snapshot) : null;
    }

    @Override public int voidStoppedRun(String currentRunId, String stoppedRunId) throws IOException {
        required(currentRunId, "INVALID_RUN");
        required(stoppedRunId, "INVALID_RUN");
        if (currentRunId.equals(stoppedRunId)) throw new IOException("INVALID_RUN");
        return transaction(tx -> {
            long now = clock.millis();
            DocumentSnapshot gate = await(tx.get(control));
            assertLease(gate, currentRunId, false, now);
            Recovery recovery = readRecovery(tx, Collections.singleton(stoppedRunId));
            writeRecovery(tx, recovery, now);
            return recovery.games.size();
        });
    }

    public List<LeaderboardEntry> top100() throws IOException {
        Query query = players.whereEqualTo("ranked", true).orderBy("rating", Query.Direction.DESCENDING)
                .orderBy("userId", Query.Direction.ASCENDING).limit(100);
        QuerySnapshot snapshot = await(query.get());
        List<LeaderboardEntry> results = new ArrayList<>();
        int rank = 0, previousRating = Integer.MIN_VALUE;
        for (QueryDocumentSnapshot player : snapshot.getDocuments()) {
            int rating = intValue(player, "rating", 1000);
            if (rating != previousRating) rank = results.size() + 1;
            previousRating = rating;
            results.add(new LeaderboardEntry(rank, string(player, "userId"),
                    string(player, "displayName"), rating, intValue(player, "wins", 0),
                    intValue(player, "losses", 0), intValue(player, "games", 0)));
        }
        return results;
    }

    private Recovery readRecovery(Transaction tx, Set<String> staleRuns) throws Exception {
        Recovery recovery = new Recovery();
        for (String runId : staleRuns) {
            DocumentReference runRef = runs.document(runId);
            recovery.runs.put(runRef, await(tx.get(runRef)));
            QuerySnapshot active = await(tx.get(matches.whereEqualTo("serverRunId", runId)
                    .whereEqualTo("status", "RUNNING")));
            recovery.games.addAll(active.getDocuments());
        }
        if (recovery.games.size() > MAX_RECOVERY_MATCHES) throw invalid("RECOVERY_LIMIT_EXCEEDED");
        for (QueryDocumentSnapshot game : recovery.games) {
            recovery.players.add(player(string(game, "firstUserId")));
            recovery.players.add(player(string(game, "secondUserId")));
        }
        for (DocumentReference player : recovery.players)
            recovery.playerSnapshots.put(player, await(tx.get(player)));
        return recovery;
    }

    private void writeRecovery(Transaction tx, Recovery recovery, long now) {
        for (Map.Entry<DocumentReference, DocumentSnapshot> run : recovery.runs.entrySet()) {
            Map<String, Object> data = run.getValue().exists() ? copy(run.getValue())
                    : runFields("STOPPED", now, now);
            data.put("state", "STOPPED");
            if (data.get("stoppedAtMillis") == null) data.put("stoppedAtMillis", now);
            tx.set(run.getKey(), data);
        }
        Map<DocumentReference, Map<String, Object>> updated = new LinkedHashMap<>();
        for (QueryDocumentSnapshot game : recovery.games) {
            Map<String, Object> data = copy(game);
            data.put("status", "VOID");
            data.put("reason", "SERVER_STOPPED");
            data.put("endedAtMillis", now);
            tx.set(game.getReference(), data);
            for (String uid : new String[] {string(game, "firstUserId"), string(game, "secondUserId")}) {
                DocumentReference ref = player(uid);
                DocumentSnapshot snapshot = recovery.playerSnapshots.get(ref);
                if (!game.getId().equals(string(snapshot, "activeMatchId")))
                    throw invalid("ACTIVE_MATCH_CONFLICT");
                Map<String, Object> stats = copy(snapshot);
                stats.put("activeMatchId", null);
                updated.put(ref, stats);
            }
        }
        for (Map.Entry<DocumentReference, Map<String, Object>> player : updated.entrySet())
            tx.set(player.getKey(), player.getValue());
    }

    private static final class Recovery {
        final Map<DocumentReference, DocumentSnapshot> runs = new LinkedHashMap<>();
        final List<QueryDocumentSnapshot> games = new ArrayList<>();
        final Set<DocumentReference> players = new LinkedHashSet<>();
        final Map<DocumentReference, DocumentSnapshot> playerSnapshots = new HashMap<>();
    }

    private void assertLease(DocumentSnapshot gate, String runId, boolean requireOpen, long now) {
        if (!runId.equals(string(gate, "ownerRunId"))
                || number(gate, "leaseUntilMillis", 0) <= now
                || (requireOpen && !bool(gate, "enabled"))) throw invalid("RUN_NOT_OWNER");
    }

    private DocumentReference player(String userId) {
        String safe = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(userId.getBytes(StandardCharsets.UTF_8));
        return players.document(safe);
    }

    private String profileName(String uid) throws IOException {
        if (auth == null) return "Player " + uid.substring(0, Math.min(8, uid.length()));
        try {
            UserRecord user = auth.getUser(uid);
            String name = UsernameIdentity.fromEmail(user.getEmail());
            if (name == null) name = user.getDisplayName();
            if (name == null || name.trim().isEmpty() || name.length() > 32)
                name = "Player " + uid.substring(0, Math.min(8, uid.length()));
            return name;
        } catch (FirebaseAuthException e) { throw new IOException("UNKNOWN_ACCOUNT", e); }
    }

    private static Map<String, Object> playerData(DocumentSnapshot existing, String uid, String name) {
        if (existing.exists()) return copy(existing);
        Map<String, Object> result = new HashMap<>();
        result.put("userId", uid);
        result.put("displayName", name);
        result.put("rating", 1000);
        result.put("wins", 0);
        result.put("losses", 0);
        result.put("games", 0);
        result.put("ranked", false);
        result.put("activeMatchId", null);
        return result;
    }

    private static Map<String, Object> settledPlayer(DocumentSnapshot existing, int rating, boolean won) {
        Map<String, Object> player = copy(existing);
        player.put("rating", rating);
        player.put("wins", intValue(existing, "wins", 0) + (won ? 1 : 0));
        player.put("losses", intValue(existing, "losses", 0) + (won ? 0 : 1));
        player.put("games", intValue(existing, "games", 0) + 1);
        player.put("ranked", true);
        player.put("activeMatchId", null);
        return player;
    }

    /** PostgreSQL numeric round() for a positive Elo delta is equivalent to this calculation. */
    static int eloDelta(int winnerRating, int loserRating) {
        double expected = 1.0 / (1.0 + Math.pow(10.0, (loserRating - winnerRating) / 400.0));
        return (int) Math.floor(32.0 * (1.0 - expected) + 0.5);
    }

    private static MatchRecord record(DocumentSnapshot snapshot) { return record(snapshot.getData()); }
    private static MatchRecord record(Map<String, Object> data) {
        return new MatchRecord((String) data.get("matchId"), (String) data.get("serverRunId"),
                (String) data.get("firstUserId"), (String) data.get("secondUserId"),
                MatchRecord.Status.valueOf((String) data.get("status")),
                (String) data.get("winnerUserId"), (String) data.get("reason"),
                nullableInt(data.get("firstRatingBefore")), nullableInt(data.get("firstRatingAfter")),
                nullableInt(data.get("secondRatingBefore")), nullableInt(data.get("secondRatingAfter")));
    }

    private static Integer nullableInt(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private static Map<String, Object> copy(DocumentSnapshot snapshot) {
        return new HashMap<>(snapshot.getData());
    }

    private static Map<String, Object> controlFields(String owner, long until, boolean enabled) {
        Map<String, Object> result = new HashMap<>();
        result.put("ownerRunId", owner);
        result.put("leaseUntilMillis", until);
        result.put("enabled", enabled);
        return result;
    }

    private static Map<String, Object> runFields(String state, long created, Long stopped) {
        Map<String, Object> result = new HashMap<>();
        result.put("state", state);
        result.put("createdAtMillis", created);
        result.put("stoppedAtMillis", stopped);
        return result;
    }

    private static String string(DocumentSnapshot doc, String field) {
        return doc.exists() ? doc.getString(field) : null;
    }

    private static boolean bool(DocumentSnapshot doc, String field) {
        return doc.exists() && Boolean.TRUE.equals(doc.getBoolean(field));
    }

    private static long number(DocumentSnapshot doc, String field, long fallback) {
        Long value = doc.exists() ? doc.getLong(field) : null;
        return value == null ? fallback : value;
    }

    private static int intValue(DocumentSnapshot doc, String field, int fallback) {
        Long value = doc.exists() ? doc.getLong(field) : null;
        return value == null ? fallback : value.intValue();
    }

    private static void required(String value, String error) throws IOException {
        if (value == null || value.isEmpty() || value.length() > 256 || value.indexOf('/') >= 0)
            throw new IOException(error);
    }

    private static void bounded(String value, String error) throws IOException {
        if (value == null || value.isEmpty() || value.length() > 100) throw new IOException(error);
    }

    private static IllegalStateException invalid(String code) { return new IllegalStateException(code); }

    private interface Work<T> { T apply(Transaction tx) throws Exception; }

    private <T> T transaction(Work<T> work) throws IOException {
        return await(firestore.runTransaction(work::apply));
    }

    private static <T> T await(ApiFuture<T> future) throws IOException {
        try { return future.get(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Interrupted", e); }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            while (cause != null && cause.getCause() != null
                    && !(cause instanceof IllegalStateException)) cause = cause.getCause();
            if (cause instanceof IllegalStateException) throw new IOException(cause.getMessage(), cause);
            throw new IOException("Firestore operation failed", e.getCause());
        }
    }
}
