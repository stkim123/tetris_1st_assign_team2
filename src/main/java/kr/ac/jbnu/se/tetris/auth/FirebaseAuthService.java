package kr.ac.jbnu.se.tetris.auth;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import kr.ac.jbnu.se.tetris.firebase.FirebaseConfig;
import kr.ac.jbnu.se.tetris.firebase.FirebaseHttp;

/** Firebase Identity Toolkit client for the Java 8 desktop. No Admin credentials belong here. */
public final class FirebaseAuthService implements AuthService {
    private final FirebaseHttp http;
    private AuthSession session;

    public FirebaseAuthService(FirebaseConfig config) { http = new FirebaseHttp(config); }

    @Override public synchronized SignUpResult signUp(String email, String password) throws AuthException, IOException {
        session = null;
        JsonObject body = credentials(email, password);
        session = parseSession(http.auth("accounts:signUp", body), false);
        return new SignUpResult(session.getUserId(), false, session);
    }

    @Override public synchronized SignUpResult signUpUsername(String username, String password)
            throws AuthException, IOException {
        String normalized = UsernameIdentity.normalize(username);
        if (password == null || password.length() < 8)
            throw new IllegalArgumentException("password must have at least 8 characters");
        // The verified email is also the canonical profile name source on the game server.
        return signUp(UsernameIdentity.emailFor(normalized), password);
    }

    @Override public synchronized AuthSession signIn(String email, String password) throws AuthException, IOException {
        session = null;
        session = parseSession(http.auth("accounts:signInWithPassword", credentials(email, password)), false);
        return session;
    }

    @Override public synchronized AuthSession signInUsername(String username, String password)
            throws AuthException, IOException {
        return signIn(UsernameIdentity.emailFor(username), password);
    }

    @Override public synchronized AuthSession refresh() throws AuthException, IOException {
        AuthSession current = requireSession();
        try {
            AuthSession updated = parseSession(http.refresh(current.getRefreshToken()), true);
            if (!current.getUserId().equals(updated.getUserId())) {
                session = null;
                throw new AuthException("IDENTITY_MISMATCH");
            }
            session = updated;
            return updated;
        } catch (AuthException error) {
            if (invalidSession(error)) session = null;
            throw error;
        }
    }

    @Override public synchronized void signOut() { session = null; }

    /** Username accounts use a non-deliverable address; email recovery is only for real email accounts. */
    @Override public void recoverPassword(String email) throws AuthException, IOException {
        String address = required(email, "email");
        if (UsernameIdentity.fromEmail(address) != null)
            throw new AuthException("RECOVERY_UNAVAILABLE_FOR_USERNAME");
        JsonObject body = new JsonObject();
        body.addProperty("requestType", "PASSWORD_RESET");
        body.addProperty("email", address);
        http.auth("accounts:sendOobCode", body);
    }

    @Override public synchronized AuthSession completePasswordRecovery(String email, String code, String newPassword)
            throws AuthException, IOException {
        session = null;
        String address = required(email, "email");
        if (UsernameIdentity.fromEmail(address) != null)
            throw new AuthException("RECOVERY_UNAVAILABLE_FOR_USERNAME");
        JsonObject verify = new JsonObject();
        verify.addProperty("oobCode", required(code, "code"));
        JsonObject verified = http.auth("accounts:resetPassword", verify);
        if (!address.equalsIgnoreCase(AuthJson.string(verified, "email")))
            throw new AuthException("RECOVERY_EMAIL_MISMATCH");
        verify.addProperty("newPassword", requiredPassword(newPassword));
        http.auth("accounts:resetPassword", verify);
        return signIn(address, newPassword);
    }

    @Override public synchronized AuthSession changePassword(String newPassword) throws AuthException, IOException {
        AuthSession current = requireSession();
        JsonObject body = new JsonObject();
        body.addProperty("idToken", current.getAccessToken());
        body.addProperty("password", requiredPassword(newPassword));
        body.addProperty("returnSecureToken", true);
        try {
            AuthSession updated = parseSession(http.auth("accounts:update", body), false);
            if (!current.getUserId().equals(updated.getUserId())) {
                session = null;
                throw new AuthException("IDENTITY_MISMATCH");
            }
            session = updated;
            return updated;
        } catch (AuthException error) {
            if (invalidSession(error)) session = null;
            throw error;
        }
    }

    @Override public synchronized AuthSession getSession() { return session; }

    @Override public synchronized void discardSessionIf(AuthSession discarded) {
        if (session == discarded) session = null;
    }

    private AuthSession requireSession() throws AuthException {
        if (session == null) throw new AuthException("NOT_SIGNED_IN");
        return session;
    }

    private static JsonObject credentials(String email, String password) {
        JsonObject body = new JsonObject();
        body.addProperty("email", required(email, "email"));
        body.addProperty("password", requiredPassword(password));
        body.addProperty("returnSecureToken", true);
        return body;
    }

    private static String required(String value, String name) {
        if (value == null || value.trim().isEmpty() || value.length() > 4096)
            throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String requiredPassword(String value) {
        if (value == null || value.isEmpty() || value.length() > 4096)
            throw new IllegalArgumentException("password is required");
        return value;
    }

    private static boolean invalidSession(AuthException error) {
        String code = error.getCode().toUpperCase(Locale.ROOT);
        return "IDENTITY_MISMATCH".equals(code) || "INVALID_ID_TOKEN".equals(code)
                || "TOKEN_EXPIRED".equals(code) || "INVALID_REFRESH_TOKEN".equals(code)
                || "USER_DISABLED".equals(code) || "USER_NOT_FOUND".equals(code)
                || "CREDENTIAL_TOO_OLD_LOGIN_AGAIN".equals(code);
    }

    private static AuthSession parseSession(JsonObject body, boolean refreshed) throws IOException {
        String token = AuthJson.string(body, refreshed ? "id_token" : "idToken");
        String refresh = AuthJson.string(body, refreshed ? "refresh_token" : "refreshToken");
        String userId = AuthJson.string(body, refreshed ? "user_id" : "localId");
        long duration;
        try { duration = AuthJson.longValue(body, refreshed ? "expires_in" : "expiresIn"); }
        catch (RuntimeException invalid) { throw new IOException("Invalid Firebase Auth session response", invalid); }
        if (duration <= 0 || duration > 86400) throw new IOException("Invalid Firebase Auth expiry");
        try { return new AuthSession(token, refresh, userId, Instant.now().getEpochSecond() + duration); }
        catch (IllegalArgumentException invalid) { throw new IOException("Invalid Firebase Auth session response", invalid); }
    }
}
