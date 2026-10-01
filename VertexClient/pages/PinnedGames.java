package pages;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * PinnedGames
 * -----------
 * The games a player chose to keep handy on the Home page ("Continue playing" shows them first). A small
 * per-computer list, stored like the other display settings (java.util.prefs), not account data - it
 * doesn't sync to other computers or servers. Pin/unpin from the Home page tiles and the game detail page;
 * anything listening (the Home page) is told when the list changes.
 */
public final class PinnedGames
{
    private static final Preferences PREFS = Preferences.userNodeForPackage(PinnedGames.class);
    private static final String KEY = "pinnedGameIds";
    /** More than this would stop being "handy" and just crowd the Home page. */
    public static final int MAX_PINNED = 8;

    private static final List<Runnable> listeners = new ArrayList<Runnable>();

    private PinnedGames() { }

    /** Pinned game ids in the order they were pinned. */
    public static synchronized List<String> getAll()
    {
        List<String> result = new ArrayList<String>();
        String stored = PREFS.get(KEY, "");
        if (!stored.isEmpty())
        {
            for (String id : stored.split(","))
            {
                if (!id.isEmpty() && !result.contains(id)) result.add(id);
            }
        }
        return result;
    }

    public static synchronized boolean isPinned(String gameId)
    {
        return getAll().contains(gameId);
    }

    /** Pins the game if it isn't, unpins it if it is. Returns false only when pinning was refused because the list is full. */
    public static boolean toggle(String gameId)
    {
        synchronized (PinnedGames.class)
        {
            List<String> all = getAll();
            if (all.contains(gameId))
            {
                all.remove(gameId);
            }
            else
            {
                if (all.size() >= MAX_PINNED)
                {
                    return false;
                }
                all.add(gameId);
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < all.size(); i++)
            {
                if (i > 0) sb.append(",");
                sb.append(all.get(i));
            }
            PREFS.put(KEY, sb.toString());
        }
        fireChanged();
        return true;
    }

    public static synchronized void addListener(Runnable listener)
    {
        listeners.add(listener);
    }

    private static void fireChanged()
    {
        List<Runnable> copy;
        synchronized (PinnedGames.class)
        {
            copy = new ArrayList<Runnable>(listeners);
        }
        for (Runnable r : copy) r.run();
    }
}
