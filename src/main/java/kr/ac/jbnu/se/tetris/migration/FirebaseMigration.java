package kr.ac.jbnu.se.tetris.migration;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.ExportedUserRecord;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.ImportUserRecord;
import com.google.firebase.auth.UserImportOptions;
import com.google.firebase.auth.UserImportResult;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.hash.Bcrypt;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import kr.ac.jbnu.se.tetris.firebase.server.FirebaseServerContext;

/** One-time, resumable migration. Dry-run is the default; no source database is changed. */
public final class FirebaseMigration {
    private FirebaseMigration() { }

    public static void main(String[] args) {
        try {
            Arguments options = Arguments.parse(args);
            FirebaseMigrationPlan plan = FirebaseMigrationPlan.load(options.input);
            System.out.println("Export SHA-256: " + plan.getSha256());
            System.out.println("Validated users=" + plan.getUsers().size() + " players="
                    + plan.getPlayers().size() + " matches=" + plan.getMatches().size()
                    + " historical runs=" + plan.getRuns().size());
            if (!options.apply) {
                System.out.println("Dry run complete; Firebase was not contacted.");
                return;
            }
            if (!plan.getSha256().equalsIgnoreCase(options.confirmSha256))
                throw new IOException("Confirmed export SHA-256 does not match the input file");
            String project = System.getenv("FIREBASE_PROJECT_ID");
            if (project == null || project.trim().isEmpty())
                throw new IOException("FIREBASE_PROJECT_ID must explicitly select the target project");
            String ambientProject = System.getenv("GOOGLE_CLOUD_PROJECT");
            if (ambientProject != null && !ambientProject.trim().isEmpty()
                    && !ambientProject.trim().equals(project.trim()))
                throw new IOException("FIREBASE_PROJECT_ID and GOOGLE_CLOUD_PROJECT disagree");
            try (FirebaseServerContext context = FirebaseServerContext.fromEnvironment()) {
                if (!project.trim().equals(context.getProjectId()))
                    throw new IOException("Firebase context selected a different project");
                apply(plan, context.getAuth(), context.getFirestore());
            }
            System.out.println("Migration complete. Ranked admission remains closed.");
            System.out.println("Verify a live imported bcrypt login canary before client cutover.");
        } catch (Exception failed) {
            // Export contents include password hashes. Never print raw SDK requests or nested causes.
            System.err.println("Migration failed: " + safe(failed));
            System.exit(1);
        }
    }

    static void apply(FirebaseMigrationPlan plan, FirebaseAuth auth, Firestore firestore) throws IOException {
        DocumentReference marker = firestore.collection("migration_state").document("supabase_v1");
        DocumentReference control = firestore.collection("ranked_control").document("singleton");
        DocumentSnapshot prior = await(marker.get());
        boolean resume = prior.exists();
        if (resume) {
            if (!plan.getSha256().equals(prior.getString("sha256")))
                throw new IOException("Target has a different Supabase migration digest");
            String status = prior.getString("status");
            if (!("IN_PROGRESS".equals(status) || "COMPLETE".equals(status)))
                throw new IOException("Migration marker has an unknown state");
        } else {
            // A new target must be empty. Imports never overwrite live player or Auth data.
            for (String name : new String[] {"ranked_control", "ranked_runs", "ranked_matches",
                    "ranked_players", "ranked_identities", "ranked_usernames", "migration_state"}) {
                if (!await(firestore.collection(name).limit(1).get()).isEmpty())
                    throw new IOException("Target ranked data is not empty");
            }
            try {
                if (auth.listUsers(null, 1).getValues().iterator().hasNext())
                    throw new IOException("Target Firebase Auth is not empty");
            } catch (FirebaseAuthException error) { throw new IOException("Unable to inspect target Auth", error); }
            Map<String, Object> initial = markerFields(plan, "IN_PROGRESS");
            create(marker, initial);
        }
        Map<String, Object> closed = fields("ownerRunId", null, "leaseUntilMillis", null,
                "enabled", false, "migrationDigest", plan.getSha256());
        if (resume) validateExisting(plan, auth, firestore, closed);
        createOrCheck(control, closed);
        importUsers(plan, auth);
        for (Map.Entry<String, Map<String, Object>> row : plan.getPlayers().entrySet())
            createOrCheck(firestore.collection("ranked_players").document(
                    FirebaseMigrationPlan.playerDocumentId(row.getKey())), row.getValue());
        for (Map.Entry<String, Map<String, Object>> row : plan.getIdentities().entrySet())
            createOrCheck(firestore.collection("ranked_identities").document(row.getKey()), row.getValue());
        for (Map.Entry<String, Map<String, Object>> row : plan.getUsernames().entrySet())
            createOrCheck(firestore.collection("ranked_usernames").document(row.getKey()), row.getValue());
        for (Map.Entry<String, Map<String, Object>> row : plan.getRuns().entrySet())
            createOrCheck(firestore.collection("ranked_runs").document(row.getKey()), row.getValue());
        for (Map.Entry<String, Map<String, Object>> row : plan.getMatches().entrySet())
            createOrCheck(firestore.collection("ranked_matches").document(row.getKey()), row.getValue());
        // Verify the gate again before completion. Another process must not have opened ranked admission.
        if (!same(closed, await(control.get()).getData()))
            throw new IOException("Ranked admission changed during migration");
        await(marker.set(markerFields(plan, "COMPLETE")));
    }

