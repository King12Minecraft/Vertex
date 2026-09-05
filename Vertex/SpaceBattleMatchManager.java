import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SpaceBattleMatchManager
 * -----------------------
 * Matchmaking for online Space Battle - 3 to 6 pilots, same shape as
 * RacingMatchManager (ranked placement needs at least 3 competitors).
 * Starts the moment MIN_PILOTS are waiting, taking up to MAX_PILOTS
 * if more happen to be queued at that instant.
 */
public class SpaceBattleMatchManager
{
    private static final String GAME_ID = "space-battle";
    private static final int MIN_PILOTS = 3;
    private static final int MAX_PILOTS = 6;

    private final List<ClientHandler> waitingPlayers = new ArrayList<ClientHandler>();
    private final Map<String, SpaceBattleMatch> activeMatches = new HashMap<String, SpaceBattleMatch>();
    private int nextMatchId = 1;
    private final GameHistoryManager gameHistoryManager;
    private final ChatManager chatManager;
    private final EconomyManager economyManager;
    private final AchievementManager achievementManager;
    private final LeaderboardManager leaderboardManager;

    public SpaceBattleMatchManager(GameHistoryManager gameHistoryManager, ChatManager chatManager,
                                    EconomyManager economyManager, AchievementManager achievementManager,
                                    LeaderboardManager leaderboardManager)
    {
        this.gameHistoryManager = gameHistoryManager;
        this.chatManager = chatManager;
        this.economyManager = economyManager;
        this.achievementManager = achievementManager;
        this.leaderboardManager = leaderboardManager;
    }

    public synchronized void findMatch(ClientHandler player)
    {
        if (waitingPlayers.contains(player))
        {
            return;
        }

        waitingPlayers.add(player);

        if (waitingPlayers.size() >= MIN_PILOTS)
        {
            int takeCount = Math.min(waitingPlayers.size(), MAX_PILOTS);
            List<ClientHandler> pilots = new ArrayList<ClientHandler>(waitingPlayers.subList(0, takeCount));
            for (int i = 0; i < takeCount; i++)
            {
                waitingPlayers.remove(0);
            }

            String matchId = "space-" + (nextMatchId++);
            long seed = System.nanoTime();

            SpaceBattleMatch match = new SpaceBattleMatch(matchId, pilots, seed, this, economyManager, achievementManager, leaderboardManager);
            activeMatches.put(matchId, match);
            for (int i = 0; i < pilots.size(); i++)
            {
                pilots.get(i).setCurrentSpaceBattleMatch(match);
                recordPlay(pilots.get(i));
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
