package kr.ac.jbnu.se.tetris.story;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 하나 이상의 전투를 담는 불변 레벨 데이터. 패턴 순서는 자유롭다. */
public final class Stage {
    private final String id;
    private final String name;
    private final List<MonsterSpec> encounters;

    public Stage(String id, String name, List<MonsterSpec> encounters) {
        if (id == null || id.trim().isEmpty() || name == null || name.trim().isEmpty()
                || encounters == null || encounters.isEmpty()) {
            throw new IllegalArgumentException("Stage needs an ID, name, and encounters");
        }
        Set<String> ids = new HashSet<String>();
        for (MonsterSpec monster : encounters) {
            if (monster == null || !ids.add(monster.getId())) {
                throw new IllegalArgumentException("Stage encounters need unique IDs");
            }
        }
        this.id = id;
        this.name = name;
        this.encounters = Collections.unmodifiableList(new ArrayList<MonsterSpec>(encounters));
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<MonsterSpec> getEncounters() { return encounters; }
}
