import java.util.ArrayList;
import java.util.List;

/**
 * CustomGameManager
 * -----------------
 * Client-side cache of the uploaded-games catalog, same "static
 * cache + listener list + blocking refresh()" shape as GameManager
 * uses for the built-in game list.
 */
public class CustomGameManager
{
    private static List<CustomGameInfo> cachedGames = new ArrayList<CustomGameInfo>();
    private static final List<Runnable> listeners = new ArrayList<Runnable>();

    private CustomGameManager()
    {
        // Static utility class - never instantiated.
    }

    public static List<CustomGameInfo> getCachedGames()
    {
        return cachedGames;
    }

    public static void addListener(Runnable listener)
    {
        listeners.add(listener);
    }

    /**
     * Fetches the current custom-game catalog from the server. BLOCKING -
     * always call from a background thread, never the Swing event
     * thread. Returns false if the server couldn't be reached; the
     * cache is left unchanged in that case.
     */
    public static boolean refresh()
    {
        Message request = new Message();
        request.setType(MessageType.CUSTOM_GAME_LIST_REQUEST);

        Message response = NetworkManager.send(request);
        if (response == null || !response.isSuccess())
        {
            return false;
        }

        List<CustomGameInfo> parsed = new ArrayList<CustomGameInfo>();
        List<String> entries = response.getCustomGameEntries();
        if (entries != null)
        {
            for (int i = 0; i < entries.size(); i++)
            {
                CustomGameInfo info = CustomGameInfo.parse(entries.get(i));
                if (info != null)
                {
                    parsed.add(info);
                }
            }
        }

        cachedGames = parsed;
        notifyListeners();
        return true;
    }

    private static void notifyListeners()
    {
        for (int i = 0; i < listeners.size(); i++)
        {
            javax.swing.SwingUtilities.invokeLater(listeners.get(i));
        }
    }
}
