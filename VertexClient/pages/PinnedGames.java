package pages;

import java.util.ArrayList;
import java.util.List;
import economy.PinnedGamesStore;

/**
 * PinnedGames
 * -----------
 * The games a player chose to keep handy on the Home page ("Continue playing" shows them first). A small
 * per-computer list (java.util.prefs via economy.PinnedGamesStore, which the Games page's Pin buttons already
 * used - one list, two places to edit it), not account data - it doesn't sync to other computers or servers. Pin/unpin from the Home page tiles and the game detail page;
 * anything listening (the Home page) is told when the list changes.
 */
public final class PinnedGames
{
    /** More than this would stop being "handy" and just crowd the Home page. */
    public static final int MAX_PINNED = 8;

    private static final List<Runnable> listeners = new ArrayList<Runnable>();

    private PinnedGames() { }

    /** Pinned game ids in the order they were pinned (the same list the Games page's Pin buttons use - economy.PinnedGamesStore). */
    public static List<String> getAll()
    {
        return PinnedGamesStore.getPinned();
    }

    public static boolean isPinned(String gameId)
    {
        return PinnedGamesStore.isPinned(gameId);
    }

    /** Pins the game if it isn't, unpins it if it is. Returns false only when pinning was refused because the list is full. */
    public static boolean toggle(String gameId)
    {
        if (!PinnedGamesStore.isPinned(gameId) && PinnedGamesStore.getPinned().size() >= MAX_PINNED)
        {
            return false;
        }
        PinnedGamesStore.toggle(gameId);
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
        for (Runnable r : copy)
        {
            r.run();
        }
    }
}
