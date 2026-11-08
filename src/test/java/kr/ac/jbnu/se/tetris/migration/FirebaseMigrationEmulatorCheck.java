package kr.ac.jbnu.se.tetris.migration;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.firebase.auth.ExportedUserRecord;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import kr.ac.jbnu.se.tetris.firebase.server.FirebaseServerContext;
import kr.ac.jbnu.se.tetris.firebase.server.FirestoreRankedMatchStore;

/** Explicit integration check; excluded from the ordinary *Test.java headless suite. */
public final class FirebaseMigrationEmulatorCheck {
    private FirebaseMigrationEmulatorCheck() { }

    public static void main(String[] args) throws Exception {
        if (!"demo-campus-migration".equals(System.getenv("FIREBASE_PROJECT_ID")))
            throw new IllegalStateException("Use only demo-campus-migration");
        if (!loopback(System.getenv("FIREBASE_AUTH_EMULATOR_HOST"))
                || !loopback(System.getenv("FIRESTORE_EMULATOR_HOST")))
            throw new IllegalStateException("Both Firebase emulators must use localhost");
        Path file = Files.createTempFile("firebase-migration-emulator-", ".json");
        try {
            JsonObject export = FirebaseMigrationPlanTest.fixture();
            Files.write(file, export.toString().getBytes(StandardCharsets.UTF_8));
            FirebaseMigrationPlan plan = FirebaseMigrationPlan.load(file);
            String first = plan.getUsers().get(0).getUid();
            String second = plan.getUsers().get(1).getUid();
            String match = plan.getMatches().keySet().iterator().next();
            try (FirebaseServerContext context = FirebaseServerContext.fromEnvironment()) {
                FirebaseMigration.apply(plan, context.getAuth(), context.getFirestore());
                com.google.cloud.firestore.DocumentReference marker = context.getFirestore()
                        .collection("migration_state").document("supabase_v1");
                Map<String, Object> interrupted = new HashMap<>(marker.get().get(30, TimeUnit.SECONDS).getData());
                interrupted.put("status", "IN_PROGRESS");
                marker.set(interrupted).get(30, TimeUnit.SECONDS);
                try { new FirestoreRankedMatchStore(context.getFirestore(), context.getAuth())
                        .setAdmission("66666666-6666-4666-8666-666666666666", true);
                    throw new AssertionError("ranked opened during incomplete migration"); }
                catch (IOException expected) {
                    check("MIGRATION_IN_PROGRESS".equals(expected.getMessage()), "server migration fence");
                }
                FirebaseMigration.apply(plan, context.getAuth(), context.getFirestore());
                check("COMPLETE".equals(marker.get().get(30, TimeUnit.SECONDS).getString("status")),
                        "same export resumes incomplete marker");
                FirebaseMigration.apply(plan, context.getAuth(), context.getFirestore());
                check(context.getAuth().getUser(first).getEmail().equals(
                        "player_01@players.campus-quest.invalid"), "UID and synthetic email preserved");
                check(context.getAuth().getUser(second).isDisabled(), "Supabase ban preserved");
                check(Boolean.TRUE.equals(context.getAuth().getUser(first).getCustomClaims().get("campusQuest")),
                        "trusted legacy account claim preserved");
                DocumentSnapshot player = context.getFirestore().collection("ranked_players")
                        .document(FirebaseMigrationPlan.playerDocumentId(first)).get().get(30, TimeUnit.SECONDS);
                check(player.getLong("rating") == 1016 && player.getLong("games") == 1,
                        "rating and games preserved");
                DocumentSnapshot game = context.getFirestore().collection("ranked_matches")
                        .document(match).get().get(30, TimeUnit.SECONDS);
                check("FINALIZED".equals(game.getString("status"))
                        && game.getLong("firstRatingAfter") == 1016, "settled match history preserved");
                DocumentSnapshot identity = context.getFirestore().collection("ranked_identities")
                        .document(FirebaseMigrationPlan.playerDocumentId(first)).get().get(30, TimeUnit.SECONDS);
                check(first.equals(identity.getString("userId"))
                        && "player_01".equals(identity.getString("displayName")), "identity binding preserved");
                DocumentSnapshot username = context.getFirestore().collection("ranked_usernames")
                        .document("player_01").get().get(30, TimeUnit.SECONDS);
                check(first.equals(username.getString("userId")), "username reservation preserved");
                DocumentSnapshot gate = context.getFirestore().collection("ranked_control")
                        .document("singleton").get().get(30, TimeUnit.SECONDS);
                check(Boolean.FALSE.equals(gate.getBoolean("enabled")), "admission stays closed");

                boolean hashPreserved = false;
                for (ExportedUserRecord account : context.getAuth().listUsers(null, 1000).iterateAll()) {
                    if (first.equals(account.getUid())) {
                        String imported = account.getPasswordHash();
                        hashPreserved = imported != null && java.util.Arrays.equals(
                                java.util.Base64.getDecoder().decode(imported),
                                plan.getUsers().get(0).getBcryptHash().getBytes(StandardCharsets.UTF_8));
                    }
                }
                check(hashPreserved, "bcrypt bytes preserved in Auth export");
                // Firebase CLI Auth emulator stores imported hashes but compares only its fakeHash format.
                // Real bcrypt password login therefore needs a separate live Auth canary after project setup.
                System.out.println("SKIP imported bcrypt login: Auth emulator does not verify imported bcrypt");

                FirebaseMigration.apply(plan, context.getAuth(), context.getFirestore());
                export.addProperty("exportedAt", "2025-01-01T00:00:01Z");
                Files.write(file, export.toString().getBytes(StandardCharsets.UTF_8));
                FirebaseMigrationPlan changed = FirebaseMigrationPlan.load(file);
                try { FirebaseMigration.apply(changed, context.getAuth(), context.getFirestore());
                    throw new AssertionError("different export replaced migration"); }
                catch (IOException expected) { check(expected.getMessage().contains("digest"),
                        "different digest rejected before writes"); }
            }
            System.out.println("PASS FirebaseMigrationEmulatorCheck");
        } finally { Files.deleteIfExists(file); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static boolean loopback(String host) {
        return host != null && (host.matches("127\\.0\\.0\\.1:[0-9]{1,5}")
                || host.matches("localhost:[0-9]{1,5}")
                || host.matches("\\[::1\\]:[0-9]{1,5}"));
    }
}
