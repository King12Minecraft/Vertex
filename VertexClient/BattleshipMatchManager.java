import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BattleshipMatchManager
 * ----------------------
 * 1v1 matchmaking for Battleship - same pattern as
 * ChessMatchManager/MatchManager: pairs the first two waiting players
 * immediately.
 */
public class BattleshipMatchManager
{
    private static final String GAME_ID = "battleship";

    private final List<ClientHandler> waitingPlayers = new ArrayList<ClientHandler>();
    private final Map<String, BattleshipMatch> activeMatches = new HashMap<String, BattleshipMatch>();
    private int nextMatchId = 1;
    private final GameHistoryManager gameHistoryManager;
    private final ChatManager chatManager;
    private final LeaderboardManager leaderboardManager;
    private final ReplayManager replayManager;

    public BattleshipMatchManager(GameHistoryManager gameHistoryManager, ChatManager chatManager, LeaderboardManager leaderboardManager, ReplayManager replayManager)
    {
        this.gameHistoryManager = gameHistoryManager;
        this.chatManager = chatManager;
        this.leaderboardManager = leaderboardManager;
        this.replayManager = replayManager;
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
            String matchId = "battleship-" + (nextMatchId++);
            BattleshipMatch match = new BattleshipMatch(matchId, opponent, player, this, leaderboardManager, replayManager);
            activeMatches.put(matchId, match);
            opponent.setCurrentBattleshipMatch(match);
            player.setCurrentBattleshipMatch(match);
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
        String matchId = "battleship-" + (nextMatchId++);
        BattleshipMatch match = new BattleshipMatch(matchId, playerA, playerB, this, leaderboardManager, replayManager);
        activeMatches.put(matchId, match);
        playerA.setCurrentBattleshipMatch(match);
        playerB.setCurrentBattleshipMatch(match);
        match.start();

        recordPlay(playerA);
        recordPlay(playerB);
    }

    /** Every currently-live match, "matchId|playerA|playerB" per entry - for the spectator browser. */
    public synchronized List<String> listSpectatableMatches()
    {
        List<String> result = new ArrayList<String>();
        for (Map.Entry<String, BattleshipMatch> entry : activeMatches.entrySet())
        {
            BattleshipMatch match = entry.getValue();
            result.add(entry.getKey() + "|" + match.getPlayerAUsername() + "|" + match.getPlayerBUsername());
        }
        return result;
    }

    public synchronized void addSpectator(ClientHandler spectator, String matchId)
    {
        BattleshipMatch match = activeMatches.get(matchId);
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
