package kr.ac.jbnu.se.tetris.firebase.server;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.cloud.firestore.Firestore;
import java.io.IOException;
import java.time.Instant;
import kr.ac.jbnu.se.tetris.auth.AuthException;
import kr.ac.jbnu.se.tetris.auth.AuthIdentity;
import kr.ac.jbnu.se.tetris.auth.TokenVerifier;
import kr.ac.jbnu.se.tetris.auth.UsernameIdentity;

/** Verifies current Firebase ID tokens, including revocation and disabled accounts. */
public final class FirebaseTokenVerifier implements TokenVerifier {
    private final FirebaseAuth auth;
    private final FirebaseIdentityRegistry identities;

    public FirebaseTokenVerifier(FirebaseAuth auth, Firestore firestore) {
        if (auth == null || firestore == null) throw new IllegalArgumentException("auth and firestore required");
        this.auth = auth;
        this.identities = new FirebaseIdentityRegistry(firestore);
    }

    @Override public AuthIdentity verify(String accessToken) throws AuthException, IOException {
        if (accessToken == null || accessToken.length() > 16384
                || !accessToken.matches("[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]*"))
            throw new AuthException("INVALID_TOKEN");
        FirebaseToken token;
        try { token = auth.verifyIdToken(accessToken, true); }
        catch (FirebaseAuthException e) {
            String code = e.getAuthErrorCode() == null ? "" : e.getAuthErrorCode().name();
            if (code.contains("TOKEN") || code.contains("USER_DISABLED")
                    || code.contains("USER_NOT_FOUND") || code.contains("INVALID_CREDENTIAL"))
                throw new AuthException("INVALID_TOKEN");
            throw new IOException("Firebase Auth verification unavailable", e);
        }
        Object exp = token.getClaims().get("exp");
        if (token.getUid() == null || token.getUid().isEmpty()
                || !(exp instanceof Number)
                || ((Number) exp).longValue() <= Instant.now().getEpochSecond())
            throw new AuthException("INVALID_TOKEN");
        String name = identities.resolve(token.getUid(), token.getEmail(),
                Boolean.TRUE.equals(token.getClaims().get("campusQuest")));
        return new AuthIdentity(token.getUid(), name, ((Number) exp).longValue());
    }
}
