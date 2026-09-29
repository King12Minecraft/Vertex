package games;
import economy.LeaderboardManager;
import social.ChatManager;
import economy.GameHistoryManager;
import economy.EconomyManager;
import net.ClientHandler;

/**
 * TypingDuelMatchManager
 * -----------------------
 * Matchmaking for Typing Duel - thin wrapper over the shared MatchmakingKernel
 * (see its javadoc), same conversion CheckersMatchManager/ConnectFourMatchManager/
 * ReversiMatchManager/DotsAndBoxesMatchManager already went through. Supplies the
 * two things that actually vary per game: how to construct+start a
 * TypingDuelMatch, and how to attach it to a ClientHandler. TypingDuelMatch's own
 * constructor doesn't take a ReconnectRegistry (this game hasn't adopted reconnect
 * support), so unlike CheckersMatchManager this doesn't pass
 * kernel.getReconnectRegistry() through - exposing it here would be a capability
 * this game doesn't actually have.
 * Explicitly passes MATCH_ID_PREFIX ("typingduel") separately from GAME_ID
 * ("typing-duel") - the original hand-rolled matchId format used no hyphen while
 * GAME_ID (used for QUEUE_UPDATE/game history) does, the same real divergence
 * Connect Four/Dots and Boxes already hit converting to this kernel. Preserving it
 * exactly keeps this conversion a genuine zero-observable-behavior-change refactor.
 */
public class TypingDuelMatchManager
{
    private static final String GAME_ID = "typing-duel";
    private static final String MATCH_ID_PREFIX = "typingduel";

    private final MatchmakingKernel<TypingDuelMatch> kernel;

    public TypingDuelMatchManager(final EconomyManager economyManager, GameHistoryManager gameHistoryManager,
                                   ChatManager chatManager, final LeaderboardManager leaderboardManager)
    {
        kernel = new MatchmakingKernel<TypingDuelMatch>(GAME_ID, MATCH_ID_PREFIX, gameHistoryManager, chatManager,
            new MatchmakingKernel.PairHandler<TypingDuelMatch>()
            {
                public TypingDuelMatch pair(String matchId, ClientHandler playerA, ClientHandler playerB)
                {
                    TypingDuelMatch match = new TypingDuelMatch(matchId, playerA, playerB, TypingDuelMatchManager.this,
                        economyManager, leaderboardManager);
                    match.start();
                    return match;
                }

                public void attach(ClientHandler handler, TypingDuelMatch match)
                {
                    handler.setCurrentTypingDuelMatch(match);
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
