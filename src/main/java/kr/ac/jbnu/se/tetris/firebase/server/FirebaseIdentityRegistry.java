package kr.ac.jbnu.se.tetris.firebase.server;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import kr.ac.jbnu.se.tetris.auth.AuthException;
import kr.ac.jbnu.se.tetris.auth.UsernameIdentity;

/** Binds verified Auth identities to permanent game IDs; public Auth profile edits grant no new name. */
final class FirebaseIdentityRegistry {
    private final Firestore firestore;
    FirebaseIdentityRegistry(Firestore firestore) { this.firestore = firestore; }

    String resolve(String uid, String email, boolean migrated) throws IOException, AuthException {
        final String username = UsernameIdentity.fromEmail(email);
        if (email == null || (username == null && !migrated)) throw new AuthException("GAME_ID_REQUIRED");
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(uid.getBytes(StandardCharsets.UTF_8));
        DocumentReference identity = firestore.collection("ranked_identities").document(key);
        DocumentReference name = username == null ? null : firestore.collection("ranked_usernames").document(username);
        ApiFuture<String> operation = firestore.runTransaction(tx -> {
            DocumentSnapshot existing = tx.get(identity).get();
            DocumentSnapshot reserved = name == null ? null : tx.get(name).get();
            if (existing.exists()) {
                if (!uid.equals(existing.getString("userId")) || !email.equalsIgnoreCase(existing.getString("email")))
                    throw new IdentityRejected("GAME_ID_CHANGED");
            } else if (username == null) {
                // Only the migration tool may seed a real-email legacy account and its trusted name.
                throw new IdentityRejected("LEGACY_IDENTITY_REQUIRED");
            }
            if (reserved != null && reserved.exists() && !uid.equals(reserved.getString("userId")))
                throw new IdentityRejected("GAME_ID_RESERVED");
            String displayName = existing.exists() ? existing.getString("displayName") : username;
            if (displayName == null || displayName.isEmpty() || displayName.length() > 32
                    || (username != null && !username.equals(displayName)))
                throw new IdentityRejected("INVALID_GAME_IDENTITY");
            if (!existing.exists()) {
                Map<String, Object> fields = new HashMap<>();
                fields.put("userId", uid); fields.put("email", email); fields.put("displayName", displayName);
                tx.create(identity, fields);
            }
            if (reserved != null && !reserved.exists()) {
                Map<String, Object> fields = new HashMap<>(); fields.put("userId", uid);
                tx.create(name, fields);
            }
            return displayName;
        });
        try { return operation.get(15, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new IOException("Identity lookup interrupted", interrupted);
        } catch (TimeoutException unavailable) {
            operation.cancel(false); throw new IOException("Identity lookup timed out", unavailable);
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            while (cause != null) {
                if (cause instanceof IdentityRejected) throw new AuthException(cause.getMessage());
                cause = cause.getCause();
            }
            throw new IOException("Identity lookup unavailable", failed.getCause());
        }
    }

    private static final class IdentityRejected extends RuntimeException {
        private static final long serialVersionUID = 1L;
        IdentityRejected(String code) { super(code); }
    }
}
