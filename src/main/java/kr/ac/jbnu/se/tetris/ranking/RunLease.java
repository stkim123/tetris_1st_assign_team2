package kr.ac.jbnu.se.tetris.ranking;

/** DB-confirmed ownership and durable admission policy for one server run. */
public final class RunLease {
    private final boolean owned;
    private final boolean enabled;

    public RunLease(boolean owned, boolean enabled) {
        this.owned = owned;
        this.enabled = enabled;
    }

    public boolean isOwned() { return owned; }
    public boolean isEnabled() { return enabled; }
}
