package kr.ac.jbnu.se.tetris.firebase.server;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Server-side Firebase access. Credentials come from the Cloud Run service identity. */
public final class FirebaseServerContext implements AutoCloseable {
    private final FirebaseApp app;
    private final Firestore firestore;
    private final FirebaseAuth auth;
    private final String projectId;
    private final AtomicBoolean closed = new AtomicBoolean();

    private FirebaseServerContext(FirebaseApp app, String projectId) {
        this.app = app;
        this.projectId = projectId;
        this.firestore = FirestoreClient.getFirestore(app);
        this.auth = FirebaseAuth.getInstance(app);
    }

    public static FirebaseServerContext fromEnvironment() throws IOException {
        String projectId = firstNonempty(System.getenv("FIREBASE_PROJECT_ID"),
                System.getenv("GOOGLE_CLOUD_PROJECT"));
        if (projectId == null) throw new IOException("FIREBASE_PROJECT_ID is required");
        String firestoreHost = System.getenv("FIRESTORE_EMULATOR_HOST");
        String authHost = System.getenv("FIREBASE_AUTH_EMULATOR_HOST");
        boolean emulator = firestoreHost != null || authHost != null;
        if (emulator && (!projectId.startsWith("demo-") || !loopback(firestoreHost)
                || !loopback(authHost)))
            throw new IOException("Firebase emulators require a demo project and both localhost hosts");
        GoogleCredentials credentials = emulator
                ? GoogleCredentials.create(new AccessToken("owner", new Date(Long.MAX_VALUE)))
                : GoogleCredentials.getApplicationDefault();
        FirebaseOptions options = FirebaseOptions.builder().setProjectId(projectId)
                .setCredentials(credentials).build();
        FirebaseApp app = FirebaseApp.initializeApp(options, "campus-quest-" + UUID.randomUUID());
        try { return new FirebaseServerContext(app, projectId); }
        catch (RuntimeException e) { app.delete(); throw e; }
    }

    private static String firstNonempty(String first, String second) {
        if (first != null && !first.trim().isEmpty()) return first.trim();
        if (second != null && !second.trim().isEmpty()) return second.trim();
        return null;
    }

    private static boolean loopback(String host) {
        return host != null && (host.matches("localhost:[0-9]{1,5}")
                || host.matches("127\\.0\\.0\\.1:[0-9]{1,5}")
                || host.matches("\\[::1\\]:[0-9]{1,5}"));
    }

    public Firestore getFirestore() { return firestore; }
    public FirebaseAuth getAuth() { return auth; }
    public String getProjectId() { return projectId; }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { firestore.close(); }
        catch (Exception e) { throw new IllegalStateException("Firebase close failed", e); }
        finally { app.delete(); }
    }
}
