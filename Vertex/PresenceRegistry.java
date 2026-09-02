import java.util.HashMap;
import java.util.Map;

/**
 * PresenceRegistry
 * ------------------
 * Only meaningful on the main server - tracks which server address
 * (if any) each account is CURRENTLY online at, across every
 * satellite that's reporting to this main. Deliberately in-memory
 * only, never persisted: presence is inherently a live, transient
 * fact, not something that should survive a restart (a server
 * restarting doesn't mean the accounts that were online are still
 * online - they'd need to reconnect and re-report anyway).
 *
 * Populated by PRESENCE_UPDATE messages (satellites report their own
 * players logging in/out), read by FRIEND_LOCATION_REQUEST (a client
 * asking "where is my friend, if anywhere" so it can offer a Join
 * button).
 */
public class PresenceRegistry
{
    private final Map<String, String> addressByUsername = new HashMap<String, String>();

    public synchronized void setOnline(String username, String address)
    {
        addressByUsername.put(username, address);
    }

    public synchronized void setOffline(String username)
    {
        addressByUsername.remove(username);
    }

    /** Null if this account isn't known to be online anywhere right now. */
    public synchronized String getAddress(String username)
    {
        return addressByUsername.get(username);
    }
}
