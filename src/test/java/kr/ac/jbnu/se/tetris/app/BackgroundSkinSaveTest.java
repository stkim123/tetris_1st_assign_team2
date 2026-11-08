package kr.ac.jbnu.se.tetris.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Properties;
import java.io.Reader;
import java.io.Writer;

/** Runnable without external testing libraries: java -ea ... BackgroundSkinSaveTest */
public final class BackgroundSkinSaveTest {
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("tetris-skin", ".properties");
        try {
            PlayerSaveStore store = new PlayerSaveStore(file);
            PlayerSaveStore.Data data = PlayerSaveStore.Data.initial();
            data = data.withFirstClear("level_1_monster", 350);
            store.save(data);
            PlayerSaveStore.Data old = store.load();
            assert "default".equals(old.getEquippedBackgroundSkin());
            assert old.getOwnedBackgroundSkins().contains("default");
            PlayerSaveStore.Data winter = old.withPurchasedBackgroundSkin("winter");
            assert winter.getCoins() == 50;
            assert winter.getOwnedBackgroundSkins().contains("winter");
            assert "winter".equals(winter.getEquippedBackgroundSkin());
            winter = winter.withPurchasedBackgroundSkin("winter");
            assert winter.getCoins() == 50 : "Repeated purchase must not charge twice";
            winter = winter.withFirstClear("level_2_monster", 30);
            assert winter.getCoins() == 80;
            assert "winter".equals(winter.getEquippedBackgroundSkin()) : "Reward lost skin";
            winter = winter.withSelected("student");
            assert "winter".equals(winter.getEquippedBackgroundSkin()) : "Selection lost skin";
            store.save(winter);
            PlayerSaveStore.Data reloaded = store.load();
            assert reloaded.getCoins() == 80;
            assert reloaded.getCleared().size() == 2;
            assert "winter".equals(reloaded.getEquippedBackgroundSkin());
            reloaded = reloaded.withEquippedBackgroundSkin("default");
            assert "default".equals(reloaded.getEquippedBackgroundSkin());
            assert reloaded.getOwnedBackgroundSkins().contains("winter");
            store.save(reloaded);
            assert "default".equals(store.load().getEquippedBackgroundSkin());
            // Backward compatibility: version 2 save without skin keys.
            Properties legacy = new Properties();
            legacy.setProperty("version", "2"); legacy.setProperty("coins", "91");
            legacy.setProperty("selected", "student"); legacy.setProperty("owned", "student");
            legacy.setProperty("cleared", "level_1_monster");
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) { legacy.store(out, "old save"); }
            PlayerSaveStore.Data upgraded = store.load();
            assert upgraded.getCoins() == 91;
            assert "default".equals(upgraded.getEquippedBackgroundSkin());
            assert upgraded.getCleared().contains("level_1_monster");
            System.out.println("PASS BackgroundSkinSaveTest");
        } finally { Files.deleteIfExists(file); }
    }
}
