import java.util.ArrayList;
import java.util.List;

/**
 * NotificationCenter
 * ------------------
 * Client-side, in-memory notification list (Phase 9). Populated by
 * ChatPanel when a DM arrives or you're added to a group while not
 * currently viewing that channel - deliberately NOT triggered by
 * general chat, which would be far too noisy. No persistence - clears
 * on app restart, same limitation as chat itself right now.
 */
public class NotificationCenter
{
    public static class NotificationItem
    {
        public final String title;
        public final String body;

        public NotificationItem(String title, String body)
        {
            this.title = title;
            this.body = body;
        }
    }

    private static final List<NotificationItem> items = new ArrayList<NotificationItem>();
    private static int unreadCount = 0;
    private static final List<Runnable> listeners = new ArrayList<Runnable>();

    private NotificationCenter()
    {
        // Static utility class - never instantiated.
    }

    public static void add(String title, String body)
    {
        items.add(0, new NotificationItem(title, body));
        unreadCount++;
        notifyListeners();
    }

    public static List<NotificationItem> getAll()
    {
        return items;
    }

    public static int getUnreadCount()
    {
        return unreadCount;
    }

    public static void markAllRead()
    {
        if (unreadCount != 0)
        {
            unreadCount = 0;
            notifyListeners();
        }
    }

    public static void clearAll()
    {
        items.clear();
        unreadCount = 0;
        notifyListeners();
    }

    public static void addListener(Runnable listener)
    {
        listeners.add(listener);
    }

    private static void notifyListeners()
    {
        for (int i = 0; i < listeners.size(); i++)
        {
            javax.swing.SwingUtilities.invokeLater(listeners.get(i));
        }
    }
}
