package kr.ac.jbnu.se.tetris.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Instant;
import kr.ac.jbnu.se.tetris.supabase.SupabaseConfig;
import kr.ac.jbnu.se.tetris.supabase.SupabaseHttp;
import kr.ac.jbnu.se.tetris.supabase.SupabaseHttpException;

/** Desktop authentication client. Requests are synchronous and must run off the EDT. */
public final class SupabaseAuthService implements AuthService {
    private final SupabaseHttp http;
    private AuthSession session;

    public SupabaseAuthService(SupabaseConfig config) { http = new SupabaseHttp(config); }

    public synchronized SignUpResult signUp(String email, String password) throws AuthException, IOException {
        session = null;
        JsonObject request = credentials(email, password);
        return signUpRequest(request);
    }

    /** Email stays inside the Auth protocol; players provide only an ID and password. */
    public synchronized SignUpResult signUpUsername(String username, String password) throws AuthException, IOException {
        String normalized = UsernameIdentity.normalize(username);
        if (password == null || password.length() < 8)
            throw new IllegalArgumentException("password must have at least 8 characters");
        session = null;
        JsonObject request = credentials(UsernameIdentity.emailFor(normalized), password);
        JsonObject metadata = new JsonObject();
        metadata.addProperty("display_name", normalized);
        request.add("data", metadata);
        return signUpRequest(request);
    }

    private SignUpResult signUpRequest(JsonObject body) throws AuthException, IOException {
        JsonObject result = request("POST", "/auth/v1/signup", body, null);
        JsonObject user = result.has("user") && result.get("user").isJsonObject()
                ? result.getAsJsonObject("user") : result;
        String userId = AuthJson.string(user, "id");
        if (userId == null) throw new IOException("Invalid signup response");
        if (result.has("access_token") && !result.get("access_token").isJsonNull()) {
            session = parseSession(result);
            return new SignUpResult(userId, false, session);
        }
        return new SignUpResult(userId, true, null);
    }

    public synchronized AuthSession signIn(String email, String password) throws AuthException, IOException {
        session = null;
        session = parseSession(request("POST", "/auth/v1/token?grant_type=password",
                credentials(email, password), null));
        return session;
    }

    public synchronized AuthSession signInUsername(String username, String password) throws AuthException, IOException {
        return signIn(UsernameIdentity.emailFor(username), password);
    }

    public synchronized AuthSession refresh() throws AuthException, IOException {
        AuthSession current = requireSession();
        JsonObject body = new JsonObject();
        body.addProperty("refresh_token", current.getRefreshToken());
        try {
            AuthSession updated = parseSession(request("POST", "/auth/v1/token?grant_type=refresh_token", body, null));
            if (!current.getUserId().equals(updated.getUserId())) {
                session = null;
                throw new AuthException("IDENTITY_MISMATCH");
            }
            session = updated;
            return updated;
        } catch (AuthException e) {
            String code = e.getCode().toLowerCase(java.util.Locale.ROOT);
            if (e.getHttpStatus() == 401 || e.getHttpStatus() == 403
                    || "invalid_credentials".equals(code) || "invalid_grant".equals(code)
                    || "refresh_token_not_found".equals(code)
                    || "refresh_token_already_used".equals(code)
                    || "refresh_token_expired".equals(code) || "session_not_found".equals(code))
                session = null;
            throw e;
        }
    }

    public synchronized void signOut() throws AuthException, IOException {
        AuthSession current = session;
        session = null;
        if (current == null) return;
        request("POST", "/auth/v1/logout?scope=local", new JsonObject(), current.getAccessToken());
    }

    public void recoverPassword(String email) throws AuthException, IOException {
        JsonObject body = new JsonObject();
        body.addProperty("email", required(email, "email"));
        request("POST", "/auth/v1/recover", body, null);
    }

    /** Requires a recovery email template containing {{ .Token }} as a one-time code. */
    public synchronized AuthSession completePasswordRecovery(String email, String otp, String newPassword)
            throws AuthException, IOException {
        session = null;
        JsonObject body = new JsonObject();
        body.addProperty("type", "recovery");
        body.addProperty("email", required(email, "email"));
        body.addProperty("token", required(otp, "otp"));
        AuthSession recovered = parseSession(request("POST", "/auth/v1/verify", body, null));
        JsonObject change = new JsonObject();
        change.addProperty("password", requiredPassword(newPassword));
        request("PUT", "/auth/v1/user", change, recovered.getAccessToken());
        session = recovered;
        return recovered;
    }

    public synchronized AuthSession changePassword(String newPassword) throws AuthException, IOException {
        AuthSession current = requireSession();
        JsonObject body = new JsonObject();
        body.addProperty("password", requiredPassword(newPassword));
        request("PUT", "/auth/v1/user", body, current.getAccessToken());
        return current;
    }

    public synchronized AuthSession getSession() { return session; }

    /** A cancelled UI request may finish after another login; discard only its own session. */
    public synchronized void discardSessionIf(AuthSession discarded) {
        if (session == discarded) session = null;
    }

    private AuthSession requireSession() throws AuthException {
        if (session == null) throw new AuthException("NOT_SIGNED_IN");
        return session;
    }

    private JsonObject request(String method, String path, JsonElement body, String token)
            throws AuthException, IOException {
        try { return http.request(method, path, body, token, false).getAsJsonObject(); }
        catch (SupabaseHttpException e) {
            if (e.getStatus() == 400 || e.getStatus() == 401 || e.getStatus() == 403
                    || e.getStatus() == 422 || e.getStatus() == 429)
                throw new AuthException(e.getCode(), e.getStatus());
            throw e;
        } catch (IllegalStateException e) { throw new IOException("Invalid Auth response", e); }
    }

    private static JsonObject credentials(String email, String password) {
        JsonObject result = new JsonObject();
        result.addProperty("email", required(email, "email"));
        result.addProperty("password", requiredPassword(password));
        return result;
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

    private static AuthSession parseSession(JsonObject body) throws IOException {
        String token = AuthJson.string(body, "access_token");
        String refresh = AuthJson.string(body, "refresh_token");
        String userId = body.has("user") && body.get("user").isJsonObject()
                ? AuthJson.string(body.getAsJsonObject("user"), "id") : null;
        long expiresAt = AuthJson.longValue(body, "expires_at");
        if (expiresAt <= 0) {
            long duration = AuthJson.longValue(body, "expires_in");
            if (duration > 0) expiresAt = Instant.now().getEpochSecond() + duration;
        }
        if (expiresAt <= Instant.now().getEpochSecond()) throw new IOException("Expired Auth session response");
        try { return new AuthSession(token, refresh, userId, expiresAt); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid Auth session response", e); }
    }
}
