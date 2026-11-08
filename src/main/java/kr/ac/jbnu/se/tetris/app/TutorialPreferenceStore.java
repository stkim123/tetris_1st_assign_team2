package kr.ac.jbnu.se.tetris.app;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** 첫 플레이 튜토리얼 표시 여부를 게임 저장과 독립적으로 보관한다. */
public final class TutorialPreferenceStore {
    private final Path file;
    public TutorialPreferenceStore(Path file) {
        if (file == null) throw new IllegalArgumentException("file");
        this.file = file.toAbsolutePath().normalize();
    }
    public static TutorialPreferenceStore defaultStore() {
        return new TutorialPreferenceStore(Paths.get(System.getProperty("user.home"),
                ".tetris-monster", "tutorial.properties"));
    }
    public boolean loadSkip() throws IOException {
        if (!Files.exists(file)) return false;
        Properties data = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { data.load(reader); }
        String value = data.getProperty("skipTutorial");
        if (!"true".equals(value) && !"false".equals(value))
            throw new IOException("Invalid tutorial preference");
        return Boolean.parseBoolean(value);
    }
    public void saveSkip(boolean skip) throws IOException {
        Path parent = file.getParent();
        if (parent == null) throw new IOException("No settings directory");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "tutorial-", ".tmp");
        try {
            Properties data = new Properties(); data.setProperty("skipTutorial", Boolean.toString(skip));
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                data.store(writer, "Tetris Monster tutorial preference");
            }
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
