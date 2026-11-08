package kr.ac.jbnu.se.tetris.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.Properties;
import kr.ac.jbnu.se.tetris.auth.AuthService;
import kr.ac.jbnu.se.tetris.auth.FirebaseAuthService;
import kr.ac.jbnu.se.tetris.auth.SupabaseAuthService;
import kr.ac.jbnu.se.tetris.firebase.FirebaseConfig;
import kr.ac.jbnu.se.tetris.ranking.LeaderboardService;
import kr.ac.jbnu.se.tetris.supabase.SupabaseConfig;

/** Public online endpoints and publishable Auth keys. Never stores account tokens. */
public final class RankedOnlineConfig {
    private final URI serverUri;
    private final SupabaseConfig supabaseConfig;
    private final FirebaseConfig firebaseConfig;

    public RankedOnlineConfig(URI serverUri, SupabaseConfig supabaseConfig) {
        if (serverUri == null || supabaseConfig == null) throw new IllegalArgumentException("Online configuration required");
        new ConnectionOptions(serverUri, "configuration-validation");
        this.serverUri = serverUri;
        this.supabaseConfig = supabaseConfig;
        this.firebaseConfig = null;
    }

    public RankedOnlineConfig(URI serverUri, FirebaseConfig firebaseConfig) {
        if (serverUri == null || firebaseConfig == null) throw new IllegalArgumentException("Online configuration required");
        new ConnectionOptions(serverUri, "configuration-validation");
        this.serverUri = serverUri;
        this.supabaseConfig = null;
        this.firebaseConfig = firebaseConfig;
    }

    public URI getServerUri() { return serverUri; }
    public SupabaseConfig getSupabaseConfig() { return supabaseConfig; }
    public FirebaseConfig getFirebaseConfig() { return firebaseConfig; }
    public boolean isFirebase() { return firebaseConfig != null; }
    public AuthService createAuthService() {
        return isFirebase() ? new FirebaseAuthService(firebaseConfig) : new SupabaseAuthService(supabaseConfig);
    }
    public LeaderboardService createLeaderboardService() {
        return isFirebase() ? new LeaderboardService(serverUri) : new LeaderboardService(supabaseConfig);
    }

    public static RankedOnlineConfig load() throws IOException {
        Properties properties = new Properties();
        Path file = configFile();
        if (file != null && Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } else {
            // 공개 접속 정보만 기본값으로 배포해 새 PC의 dev 빌드도 온라인 서버를 찾는다.
            // 외부 파일·환경 변수·시스템 속성은 기존처럼 우선하며 비밀 키/토큰은 포함하지 않는다.
            try (InputStream defaults = RankedOnlineConfig.class.getResourceAsStream("/online-defaults.properties")) {
                if (defaults != null) properties.load(defaults);
            }
        }
        String server = value(properties, "server.url", "TETRIS_SERVER_URL");
        String provider = value(properties, "online.provider", "TETRIS_ONLINE_PROVIDER");
        String firebaseProject = value(properties, "firebase.projectId", "FIREBASE_PROJECT_ID");
        String firebaseKey = value(properties, "firebase.apiKey", "FIREBASE_API_KEY");
        String url = value(properties, "supabase.url", "SUPABASE_URL");
        String key = value(properties, "supabase.publishableKey", "SUPABASE_PUBLISHABLE_KEY");
        if (server.isEmpty() && provider.isEmpty() && firebaseProject.isEmpty()
                && firebaseKey.isEmpty() && url.isEmpty() && key.isEmpty()) return null;
        if (provider.isEmpty()) provider = !firebaseProject.isEmpty() || !firebaseKey.isEmpty() ? "firebase" : "supabase";
        if (!"firebase".equalsIgnoreCase(provider) && !"supabase".equalsIgnoreCase(provider))
            throw new IOException("Unknown online provider");
        try {
            if ("firebase".equalsIgnoreCase(provider)) {
                if (server.isEmpty() || firebaseProject.isEmpty() || firebaseKey.isEmpty())
                    throw new IOException("Incomplete public Firebase online configuration");
                FirebaseConfig firebase = new FirebaseConfig(firebaseProject, firebaseKey);
                String authEmulator = value(properties, "firebase.authEmulatorUrl", "FIREBASE_AUTH_EMULATOR_URL");
                String tokenEmulator = value(properties, "firebase.tokenEmulatorUrl", "FIREBASE_TOKEN_EMULATOR_URL");
                if (!authEmulator.isEmpty() || !tokenEmulator.isEmpty()) {
                    if (authEmulator.isEmpty() || tokenEmulator.isEmpty())
                        throw new IOException("Both local Firebase emulator URLs are required");
                    firebase = FirebaseConfig.forLocalEmulator(firebaseProject, firebaseKey,
                            URI.create(authEmulator), URI.create(tokenEmulator));
                }
                return new RankedOnlineConfig(URI.create(server), firebase);
            }
            if (server.isEmpty() || url.isEmpty() || key.isEmpty())
                throw new IOException("Incomplete public Supabase online configuration");
            return new RankedOnlineConfig(URI.create(server), new SupabaseConfig(url, key));
        } catch (IllegalArgumentException | IllegalStateException error) {
            throw new IOException("Invalid public online configuration", error);
        }
    }

    private static Path configFile() {
        String override = System.getProperty("tetris.client.config");
        if (override != null && !override.trim().isEmpty()) return Paths.get(override.trim());
        Path cwd = Paths.get("tetris-client.properties");
        if (Files.isRegularFile(cwd)) return cwd;
        try {
            CodeSource source = RankedOnlineConfig.class.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) {
                Path code = Paths.get(source.getLocation().toURI()).toAbsolutePath();
                Path sibling = code.getParent().resolve("tetris-client.properties");
                if (Files.isRegularFile(sibling)) return sibling;
            }
        } catch (Exception ignored) { /* 설정이 없으면 로컬 게임으로 진입한다. */ }
        return cwd;
    }

    private static String value(Properties properties, String name, String environment) {
        String found = System.getProperty("tetris." + name);
        if (found == null || found.trim().isEmpty()) found = System.getenv(environment);
        if (found == null || found.trim().isEmpty()) found = properties.getProperty(name, "");
        return found.trim();
    }
}
