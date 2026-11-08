package kr.ac.jbnu.se.tetris.battle;

import kr.ac.jbnu.se.tetris.character.CharacterSpec;

/** 참가자 한 명의 ID·표시 이름·캐릭터(최대 HP, 공격 버프 등)를 담은 불변 전투 설정 */
public final class ParticipantSpec {
    private final String id;
    private final String name;
    private final CharacterSpec character;

    /** 기본형 능력치에 최대 HP만 지정하는 생성, 몬스터와 테스트용 */
    public ParticipantSpec(String id, String name, int maxHp) {
        this(id, name, basicWithHp(maxHp));
    }

    public ParticipantSpec(String id, String name, CharacterSpec character) {
        if (id == null || id.trim().isEmpty() || name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Participant needs an ID and name");
        }
        if (character == null) throw new IllegalArgumentException("Character is required");
        this.id = id;
        this.name = name;
        this.character = character;
    }

    private static CharacterSpec basicWithHp(int maxHp) {
        if (maxHp <= 0) throw new IllegalArgumentException("Participant needs an ID, name, and positive max HP");
        return new CharacterSpec(CharacterSpec.DEFAULT.getId(), CharacterSpec.DEFAULT.getName(), maxHp);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getMaxHp() { return character.getMaxHp(); }
    public CharacterSpec getCharacter() { return character; }
}
