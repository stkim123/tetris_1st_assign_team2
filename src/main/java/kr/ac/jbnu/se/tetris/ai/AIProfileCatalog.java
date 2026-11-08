package kr.ac.jbnu.se.tetris.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** 클래스패스 AI 설정의 로딩·검증·조회 경계 */
public final class AIProfileCatalog {
    private static final Set<String> FIELDS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "policy", "delayMillis", "maxSearchStates", "budgetMillis", "maxWeightDeltaRatio",
            "line", "fourLineBonus", "aggregateHeight", "maximumHeight", "holes",
            "bumpiness", "wells")));
    private final Map<String, AIProfile> profiles;

    private AIProfileCatalog(Map<String, AIProfile> profiles) {
        this.profiles = Collections.unmodifiableMap(new LinkedHashMap<String, AIProfile>(profiles));
    }

    public static AIProfileCatalog loadDefault() {
        InputStream input = AIProfileCatalog.class.getClassLoader()
                .getResourceAsStream("ai/profiles.properties");
        if (input == null) throw new IllegalStateException("Missing ai/profiles.properties");
        return load(input);
    }

    /** 제공한 스트림의 소유권 이전과 설정 오류의 명시적 거절 */
    public static AIProfileCatalog load(InputStream input) {
        if (input == null) throw new IllegalArgumentException("AI profile input is required");
        Properties properties = new Properties();
        try (InputStream stream = input;
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read AI profiles", failure);
        }
        String list = required(properties, "profiles");
        Map<String, AIProfile> result = new LinkedHashMap<String, AIProfile>();
        for (String entry : list.split(",", -1)) {
            String id = entry.trim();
            if (!id.matches("[a-z][a-z0-9_]*") || result.containsKey(id)) {
                throw new IllegalArgumentException("Invalid or duplicate AI profile ID: " + id);
            }
            String prefix = id + ".";
            String policyId = required(properties, prefix + "policy");
            if (!"FIXED".equals(policyId) && !"ADAPTIVE".equals(policyId)
                    && !"BOSS".equals(policyId)) {
                throw new IllegalArgumentException("Unsupported AI policy: " + policyId);
            }
            HeuristicWeights weights = new HeuristicWeights(
                    number(properties, prefix + "line"),
                    number(properties, prefix + "fourLineBonus"),
                    number(properties, prefix + "aggregateHeight"),
                    number(properties, prefix + "maximumHeight"),
                    number(properties, prefix + "holes"),
                    number(properties, prefix + "bumpiness"),
                    number(properties, prefix + "wells"));
            result.put(id, new AIProfile(id, policyId, weights,
                    integer(properties, prefix + "delayMillis"),
                    integer(properties, prefix + "maxSearchStates"),
                    integer(properties, prefix + "budgetMillis"),
                    number(properties, prefix + "maxWeightDeltaRatio")));
        }
        if (result.isEmpty()) throw new IllegalArgumentException("No AI profiles");
        for (String key : properties.stringPropertyNames()) {
            if ("profiles".equals(key)) continue;
            boolean known = false;
            for (String id : result.keySet()) {
                if (key.startsWith(id + ".") && FIELDS.contains(key.substring(id.length() + 1))) {
                    known = true;
                }
            }
            if (!known) throw new IllegalArgumentException("Unknown AI profile key: " + key);
        }
        return new AIProfileCatalog(result);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing AI profile property: " + key);
        }
        return value.trim();
    }

    private static int integer(Properties properties, String key) {
        try { return Integer.parseInt(required(properties, key)); }
        catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid integer AI profile property: " + key, invalid);
        }
    }

    private static double number(Properties properties, String key) {
        try { return Double.parseDouble(required(properties, key)); }
        catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid numeric AI profile property: " + key, invalid);
        }
    }

    public AIProfile get(String profileId) {
        AIProfile profile = profiles.get(profileId);
        if (profile == null) throw new IllegalArgumentException("Unknown AI profile: " + profileId);
        return profile;
    }

    public Map<String, AIProfile> all() { return profiles; }
}
