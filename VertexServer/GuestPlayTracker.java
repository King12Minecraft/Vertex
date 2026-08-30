import java.util.ArrayList;
import java.util.List;

/**
 * GuestPlayTracker
 * -----------------
 * Snake can be played without being logged in at all (e.g. from the
 * "Play Offline" button on the login screen). Since the server can't
 * attribute a play - or its score-based coin reward - to any account
 * without a logged-in session, GAME_PLAYED_REQUEST would otherwise
 * just be silently dropped (ClientHandler only records/rewards a play
 * if it knows who's logged in). This queues those guest plays (with
 * their scores) locally instead, and flushes them to the server the
 * moment a real login succeeds - "played offline, coins/history
 * synced once you're back and logged in."
 */
public class GuestPlayTracker
{
    private static class QueuedPlay
    {
        final String gameId;
        final int score;

        QueuedPlay(String gameId, int score)
        {
            this.gameId = gameId;
            this.score = score;
        }
    }

    private static final List<QueuedPlay> queuedPlays = new ArrayList<QueuedPlay>();

    private GuestPlayTracker()
    {
        // Static utility class - never instantiated.
    }

    public static synchronized void recordGuestPlay(String gameId, int score)
    {
        if (gameId != null)
        {
            queuedPlays.add(new QueuedPlay(gameId, score));
        }
    }

    public static synchronized int getPendingCount()
    {
        return queuedPlays.size();
    }

    /** Call once login succeeds - attributes any guest plays (and their rewards) to the now-known account. */
    public static synchronized void flushToServer()
    {
        for (int i = 0; i < queuedPlays.size(); i++)
        {
            QueuedPlay play = queuedPlays.get(i);
            Message request = new Message();
            request.setType(MessageType.GAME_PLAYED_REQUEST);
            request.setGameId(play.gameId);
            request.setScore(play.score);
            NetworkManager.sendAsync(request);
        }
        queuedPlays.clear();
    }
}
