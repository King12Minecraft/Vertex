package games;

import net.ClientHandler;
import net.Message;
import net.MessageType;
import support.Check;
import support.FakeClientHandler;

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
 * Four hit this first), and all nine of these conversions hit it too - the
 * original hand-rolled matchId formats ("diceduel-1"/"typingduel-1"/
 * "airhockey-1"/"memory-1"/"signalgrid-1"/"fusiongrid-1"/"cardrush-1"/
 * "snakearena-1"/"tetrisduel-1") diverge from each GAME_ID used for
 * QUEUE_UPDATE/game history ("dice-duel"/"typing-duel"/"air-hockey"/
 * "memory-match"/"signal-grid"/"fusion-grid"/"card-rush"/"snake-arena"/
 * "tetris-duel"). A naive conversion using the short 4-arg MatchmakingKernel
 * constructor would silently produce the hyphenated form instead - this test
 * would have caught that regression.
 */
public class MatchmakingKernelAdoptersTest
{
    public static void main(String[] args)
    {
        Check check = new Check();

        testDiceDuelMatchIdPrefixAndPairing(check);
        testTypingDuelMatchIdPrefixAndPairing(check);
        testAirHockeyMatchIdPrefixAndPairing(check);
        testMemoryMatchMatchIdPrefixAndPairing(check);
        testSignalGridMatchIdPrefixAndPairing(check);
        testFusionGridMatchIdPrefixAndPairing(check);
        testCardRushMatchIdPrefixAndPairing(check);
        testSnakeArenaMatchIdPrefixAndPairing(check);
        testTetrisDuelMatchIdPrefixAndPairing(check);
        testQueueingAndCancelling(check);

        check.finish();
    }

