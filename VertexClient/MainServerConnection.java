import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

/**
 * MainServerConnection
 * ----------------------
 * A satellite server's outbound connection to the main/canonical
 * server - not client-facing, this is server-to-server. Uses the same
 * Message/MessageType protocol and raw socket + ObjectStream approach
 * as the ordinary client<->server connection, since a server acting
 * as a "client" of another server needs nothing more exotic than that.
 *
 * Deliberately opens and closes a fresh socket per call rather than
 * holding a persistent connection - sync operations (login-time
 * auth-check, post-match/purchase push) are infrequent enough that
 * connection-reuse efficiency doesn't matter, and a fresh connection
 * per call means one failed sync attempt can never leave a stale,
 * half-broken socket sitting around to trip up the next one.
 *
 * Every method here is best-effort: if the main server is unreachable,
 * these return null/false rather than throwing, so a satellite server
 * can keep running its own local games even while temporarily unable
 * to reach the main server - see PART 3 (push-sync queueing) for how
 * satellites handle that gap once wired up.
 */
public class MainServerConnection
{
    private final String mainHost;
    private final int mainPort;

    public MainServerConnection(String mainHost, int mainPort)
    {
        this.mainHost = mainHost;
        this.mainPort = mainPort;
    }

    /** Forwards a login attempt to the main server for verification - the satellite never needs to know the canonical password hash itself, only whether the main server accepts these credentials. On success, returns the main server's SYNC_AUTH_RESPONSE (carrying the canonical Account + ratings the satellite should cache locally). Returns null on any failure - wrong credentials, unreachable main server, or a malformed reply. */
    public Message authenticateAgainstMain(String username, String password)
    {
        Socket socket = null;
        try
        {
            socket = new Socket(mainHost, mainPort);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            Message request = new Message();
            request.setType(MessageType.SYNC_AUTH_REQUEST);
            request.setUsername(username);
            request.setPassword(password);
            out.writeObject(request);
            out.flush();

            Object response = in.readObject();
            if (response instanceof Message && ((Message) response).getType() == MessageType.SYNC_AUTH_RESPONSE)
            {
                return (Message) response;
            }
            return null;
        }
        catch (IOException e)
        {
            return null;
        }
        catch (ClassNotFoundException e)
        {
            return null;
        }
        finally
        {
            closeQuietly(socket);
        }
    }

    /** Pushes a locally-changed account (new coin total, updated ratings, newly-unlocked achievements) up to the main server so it stays the canonical source of truth. Fire-and-forget from the satellite's point of view - a failed push here means the change stays queued locally for retry (PART 3), not lost, but this call itself just reports whether THIS attempt succeeded. */
    public boolean pushToMain(Account account, List<String> ratings, List<String> unlockedAchievementIds)
    {
        Socket socket = null;
        try
        {
            socket = new Socket(mainHost, mainPort);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            Message request = new Message();
            request.setType(MessageType.SYNC_PUSH_REQUEST);
            request.setSyncAccount(account);
            request.setSyncRatings(ratings);
            request.setUnlockedAchievementIds(unlockedAchievementIds);
            out.writeObject(request);
            out.flush();

            Object response = in.readObject();
            return response instanceof Message && ((Message) response).getType() == MessageType.SYNC_PUSH_RESPONSE
                && ((Message) response).isSuccess();
        }
        catch (IOException e)
        {
            return false;
        }
        catch (ClassNotFoundException e)
        {
            return false;
        }
        finally
        {
            closeQuietly(socket);
        }
    }

    /** Sent once, when a satellite server starts up - tells main "I exist, here's the port other players actually connect to me on" so an admin can see every known satellite in one place (SatelliteRegistry). Best-effort like everything else here: if main is unreachable at startup, the satellite just isn't registered yet, and will get picked up on its next successful sync push instead - not calling this again automatically is a known, acceptable gap, since it's a low-stakes "nice to have visible" feature, not something correctness depends on. */
    public boolean registerAsSatellite(int myOwnPort)
    {
        Socket socket = null;
        try
        {
            socket = new Socket(mainHost, mainPort);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            Message request = new Message();
            request.setType(MessageType.SATELLITE_REGISTER_REQUEST);
            request.setSatellitePort(myOwnPort);
            out.writeObject(request);
            out.flush();

            in.readObject();
            return true;
        }
        catch (IOException e)
        {
            return false;
        }
        catch (ClassNotFoundException e)
        {
            return false;
        }
        finally
        {
            closeQuietly(socket);
        }
    }

    private void closeQuietly(Socket socket)
    {
        if (socket != null)
        {
            try { socket.close(); } catch (IOException e) { /* already closing, nothing to do */ }
        }
    }
}
