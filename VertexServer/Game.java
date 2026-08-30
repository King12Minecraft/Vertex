/**
 * Game
 * ----
 * The contract every real game will implement, starting with the first
 * converted game in Phase 7. Nothing implements this yet - it's
 * scaffolding so that phase has a concrete interface to build against,
 * per the modular game architecture goal (Section 8).
 */
public interface Game
{
    GameInfo getInfo();

    void start();
    void pause();

    /** Serializes current progress for cross-device sync (Phase 12). */
    String saveState();

    /** Restores progress from a previously saved state. */
    void loadState(String savedState);
}