    private static void testDiceDuelMatchIdPrefixAndPairing(Check check)
    {
        DiceDuelMatchManager manager = new DiceDuelMatchManager(null,
            new economy.GameHistoryManager(), new social.ChatManager(), null);

        FakeClientHandler a = new FakeClientHandler("Alice", 1);
        FakeClientHandler b = new FakeClientHandler("Bob", 2);
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

        FakeClientHandler a = new FakeClientHandler("Carol", 3);
        FakeClientHandler b = new FakeClientHandler("Dave", 4);
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

    private static void testAirHockeyMatchIdPrefixAndPairing(Check check)
    {
        AirHockeyMatchManager manager = new AirHockeyMatchManager(null,
            new economy.GameHistoryManager(), new social.ChatManager(), null);

        FakeClientHandler a = new FakeClientHandler("Frank", 6);
        FakeClientHandler b = new FakeClientHandler("Grace", 7);
        manager.findMatch(a);
        manager.findMatch(b);

        Message found = a.lastOfType(MessageType.AIRHOCKEY_MATCH_FOUND);
        check.check("AirHockey: both players get matched (queue empties)", manager.getQueueCount() == 0);
        check.check("AirHockey: a real match-found message was sent", found != null);
        check.check("AirHockey: matchId keeps its original no-hyphen prefix ('airhockey-', not 'air-hockey-')",
            found != null && found.getMatchId() != null && found.getMatchId().startsWith("airhockey-"));
        check.check("AirHockey: matchId does NOT use the hyphenated GAME_ID as its prefix",
            found == null || !found.getMatchId().startsWith("air-hockey-"));
        check.check("AirHockey: the opponent also received a match-found message",
            b.lastOfType(MessageType.AIRHOCKEY_MATCH_FOUND) != null);
    }

    private static void testMemoryMatchMatchIdPrefixAndPairing(Check check)
    {
        MemoryMatchMatchManager manager = new MemoryMatchMatchManager(null,
            new economy.GameHistoryManager(), new social.ChatManager(), null);

        FakeClientHandler a = new FakeClientHandler("Heidi", 8);
        FakeClientHandler b = new FakeClientHandler("Ivan", 9);
        manager.findMatch(a);
        manager.findMatch(b);

        Message found = a.lastOfType(MessageType.MEMORY_MATCH_FOUND);
        check.check("MemoryMatch: both players get matched (queue empties)", manager.getQueueCount() == 0);
        check.check("MemoryMatch: a real match-found message was sent", found != null);
        check.check("MemoryMatch: matchId keeps its original short prefix ('memory-', not 'memory-match-')",
            found != null && found.getMatchId() != null && found.getMatchId().startsWith("memory-"));
        check.check("MemoryMatch: matchId does NOT use the hyphenated GAME_ID as its prefix",
            found == null || !found.getMatchId().startsWith("memory-match-"));
        check.check("MemoryMatch: the opponent also received a match-found message",
            b.lastOfType(MessageType.MEMORY_MATCH_FOUND) != null);
    }

    private static void testSignalGridMatchIdPrefixAndPairing(Check check)
    {
        SignalGridMatchManager manager = new SignalGridMatchManager(null,
            new economy.GameHistoryManager(), new social.ChatManager(), null);

        FakeClientHandler a = new FakeClientHandler("Judy", 10);
        FakeClientHandler b = new FakeClientHandler("Kevin", 11);
        manager.findMatch(a);
        manager.findMatch(b);

        Message found = a.lastOfType(MessageType.SIGNALGRID_MATCH_FOUND);
        check.check("SignalGrid: both players get matched (queue empties)", manager.getQueueCount() == 0);
        check.check("SignalGrid: a real match-found message was sent", found != null);
        check.check("SignalGrid: matchId keeps its original no-hyphen prefix ('signalgrid-', not 'signal-grid-')",
            found != null && found.getMatchId() != null && found.getMatchId().startsWith("signalgrid-"));
        check.check("SignalGrid: matchId does NOT use the hyphenated GAME_ID as its prefix",
            found == null || !found.getMatchId().startsWith("signal-grid-"));
        check.check("SignalGrid: the opponent also received a match-found message",
            b.lastOfType(MessageType.SIGNALGRID_MATCH_FOUND) != null);
    }

    private static void testFusionGridMatchIdPrefixAndPairing(Check check)
    {
        FusionGridMatchManager manager = new FusionGridMatchManager(null,
            new economy.GameHistoryManager(), new social.ChatManager(), null);

        FakeClientHandler a = new FakeClientHandler("Laura", 12);
        FakeClientHandler b = new FakeClientHandler("Mallory", 13);
        manager.findMatch(a);
        manager.findMatch(b);

        Message found = a.lastOfType(MessageType.FUSIONGRID_MATCH_FOUND);
        check.check("FusionGrid: both players get matched (queue empties)", manager.getQueueCount() == 0);
        check.check("FusionGrid: a real match-found message was sent", found != null);
        check.check("FusionGrid: matchId keeps its original no-hyphen prefix ('fusiongrid-', not 'fusion-grid-')",
            found != null && found.getMatchId() != null && found.getMatchId().startsWith("fusiongrid-"));
        check.check("FusionGrid: matchId does NOT use the hyphenated GAME_ID as its prefix",
            found == null || !found.getMatchId().startsWith("fusion-grid-"));
        check.check("FusionGrid: the opponent also received a match-found message",
            b.lastOfType(MessageType.FUSIONGRID_MATCH_FOUND) != null);
    }

    private static void testCardRushMatchIdPrefixAndPairing(Check check)
    {
        CardRushMatchManager manager = new CardRushMatchManager(null,
            new economy.GameHistoryManager(), new social.ChatManager(), null);

        FakeClientHandler a = new FakeClientHandler("Niaj", 14);
        FakeClientHandler b = new FakeClientHandler("Olivia", 15);
        manager.findMatch(a);
        manager.findMatch(b);

        Message found = a.lastOfType(MessageType.CARDRUSH_MATCH_FOUND);
        check.check("CardRush: both players get matched (queue empties)", manager.getQueueCount() == 0);
        check.check("CardRush: a real match-found message was sent", found != null);
        check.check("CardRush: matchId keeps its original no-hyphen prefix ('cardrush-', not 'card-rush-')",
            found != null && found.getMatchId() != null && found.getMatchId().startsWith("cardrush-"));
        check.check("CardRush: matchId does NOT use the hyphenated GAME_ID as its prefix",
            found == null || !found.getMatchId().startsWith("card-rush-"));
        check.check("CardRush: the opponent also received a match-found message",
            b.lastOfType(MessageType.CARDRUSH_MATCH_FOUND) != null);
    }

    private static void testSnakeArenaMatchIdPrefixAndPairing(Check check)
    {
        SnakeArenaMatchManager manager = new SnakeArenaMatchManager(null,
            new economy.GameHistoryManager(), new social.ChatManager(), null);

        FakeClientHandler a = new FakeClientHandler("Peggy", 16);
        FakeClientHandler b = new FakeClientHandler("Quentin", 17);
        manager.findMatch(a);
        manager.findMatch(b);

        Message found = a.lastOfType(MessageType.SNAKEARENA_MATCH_FOUND);
        check.check("SnakeArena: both players get matched (queue empties)", manager.getQueueCount() == 0);
        check.check("SnakeArena: a real match-found message was sent", found != null);
        check.check("SnakeArena: matchId keeps its original no-hyphen prefix ('snakearena-', not 'snake-arena-')",
            found != null && found.getMatchId() != null && found.getMatchId().startsWith("snakearena-"));
        check.check("SnakeArena: matchId does NOT use the hyphenated GAME_ID as its prefix",
            found == null || !found.getMatchId().startsWith("snake-arena-"));
        check.check("SnakeArena: the opponent also received a match-found message",
            b.lastOfType(MessageType.SNAKEARENA_MATCH_FOUND) != null);
    }

    private static void testTetrisDuelMatchIdPrefixAndPairing(Check check)
    {
        TetrisDuelMatchManager manager = new TetrisDuelMatchManager(null,
            new economy.GameHistoryManager(), new social.ChatManager(), null);

        FakeClientHandler a = new FakeClientHandler("Rupert", 18);
        FakeClientHandler b = new FakeClientHandler("Sybil", 19);
        manager.findMatch(a);
        manager.findMatch(b);

        Message found = a.lastOfType(MessageType.TETRISDUEL_MATCH_FOUND);
        check.check("TetrisDuel: both players get matched (queue empties)", manager.getQueueCount() == 0);
        check.check("TetrisDuel: a real match-found message was sent", found != null);
        check.check("TetrisDuel: matchId keeps its original no-hyphen prefix ('tetrisduel-', not 'tetris-duel-')",
            found != null && found.getMatchId() != null && found.getMatchId().startsWith("tetrisduel-"));
        check.check("TetrisDuel: matchId does NOT use the hyphenated GAME_ID as its prefix",
            found == null || !found.getMatchId().startsWith("tetris-duel-"));
        check.check("TetrisDuel: the opponent also received a match-found message",
            b.lastOfType(MessageType.TETRISDUEL_MATCH_FOUND) != null);
    }

    private static void testQueueingAndCancelling(Check check)
    {
        DiceDuelMatchManager manager = new DiceDuelMatchManager(null,
            new economy.GameHistoryManager(), new social.ChatManager(), null);

        FakeClientHandler solo = new FakeClientHandler("Erin", 5);
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
