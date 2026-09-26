package games;
import economy.LeaderboardManager;
import social.ChatManager;
import economy.GameHistoryManager;
import economy.EconomyManager;
import net.ClientHandler;

/**
 * ReversiMatchManager
 * -------------------
 * Matchmaking for Reversi - thin wrapper over the shared MatchmakingKernel
 * (see its javadoc), converted from a bespoke hand-rolled queue to this
 * kernel wrapper while adding reconnect support (which the kernel now
 * supplies for free to every adopter - see MatchmakingKernel.getReconnectRegistry()),
 * same shape as CheckersMatchManager/ConnectFourMatchManager.
 */
public class ReversiMatchManager
{
    private static final String GAME_ID = "reversi";

    private final MatchmakingKernel<ReversiMatch> kernel;

    public ReversiMatchManager(final EconomyManager economyManager, GameHistoryManager gameHistoryManager,
                                ChatManager chatManager, final LeaderboardManager leaderboardManager)
    {
        kernel = new MatchmakingKernel<ReversiMatch>(GAME_ID, gameHistoryManager, chatManager,
            new MatchmakingKernel.PairHandler<ReversiMatch>()
            {
                public ReversiMatch pair(String matchId, ClientHandler playerA, ClientHandler playerB)
                {
                    ReversiMatch match = new ReversiMatch(matchId, playerA, playerB, ReversiMatchManager.this,
                        economyManager, leaderboardManager, kernel.getReconnectRegistry());
                    match.start();
                    return match;
                }

                public void attach(ClientHandler handler, ReversiMatch match)
                {
                    handler.setCurrentReversiMatch(match);
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
