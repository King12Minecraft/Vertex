package games;
import economy.LeaderboardManager;
import social.ChatManager;
import economy.GameHistoryManager;
import economy.EconomyManager;
import net.ClientHandler;

/**
 * AirHockeyMatchManager
 * ----------------------
 * Matchmaking for Air Hockey - thin wrapper over the shared MatchmakingKernel
 * (see its javadoc), same conversion CheckersMatchManager/ConnectFourMatchManager/
 * ReversiMatchManager/DotsAndBoxesMatchManager/DiceDuelMatchManager/
 * TypingDuelMatchManager already went through. Supplies the two things that
 * actually vary per game: how to construct+start an AirHockeyMatch, and how to
 * attach it to a ClientHandler. AirHockeyMatch's own constructor doesn't take a
 * ReconnectRegistry (this game hasn't adopted reconnect support), so unlike
 * CheckersMatchManager this doesn't pass kernel.getReconnectRegistry() through -
 * exposing it here would be a capability this game doesn't actually have.
 * Explicitly passes MATCH_ID_PREFIX ("airhockey") separately from GAME_ID
 * ("air-hockey") - the original hand-rolled matchId format used no hyphen while
 * GAME_ID (used for QUEUE_UPDATE/game history) does, the same real divergence
 * Connect Four/Dots and Boxes/Dice Duel/Typing Duel all needed. Preserving it
 * exactly keeps this conversion a genuine zero-observable-behavior-change refactor.
 */
public class AirHockeyMatchManager
{
    private static final String GAME_ID = "air-hockey";
    private static final String MATCH_ID_PREFIX = "airhockey";

    private final MatchmakingKernel<AirHockeyMatch> kernel;

    public AirHockeyMatchManager(final EconomyManager economyManager, GameHistoryManager gameHistoryManager,
                                  ChatManager chatManager, final LeaderboardManager leaderboardManager)
    {
        kernel = new MatchmakingKernel<AirHockeyMatch>(GAME_ID, MATCH_ID_PREFIX, gameHistoryManager, chatManager,
            new MatchmakingKernel.PairHandler<AirHockeyMatch>()
            {
                public AirHockeyMatch pair(String matchId, ClientHandler playerA, ClientHandler playerB)
                {
                    AirHockeyMatch match = new AirHockeyMatch(matchId, playerA, playerB, AirHockeyMatchManager.this,
                        economyManager, leaderboardManager);
                    match.start();
                    return match;
                }

                public void attach(ClientHandler handler, AirHockeyMatch match)
                {
                    handler.setCurrentAirHockeyMatch(match);
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
