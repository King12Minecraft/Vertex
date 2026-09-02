import java.util.prefs.Preferences;

/**
 * LastGameModeStore
 * -----------------
 * Remembers which mode (e.g. "vs Player" / "vs AI") you last picked
 * for a given game, so its mode-select screen can show a small "Last
 * played" badge on that card - a hint, not an auto-skip. Deliberately
 * doesn't jump straight past mode-select: doing that would trap
 * someone who wants to switch modes with no easy way back except
 * clearing this preference by hand. Same Preferences-backed, no-
 * server-round-trip pattern as PinnedGamesStore/PinnedFriendsStore.
 */
public class LastGameModeStore
{
    private static final Preferences PREFS = Preferences.userNodeForPackage(LastGameModeStore.class);

    private LastGameModeStore()
    {
        // Static utility class - never instantiated.
    }

    public static void setLastMode(String gameId, String mode)
    {
        PREFS.put(gameId, mode);
    }

    /** Returns the last-used mode for this game, or null if it's never been played. */
    public static String getLastMode(String gameId)
    {
        return PREFS.get(gameId, null);
    }
}
