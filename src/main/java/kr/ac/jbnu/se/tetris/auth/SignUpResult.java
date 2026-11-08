package kr.ac.jbnu.se.tetris.auth;

public final class SignUpResult {
    private final String userId;
    private final boolean pendingEmailVerification;
    private final AuthSession session;

    public SignUpResult(String userId, boolean pendingEmailVerification, AuthSession session) {
        this.userId = userId;
        this.pendingEmailVerification = pendingEmailVerification;
        this.session = session;
    }

    public String getUserId() { return userId; }
    public boolean isPendingEmailVerification() { return pendingEmailVerification; }
    public AuthSession getSession() { return session; }
}
