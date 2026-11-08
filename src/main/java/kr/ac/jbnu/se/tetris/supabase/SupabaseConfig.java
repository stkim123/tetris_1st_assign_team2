package kr.ac.jbnu.se.tetris.supabase;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Public project configuration is usable by the desktop; the secret is server-only. */
public final class SupabaseConfig {
    private final URI baseUri;
    private final String publishableKey;
    private final String serverSecretKey;

    public SupabaseConfig(String baseUrl, String publishableKey) {
        this(baseUrl, publishableKey, null);
    }

    public SupabaseConfig(String baseUrl, String publishableKey, String serverSecretKey) {
        try {
            URI uri = new URI(required(baseUrl, "SUPABASE_URL")).normalize();
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || (uri.getRawPath() != null
                    && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath()))) {
                throw new IllegalArgumentException("Invalid Supabase URL");
            }
            boolean local = "localhost".equalsIgnoreCase(uri.getHost())
                    || "127.0.0.1".equals(uri.getHost()) || "::1".equals(uri.getHost());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    && !(local && "http".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("Supabase URL must use HTTPS");
            }
            this.baseUri = uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid Supabase URL", e);
        }
        this.publishableKey = required(publishableKey, "SUPABASE_PUBLISHABLE_KEY");
        if (this.publishableKey.startsWith("sb_secret_") || isLegacyServiceRoleJwt(this.publishableKey))
            throw new IllegalArgumentException("Server secret cannot be used as publishable key");
        this.serverSecretKey = serverSecretKey == null || serverSecretKey.trim().isEmpty()
                ? null : serverSecretKey.trim();
    }

    public static SupabaseConfig fromEnvironment() {
        return new SupabaseConfig(System.getenv("SUPABASE_URL"),
                System.getenv("SUPABASE_PUBLISHABLE_KEY"), System.getenv("SUPABASE_SECRET_KEY"));
    }

    public URI getBaseUri() { return baseUri; }
    public String getPublishableKey() { return publishableKey; }

    public String requireServerSecretKey() {
        if (serverSecretKey == null) throw new IllegalStateException("SUPABASE_SECRET_KEY is required on the game server");
        return serverSecretKey;
    }

    private static String required(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalStateException(name + " is required");
        return value.trim();
    }

    private static boolean isLegacyServiceRoleJwt(String key) {
        String[] parts = key.split("\\.", -1);
        if (parts.length != 3 || parts[1].length() > 12000) return false;
        try {
            JsonObject claims = JsonParser.parseString(new String(
                    Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)).getAsJsonObject();
            return claims.has("role") && "service_role".equals(claims.get("role").getAsString());
        } catch (RuntimeException ignored) { return false; }
    }
}
