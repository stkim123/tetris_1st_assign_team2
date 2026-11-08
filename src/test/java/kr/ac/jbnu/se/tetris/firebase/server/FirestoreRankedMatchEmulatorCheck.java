package kr.ac.jbnu.se.tetris.firebase.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import kr.ac.jbnu.se.tetris.auth.AuthException;
import kr.ac.jbnu.se.tetris.auth.AuthIdentity;
import kr.ac.jbnu.se.tetris.auth.UsernameIdentity;
import kr.ac.jbnu.se.tetris.ranking.LeaderboardEntry;
import kr.ac.jbnu.se.tetris.ranking.MatchRecord;
import kr.ac.jbnu.se.tetris.ranking.RunLease;

/** Explicit integration check. Run only with both local Firebase emulators and a fresh demo project. */
public final class FirestoreRankedMatchEmulatorCheck {
    private FirestoreRankedMatchEmulatorCheck() { }

    public static void main(String[] args) throws Exception {
        String authHost = System.getenv("FIREBASE_AUTH_EMULATOR_HOST");
        String dbHost = System.getenv("FIRESTORE_EMULATOR_HOST");
        String project = System.getenv("FIREBASE_PROJECT_ID");
        require("127.0.0.1:9099".equals(authHost) && "127.0.0.1:8085".equals(dbHost)
                && project != null && project.startsWith("demo-"), "Local emulators required");
        MutableClock clock = new MutableClock();
        try (FirebaseServerContext context = FirebaseServerContext.fromEnvironment()) {
            require(!context.getFirestore().collection("ranked_control").document("singleton")
                    .get().get().exists(), "Use a fresh emulator database");
            FirestoreRankedMatchStore store = new FirestoreRankedMatchStore(
                    context.getFirestore(), context.getAuth(), clock);
            FirebaseTokenVerifier verifier = new FirebaseTokenVerifier(context.getAuth(), context.getFirestore());
            Account first = signup(authHost, "testa" + shortId());
            Account second = signup(authHost, "testb" + shortId());
            Account third = signup(authHost, "testc" + shortId());
            require(first.token.split("\\.", -1).length == 3, "Emulator returned a JWT");
            AuthIdentity checked = verifier.verify(first.token);
            require(first.uid.equals(checked.getUserId()) && first.username.equals(checked.getDisplayName()),
                    "Verified username identity");
            try { verifier.verify("abc.def.ghi"); throw new AssertionError("Invalid token accepted"); }
            catch (AuthException expected) { /* expected */ }
            FirebaseIdentityRegistry identities = new FirebaseIdentityRegistry(context.getFirestore());
            try { identities.resolve("foreign-user", "foreign@example.test", false);
                throw new AssertionError("Untrusted real-email user accepted"); }
            catch (AuthException expected) { require("GAME_ID_REQUIRED".equals(expected.getCode()), "Email namespace policy"); }
            try { identities.resolve(first.uid, UsernameIdentity.emailFor("changed" + shortId()), false);
                throw new AssertionError("Registered username changed"); }
            catch (AuthException expected) { require("GAME_ID_CHANGED".equals(expected.getCode()), "Immutable identity"); }
            try { identities.resolve("replacement-user", UsernameIdentity.emailFor(first.username), false);
                throw new AssertionError("Existing username reclaimed by another UID"); }
            catch (AuthException expected) { require("GAME_ID_RESERVED".equals(expected.getCode()), "Permanent username reservation"); }
            try { identities.resolve("unseeded-legacy", "legacy@example.test", true);
                throw new AssertionError("Legacy custom claim allowed unseeded identity"); }
            catch (AuthException expected) { require("LEGACY_IDENTITY_REQUIRED".equals(expected.getCode()), "Legacy identity must be migrated"); }

            String runA = UUID.randomUUID().toString(), runB = UUID.randomUUID().toString();
            require(!store.claimRun(runA).isOwned(), "New database must start drained");
            RunLease opened = store.setAdmission(runA, true);
            require(opened.isOwned() && opened.isEnabled(), "Operator open claims first run");
            String game1 = UUID.randomUUID().toString();
            MatchRecord begin = store.beginMatch(game1, runA, "pvp-v1", first.uid, second.uid);
            require(begin.getStatus() == MatchRecord.Status.RUNNING, "First game began");
            require(store.beginMatch(game1, runA, "pvp-v1", first.uid, second.uid)
                    .getStatus() == MatchRecord.Status.RUNNING, "Duplicate begin idempotent");
            expectFailure(() -> store.beginMatch(UUID.randomUUID().toString(), runA,
                    "pvp-v1", first.uid, third.uid), "ACCOUNT_IN_ACTIVE_MATCH");
            MatchRecord settled = store.finishMatch(game1, runA, first.uid, "WIN");
            require(settled.getFirstRatingBefore() == 1000 && settled.getFirstRatingAfter() == 1016
                    && settled.getSecondRatingAfter() == 984, "Elo applied atomically");
            require(store.finishMatch(game1, runA, first.uid, "WIN").getFirstRatingAfter() == 1016,
                    "Duplicate result idempotent");
            expectFailure(() -> store.finishMatch(game1, runA, second.uid, "WIN"), "RESULT_CONFLICT");
            List<LeaderboardEntry> board = store.top100();
            require(board.size() == 2 && board.get(0).getUserId().equals(first.uid)
                    && board.get(0).getRank() == 1 && board.get(0).getDisplayName().equals(first.username),
                    "Leaderboard ranks and canonical names");

            String game2 = UUID.randomUUID().toString();
            store.beginMatch(game2, runA, "pvp-v1", first.uid, second.uid);
            clock.advanceMillis(31_000);
            require(!store.renewRun(runA).isOwned(), "Expired run cannot renew");
            require(!store.claimRun(runA).isOwned(), "Expired run cannot reclaim its ID");
            require(store.claimRun(runB).isOwned(), "New run takes expired lease");
            require(store.getMatch(game2).getStatus() == MatchRecord.Status.VOID,
                    "Takeover voids unresolved game");
            require("SERVER_STOPPED".equals(store.getMatch(game2).getReason()), "Recovery reason");
            expectFailure(() -> store.finishMatch(game2, runA, first.uid, "WIN"), "RUN_NOT_OWNER");
            require(store.top100().get(0).getRating() == 1016, "Recovery does not change rating");

            // Two independent clients race to use the same user; at most one match may commit.
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            String race1 = UUID.randomUUID().toString(), race2 = UUID.randomUUID().toString();
            try {
                List<Future<Boolean>> results = new ArrayList<>();
                results.add(pool.submit(race(start, store, race1, runB, first.uid, second.uid)));
                results.add(pool.submit(race(start, store, race2, runB, first.uid, third.uid)));
                start.countDown();
                int successes = 0;
                for (Future<Boolean> future : results) if (future.get()) successes++;
                require(successes == 1, "Exactly one concurrent match began");
                if (store.getMatch(race1) != null) store.voidMatch(race1, runB, "TEST_VOID");
                if (store.getMatch(race2) != null) store.voidMatch(race2, runB, "TEST_VOID");
            } finally { pool.shutdownNow(); }
            require(!store.setAdmission(runB, false).isEnabled(), "Drain persisted");
            require(!store.claimRun(UUID.randomUUID().toString()).isOwned(), "Drained takeover refused");
            System.out.println("Firebase Auth and Firestore emulator integration PASS");
        }
    }

