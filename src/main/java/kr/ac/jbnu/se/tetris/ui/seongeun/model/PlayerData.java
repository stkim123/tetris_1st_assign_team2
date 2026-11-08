package kr.ac.jbnu.se.tetris.ui.seongeun.model;

public class PlayerData {
    private final String nickname;
    private final int level;
    private final String characterName;
    private boolean ready;

    public PlayerData(String nickname, int level, String characterName, boolean ready) {
        this.nickname = nickname;
        this.level = level;
        this.characterName = characterName;
        this.ready = ready;
    }

    public String getNickname() {
        return nickname;
    }

    public int getLevel() {
        return level;
    }

    public String getCharacterName() {
        return characterName;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }
}
