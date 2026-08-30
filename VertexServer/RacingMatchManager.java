import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RacingMatchManager
 * ------------------
 * Matchmaking for online Racing - now N-player (3 to 6 racers) rather
 * than 1v1, since ranked 1st/2nd/3rd placement needs at least 3
 * competitors. Starts a race the moment MIN_RACERS are waiting, taking
 * up to MAX_RACERS if more happen to be queued at that instant - no
 * grace-period timer to let stragglers join, which keeps this simple
 * (a scheduled countdown would need its own timer thread; this doesn't
 * need one). The tradeoff: someone queuing right as a race of exactly
 * 3 starts won't get pulled in and has to wait for the next one.
 */
public class RacingMatchManager
{
    private static final String GAME_ID = "racing";
    private static final int MIN_RACERS = 3;
    private static final int MAX_RACERS = 6;

    private final List<ClientHandler> waitingPlayers = new ArrayList<ClientHandler>();
    private final Map<String, RacingMatch> activeMatches = new HashMap<String, RacingMatch>();
    private int nextMatchId = 1;
    private final GameHistoryManager gameHistoryManager;
    private final ChatManager chatManager;
    private final EconomyManager economyManager;
    private final AchievementManager achievementManager;

    public RacingMatchManager(GameHistoryManager gameHistoryManager, ChatManager chatManager, EconomyManager economyManager, AchievementManager achievementManager)
    {
        this.gameHistoryManager = gameHistoryManager;
        this.chatManager = chatManager;
        this.economyManager = economyManager;
        this.achievementManager = achievementManager;
    }

    public synchronized void findMatch(ClientHandler player)
    {
        if (waitingPlayers.contains(player))
        {
            return;
        }

        waitingPlayers.add(player);

        if (waitingPlayers.size() >= MIN_RACERS)
        {
            int takeCount = Math.min(waitingPlayers.size(), MAX_RACERS);
            List<ClientHandler> racers = new ArrayList<ClientHandler>(waitingPlayers.subList(0, takeCount));
            for (int i = 0; i < takeCount; i++)
            {
                waitingPlayers.remove(0);
            }

            String matchId = "race-" + (nextMatchId++);
            long seed = System.nanoTime();

            RacingMatch match = new RacingMatch(matchId, racers, seed, this, economyManager, achievementManager);
            activeMatches.put(matchId, match);
            for (int i = 0; i < racers.size(); i++)
            {
                racers.get(i).setCurrentRacingMatch(match);
                recordPlay(racers.get(i));
            }
            match.start();
        }

        broadcastQueueCount();
    }

    private void recordPlay(ClientHandler handler)
    {
        if (handler.getLoggedInUsername() != null && handler.getAccountId() != null)
        {
            gameHistoryManager.recordPlay(handler.getAccountId(), GAME_ID);
        }
    }

    public synchronized void cancelWaiting(ClientHandler player)
    {
        boolean removed = waitingPlayers.remove(player);
        if (removed)
        {
            broadcastQueueCount();
        }
    }

    public synchronized void endMatch(String matchId)
    {
        activeMatches.remove(matchId);
    }

    public synchronized int getQueueCount()
    {
        return waitingPlayers.size();
    }

    private void broadcastQueueCount()
    {
        Message msg = new Message();
        msg.setType(MessageType.QUEUE_UPDATE);
        msg.setQueueGameId(GAME_ID);
        msg.setQueueCount(waitingPlayers.size());
        chatManager.broadcastToAll(msg);
    }
}
