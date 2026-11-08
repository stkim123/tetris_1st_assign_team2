package kr.ac.jbnu.se.tetris.supabase;

import java.io.IOException;

/** Error codes intentionally exclude request content, tokens and passwords. */
public final class SupabaseHttpException extends IOException {
    private final int status;
    private final String code;

    public SupabaseHttpException(int status, String code) {
        super("Supabase request failed: " + status + " (" + code + ")");
        this.status = status;
        this.code = code;
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }
}
