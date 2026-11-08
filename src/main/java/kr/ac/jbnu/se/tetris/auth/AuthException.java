package kr.ac.jbnu.se.tetris.auth;

public final class AuthException extends Exception {
    private final String code;
    private final int httpStatus;

    public AuthException(String code) {
        this(code, 0);
    }

    public AuthException(String code, int httpStatus) {
        super(code);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
}
