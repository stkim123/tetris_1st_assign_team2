package kr.ac.jbnu.se.tetris.story;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

/**
 * 전투별 대사 문구 모음. /story/dialogues.properties(UTF-8)에서 읽는다.
 * 키 형식은 "<전투 ID>.<상황>"이고, 없는 키는 null을 돌려주어 호출 쪽이 기본 문구로 대체한다.
 */
final class StoryDialogueLines {
    static final String RESOURCE = "/story/dialogues.properties";

    private final Properties lines;

    private StoryDialogueLines(Properties lines) { this.lines = lines; }

    /** 기본 대사 파일을 읽는다. 파일이 없으면 빈 모음(모두 기본 문구 사용)을 돌려준다. */
    static StoryDialogueLines loadDefault() {
        InputStream stream = StoryDialogueLines.class.getResourceAsStream(RESOURCE);
        if (stream == null) return new StoryDialogueLines(new Properties());
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return load(reader);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read " + RESOURCE, error);
        }
    }

    static StoryDialogueLines load(Reader reader) throws IOException {
        Properties properties = new Properties();
        properties.load(reader);
        return new StoryDialogueLines(properties);
    }

    /** 문구가 없거나 비어 있으면 null. */
    String get(String encounterId, String situation) {
        String value = lines.getProperty(encounterId + "." + situation);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /** 파일에 있는 모든 키(철자 검증용). */
    Set<String> keys() {
        Set<String> keys = new LinkedHashSet<String>();
        Enumeration<?> names = lines.propertyNames();
        while (names.hasMoreElements()) keys.add((String) names.nextElement());
        return Collections.unmodifiableSet(keys);
    }
}
