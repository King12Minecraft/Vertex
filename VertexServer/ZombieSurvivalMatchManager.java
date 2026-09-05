import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ZombieSurvivalMatchManager
 * --------------------------
 * Matchmaking for online Zombie Survival - 2 to 4 players. Same shape
 * as RacingMatchManager: starts the moment MIN_PLAYERS are waiting,
 * taking up to MAX_PLAYERS if more happen to be queued at that
 * instant, no grace-period timer for stragglers.
 */
public class ZombieSurvivalMatchManager
{
    private static final String GAME_ID = "zombie-survival";
    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 4;

    private final List<ClientHandler> waitingPlayers = new ArrayList<ClientHandler>();
    private final Map<String, ZombieSurvivalMatch> activeMatches = new HashMap<String, ZombieSurvivalMatch>();
    private int nextMatchId = 1;
    private final GameHistoryManager gameHistoryManager;
    private final ChatManager chatManager;
    private final EconomyManager economyManager;
    private final AchievementManager achievementManager;
    private final LeaderboardManager leaderboardManager;

    public ZombieSurvivalMatchManager(GameHistoryManager gameHistoryManager, ChatManager chatManager,
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

        if (waitingPlayers.size() >= MIN_PLAYERS)
        {
            int takeCount = Math.min(waitingPlayers.size(), MAX_PLAYERS);
            List<ClientHandler> group = new ArrayList<ClientHandler>(waitingPlayers.subList(0, takeCount));
            for (int i = 0; i < takeCount; i++)
            {
                waitingPlayers.remove(0);
            }

            String matchId = "zombie-" + (nextMatchId++);
            long seed = System.nanoTime();

            ZombieSurvivalMatch match = new ZombieSurvivalMatch(matchId, group, seed, this, economyManager, achievementManager, leaderboardManager);
            activeMatches.put(matchId, match);
            for (int i = 0; i < group.size(); i++)
            {
                group.get(i).setCurrentZombieMatch(match);
                recordPlay(group.get(i));
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