    private static void validateExisting(FirebaseMigrationPlan plan, FirebaseAuth auth, Firestore firestore,
                                         Map<String, Object> closed) throws IOException {
        Map<String, FirebaseMigrationPlan.User> expectedUsers = new HashMap<>();
        for (FirebaseMigrationPlan.User user : plan.getUsers()) expectedUsers.put(user.getUid(), user);
        try {
            for (ExportedUserRecord existing : auth.listUsers(null, 1000).iterateAll()) {
                FirebaseMigrationPlan.User user = expectedUsers.get(existing.getUid());
                if (user == null || !sameUser(plan, user, existing) || !samePassword(user, existing))
                    throw new IOException("Unrelated Auth user exists in migration target");
            }
        } catch (FirebaseAuthException error) { throw new IOException("Unable to inspect target Auth", error); }
        Map<String, Map<String, Object>> players = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : plan.getPlayers().entrySet())
            players.put(FirebaseMigrationPlan.playerDocumentId(entry.getKey()), entry.getValue());
        validateCollection(firestore.collection("ranked_players"), players);
        validateCollection(firestore.collection("ranked_identities"), plan.getIdentities());
        validateCollection(firestore.collection("ranked_usernames"), plan.getUsernames());
        validateCollection(firestore.collection("ranked_matches"), plan.getMatches());
        validateCollection(firestore.collection("ranked_runs"), plan.getRuns());
        Map<String, Map<String, Object>> controlExpected = new HashMap<>();
        controlExpected.put("singleton", closed);
        validateCollection(firestore.collection("ranked_control"), controlExpected);
        for (QueryDocumentSnapshot document : await(firestore.collection("migration_state").get()).getDocuments())
            if (!"supabase_v1".equals(document.getId()))
                throw new IOException("Unrelated migration marker exists in target");
    }

    private static void validateCollection(CollectionReference collection,
                                           Map<String, Map<String, Object>> expected) throws IOException {
        for (QueryDocumentSnapshot document : await(collection.get()).getDocuments()) {
            Map<String, Object> row = expected.get(document.getId());
            if (row == null || !same(row, document.getData()))
                throw new IOException("Unrelated or changed ranked document exists in migration target");
        }
    }

    private static void importUsers(FirebaseMigrationPlan plan, FirebaseAuth auth) throws IOException {
        List<ImportUserRecord> pending = new ArrayList<>();
        for (FirebaseMigrationPlan.User user : plan.getUsers()) {
            UserRecord existing;
            try { existing = auth.getUser(user.getUid()); }
            catch (FirebaseAuthException error) {
                if (error.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) existing = null;
                else throw new IOException("Unable to inspect target user", error);
            }
            if (existing != null) {
                if (!sameUser(plan, user, existing))
                    throw new IOException("Target Auth user conflicts with this migration");
                continue;
            }
            ImportUserRecord.Builder record = ImportUserRecord.builder().setUid(user.getUid())
                    .setEmail(user.getEmail()).setEmailVerified(user.isEmailVerified())
                    .setDisabled(user.isDisabled()).putCustomClaim("campusQuest", true)
                    .putCustomClaim("migrationDigest", plan.getSha256());
            String displayName = (String) plan.getPlayers().get(user.getUid()).get("displayName");
            record.setDisplayName(displayName);
            if (user.getBcryptHash() != null)
                record.setPasswordHash(user.getBcryptHash().getBytes(StandardCharsets.UTF_8));
            pending.add(record.build());
            if (pending.size() == 1000) { importBatch(auth, pending); pending.clear(); }
        }
        if (!pending.isEmpty()) importBatch(auth, pending);
    }

    private static boolean sameUser(FirebaseMigrationPlan plan, FirebaseMigrationPlan.User user,
                                    UserRecord existing) {
        Map<String, Object> claims = existing.getCustomClaims();
        return user.getEmail().equalsIgnoreCase(existing.getEmail())
                && Objects.equals(plan.getPlayers().get(user.getUid()).get("displayName"), existing.getDisplayName())
                && user.isEmailVerified() == existing.isEmailVerified()
                && user.isDisabled() == existing.isDisabled()
                && claims != null && Boolean.TRUE.equals(claims.get("campusQuest"))
                && plan.getSha256().equals(claims.get("migrationDigest"));
    }

    private static boolean samePassword(FirebaseMigrationPlan.User user, ExportedUserRecord existing) {
        String expected = user.getBcryptHash();
        String saved = existing.getPasswordHash();
        if (expected == null) return saved == null || saved.isEmpty();
        if (saved == null) return false;
        try { return Arrays.equals(expected.getBytes(StandardCharsets.UTF_8), Base64.getDecoder().decode(saved)); }
        catch (IllegalArgumentException invalid) { return false; }
    }

    private static void importBatch(FirebaseAuth auth, List<ImportUserRecord> users) throws IOException {
        try {
            UserImportOptions options = UserImportOptions.builder().setHash(Bcrypt.getInstance()).build();
            UserImportResult result = auth.importUsers(new ArrayList<>(users), options);
            if (result.getFailureCount() != 0 || result.getSuccessCount() != users.size())
                throw new IOException("Firebase Auth imported only part of a batch; rerun the same export");
        } catch (FirebaseAuthException error) { throw new IOException("Firebase Auth batch import failed", error); }
    }

    private static Map<String, Object> markerFields(FirebaseMigrationPlan plan, String status) {
        return fields("sha256", plan.getSha256(), "status", status,
                "exportedAtMillis", plan.getExportedAtMillis(), "userCount", plan.getUsers().size(),
                "playerCount", plan.getPlayers().size(), "matchCount", plan.getMatches().size(),
                "runCount", plan.getRuns().size());
    }

    private static Map<String, Object> fields(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }

    private static void createOrCheck(DocumentReference document, Map<String, Object> expected) throws IOException {
        DocumentSnapshot existing = await(document.get());
        if (existing.exists()) {
            if (!same(expected, existing.getData()))
                throw new IOException("Target document conflicts with migration");
        } else create(document, expected);
    }

    private static void create(DocumentReference document, Map<String, Object> contents) throws IOException {
        try { await(document.create(contents)); }
        catch (IOException failed) {
            // A write may have committed before a timeout; exact contents allow safe same-file resume.
            DocumentSnapshot existing = await(document.get());
            if (!existing.exists() || !same(contents, existing.getData())) throw failed;
        }
    }

    private static boolean same(Map<String, Object> expected, Map<String, Object> actual) {
        if (actual == null || expected.size() != actual.size()) return false;
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            if (!actual.containsKey(entry.getKey())) return false;
            Object left = entry.getValue(), right = actual.get(entry.getKey());
            if (left instanceof Number && right instanceof Number) {
                if (((Number) left).longValue() != ((Number) right).longValue()) return false;
            } else if (!Objects.equals(left, right)) return false;
        }
        return true;
    }

    private static <T> T await(ApiFuture<T> future) throws IOException {
        try { return future.get(30, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new IOException("Firebase request interrupted", interrupted);
        } catch (TimeoutException timeout) { throw new IOException("Firebase request timed out", timeout); }
        catch (ExecutionException failed) { throw new IOException("Firebase request failed", failed.getCause()); }
    }

    private static String safe(Exception error) {
        if (error instanceof IOException) return error.getMessage();
        return error.getClass().getSimpleName();
    }

    private static final class Arguments {
        final Path input;
        final boolean apply;
        final String confirmSha256;
        private Arguments(Path input, boolean apply, String confirmSha256) {
            this.input = input; this.apply = apply; this.confirmSha256 = confirmSha256;
        }
        static Arguments parse(String[] args) throws IOException {
            Path input = null;
            boolean apply = false;
            String digest = null;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--input":
                        if (++i >= args.length) throw new IOException("--input requires a path");
                        input = Paths.get(args[i]); break;
                    case "--apply": apply = true; break;
                    case "--confirm-sha256":
                        if (++i >= args.length) throw new IOException("--confirm-sha256 requires a digest");
                        digest = args[i]; break;
                    default: throw new IOException("Unknown migration argument");
                }
            }
            if (input == null) throw new IOException("--input is required");
            if (apply && (digest == null || !digest.matches("[0-9a-fA-F]{64}")))
                throw new IOException("--apply requires --confirm-sha256");
            if (!apply && digest != null) throw new IOException("Digest confirmation requires --apply");
            return new Arguments(input, apply, digest);
        }
    }
}
