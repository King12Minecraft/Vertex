package games;

import net.ClientHandler;
import net.Message;
import net.MessageType;
import support.Check;

import java.util.ArrayList;
import java.util.List;

/**
 * MatchmakingKernelAdoptersTest
 * ------------------------------
 * Covers DiceDuelMatchManager/TypingDuelMatchManager's conversion onto the
 * shared MatchmakingKernel (the same kernel CheckersMatchManager/
 * ConnectFourMatchManager/ReversiMatchManager/DotsAndBoxesMatchManager already
 * adopted) - a "zero observable behavior change" refactor, verified rather than
 * assumed. Specifically exercises the exact gotcha MatchmakingKernel's own
 * javadoc calls out: matchIdPrefix can genuinely diverge from gameId (Connect
 * Four hit this first), and both of these conversions hit it too - the original
 * hand-rolled matchId format ("diceduel-1"/"typingduel-1") has no hyphen, while
 * each GAME_ID used for QUEUE_UPDATE/game history does ("dice-duel"/
 * "typing-duel"). A naive conversion using the short 4-arg MatchmakingKernel
 * constructor would silently produce "dice-duel-1"/"typing-duel-1" instead -
 * this test would have caught that regression.
 */
public class MatchmakingKernelAdoptersTest
{
    static class FakeHandler extends ClientHandler
    {
        final String username;
        final Integer accountId;
        final List<Message> sent = new ArrayList<Message>();

        FakeHandler(String username, Integer accountId)
        {
            super(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
            this.username = username;
            this.accountId = accountId;
        }

        @Override
        public String getLoggedInUsername() { return username; }

        @Override
        public Integer getAccountId() { return accountId; }

        @Override
        public void sendMessage(Message message) { sent.add(message); }

        Message lastOfType(MessageType type)
        {
            for (int i = sent.size() - 1; i >= 0; i--)
            {
                if (sent.get(i).getType() == type) return sent.get(i);
            }
            return null;
        }
    }

    public static void main(String[] args)
    {
        Check check = new Check();

        testDiceDuelMatchIdPrefixAndPairing(check);
        testTypingDuelMatchIdPrefixAndPairing(check);
        testQueueingAndCancelling(check);

        check.finish();
    }

    private static void testDiceDuelMatchIdPrefixAndPairing(Check check)
    {
        DiceDuelMatchManager manager = new DiceDuelMatchManager(null,
            new economy.GameHistoryManager(), new social.ChatManager(), null);

        FakeHandler a = new FakeHandler("Alice", 1);
        FakeHandler b = new FakeHandler("Bob", 2);
        manager.findMatch(a);
        manager.findMatch(b);

        Message found = a.lastOfType(MessageType.DICEDUEL_MATCH_FOUND);
        check.check("DiceDuel: both players get matched (queue empties)", manager.getQueueCount() == 0);
        check.check("DiceDuel: a real match-found message was sent", found != null);
        check.check("DiceDuel: matchId keeps its original no-hyphen prefix ('diceduel-', not 'dice-duel-')",
            found != null && found.getMatchId() != null && found.getMatchId().startsWith("diceduel-"));
        check.check("DiceDuel: matchId does NOT use the hyphenated GAME_ID as its prefix",
            found == null || !found.getMatchId().startsWith("dice-duel-"));
        check.check("DiceDuel: the opponent also received a match-found message",
            b.lastOfType(MessageType.DICEDUEL_MATCH_FOUND) != null);
    }

    private static void testTypingDuelMatchIdPrefixAndPairing(Check check)
    {
        TypingDuelMatchManager manager = new TypingDuelMatchManager(null,
            new economy.GameHistoryManager(), new social.ChatManager(), null);

        FakeHandler a = new FakeHandler("Carol", 3);
        FakeHandler b = new FakeHandler("Dave", 4);
        manager.findMatch(a);
        manager.findMatch(b);

        Message found = a.lastOfType(MessageType.TYPINGDUEL_MATCH_FOUND);
        check.check("TypingDuel: both players get matched (queue empties)", manager.getQueueCount() == 0);
        check.check("TypingDuel: a real match-found message was sent", found != null);
        check.check("TypingDuel: matchId keeps its original no-hyphen prefix ('typingduel-', not 'typing-duel-')",
            found != null && found.getMatchId() != null && found.getMatchId().startsWith("typingduel-"));
        check.check("TypingDuel: matchId does NOT use the hyphenated GAME_ID as its prefix",
            found == null || !found.getMatchId().startsWith("typing-duel-"));
        check.check("TypingDuel: the opponent also received a match-found message",
            b.lastOfType(MessageType.TYPINGDUEL_MATCH_FOUND) != null);
    }

    private static void testQueueingAndCancelling(Check check)
    {
        DiceDuelMatchManager manager = new DiceDuelMatchManager(null,
            new economy.GameHistoryManager(), new social.ChatManager(), null);

        FakeHandler solo = new FakeHandler("Erin", 5);
        manager.findMatch(solo);
        check.check("A lone player waits in queue rather than matching against nobody",
            manager.getQueueCount() == 1);

        manager.cancelWaiting(solo);
        check.check("Cancelling removes the waiting player from the queue", manager.getQueueCount() == 0);

        // Cancelling a player who isn't actually waiting is a harmless no-op, not
        // an error or a stray QUEUE_UPDATE.
        manager.cancelWaiting(solo);
        check.check("Cancelling an already-gone player stays a no-op", manager.getQueueCount() == 0);

        // The same player calling findMatch twice in a row without cancelling
        // shouldn't be queued twice against themselves.
        manager.findMatch(solo);
        manager.findMatch(solo);
        check.check("Calling findMatch twice for the same waiting player doesn't double-queue them",
            manager.getQueueCount() == 1);
    }
}
