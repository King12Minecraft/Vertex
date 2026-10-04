package games;
import economy.LeaderboardManager;
import social.ChatManager;
import economy.GameHistoryManager;
import economy.EconomyManager;
import net.ClientHandler;

/**
 * CardRushMatchManager
 * ----------------------
 * Matchmaking for Card Rush - thin wrapper over the shared MatchmakingKernel
 * (see its javadoc), same conversion CheckersMatchManager/ConnectFourMatchManager/
 * ReversiMatchManager/DotsAndBoxesMatchManager/DiceDuelMatchManager/
 * TypingDuelMatchManager/AirHockeyMatchManager/MemoryMatchMatchManager/
 * SignalGridMatchManager/FusionGridMatchManager already went through. Supplies
 * the two things that actually vary per game: how to construct+start a
 * CardRushMatch, and how to attach it to a ClientHandler. CardRushMatch's own
 * constructor doesn't take a ReconnectRegistry (this game hasn't adopted
 * reconnect support), so unlike CheckersMatchManager this doesn't pass
 * kernel.getReconnectRegistry() through - exposing it here would be a
 * capability this game doesn't actually have.
 * Explicitly passes MATCH_ID_PREFIX ("cardrush") separately from GAME_ID
 * ("card-rush") - the original hand-rolled matchId format used no hyphen while
 * GAME_ID (used for QUEUE_UPDATE/game history) does, the same real divergence
 * every prior adopter with a hyphenated GAME_ID needed. Preserving it exactly
 * keeps this conversion a genuine zero-observable-behavior-change refactor.
 */
public class CardRushMatchManager
{
    private static final String GAME_ID = "card-rush";
    private static final String MATCH_ID_PREFIX = "cardrush";

    private final MatchmakingKernel<CardRushMatch> kernel;

    public CardRushMatchManager(final EconomyManager economyManager, GameHistoryManager gameHistoryManager,
                                 ChatManager chatManager, final LeaderboardManager leaderboardManager)
    {
        kernel = new MatchmakingKernel<CardRushMatch>(GAME_ID, MATCH_ID_PREFIX, gameHistoryManager, chatManager,
            new MatchmakingKernel.PairHandler<CardRushMatch>()
            {
                public CardRushMatch pair(String matchId, ClientHandler playerA, ClientHandler playerB)
                {
                    CardRushMatch match = new CardRushMatch(matchId, playerA, playerB, CardRushMatchManager.this,
                        economyManager, leaderboardManager);
                    match.start();
                    return match;
                }

                public void attach(ClientHandler handler, CardRushMatch match)
                {
                    handler.setCurrentCardRushMatch(match);
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
