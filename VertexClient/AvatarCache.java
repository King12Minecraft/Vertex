import java.awt.Image;
import java.awt.Toolkit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.SwingUtilities;

/**
 * AvatarCache
 * -----------
 * Client-side cache of username -> avatar Image, so every panel that
 * wants to show someone's avatar (Settings today; Chat/Friends/
 * Leaderboards are natural next additions) doesn't need its own
 * fetch-and-remember logic. Fetches are fire-and-forget in a
 * background thread; callers pass a Runnable to be notified once the
 * image is actually available, since the first request for any given
 * username is always a cache miss.
 */
public class AvatarCache
{
    private static final Map<String, Image> cache = new HashMap<String, Image>();
    private static final Set<String> inFlight = new HashSet<String>();

    public interface Listener
    {
        void onLoaded(Image image);
    }

    /** Returns the cached image immediately if we already have it (listener is still called, for a consistent call pattern), otherwise kicks off a background fetch and calls the listener once it lands. image is null if that account has no avatar set. */
    public static void get(final String username, final Listener listener)
    {
        if (username == null)
        {
            listener.onLoaded(null);
            return;
        }

        synchronized (cache)
        {
            if (cache.containsKey(username.toLowerCase()))
            {
                listener.onLoaded(cache.get(username.toLowerCase()));
                return;
            }
            if (inFlight.contains(username.toLowerCase()))
            {
                // Already fetching - don't fire a second network request, just miss silently
                // for this caller. Whoever's already in flight will populate the cache soon,
                // and the next get() call for this username will pick it up.
                listener.onLoaded(null);
                return;
            }
            inFlight.add(username.toLowerCase());
        }

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.AVATAR_DOWNLOAD_REQUEST);
                request.setUsername(username);
                final Message response = NetworkManager.send(request);

                final Image image = (response != null && response.isSuccess() && response.getFileData() != null)
                    ? Toolkit.getDefaultToolkit().createImage(response.getFileData())
                    : null;

                synchronized (cache)
                {
                    cache.put(username.toLowerCase(), image);
                    inFlight.remove(username.toLowerCase());
                }

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run() { listener.onLoaded(image); }
                });
            }
        });
        worker.start();
    }

    /** Call after a successful upload so the next get() re-fetches instead of returning the stale cached image (or lack thereof). */
    public static void invalidate(String username)
    {
        if (username == null) return;
        synchronized (cache)
        {
            cache.remove(username.toLowerCase());
        }
    }
}
