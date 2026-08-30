import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * PinnedFriendsStore
 * ------------------
 * Favorite friends shown at the top of the Friends list - stored via
 * java.util.prefs.Preferences (no server round-trip, no new protocol
 * needed), same pattern as PinnedGamesStore. Local/per-machine
 * convenience data, not account data.
 */
public class PinnedFriendsStore
{
    private static final Preferences PREFS = Preferences.userNodeForPackage(PinnedFriendsStore.class);
    private static final String KEY = "vertex_pinned_friends";

    private PinnedFriendsStore()
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

    public static boolean isPinned(String username)
    {
        return getPinned().contains(username);
    }

    public static void toggle(String username)
    {
        List<String> pinned = getPinned();
        if (pinned.contains(username))
        {
            pinned.remove(username);
        }
        else
        {
            pinned.add(username);
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