    private static Callable<Boolean> race(CountDownLatch start, FirestoreRankedMatchStore store,
            String game, String run, String first, String second) {
        return () -> {
            start.await();
            try { store.beginMatch(game, run, "pvp-v1", first, second); return true; }
            catch (IOException expected) {
                require(expected.getMessage().contains("ACCOUNT_IN_ACTIVE_MATCH"),
                        "Race loser should be fenced by player active slot: " + expected.getMessage());
                return false;
            }
        };
    }

    private interface Checked { void run() throws Exception; }
    private static void expectFailure(Checked work, String code) throws Exception {
        try { work.run(); throw new AssertionError("Expected " + code); }
        catch (IOException expected) {
            require(expected.getMessage().contains(code), "Expected " + code + ", got " + expected);
        }
    }

    private static Account signup(String host, String username) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("email", UsernameIdentity.emailFor(username));
        body.addProperty("password", "IntegrationOnlyPassword123!");
        body.addProperty("returnSecureToken", true);
        HttpURLConnection connection = (HttpURLConnection) new URL("http://" + host
                + "/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key").openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        try (InputStream input = connection.getInputStream()) {
            JsonObject response = JsonParser.parseString(read(input)).getAsJsonObject();
            return new Account(username, response.get("localId").getAsString(),
                    response.get("idToken").getAsString());
        } finally { connection.disconnect(); }
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] bytes = new byte[1024];
        int n;
        while ((n = input.read(bytes)) != -1) output.write(bytes, 0, n);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String shortId() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class Account {
        final String username, uid, token;
        Account(String username, String uid, String token) {
            this.username = username; this.uid = uid; this.token = token;
        }
    }

    private static final class MutableClock extends Clock {
        private volatile long time = Instant.now().toEpochMilli();
        void advanceMillis(long amount) { time += amount; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(time); }
        @Override public long millis() { return time; }
    }
}
