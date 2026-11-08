package kr.ac.jbnu.se.tetris.firebase;

import java.net.URI;
import java.util.regex.Pattern;

/** Public desktop settings. These values are not service-account credentials. */
public final class FirebaseConfig {
    private static final Pattern PROJECT = Pattern.compile("[a-z][a-z0-9-]{3,62}");
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{8,256}");
    private final String projectId;
    private final String apiKey;
    private final URI authBaseUri;
    private final URI tokenBaseUri;

    public FirebaseConfig(String projectId, String apiKey) {
        this(projectId, apiKey, URI.create("https://identitytoolkit.googleapis.com/v1/"),
                URI.create("https://securetoken.googleapis.com/v1/"));
    }

    /** Explicit local Auth emulator URLs; never route production credentials to a remote HTTP host. */
    public static FirebaseConfig forLocalEmulator(String projectId, String apiKey,
                                                   URI authBaseUri, URI tokenBaseUri) {
        if (!loopbackHttp(authBaseUri) || !loopbackHttp(tokenBaseUri))
            throw new IllegalArgumentException("Firebase emulator URLs must use HTTP loopback");
        return new FirebaseConfig(projectId, apiKey, authBaseUri, tokenBaseUri);
    }

    private FirebaseConfig(String projectId, String apiKey, URI authBaseUri, URI tokenBaseUri) {
        if (projectId == null || !PROJECT.matcher(projectId).matches())
            throw new IllegalArgumentException("Invalid Firebase project ID");
        if (apiKey == null || !KEY.matcher(apiKey).matches())
            throw new IllegalArgumentException("Invalid Firebase public API key");
        validateBase(authBaseUri);
        validateBase(tokenBaseUri);
        this.projectId = projectId;
        this.apiKey = apiKey;
        this.authBaseUri = authBaseUri;
        this.tokenBaseUri = tokenBaseUri;
    }

    public String getProjectId() { return projectId; }
    public String getApiKey() { return apiKey; }
    public URI getAuthBaseUri() { return authBaseUri; }
    public URI getTokenBaseUri() { return tokenBaseUri; }

    private static boolean loopbackHttp(URI uri) {
        return uri != null && "http".equalsIgnoreCase(uri.getScheme()) && loopback(uri.getHost());
    }

    private static boolean loopback(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    private static void validateBase(URI uri) {
        if (uri == null || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !("https".equalsIgnoreCase(uri.getScheme()) || loopbackHttp(uri))
                || !uri.getPath().endsWith("/"))
            throw new IllegalArgumentException("Invalid Firebase endpoint");
    }
}
