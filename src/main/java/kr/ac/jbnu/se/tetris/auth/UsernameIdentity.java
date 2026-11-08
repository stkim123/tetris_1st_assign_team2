package kr.ac.jbnu.se.tetris.auth;

import java.util.Locale;
import java.util.regex.Pattern;

/** Stable, non-deliverable Auth email namespace for username/password accounts. */
public final class UsernameIdentity {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]{2,19}");
    private static final String DOMAIN = "players.campus-quest.invalid";

    private UsernameIdentity() { }

    /** IDs are case-insensitive and ASCII-only to avoid ambiguous account spellings. */
    public static String normalize(String input) {
        String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (!ID.matcher(normalized).matches())
            throw new IllegalArgumentException("ID must be 3-20 letters, digits or underscores, starting with a letter");
        return normalized;
    }

    public static String emailFor(String input) {
        return normalize(input) + "@" + DOMAIN;
    }

    /** Return the canonical ID only for addresses in the reserved Auth namespace. */
    public static String fromEmail(String email) {
        String suffix = "@" + DOMAIN;
        if (email == null || !email.endsWith(suffix)) return null;
        String username = email.substring(0, email.length() - suffix.length());
        return ID.matcher(username).matches() ? username : null;
    }
}
