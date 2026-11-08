package kr.ac.jbnu.se.tetris.auth;

/** Short-lived access and rotating refresh tokens. Keep only in process memory. */
public final class AuthSession {
    private final String accessToken;
    private final String refreshToken;
    private final String userId;
    private final long expiresAtEpochSecond;

    public AuthSession(String accessToken, String refreshToken, String userId, long expiresAtEpochSecond) {
        if (accessToken == null || accessToken.isEmpty() || refreshToken == null || refreshToken.isEmpty()
                || userId == null || userId.isEmpty()) throw new IllegalArgumentException("Invalid session");
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.expiresAtEpochSecond = expiresAtEpochSecond;
    }

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getUserId() { return userId; }
    public long getExpiresAtEpochSecond() { return expiresAtEpochSecond; }

    @Override public String toString() { return "AuthSession{userId=" + userId + ", tokens=[redacted]}"; }
}
