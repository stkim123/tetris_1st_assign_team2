package kr.ac.jbnu.se.tetris.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.swing.SwingUtilities;
import kr.ac.jbnu.se.tetris.story.*;

/** 저장 재진입과 중복 보상, 손상 세이브 거절을 확인한다. */
public final class PlayerSaveStoreTest {
    private PlayerSaveStoreTest() { }
    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("tetris-save-test-");
        try {
            Path path = folder.resolve("save.properties");
            PlayerSaveStore store = new PlayerSaveStore(path);
            PlayerSaveStore.Data initial = store.load();
            assert initial.getCoins() == 0 && initial.getOwned().contains("student");
            StageCatalog catalog = StageCatalog.loadDefault();
            String first = catalog.getStages().get(0).getEncounters().get(0).getId();
            String second = catalog.getStages().get(0).getEncounters().get(1).getId();
            PlayerSaveStore.Data afterFirst = initial.withFirstClear(first, 30);
            assert afterFirst.withFirstClear(first, 30) == afterFirst;
            // 재클리어 보상: 클리어 기록은 그대로 두고 코인만 더한다.
            PlayerSaveStore.Data replayed = afterFirst.withCoins(6);
            assert replayed.getCoins() == afterFirst.getCoins() + 6
                    && replayed.getCleared().equals(afterFirst.getCleared());
            assert afterFirst.withCoins(0) == afterFirst;
            PlayerSaveStore.Data afterSecond = afterFirst.withFirstClear(second, 50);
            store.save(afterSecond);
            PlayerSaveStore.Data reloaded = store.load();
            assert reloaded.getCoins() == 80 && reloaded.getCleared().size() == 2;
            StoryProgressService progress = new StoryProgressService(catalog);
            progress.restoreCompletedEncounterIds(reloaded.getCleared());
            assert progress.getCampaignProgress().isEncounterUnlocked(
                    catalog.getStages().get(0).getId(), MonsterTier.BOSS);
            PlayerSaveStore.Data purchased = reloaded.withCharacter("attacker", 80);
            store.save(purchased);
            assert store.load().getSelected().equals("attacker");
            assert store.load().getCoins() == 0;
            legacyProgressKeepsProfileAndBackup(folder);
            Files.write(path, Arrays.asList("version=1", "coins=-1", "owned=student",
                    "selected=student"), StandardCharsets.UTF_8);
            boolean rejected = false;
            try { store.load(); } catch (IOException expected) { rejected = true; }
            assert rejected;
            Set<String> gap = new LinkedHashSet<String>();
            gap.add(second);
            rejected = false;
            try { new StoryProgressService(catalog).restoreCompletedEncounterIds(gap); }
            catch (IllegalArgumentException expected) { rejected = true; }
            assert rejected;
            Path interruptedPath = folder.resolve("interrupted.properties");
            final SeongeunApplication[] app = new SeongeunApplication[1];
            SwingUtilities.invokeAndWait(() -> app[0] = new SeongeunApplication(
                    new Random(19), new PlayerSaveStore(interruptedPath)));
            try {
                Files.createDirectory(interruptedPath);
                SwingUtilities.invokeAndWait(() -> app[0].commitSave(
                        app[0].getSaveData().withFirstClear(first, 30)));
                assert app[0].isSaveDirty();
                Files.delete(interruptedPath);
                SwingUtilities.invokeAndWait(() -> app[0].commitSave(
                        app[0].getSaveData().withFirstClear(second, 50)));
                assert !app[0].isSaveDirty();
                PlayerSaveStore.Data recovered = new PlayerSaveStore(interruptedPath).load();
                assert recovered.getCoins() == 80 && recovered.getCleared().size() == 2;
                new StoryProgressService(catalog).restoreCompletedEncounterIds(recovered.getCleared());
            } finally { SwingUtilities.invokeAndWait(() -> app[0].close()); }
            System.out.println("PASS PlayerSaveStoreTest");
        } finally {
            try (java.util.stream.Stream<Path> files = Files.walk(folder)) {
                files.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                });
            }
        }
    }

    private static void legacyProgressKeepsProfileAndBackup(Path folder) throws Exception {
        Path path = folder.resolve("legacy.properties");
        java.util.List<String> original = Arrays.asList("version=1", "coins=120", "owned=student,attacker",
                "selected=attacker", "cleared=calculus_textbook,lab_preview_examiner,bachelor_guardian");
        Files.write(path, original, StandardCharsets.UTF_8);
        byte[] originalBytes = Files.readAllBytes(path);
        PlayerSaveStore store = new PlayerSaveStore(path);
        PlayerSaveStore.Data migrated = store.load();
        assert migrated.getCoins() == 120 && "attacker".equals(migrated.getSelected());
        assert migrated.getCleared().equals(new LinkedHashSet<String>(Arrays.asList(
                "level_1_monster", "level_2_monster", "level_3_monster")));
        new StoryProgressService(StageCatalog.loadDefault()).restoreCompletedEncounterIds(migrated.getCleared());
        assert Arrays.equals(originalBytes, Files.readAllBytes(path)) : "Reading must not replace original save";
        store.save(migrated);
        assert Arrays.equals(originalBytes, Files.readAllBytes(path.resolveSibling("legacy.properties.v1.bak")));
        assert Files.readAllLines(path, StandardCharsets.UTF_8).contains("version=2");
        assert store.load().getCoins() == 120;
        Files.write(path, Arrays.asList("version=1", "coins=120", "owned=student",
                "selected=student", "cleared=bachelor_guardian"), StandardCharsets.UTF_8);
        boolean rejected = false;
        try { new PlayerSaveStore(path).load(); } catch (IOException expected) { rejected = true; }
        assert rejected : "Incomplete legacy prefix must be rejected";
    }
}
