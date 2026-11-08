package kr.ac.jbnu.se.tetris.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import kr.ac.jbnu.se.tetris.supabase.SupabaseConfig;
import kr.ac.jbnu.se.tetris.supabase.SupabaseHttp;
import kr.ac.jbnu.se.tetris.supabase.SupabaseHttpException;

/** Supabase Auth /user is the signature authority, including projects using HS256. */
public final class SupabaseTokenVerifier implements TokenVerifier {
    private final SupabaseConfig config;
    private final SupabaseHttp http;

    public SupabaseTokenVerifier(SupabaseConfig config) {
        this.config = config;
        this.http = new SupabaseHttp(config);
    }

    @Override public AuthIdentity verify(String accessToken) throws AuthException, IOException {
        if (accessToken == null || accessToken.length() > 16384
                || !accessToken.matches("[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+"))
            throw new AuthException("INVALID_TOKEN");
        JsonObject claims;
        try {
            String payload = accessToken.split("\\.", -1)[1];
            if (payload.length() > 12000) throw new IllegalArgumentException();
            claims = JsonParser.parseString(new String(Base64.getUrlDecoder().decode(payload),
                    StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) { throw new AuthException("INVALID_TOKEN"); }
        String issuer = AuthJson.string(claims, "iss");
        String userId = AuthJson.string(claims, "sub");
        long expiry = AuthJson.longValue(claims, "exp");
        if (!config.getBaseUri().resolve("/auth/v1").toString().equals(issuer)
                || userId == null || !userId.matches("[0-9a-fA-F-]{36}")
                || expiry <= Instant.now().getEpochSecond()
                || !hasAuthenticatedAudience(claims.get("aud"))
                || !"authenticated".equals(AuthJson.string(claims, "role")))
            throw new AuthException("INVALID_TOKEN");
        JsonObject latest;
        try {
            JsonElement response = http.request("GET", "/auth/v1/user", null, accessToken, false);
            latest = response.getAsJsonObject();
        } catch (SupabaseHttpException e) {
            if (e.getStatus() == 400 || e.getStatus() == 401 || e.getStatus() == 403)
                throw new AuthException("INVALID_TOKEN");
            throw e;
        } catch (IllegalStateException e) { throw new IOException("Invalid Auth user response", e); }
        if (!userId.equals(AuthJson.string(latest, "id"))) throw new AuthException("IDENTITY_MISMATCH");
        String displayName = UsernameIdentity.fromEmail(AuthJson.string(latest, "email"));
        if (displayName == null && latest.has("user_metadata") && latest.get("user_metadata").isJsonObject())
            displayName = AuthJson.string(latest.getAsJsonObject("user_metadata"), "display_name");
        if (displayName == null || displayName.length() > 32) displayName = "Player";
        return new AuthIdentity(userId, displayName, expiry);
    }

    private static boolean hasAuthenticatedAudience(JsonElement audience) {
        if (audience == null || audience.isJsonNull()) return false;
        if (audience.isJsonPrimitive()) return "authenticated".equals(audience.getAsString());
        if (audience.isJsonArray()) {
            for (JsonElement item : audience.getAsJsonArray())
                if (item.isJsonPrimitive() && "authenticated".equals(item.getAsString())) return true;
        }
        return false;
    }

}
