package games;
import economy.LeaderboardManager;
import social.ChatManager;
import economy.GameHistoryManager;
import economy.EconomyManager;
import net.ClientHandler;

/**
 * MemoryMatchMatchManager
 * ------------------------
 * Matchmaking for Memory Match - thin wrapper over the shared MatchmakingKernel
 * (see its javadoc), same conversion CheckersMatchManager/ConnectFourMatchManager/
 * ReversiMatchManager/DotsAndBoxesMatchManager/DiceDuelMatchManager/
 * TypingDuelMatchManager already went through. Supplies the two things that
 * actually vary per game: how to construct+start a MemoryMatchMatch, and how to
 * attach it to a ClientHandler. MemoryMatchMatch's own constructor doesn't take a
 * ReconnectRegistry (this game hasn't adopted reconnect support - see
 * PROGRAM_STRUCTURE.md's note that MemoryMatchWindow's practice mode already runs
 * on its own bespoke PracticeMatch rather than the shared ai/search one, since
 * hidden card state doesn't fit that engine's assumptions; the same "genuinely
 * different game shape" reasoning doesn't block a plain matchmaking-kernel
 * conversion though, which only cares about the online 1v1 queue, not practice
 * mode), so unlike CheckersMatchManager this doesn't pass
 * kernel.getReconnectRegistry() through - exposing it here would be a capability
 * this game doesn't actually have.
 * Explicitly passes MATCH_ID_PREFIX ("memory") separately from GAME_ID
 * ("memory-match") - the original hand-rolled matchId format was just "memory-N",
 * not "memorymatch-N" or "memory-match-N", the same real divergence Connect
 * Four/Dots and Boxes/Dice Duel/Typing Duel/Air Hockey all needed. Preserving it
 * exactly keeps this conversion a genuine zero-observable-behavior-change refactor.
 */
public class MemoryMatchMatchManager
{
    private static final String GAME_ID = "memory-match";
    private static final String MATCH_ID_PREFIX = "memory";

    private final MatchmakingKernel<MemoryMatchMatch> kernel;

    public MemoryMatchMatchManager(final EconomyManager economyManager, GameHistoryManager gameHistoryManager,
                                    ChatManager chatManager, final LeaderboardManager leaderboardManager)
    {
        kernel = new MatchmakingKernel<MemoryMatchMatch>(GAME_ID, MATCH_ID_PREFIX, gameHistoryManager, chatManager,
            new MatchmakingKernel.PairHandler<MemoryMatchMatch>()
            {
                public MemoryMatchMatch pair(String matchId, ClientHandler playerA, ClientHandler playerB)
                {
                    MemoryMatchMatch match = new MemoryMatchMatch(matchId, playerA, playerB, MemoryMatchMatchManager.this,
                        economyManager, leaderboardManager);
                    match.start();
                    return match;
                }

                public void attach(ClientHandler handler, MemoryMatchMatch match)
                {
                    handler.setCurrentMemoryMatchMatch(match);
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
