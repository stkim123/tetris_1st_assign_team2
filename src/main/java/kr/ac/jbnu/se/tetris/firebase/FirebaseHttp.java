package kr.ac.jbnu.se.tetris.firebase;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import kr.ac.jbnu.se.tetris.auth.AuthException;

/** Bounded Auth REST transport for the desktop; requests run outside the Swing EDT. */
public final class FirebaseHttp {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private final FirebaseConfig config;

    public FirebaseHttp(FirebaseConfig config) {
        if (config == null) throw new IllegalArgumentException("config");
        this.config = config;
    }

    public JsonObject auth(String endpoint, JsonObject payload) throws IOException, AuthException {
        return request(config.getAuthBaseUri().toString() + endpoint + "?key=" + config.getApiKey(),
                "application/json; charset=utf-8", payload.toString());
    }

    public JsonObject refresh(String refreshToken) throws IOException, AuthException {
        // The form body is required by securetoken.googleapis.com, unlike Identity Toolkit calls.
        String body = "grant_type=refresh_token&refresh_token="
                + java.net.URLEncoder.encode(refreshToken, "UTF-8");
        return request(config.getTokenBaseUri().toString() + "token?key=" + config.getApiKey(),
                "application/x-www-form-urlencoded; charset=utf-8", body);
    }

    private static JsonObject request(String url, String contentType, String payload) throws IOException, AuthException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", contentType);
        byte[] request = payload.getBytes(StandardCharsets.UTF_8);
        if (request.length > 65536) throw new IllegalArgumentException("Firebase request too large");
        connection.setFixedLengthStreamingMode(request.length);
        connection.setDoOutput(true);
        try {
            try (OutputStream output = connection.getOutputStream()) { output.write(request); }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            byte[] response = stream == null ? new byte[0] : readBounded(stream);
            JsonObject body;
            try { body = JsonParser.parseString(new String(response, StandardCharsets.UTF_8)).getAsJsonObject(); }
            catch (RuntimeException invalid) {
                if (status >= 400) throw new IOException("Firebase HTTP " + status, invalid);
                throw new IOException("Invalid Firebase Auth response", invalid);
            }
            if (status < 200 || status >= 300) {
                if (status >= 400 && status < 500) {
                    String code = "HTTP_" + status;
                    if (body.has("error") && body.get("error").isJsonObject()) {
                        JsonObject detail = body.getAsJsonObject("error");
                        if (detail.has("message") && detail.get("message").isJsonPrimitive())
                            code = detail.get("message").getAsString();
                    }
                    throw new AuthException(code, status);
                }
                throw new IOException("Firebase HTTP " + status);
            }
            return body;
        } finally { connection.disconnect(); }
    }

    private static byte[] readBounded(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > MAX_RESPONSE_BYTES) throw new IOException("Firebase response too large");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
}
