import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ChessMatchManager
 * -----------------
 * 1v1 matchmaking for Chess - same pattern as MatchManager
 * (Tic-Tac-Toe): pairs the first two waiting players immediately.
 */
public class ChessMatchManager
{
    private static final String GAME_ID = "chess";

    private final List<ClientHandler> waitingPlayers = new ArrayList<ClientHandler>();
    private final Map<String, ChessMatch> activeMatches = new HashMap<String, ChessMatch>();
    private int nextMatchId = 1;
    private final GameHistoryManager gameHistoryManager;
    private final ChatManager chatManager;
    private final LeaderboardManager leaderboardManager;
    private final ReplayManager replayManager;
    private final EconomyManager economyManager;

    public ChessMatchManager(GameHistoryManager gameHistoryManager, ChatManager chatManager, LeaderboardManager leaderboardManager, ReplayManager replayManager, EconomyManager economyManager)
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
            String matchId = "chess-" + (nextMatchId++);
            ChessMatch match = new ChessMatch(matchId, opponent, player, this, leaderboardManager, replayManager, economyManager);
            activeMatches.put(matchId, match);
            opponent.setCurrentChessMatch(match);
            player.setCurrentChessMatch(match);
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

    /** Pairs two specific players directly, bypassing the public queue entirely - used for rematches (and the same shape Tournament uses for Battleship/RPS brackets). Neither player needs to already be waiting. */
    public synchronized void createDirectMatch(ClientHandler playerA, ClientHandler playerB)
    {
        String matchId = "chess-" + (nextMatchId++);
        ChessMatch match = new ChessMatch(matchId, playerA, playerB, this, leaderboardManager, replayManager, economyManager);
        activeMatches.put(matchId, match);
        playerA.setCurrentChessMatch(match);
        playerB.setCurrentChessMatch(match);
        match.start();

        recordPlay(playerA);
        recordPlay(playerB);
    }

    public synchronized void endMatch(String matchId)
    {
        activeMatches.remove(matchId);
    }

    /** Every currently-live match, "matchId|whiteUsername|blackUsername" per entry - for the spectator browser. */
    public synchronized List<String> listSpectatableMatches()
    {
        List<String> result = new ArrayList<String>();
        for (Map.Entry<String, ChessMatch> entry : activeMatches.entrySet())
        {
            ChessMatch match = entry.getValue();
            result.add(entry.getKey() + "|" + match.getWhiteUsername() + "|" + match.getBlackUsername());
        }
        return result;
    }

    public synchronized void addSpectator(ClientHandler spectator, String matchId)
    {
        ChessMatch match = activeMatches.get(matchId);
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
