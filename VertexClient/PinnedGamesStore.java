import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * PinnedGamesStore
 * ----------------
 * "Quick Play" pins - a personal, local shortlist of games shown on
 * the Games Home view. Stored via java.util.prefs.Preferences (no
 * server round-trip, no new protocol needed) since this is a pure
 * convenience feature, not account data - it persists per machine,
 * same as most desktop apps' "recent/pinned" lists.
 */
public class PinnedGamesStore
{
    private static final Preferences PREFS = Preferences.userNodeForPackage(PinnedGamesStore.class);
    private static final String KEY = "gamehub_pinned_games";

    private PinnedGamesStore()
    {
        // Static utility class - never instantiated.
    }

    public static List<String> getPinned()
    {
        String raw = PREFS.get(KEY, "");
        List<String> result = new ArrayList<String>();
        if (!raw.isEmpty())
        {
            String[] parts = raw.split(",");
            for (int i = 0; i < parts.length; i++)
            {
                if (!parts[i].trim().isEmpty())
                {
                    result.add(parts[i].trim());
                }
            }
        }
        return result;
    }

    public static boolean isPinned(String gameId)
    {
        return getPinned().contains(gameId);
    }

    public static void toggle(String gameId)
    {
        List<String> pinned = getPinned();
        if (pinned.contains(gameId))
        {
            pinned.remove(gameId);
        }
        else
        {
            pinned.add(gameId);
        }
        save(pinned);
    }

    private static void save(List<String> pinned)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pinned.size(); i++)
        {
            if (i > 0) sb.append(",");
            sb.append(pinned.get(i));
        }
        PREFS.put(KEY, sb.toString());
    }
}
