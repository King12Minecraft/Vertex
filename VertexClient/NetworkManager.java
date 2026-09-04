import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

/**
 * NetworkManager
 * --------------
 * The client's connection to the Vertex server. Holds one persistent
 * socket (opened on first use, kept alive), exposes the current
 * ConnectionState, and supports THREE communication patterns:
 *
 *   1. Request/response (send) - "ask a question, block until the
 *      matching answer arrives." Used for Login, Create Account, Game
 *      List, purchases, and account changes. These CANNOT be queued
 *      offline - there's a real answer to wait for that can't be faked
 *      locally, so they simply fail with "can't reach the server" when
 *      offline, same as before.
 *   2. Fire-and-forget + async push (sendAsync + PushListener) - match
 *      actions, chat, group messages, play-history pings.
 *   3. Offline queueing for fire-and-forget messages: if sendAsync is
 *      called while disconnected, the message is queued instead of
 *      failing, and a background thread retries the connection every
 *      few seconds - once it succeeds, the whole queue flushes
 *      automatically. Callers get an optimistic "true" back either way,
 *      so e.g. a chat message clears its input field immediately and
 *      just delivers whenever the server's reachable again.
 *
 * LAN-only for now - plain sockets, no TLS. Credentials and match data
 * travel in clear text on the local network during this phase - a
 * known, deliberate limitation until an internet-play phase adds TLS.
 */
public class NetworkManager
{
    /** Implemented by any screen that wants server-pushed messages (match play, chat, etc.). */
    public interface PushListener
    {
        void onPush(Message message);
    }

    private static Socket socket;
    private static ObjectOutputStream out;
    private static ObjectInputStream in;
    private static ConnectionState state = ConnectionState.OFFLINE;
    private static final List<WeakReference<Runnable>> stateListeners = new ArrayList<WeakReference<Runnable>>();
    private static final BlockingQueue<Message> pendingResponses = new LinkedBlockingQueue<Message>();
    private static Thread listenerThread;
    private static final List<WeakReference<PushListener>> pushListeners = new ArrayList<WeakReference<PushListener>>();

    private static final List<Message> offlineQueue = new ArrayList<Message>();
    private static Thread reconnectThread;
    private static final int RECONNECT_INTERVAL_MS = 4000;

    private NetworkManager()
    {
        // Static utility class - never instantiated.
    }

    public static ConnectionState getState()
    {
        return state;
    }

    /** Number of fire-and-forget messages still waiting to sync once reconnected. */
    public static synchronized int getPendingSyncCount()
    {
        return offlineQueue.size();
    }

    public static void addListener(Runnable listener)
    {
        stateListeners.add(new WeakReference<Runnable>(listener));
    }

    /**
     * Registers a listener for server-pushed messages (match events,
     * chat, and future push types). Multiple listeners can be active at
     * once - each is responsible for filtering to the message types it
     * cares about, since e.g. a chat screen and a match window can both
     * be open simultaneously.
     */
    public static void addPushListener(PushListener listener)
    {
        pushListeners.add(new WeakReference<PushListener>(listener));
    }

    /** Still supported for callers that clean up explicitly - removes the matching listener (and, opportunistically, any already-dead ones found along the way). */
    public static void removePushListener(PushListener listener)
    {
        Iterator<WeakReference<PushListener>> it = pushListeners.iterator();
        while (it.hasNext())
        {
            PushListener existing = it.next().get();
            if (existing == null || existing == listener)
            {
                it.remove();
            }
        }
    }

