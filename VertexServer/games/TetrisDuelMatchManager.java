package games;
import economy.LeaderboardManager;
import social.ChatManager;
import economy.GameHistoryManager;
import economy.EconomyManager;
import net.ClientHandler;

/**
 * TetrisDuelMatchManager
 * ------------------------
 * Matchmaking for Competitive Tetris - thin wrapper over the shared
 * MatchmakingKernel (see its javadoc), same conversion CheckersMatchManager/
 * ConnectFourMatchManager/ReversiMatchManager/DotsAndBoxesMatchManager/
 * DiceDuelMatchManager/TypingDuelMatchManager/AirHockeyMatchManager/
 * MemoryMatchMatchManager/SignalGridMatchManager/FusionGridMatchManager already
 * went through. Supplies the two things that actually vary per game: how to
 * construct+start a TetrisDuelMatch, and how to attach it to a ClientHandler.
 * TetrisDuelMatch's own constructor doesn't take a ReconnectRegistry (this game
 * hasn't adopted reconnect support), so unlike CheckersMatchManager this doesn't
 * pass kernel.getReconnectRegistry() through - exposing it here would be a
 * capability this game doesn't actually have.
 * Explicitly passes MATCH_ID_PREFIX ("tetrisduel") separately from GAME_ID
 * ("tetris-duel") - the original hand-rolled matchId format used no hyphen
 * while GAME_ID (used for QUEUE_UPDATE/game history) does, the same real
 * divergence every prior adopter with a hyphenated GAME_ID needed. Preserving
 * it exactly keeps this conversion a genuine zero-observable-behavior-change
 * refactor.
 */
public class TetrisDuelMatchManager
{
    private static final String GAME_ID = "tetris-duel";
    private static final String MATCH_ID_PREFIX = "tetrisduel";

    private final MatchmakingKernel<TetrisDuelMatch> kernel;

    public TetrisDuelMatchManager(final EconomyManager economyManager, GameHistoryManager gameHistoryManager,
                                   ChatManager chatManager, final LeaderboardManager leaderboardManager)
    {
        kernel = new MatchmakingKernel<TetrisDuelMatch>(GAME_ID, MATCH_ID_PREFIX, gameHistoryManager, chatManager,
            new MatchmakingKernel.PairHandler<TetrisDuelMatch>()
            {
                public TetrisDuelMatch pair(String matchId, ClientHandler playerA, ClientHandler playerB)
                {
                    TetrisDuelMatch match = new TetrisDuelMatch(matchId, playerA, playerB, TetrisDuelMatchManager.this,
                        economyManager, leaderboardManager);
                    match.start();
                    return match;
                }

                public void attach(ClientHandler handler, TetrisDuelMatch match)
                {
                    handler.setCurrentTetrisDuelMatch(match);
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
