package games;

import net.ClientHandler;
import net.Message;
import net.MessageType;
import economy.EconomyManager;
import economy.GameHistoryManager;
import economy.LeaderboardManager;
import social.ChatManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AirHockeyMatchManager
 * ----------------------
 * Matchmaking for Air Hockey - same shape as CheckersMatchManager.
 */
public class AirHockeyMatchManager
{
    private static final String GAME_ID = "air-hockey";

    private final List<ClientHandler> waitingPlayers = new ArrayList<ClientHandler>();
    private final Map<String, AirHockeyMatch> activeMatches = new HashMap<String, AirHockeyMatch>();
    private int nextMatchId = 1;
    private final EconomyManager economyManager;
    private final GameHistoryManager gameHistoryManager;
    private final ChatManager chatManager;
    private final LeaderboardManager leaderboardManager;

    public AirHockeyMatchManager(EconomyManager economyManager, GameHistoryManager gameHistoryManager,
                                  ChatManager chatManager, LeaderboardManager leaderboardManager)
    {
        this.economyManager = economyManager;
        this.gameHistoryManager = gameHistoryManager;
        this.chatManager = chatManager;
        this.leaderboardManager = leaderboardManager;
    }

    public synchronized void findMatch(ClientHandler player)
    {
        if (waitingPlayers.contains(player))
        {
            return;
        }

        if (!waitingPlayers.isEmpty())
        {
            ClientHandler opponent = waitingPlayers.remove(0);
            String matchId = "airhockey-" + (nextMatchId++);
            AirHockeyMatch match = new AirHockeyMatch(matchId, opponent, player, this, economyManager, leaderboardManager);
            activeMatches.put(matchId, match);
            opponent.setCurrentAirHockeyMatch(match);
            player.setCurrentAirHockeyMatch(match);
            match.start();

            recordPlay(opponent);
            recordPlay(player);

            broadcastQueueCount();
        }
        else
        {
            waitingPlayers.add(player);
            broadcastQueueCount();
        }
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