    /**
     * Same reasoning as GameManager.notifyListeners(): connect()/send()
     * run on background threads (Login, Create Account, Game List, etc.
     * are all called from worker threads to keep the UI responsive), so
     * state changes must be dispatched onto the Swing event thread
     * before touching any UI (like ConnectionIndicator).
     */
    private static void setState(ConnectionState newState)
    {
        state = newState;
        Iterator<WeakReference<Runnable>> it = stateListeners.iterator();
        while (it.hasNext())
        {
            final Runnable listener = it.next().get();
            if (listener == null)
            {
                it.remove();
                continue;
            }
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run() { listener.run(); }
            });
        }
        ensureReconnectThreadRunning();
    }

    /** Connects to the configured server if not already connected. Safe to call repeatedly. */
    public static synchronized boolean connect()
    {
        if (socket != null && socket.isConnected() && !socket.isClosed())
        {
            return true;
        }

        setState(ConnectionState.CONNECTING);
        try
        {
            socket = new Socket(NetworkConfig.getServerHost(), NetworkConfig.getServerPort());
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            setState(ConnectionState.ONLINE);
            startListenerThread();
            flushOfflineQueue();
            return true;
        }
        catch (IOException e)
        {
            closeQuietly();
            setState(ConnectionState.OFFLINE);
            return false;
        }
    }

    /** Disconnects from whatever server this instance is currently talking to and connects fresh to a different one - used by the in-app server switcher. Deliberately explicit and synchronous rather than relying on the background auto-reconnect loop, which is built to retry the SAME server it just lost, not switch to a new one. */
    public static synchronized boolean switchServer(String host, int port)
    {
        closeQuietly();
        NetworkConfig.setServerHost(host);
        NetworkConfig.setServerPort(port);
        return connect();
    }

    /**
     * Background loop, started the first time the app touches the
     * network at all: while offline, keeps retrying the connection
     * every few seconds with no user action needed - this is what makes
     * "synchronize when the server's reachable again" actually happen
     * automatically rather than requiring a manual retry.
     */
    private static void ensureReconnectThreadRunning()
    {
        if (reconnectThread != null && reconnectThread.isAlive())
        {
            return;
        }
        reconnectThread = new Thread(new Runnable()
        {
            public void run()
            {
                while (true)
                {
                    try
                    {
                        Thread.sleep(RECONNECT_INTERVAL_MS);
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (state == ConnectionState.OFFLINE)
                    {
                        connect();
                    }
                }
            }
        });
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }

    private static void startListenerThread()
    {
        listenerThread = new Thread(new Runnable()
        {
            public void run()
            {
                try
                {
                    while (true)
                    {
                        Message incoming = (Message) in.readObject();
                        routeIncoming(incoming);
                    }
                }
                catch (IOException e)
                {
                    // Socket closed/dropped - normal on disconnect, nothing to log loudly.
                }
                catch (ClassNotFoundException e)
                {
                    System.err.println("Bad message from server: " + e.getMessage());
                }
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * The only message types that are ever the direct, synchronous reply
     * to a blocking send() call (see ClientHandler.handle() on the
     * server - every type here is exactly what some handleXxx() method
     * returns as its response). Every other MessageType - including any
     * added later for a new game or feature - is routed as a push by
     * default.
     *
     * This used to be inverted: routeIncoming kept its own hand-maintained
     * whitelist of PUSH types, which only ever covered the earliest phases
     * (generic match play, chat, wallet, challenges). Every push type added
     * since - every per-game MATCH_FOUND/UPDATE/OVER for Chess, Battleship,
     * RPS, Racing, Fight Arena, Among Us, plus achievements, friend status,
     * game invites, rematches, party updates, moderation notices, queue
     * counts, spectate-ended, tournament broadcasts - was missing from it.
     * A message of any of those types landed in pendingResponses instead of
     * reaching the right onPush listener, where it sat until some unrelated
     * blocking send() call polled it off the queue and treated it as if it
     * were the answer to a completely different request. That's a silent
     * failure mode - the caller just gets a default, empty-looking Message
     * back (success=false, no error text) - which is why things like the
     * satellite server list ("Could not load the satellite list") or other
     * screens could fail to load for no visible reason, especially with
     * several panels fetching data around the same time. Defaulting to
     * push instead means a type missing from this list fails open (still
     * delivered, just via onPush) rather than corrupting some other
     * in-flight request.
     *
     * TOURNAMENT_LIST_RESPONSE and TEAM_TOURNAMENT_LIST_RESPONSE are
     * deliberately left OUT of this set even though ClientHandler also
     * returns them as a direct reply to *_LIST_REQUEST: TournamentManager/
     * TeamTournamentManager also broadcast them unsolicited on every
     * roster change, and TournamentsPanel.onPush already renders them
     * either way - so routing them as push keeps the live broadcast
     * working, and the panel's own direct request just quietly times out
     * in the background after already being satisfied by the push.
     *
     * IMPORTANT - being IN this set only means "if a blocking send() is
     * ever waiting for one, this is the type it should get." It is NOT
     * safe for a type in here to ALSO be requested via sendAsync() from
     * somewhere else, expecting to catch the reply via onPush() instead:
     * pendingResponses is one shared FIFO queue with no per-request
     * correlation, so that reply would get offered into the queue same
     * as any real blocking reply, and whichever *unrelated* send() call
     * happens to poll() next would receive it instead of its own answer
     * - which then bumps that call's real answer to the next poller, and
     * so on, cascading. This actually happened (ACHIEVEMENTS_RESPONSE,
     * FRIEND_LIST_RESPONSE, LEADERBOARD_RESPONSE, REPLAY_LIST_RESPONSE,
     * REPLAY_RESPONSE, SPECTATABLE_MATCHES_RESPONSE were each also being
     * requested via sendAsync()+onPush from one dialog/panel apiece,
     * while every other caller of the same request type blocked on
     * send() as normal) and was the real cause behind GamesPanel's
     * "random" NullPointerException right after login - fixed by making
     * every caller of a type in this set use blocking send() consistently
     * (see AchievementsPanel, FriendPickerDialog, LeaderboardPanel,
     * ReplayBrowserDialog, SpectateDialog). If a genuinely-broadcast type
     * needs to go here in the future, give it the TOURNAMENT_LIST_RESPONSE
     * treatment above instead: leave it OUT and let onPush handle it.
     */
    private static final Set<MessageType> RESPONSE_TYPES = EnumSet.of(
        MessageType.LOGIN_RESPONSE,
        MessageType.CREATE_ACCOUNT_RESPONSE,
        MessageType.GAME_LIST_RESPONSE,
        MessageType.CHANGE_USERNAME_RESPONSE,
        MessageType.CHANGE_PASSWORD_RESPONSE,
        MessageType.GROUP_CREATE_RESPONSE,
        MessageType.SHOP_ITEMS_RESPONSE,
        MessageType.PURCHASE_RESPONSE,
        MessageType.CHALLENGES_RESPONSE,
        MessageType.GAME_HISTORY_RESPONSE,
        MessageType.ONLINE_USERS_RESPONSE,
        MessageType.SELECT_COLOR_RESPONSE,
        MessageType.SELECT_BADGE_RESPONSE,
        MessageType.ADMIN_PLAYER_LIST_RESPONSE,
        MessageType.FRIEND_REQUEST_SEND_RESPONSE,
        MessageType.FRIEND_LIST_RESPONSE,
        MessageType.TRANSACTION_HISTORY_RESPONSE,
        MessageType.MOD_ACTION_RESPONSE,
        MessageType.REPORT_SUBMIT_RESPONSE,
        MessageType.REPORT_LIST_RESPONSE,
        MessageType.LEADERBOARD_RESPONSE,
        MessageType.ACHIEVEMENTS_RESPONSE,
        MessageType.SYNC_AUTH_RESPONSE,
        MessageType.SYNC_PUSH_RESPONSE,
        MessageType.SATELLITE_LIST_RESPONSE,
        MessageType.FRIEND_LOCATION_RESPONSE,
        MessageType.SPECTATABLE_MATCHES_RESPONSE,
        MessageType.REPLAY_LIST_RESPONSE,
        MessageType.REPLAY_RESPONSE,
        MessageType.FEEDBACK_SUBMIT_RESPONSE,
        MessageType.FEEDBACK_LIST_RESPONSE,
        MessageType.CLIENT_VERSION_CHECK_RESPONSE,
        MessageType.CLIENT_UPDATE_DOWNLOAD_RESPONSE,
        MessageType.CUSTOM_GAME_UPLOAD_RESPONSE,
        MessageType.CUSTOM_GAME_LIST_RESPONSE,
        MessageType.CUSTOM_GAME_DOWNLOAD_RESPONSE,
        MessageType.CUSTOM_GAME_DELETE_RESPONSE);

    private static void routeIncoming(Message message)
    {
        MessageType type = message.getType();
        boolean isPush = !RESPONSE_TYPES.contains(type);

        if (isPush)
        {
            Iterator<WeakReference<PushListener>> it = pushListeners.iterator();
            while (it.hasNext())
            {
                PushListener listener = it.next().get();
                if (listener == null)
                {
                    it.remove();
                    continue;
                }
                listener.onPush(message);
            }
        }
        else
        {
            pendingResponses.offer(message);
        }
    }

    /**
     * Sends a request and blocks (up to 10 seconds) for the matching
     * response. Use for true request/response exchanges: Login, Create
     * Account, Game List, purchases, account changes. Returns null if
     * the server couldn't be reached or didn't answer in time - these
     * cannot be queued offline, since there's a real answer to wait for.
     */
    public static synchronized Message send(Message request)
    {
        if (!connect())
        {
            return null;
        }

        try
        {
            out.writeObject(request);
            out.flush();
            return pendingResponses.poll(10, TimeUnit.SECONDS);
        }
        catch (IOException e)
        {
            handleDisconnect();
            return null;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Sends a fire-and-forget request - match actions, chat, group
     * messages, play-history pings. If currently offline, the message
     * is QUEUED instead of failing, and delivered automatically once
     * reconnected (see the background reconnect loop above). Always
     * returns true when queued, so callers proceed optimistically -
     * e.g. a chat message's input field clears right away even if it's
     * actually still waiting to send.
     */
    public static synchronized boolean sendAsync(Message request)
    {
        if (!connect())
        {
            offlineQueue.add(request);
            return true;
        }

        try
        {
            out.writeObject(request);
            out.flush();
            return true;
        }
        catch (IOException e)
        {
            handleDisconnect();
            offlineQueue.add(request);
            return true;
        }
    }

    /** Called right after a successful (re)connect - sends everything that piled up while offline. */
    private static void flushOfflineQueue()
    {
        if (offlineQueue.isEmpty())
        {
            return;
        }

        List<Message> toSend = new ArrayList<Message>(offlineQueue);
        offlineQueue.clear();

        for (int i = 0; i < toSend.size(); i++)
        {
            try
            {
                out.writeObject(toSend.get(i));
                out.flush();
            }
            catch (IOException e)
            {
                // Dropped again mid-flush - put this one and everything
                // after it back in the queue; the reconnect loop will
                // pick it up and try the whole thing again later.
                for (int j = i; j < toSend.size(); j++)
                {
                    offlineQueue.add(toSend.get(j));
                }
                handleDisconnect();
                return;
            }
        }
    }

    private static void handleDisconnect()
    {
        setState(ConnectionState.RECONNECTING);
        closeQuietly();

        if (!connect())
        {
            setState(ConnectionState.OFFLINE);
        }
    }

    private static void closeQuietly()
    {
        try
        {
            if (socket != null)
            {
                socket.close();
            }
        }
        catch (IOException ignored)
        {
            // Nothing to do - we're already tearing this connection down.
        }
        socket = null;
        out = null;
        in = null;
    }
}
