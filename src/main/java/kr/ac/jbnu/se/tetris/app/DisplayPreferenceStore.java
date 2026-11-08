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

/** 사용자의 화면 전환 효과 설정을 보관한다. */
public final class DisplayPreferenceStore {
    private final Path file;

    public DisplayPreferenceStore(Path file) {
        if (file == null) throw new IllegalArgumentException("file");
        this.file = file.toAbsolutePath().normalize();
    }

    public static DisplayPreferenceStore defaultStore() {
        return new DisplayPreferenceStore(Paths.get(System.getProperty("user.home"),
                ".tetris-monster", "display.properties"));
    }

    public boolean loadTransitionsOff() throws IOException {
        if (!Files.exists(file)) return false;
        Properties data = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            data.load(reader);
        }
        String value = data.getProperty("transitionsOff");
        if (!"true".equals(value) && !"false".equals(value))
            throw new IOException("Invalid display preference");
        return Boolean.parseBoolean(value);
    }

    public void saveTransitionsOff(boolean transitionsOff) throws IOException {
        Path parent = file.getParent();
        if (parent == null) throw new IOException("No settings directory");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "display-", ".tmp");
        try {
            Properties data = new Properties();
            data.setProperty("transitionsOff", Boolean.toString(transitionsOff));
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                data.store(writer, "Tetris Monster display preference");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
