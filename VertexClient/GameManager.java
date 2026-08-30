import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

/**
 * GameManager
 * -----------
 * Client-side cache of the game list, fetched from the server's
 * GameRegistry over the network (Section 25/26 - the refresh flow is
 * real starting now; the games themselves are still placeholders until
 * Phase 7's first conversion). Components register a listener here so
 * they refresh automatically after a successful fetch - same pattern as
 * ThemeManager/Session/NetworkManager.
 */
public class GameManager
{
    private static List<GameInfo> cachedGames = new ArrayList<GameInfo>();
    private static final List<Runnable> listeners = new ArrayList<Runnable>();

    private GameManager()
    {
        // Static utility class - never instantiated.
    }

    public static List<GameInfo> getCachedGames()
    {
        return cachedGames;
    }

    public static void addListener(Runnable listener)
    {
        listeners.add(listener);
    }

    /**
     * Fetches the current game list from the server. BLOCKING - always
     * call from a background thread, never the Swing event thread.
     * Returns false if the server couldn't be reached; the cache is
     * left unchanged in that case.
     */
    public static boolean refresh()
    {
        Message request = new Message();
        request.setType(MessageType.GAME_LIST_REQUEST);

        Message response = NetworkManager.send(request);
        if (response == null || !response.isSuccess())
        {
            return false;
        }

        List<GameInfo> newGames = response.getGameList();
        detectChanges(cachedGames, newGames);
        cachedGames = newGames;
        notifyListeners();
        return true;
    }

    /**
     * Phase 13 - compares the previous cached list against the freshly
     * fetched one and posts a Notification Centre entry for anything
     * new, updated (version string changed), or removed. Skipped on
     * the very first-ever fetch (nothing to compare against yet -
     * every game would incorrectly look "new"). No download/
     * verification step exists here on purpose - this app bundles all
     * game code statically at build time, there's no plugin/download
     * architecture to hook into, so that part of Phase 13 genuinely
     * doesn't apply to how this codebase is structured.
     */
    private static void detectChanges(List<GameInfo> oldGames, List<GameInfo> newGames)
    {
        if (oldGames.isEmpty())
        {
            return;
        }

        for (int i = 0; i < newGames.size(); i++)
        {
            GameInfo newGame = newGames.get(i);
            GameInfo oldGame = findById(oldGames, newGame.getGameId());

            if (oldGame == null)
            {
                NotificationCenter.add("New Game Added", newGame.getName() + " is now available.");
            }
            else if (!oldGame.getVersion().equals(newGame.getVersion()))
            {
                NotificationCenter.add("Game Updated", newGame.getName() + " updated to v" + newGame.getVersion() + ".");
            }
        }

        for (int i = 0; i < oldGames.size(); i++)
        {
            GameInfo oldGame = oldGames.get(i);
            if (findById(newGames, oldGame.getGameId()) == null)
            {
                NotificationCenter.add("Game Removed", oldGame.getName() + " is no longer available.");
            }
        }
    }

    private static GameInfo findById(List<GameInfo> games, String gameId)
    {
        for (int i = 0; i < games.size(); i++)
        {
            if (games.get(i).getGameId().equals(gameId))
            {
                return games.get(i);
            }
        }
        return null;
    }

    /**
     * Notifies every registered listener ON THE SWING EVENT THREAD, even
     * though refresh() itself runs on a background thread. Without this,
     * listeners that touch UI (like GamesPanel rebuilding its grid) would
     * be updating Swing components off the UI thread - which Swing
     * doesn't support safely, and typically shows up as the UI silently
     * failing to render (an empty-looking page) rather than a crash.
     */
    private static void notifyListeners()
    {
        for (int i = 0; i < listeners.size(); i++)
        {
            final Runnable listener = listeners.get(i);
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run() { listener.run(); }
            });
        }
    }
}
