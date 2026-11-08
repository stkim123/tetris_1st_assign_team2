package kr.ac.jbnu.se.tetris.audio;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/** CC0 효과음·캠퍼스 배경음의 PCM 형식과 두 채널 설정을 확인한다. */
public final class AudioServiceTest {
    private AudioServiceTest() { }
    public static void main(String[] args) throws Exception {
        Set<String> unique = new HashSet<>();
        for (AudioService.Event event : AudioService.Event.values()) {
            String path = AudioService.effectResource(event);
            check(unique.add(path), "duplicate sound for " + event);
            try (InputStream source = AudioServiceTest.class.getResourceAsStream(path)) {
                check(source != null, event + " resource missing: " + path);
                try (AudioInputStream stream = AudioSystem.getAudioInputStream(
                        new BufferedInputStream(source))) {
                    AudioFormat format = stream.getFormat();
                    check(AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding()),
                            event + " must be uncompressed PCM for Java 8");
                    check(format.getSampleSizeInBits() == 16, event + " must be 16-bit");
                    check(format.getChannels() == 1, event + " must be mono");
                    check(stream.getFrameLength() > 0 && stream.getFrameLength() <=
                            format.getFrameRate() * 1.5, event + " must be a short cue");
                }
            }
        }
        check(unique.size() == AudioService.Event.values().length, "one cue per event");
        for (String name : new String[] { "background" }) {
            try (InputStream source = AudioServiceTest.class.getResourceAsStream(
                        "/audio/university/" + name + ".wav")) {
                check(source != null, name + " resource missing");
                try (AudioInputStream stream = AudioSystem.getAudioInputStream(
                        new BufferedInputStream(source))) {
                    check(stream.getFormat().getSampleRate() > 0, name + " sample rate");
                    check(stream.getFrameLength() > 0, name + " frames");
                    check(stream.getFormat().getChannels() > 0, name + " channels");
                }
            }
        }
        Path folder = Files.createTempDirectory(
                Files.createDirectories(Paths.get("out", "audio-test")), "sound-");
        Path settings = folder.resolve("sound.properties");
        AudioService service = new AudioService(settings);
        service.setMuted(true);
        service.setVolume(0.42f);
        service.startBgm();
        service.setBgmMuted(true);
        service.setBgmVolume(0.23f);
        service.play(AudioService.Event.VICTORY);
        check(service.isMuted(), "mute state");
        check(Math.abs(service.getVolume() - .42f) < .001f, "volume state");
        check(service.isBgmMuted(), "BGM mute state");
        check(Math.abs(service.getBgmVolume() - .23f) < .001f, "BGM volume state");
        service.stopBgm();
        service.close();
        check(Files.isRegularFile(settings) && savedSettingsReady(settings),
                "settings must be saved before close returns");
        AudioService restored = new AudioService(settings);
        check(restored.isMuted(), "saved mute state");
        check(Math.abs(restored.getVolume() - .42f) < .001f, "saved volume");
        check(restored.isBgmMuted(), "saved BGM mute state");
        check(Math.abs(restored.getBgmVolume() - .23f) < .001f, "saved BGM volume");
        restored.close();
        System.out.println("PASS AudioServiceTest");
    }
    private static boolean savedSettingsReady(Path settings) {
        java.util.Properties properties = new java.util.Properties();
        try (InputStream stream = Files.newInputStream(settings)) {
            properties.load(stream);
            return "true".equals(properties.getProperty("muted")) &&
                    "0.42".equals(properties.getProperty("volume")) &&
                    "true".equals(properties.getProperty("bgmMuted")) &&
                    "0.23".equals(properties.getProperty("bgmVolume"));
        } catch (Exception ignored) { return false; }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
