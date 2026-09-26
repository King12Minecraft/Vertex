package games;

import net.ClientHandler;
import economy.EconomyManager;
import economy.GameHistoryManager;
import economy.LeaderboardManager;
import social.ChatManager;

/**
 * DotsAndBoxesMatchManager
 * -------------------------
 * Matchmaking for Dots and Boxes - thin wrapper over the shared
 * MatchmakingKernel (see its javadoc), converted from a bespoke
 * hand-rolled queue to this kernel wrapper while adding reconnect
 * support (which the kernel now supplies for free to every adopter -
 * see MatchmakingKernel.getReconnectRegistry()). matchId keeps its
 * original "dots-N" prefix (distinct from GAME_ID "dots-and-boxes")
 * via MatchmakingKernel's matchIdPrefix parameter, same as
 * ConnectFourMatchManager's "connect4-N" vs "connect-four".
 */
public class DotsAndBoxesMatchManager
{
    private static final String GAME_ID = "dots-and-boxes";
    private static final String MATCH_ID_PREFIX = "dots";

    private final MatchmakingKernel<DotsAndBoxesMatch> kernel;

    public DotsAndBoxesMatchManager(final EconomyManager economyManager, GameHistoryManager gameHistoryManager,
                                     ChatManager chatManager, final LeaderboardManager leaderboardManager)
    {
        kernel = new MatchmakingKernel<DotsAndBoxesMatch>(GAME_ID, MATCH_ID_PREFIX, gameHistoryManager, chatManager,
            new MatchmakingKernel.PairHandler<DotsAndBoxesMatch>()
            {
                public DotsAndBoxesMatch pair(String matchId, ClientHandler playerA, ClientHandler playerB)
                {
                    DotsAndBoxesMatch match = new DotsAndBoxesMatch(matchId, playerA, playerB, DotsAndBoxesMatchManager.this,
                        economyManager, leaderboardManager, kernel.getReconnectRegistry());
                    match.start();
                    return match;
                }

                public void attach(ClientHandler handler, DotsAndBoxesMatch match)
                {
                    handler.setCurrentDotsAndBoxesMatch(match);
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
