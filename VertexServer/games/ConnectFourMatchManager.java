package games;
import economy.LeaderboardManager;
import social.ChatManager;
import economy.GameHistoryManager;
import economy.EconomyManager;
import net.ClientHandler;

/**
 * ConnectFourMatchManager
 * -----------------------
 * Matchmaking for Connect Four - thin wrapper over the shared
 * MatchmakingKernel (see its javadoc), the second adopter after
 * CheckersMatchManager. matchId keeps its original "connect4-N" prefix
 * (distinct from GAME_ID "connect-four") via MatchmakingKernel's
 * matchIdPrefix parameter - a cosmetic detail preserved exactly rather
 * than silently changed.
 */
public class ConnectFourMatchManager
{
    private static final String GAME_ID = "connect-four";
    private static final String MATCH_ID_PREFIX = "connect4";

    private final MatchmakingKernel<ConnectFourMatch> kernel;

    public ConnectFourMatchManager(final EconomyManager economyManager, GameHistoryManager gameHistoryManager,
                                    ChatManager chatManager, final LeaderboardManager leaderboardManager)
    {
        kernel = new MatchmakingKernel<ConnectFourMatch>(GAME_ID, MATCH_ID_PREFIX, gameHistoryManager, chatManager,
            new MatchmakingKernel.PairHandler<ConnectFourMatch>()
            {
                public ConnectFourMatch pair(String matchId, ClientHandler playerA, ClientHandler playerB)
                {
                    ConnectFourMatch match = new ConnectFourMatch(matchId, playerA, playerB, ConnectFourMatchManager.this,
                        economyManager, leaderboardManager, kernel.getReconnectRegistry());
                    match.start();
                    return match;
                }

                public void attach(ClientHandler handler, ConnectFourMatch match)
                {
                    handler.setCurrentConnectFourMatch(match);
                }
            });
    }

    public ReconnectRegistry getReconnectRegistry() { return kernel.getReconnectRegistry(); }

    public void findMatch(ClientHandler player)
    {
        kernel.findMatch(player);
    }

    public void cancelWaiting(ClientHandler player)
    {
        kernel.cancelWaiting(player);
    }

    public void endMatch(String matchId)
    {
        kernel.endMatch(matchId);
    }

    public int getQueueCount()
    {
        return kernel.getQueueCount();
    }
}
