package kr.ac.jbnu.se.tetris.network;

import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;

/** 새 PC의 저장소 빌드가 공개 기본 설정만으로 온라인 서버를 찾는지 확인한다. */
public final class RankedOnlineConfigTest {
    private RankedOnlineConfigTest() { }

    public static void main(String[] args) throws Exception {
        Properties defaults = new Properties();
        try (InputStream stream = RankedOnlineConfig.class.getResourceAsStream("/online-defaults.properties")) {
            if (stream == null) throw new AssertionError("Public online defaults missing from classpath");
            defaults.load(stream);
        }
        if (defaults.size() != 4
                || !"firebase".equals(defaults.getProperty("online.provider"))
                || !"wss://campus-quest-pvp-633154644935.asia-northeast3.run.app/ws".equals(defaults.getProperty("server.url"))
                || !"campus-quest-tetris".equals(defaults.getProperty("firebase.projectId"))
                || !defaults.getProperty("firebase.apiKey", "").matches("AIza[A-Za-z0-9_-]{35}"))
            throw new AssertionError("Only the four intended public Firebase settings may ship");

        String[] env = { "TETRIS_SERVER_URL", "TETRIS_ONLINE_PROVIDER",
                "FIREBASE_PROJECT_ID", "FIREBASE_API_KEY", "SUPABASE_URL", "SUPABASE_PUBLISHABLE_KEY" };
        boolean ambientOverride = false;
        for (String name : env) ambientOverride |= System.getenv(name) != null;
        String old = System.getProperty("tetris.client.config");
        try {
            System.setProperty("tetris.client.config", "missing-config-" + UUID.randomUUID() + ".properties");
            RankedOnlineConfig loaded = RankedOnlineConfig.load();
            if (loaded == null) throw new AssertionError("Default online config must load without local file");
            if (!ambientOverride && (!loaded.isFirebase()
                    || !loaded.getServerUri().toString().equals(defaults.getProperty("server.url"))))
                throw new AssertionError("New checkout did not use the bundled Firebase server");
        } finally {
            if (old == null) System.clearProperty("tetris.client.config");
            else System.setProperty("tetris.client.config", old);
        }
        System.out.println("PASS RankedOnlineConfigTest");
    }
}
