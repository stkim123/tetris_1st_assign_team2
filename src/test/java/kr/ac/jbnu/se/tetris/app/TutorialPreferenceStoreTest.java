package kr.ac.jbnu.se.tetris.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** 온보딩 선택이 재실행 뒤에도 유지되는지 확인한다. */
public final class TutorialPreferenceStoreTest {
    private TutorialPreferenceStoreTest() { }
    public static void main(String[] args) throws Exception {
        Path file = Paths.get("out", "g0", "tutorial-preference-test", "tutorial.properties");
        Files.deleteIfExists(file);
        TutorialPreferenceStore store = new TutorialPreferenceStore(file);
        if (store.loadSkip()) throw new AssertionError("New player should see tutorial");
        store.saveSkip(true);
        if (!new TutorialPreferenceStore(file).loadSkip()) throw new AssertionError("Skip not saved");
        store.saveSkip(false);
        if (new TutorialPreferenceStore(file).loadSkip()) throw new AssertionError("Replay not saved");
        Files.write(file, "skipTutorial=maybe\n".getBytes(StandardCharsets.UTF_8));
        try { store.loadSkip(); throw new AssertionError("Malformed setting accepted"); }
        catch (IOException expected) { }
        System.out.println("PASS TutorialPreferenceStoreTest");
    }
}
