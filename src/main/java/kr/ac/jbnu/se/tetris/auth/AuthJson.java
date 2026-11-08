package kr.ac.jbnu.se.tetris.auth;

import com.google.gson.JsonObject;

/** Shared response parsing without a desktop dependency on server-only verification. */
final class AuthJson {
    private AuthJson() { }

    static String string(JsonObject object, String name) {
        try { return object.has(name) && object.get(name).isJsonPrimitive()
                ? object.get(name).getAsString() : null; }
        catch (RuntimeException e) { return null; }
    }

    static long longValue(JsonObject object, String name) {
        try { return object.get(name).getAsLong(); }
        catch (RuntimeException e) { return -1; }
    }
}
