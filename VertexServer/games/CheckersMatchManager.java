package games;
import economy.LeaderboardManager;
import social.ChatManager;
import economy.GameHistoryManager;
import economy.EconomyManager;
import net.ClientHandler;

/**
 * CheckersMatchManager
 * --------------------
 * Matchmaking for Checkers - thin wrapper over the shared MatchmakingKernel
 * (see its javadoc; this is the first adopter, proving the pattern
 * generalizes). Supplies the two things that actually vary per game:
 * how to construct+start a CheckersMatch, and how to attach it to a
 * ClientHandler.
 */
public class CheckersMatchManager
{
    private static final String GAME_ID = "checkers";

    private final MatchmakingKernel<CheckersMatch> kernel;

    public CheckersMatchManager(final EconomyManager economyManager, GameHistoryManager gameHistoryManager,
                                 ChatManager chatManager, final LeaderboardManager leaderboardManager)
    {
        kernel = new MatchmakingKernel<CheckersMatch>(GAME_ID, gameHistoryManager, chatManager,
            new MatchmakingKernel.PairHandler<CheckersMatch>()
            {
                public CheckersMatch pair(String matchId, ClientHandler playerA, ClientHandler playerB)
                {
                    CheckersMatch match = new CheckersMatch(matchId, playerA, playerB, CheckersMatchManager.this,
                        economyManager, leaderboardManager, kernel.getReconnectRegistry());
                    match.start();
                    return match;
                }

                public void attach(ClientHandler handler, CheckersMatch match)
                {
                    handler.setCurrentCheckersMatch(match);
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
