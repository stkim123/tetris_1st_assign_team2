package kr.ac.jbnu.se.tetris.auth;

public final class AuthIdentity {
    private final String userId;
    private final String displayName;
    private final long expiresAtEpochSecond;

    public AuthIdentity(String userId, String displayName, long expiresAtEpochSecond) {
        if (userId == null || userId.isEmpty()) throw new IllegalArgumentException("userId");
        this.userId = userId;
        this.displayName = displayName == null || displayName.trim().isEmpty() ? "Player" : displayName.trim();
        this.expiresAtEpochSecond = expiresAtEpochSecond;
    }

    public String getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public long getExpiresAtEpochSecond() { return expiresAtEpochSecond; }
}
