package games;
import economy.LeaderboardManager;
import social.ChatManager;
import economy.GameHistoryManager;
import economy.EconomyManager;
import net.ClientHandler;

/**
 * DiceDuelMatchManager
 * ---------------------
 * Matchmaking for Dice Duel - thin wrapper over the shared MatchmakingKernel
 * (see its javadoc), same conversion CheckersMatchManager/ConnectFourMatchManager/
 * ReversiMatchManager/DotsAndBoxesMatchManager already went through. Supplies the
 * two things that actually vary per game: how to construct+start a DiceDuelMatch,
 * and how to attach it to a ClientHandler. DiceDuelMatch's own constructor doesn't
 * take a ReconnectRegistry (this game hasn't adopted reconnect support), so unlike
 * CheckersMatchManager this doesn't pass kernel.getReconnectRegistry() through -
 * exposing it here would be a capability this game doesn't actually have.
 * Explicitly passes MATCH_ID_PREFIX ("diceduel") separately from GAME_ID
 * ("dice-duel") - the original hand-rolled matchId format used no hyphen while
 * GAME_ID (used for QUEUE_UPDATE/game history) does, the same real divergence
 * Connect Four/Dots and Boxes already hit converting to this kernel. Preserving it
 * exactly keeps this conversion a genuine zero-observable-behavior-change refactor.
 */
public class DiceDuelMatchManager
{
    private static final String GAME_ID = "dice-duel";
    private static final String MATCH_ID_PREFIX = "diceduel";

    private final MatchmakingKernel<DiceDuelMatch> kernel;

    public DiceDuelMatchManager(final EconomyManager economyManager, GameHistoryManager gameHistoryManager,
                                 ChatManager chatManager, final LeaderboardManager leaderboardManager)
    {
        kernel = new MatchmakingKernel<DiceDuelMatch>(GAME_ID, MATCH_ID_PREFIX, gameHistoryManager, chatManager,
            new MatchmakingKernel.PairHandler<DiceDuelMatch>()
            {
                public DiceDuelMatch pair(String matchId, ClientHandler playerA, ClientHandler playerB)
                {
                    DiceDuelMatch match = new DiceDuelMatch(matchId, playerA, playerB, DiceDuelMatchManager.this,
                        economyManager, leaderboardManager);
                    match.start();
                    return match;
                }

                public void attach(ClientHandler handler, DiceDuelMatch match)
                {
                    handler.setCurrentDiceDuelMatch(match);
                }
            });
    }

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
