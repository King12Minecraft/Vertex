import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RockPaperScissorsMatchManager
 * -----------------------------
 * 1v1 matchmaking for Rock Paper Scissors - same immediate-pairing
 * pattern as MatchManager/ChessMatchManager.
 */
public class RockPaperScissorsMatchManager
{
    private static final String GAME_ID = "rock-paper-scissors";

    private final List<ClientHandler> waitingPlayers = new ArrayList<ClientHandler>();
    private final Map<String, RockPaperScissorsMatch> activeMatches = new HashMap<String, RockPaperScissorsMatch>();
    private int nextMatchId = 1;
    private final GameHistoryManager gameHistoryManager;
    private final ChatManager chatManager;
    private final LeaderboardManager leaderboardManager;
    private final ReplayManager replayManager;
    private final EconomyManager economyManager;

    public RockPaperScissorsMatchManager(GameHistoryManager gameHistoryManager, ChatManager chatManager, LeaderboardManager leaderboardManager, ReplayManager replayManager, EconomyManager economyManager)
    {
        this.gameHistoryManager = gameHistoryManager;
        this.chatManager = chatManager;
        this.leaderboardManager = leaderboardManager;
        this.replayManager = replayManager;
        this.economyManager = economyManager;
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
            String matchId = "rps-" + (nextMatchId++);
            RockPaperScissorsMatch match = new RockPaperScissorsMatch(matchId, opponent, player, this, leaderboardManager, replayManager, economyManager);
            activeMatches.put(matchId, match);
            opponent.setCurrentRpsMatch(match);
            player.setCurrentRpsMatch(match);
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

    public synchronized void createDirectMatch(ClientHandler playerA, ClientHandler playerB)
    {
        String matchId = "rps-" + (nextMatchId++);
        RockPaperScissorsMatch match = new RockPaperScissorsMatch(matchId, playerA, playerB, this, leaderboardManager, replayManager, economyManager);
        activeMatches.put(matchId, match);
        playerA.setCurrentRpsMatch(match);
        playerB.setCurrentRpsMatch(match);
        match.start();

        recordPlay(playerA);
        recordPlay(playerB);
    }

    /** Every currently-live match, "matchId|playerA|playerB" per entry - for the spectator browser. */
    public synchronized List<String> listSpectatableMatches()
    {
        List<String> result = new ArrayList<String>();
        for (Map.Entry<String, RockPaperScissorsMatch> entry : activeMatches.entrySet())
        {
            RockPaperScissorsMatch match = entry.getValue();
            result.add(entry.getKey() + "|" + match.getPlayerAUsername() + "|" + match.getPlayerBUsername());
        }
        return result;
    }

    public synchronized void addSpectator(ClientHandler spectator, String matchId)
    {
        RockPaperScissorsMatch match = activeMatches.get(matchId);
        if (match != null)
        {
            match.addSpectator(spectator);
        }
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
