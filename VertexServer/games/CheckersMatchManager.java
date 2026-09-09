package games;
import net.MessageType;
import net.Message;
import economy.LeaderboardManager;
import social.ChatManager;
import economy.GameHistoryManager;
import economy.EconomyManager;
import net.ClientHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CheckersMatchManager
 * --------------------
 * Matchmaking for Checkers - same shape as ConnectFourMatchManager.
 */
public class CheckersMatchManager
{
    private static final String GAME_ID = "checkers";

    private final List<ClientHandler> waitingPlayers = new ArrayList<ClientHandler>();
    private final Map<String, CheckersMatch> activeMatches = new HashMap<String, CheckersMatch>();
    private int nextMatchId = 1;
    private final EconomyManager economyManager;
    private final GameHistoryManager gameHistoryManager;
    private final ChatManager chatManager;
    private final LeaderboardManager leaderboardManager;

    public CheckersMatchManager(EconomyManager economyManager, GameHistoryManager gameHistoryManager,
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
            String matchId = "checkers-" + (nextMatchId++);
            CheckersMatch match = new CheckersMatch(matchId, opponent, player, this, economyManager, leaderboardManager);
            activeMatches.put(matchId, match);
            opponent.setCurrentCheckersMatch(match);
            player.setCurrentCheckersMatch(match);
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
