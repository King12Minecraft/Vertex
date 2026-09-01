import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * SavedServersStore
 * ------------------
 * A short list of "host:port" addresses the person has connected to
 * before, so switching back to one is a click instead of retyping an
 * address - same Preferences-backed, no-server-round-trip pattern as
 * PinnedFriendsStore/LastGameModeStore. Purely local convenience data;
 * doesn't imply anything is shared between the servers themselves.
 */
public class SavedServersStore
{
    private static final Preferences PREFS = Preferences.userNodeForPackage(SavedServersStore.class);
    private static final String KEY = "vertex_saved_servers";
    private static final int MAX_SAVED = 8;

    private SavedServersStore()
    {
        // Static utility class - never instantiated.
    }

    public static List<String> getSaved()
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

    /** Adds an address to the front of the list (most-recently-used first), removing any duplicate and trimming down to MAX_SAVED entries. */
    public static void add(String address)
    {
        List<String> saved = getSaved();
        saved.remove(address);
        saved.add(0, address);
        while (saved.size() > MAX_SAVED)
        {
            saved.remove(saved.size() - 1);
        }
        save(saved);
    }

    public static void remove(String address)
    {
        List<String> saved = getSaved();
        saved.remove(address);
        save(saved);
    }

    private static void save(List<String> saved)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < saved.size(); i++)
        {
            if (i > 0) sb.append(",");
            sb.append(saved.get(i));
        }
        PREFS.put(KEY, sb.toString());
    }
}
