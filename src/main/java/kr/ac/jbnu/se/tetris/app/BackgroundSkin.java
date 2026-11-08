package kr.ac.jbnu.se.tetris.app;

/** A purchased background theme. The winter pack changes lobby and story battle scenery only. */
public enum BackgroundSkin {
    DEFAULT("default", "기본 배경", 0),
    WINTER("winter", "겨울 배경팩", 300);

    private final String id;
    private final String displayName;
    private final int price;

    BackgroundSkin(String id, String displayName, int price) {
        this.id = id;
        this.displayName = displayName;
        this.price = price;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public int getPrice() { return price; }

    public static BackgroundSkin fromId(String id) {
        for (BackgroundSkin skin : values()) if (skin.id.equals(id)) return skin;
        throw new IllegalArgumentException("Unknown background skin: " + id);
    }
}
