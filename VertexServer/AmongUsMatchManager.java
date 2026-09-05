import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AmongUsMatchManager
 * -------------------
 * Matchmaking for Among Us - 4 to 8 players, same immediate-start
 * pattern as RacingMatchManager (no grace-period timer). Hands off to
 * AmongUsMatch once enough players are waiting.
 */
public class AmongUsMatchManager
{
    private static final String GAME_ID = "among-us";
    private static final int MIN_PLAYERS = 4;
    private static final int MAX_PLAYERS = 8;

    private final List<ClientHandler> waitingPlayers = new ArrayList<ClientHandler>();
    private final Map<String, AmongUsMatch> activeMatches = new HashMap<String, AmongUsMatch>();
    private int nextMatchId = 1;
    private final GameHistoryManager gameHistoryManager;
    private final ChatManager chatManager;
    private final EconomyManager economyManager;

    public AmongUsMatchManager(GameHistoryManager gameHistoryManager, ChatManager chatManager, EconomyManager economyManager)
    {
        this.gameHistoryManager = gameHistoryManager;
        this.chatManager = chatManager;
        this.economyManager = economyManager;
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
            List<ClientHandler> matched = new ArrayList<ClientHandler>(waitingPlayers.subList(0, takeCount));
            for (int i = 0; i < takeCount; i++)
            {
                waitingPlayers.remove(0);
            }

            String matchId = "among-" + (nextMatchId++);
            AmongUsMatch match = new AmongUsMatch(matchId, matched, this, economyManager);
            activeMatches.put(matchId, match);
            for (int i = 0; i < matched.size(); i++)
            {
                matched.get(i).setCurrentAmongMatch(match);
                recordPlay(matched.get(i));
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
