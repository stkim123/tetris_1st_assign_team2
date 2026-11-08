package kr.ac.jbnu.se.tetris.supabase;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Bounded synchronous transport. Callers must run it outside game ticks and the Swing EDT. */
public final class SupabaseHttp {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private final SupabaseConfig config;

    public SupabaseHttp(SupabaseConfig config) { this.config = config; }

    public JsonElement request(String method, String path, JsonElement payload, String bearer, boolean serverKey)
            throws IOException {
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("..")
                || path.contains("#")) throw new IllegalArgumentException("Invalid Supabase path");
        URL url = config.getBaseUri().resolve(path).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("apikey", serverKey ? config.requireServerSecretKey() : config.getPublishableKey());
        if (bearer != null) connection.setRequestProperty("Authorization", "Bearer " + bearer);
        try {
            if (payload != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                if (bytes.length > 65536) throw new IllegalArgumentException("Supabase request too large");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream stream = connection.getOutputStream()) { stream.write(bytes); }
            }
            int status = connection.getResponseCode();
            InputStream body = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            byte[] bytes = body == null ? new byte[0] : readBounded(body);
            if (status < 200 || status >= 300) {
                String errorCode = "HTTP_" + status;
                try {
                    JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                    if (parsed.isJsonObject()) {
                        JsonObject object = parsed.getAsJsonObject();
                        if (object.has("code") && object.get("code").isJsonPrimitive()) errorCode = object.get("code").getAsString();
                        else if (object.has("error_code") && object.get("error_code").isJsonPrimitive())
                            errorCode = object.get("error_code").getAsString();
                    }
                } catch (RuntimeException ignored) { /* Preserve the HTTP status. */ }
                throw new SupabaseHttpException(status, errorCode);
            }
            if (bytes.length == 0) return new JsonObject();
            try { return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)); }
            catch (RuntimeException e) { throw new IOException("Invalid Supabase JSON response", e); }
        } finally { connection.disconnect(); }
    }

    private static byte[] readBounded(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > MAX_RESPONSE_BYTES) throw new IOException("Supabase response too large");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
}
