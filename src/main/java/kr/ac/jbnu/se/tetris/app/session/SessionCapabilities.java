package kr.ac.jbnu.se.tetris.app.session;

/** 화면에서 현재 허용할 조작 목록 */
public final class SessionCapabilities {
    private final boolean canSubmit;
    private final boolean canPause;
    private final boolean canUseItems;
    private final boolean canLeave;

    public SessionCapabilities(boolean canSubmit, boolean canPause,
                               boolean canUseItems, boolean canLeave) {
        this.canSubmit = canSubmit;
        this.canPause = canPause;
        this.canUseItems = canUseItems;
        this.canLeave = canLeave;
    }

    public boolean canSubmit() { return canSubmit; }
    public boolean canPause() { return canPause; }
    public boolean canUseItems() { return canUseItems; }
    public boolean canLeave() { return canLeave; }
}
