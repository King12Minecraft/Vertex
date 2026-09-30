package mechanics;

import economy.EconomyManager;
import economy.TransactionManager;
import account.ServerAccountStore;
import games.DiceDuelMatch;
import games.DiceDuelMatchManager;
import games.FusionGridMatch;
import games.AirHockeyMatch;
import games.AirHockeyMatchManager;
import games.SnakeArenaMatch;
import games.SnakeArenaMatchManager;
import games.TetrisDuelMatch;
import games.TetrisDuelMatchManager;
import games.CardRushMatch;
import games.CardRushMatchManager;
import games.MemoryMatchMatch;
import games.TypingDuelMatch;
import games.TypingDuelMatchManager;
import games.MemoryMatchMatchManager;
import games.FusionGridMatchManager;
import games.SignalGridMatch;
import games.SignalGridMatchManager;
import net.ClientHandler;
import net.Message;
import net.MessageType;
import support.Check;
import support.FakeClientHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * PairReconnectGamesTest
 * -----------------------
 * The shared PairReconnect helper as actually wired into Dice Duel, Signal Grid and Fusion Grid
 * (each supplies only a small Host adapter): a drop pauses the match and tells the other player,
 * their moves are held, the returning player takes their slot on a new handler and the game
 * carries on, the 30s timeout / guest / deliberate leave / both-gone cases end it as before.
 * One scenario, run against each game through a tiny adapter.
 */
public class PairReconnectGamesTest
{
    /** What differs between the games under test. */
    interface Game
    {
        Object create(ClientHandler a, ClientHandler b);
        void moveByA(Object match, ClientHandler a);
        MessageType updateType();
        MessageType resultType();
        String gameId();
        /** What ReconnectResult.turnSymbol should read for a fresh match at the start (player 0 to move). */
        String startTurn();
        /** How the game names player B (slot 1) in a reconnect result. */
        String slotOneSymbol();
        /** Real-time games tick on a timer: the pause freezes the ticks (checked directly) instead of holding a move. */
        default boolean realTime() { return false; }
    }

    private static EconomyManager economy()
    {
        return new EconomyManager(new ServerAccountStore(), new TransactionManager());
    }

    private static void fireTimeout(Object match) throws Exception
    {
        Field f = match.getClass().getDeclaredField("reconnect");
        f.setAccessible(true);
        Object pair = f.get(match);
        Method m = PairReconnect.class.getDeclaredMethod("onTimeout");
        m.setAccessible(true);
        m.invoke(pair);
    }

    private static void disconnect(Object match, ClientHandler who) throws Exception
    {
        match.getClass().getMethod("handleDisconnect", ClientHandler.class).invoke(match, who);
    }

    private static void setLeaving(ClientHandler h, boolean value) throws Exception
    {
        Field f = ClientHandler.class.getDeclaredField("leavingVoluntarily");
        f.setAccessible(true);
        f.setBoolean(h, value);
    }

    private static void run(Check check, Game g, int idBase) throws Exception
    {
        String name = g.gameId();
        MessageType update = g.updateType();
        MessageType result = g.resultType();

        // --- real-time: the ticks stop, then resume only after the return delay ---
        if (g.realTime())
        {
            FakeClientHandler a = new FakeClientHandler("a" + idBase, idBase);
            FakeClientHandler b = new FakeClientHandler("b" + idBase, idBase + 1);
            Object match = g.create(a, b);
            Thread.sleep(250);
            check.check(name + ": ticks flow before a drop", a.countOfType(update) > 0);
            disconnect(match, b);
            int frozen = a.countOfType(update);
            check.check(name + ": remaining player gets the waiting notice", a.countOfType(MessageType.OPPONENT_DISCONNECTED_NOTICE) == 1);
            check.check(name + ": notice mentions 30s", a.lastOfType(MessageType.OPPONENT_DISCONNECTED_NOTICE).getErrorText().contains("30"));
            Thread.sleep(500);
            check.check(name + ": the game is frozen while paused (no ticks broadcast)", a.countOfType(update) == frozen);
            check.check(name + ": no result during the pause", a.countOfType(result) == 0);

            FakeClientHandler bAgain = new FakeClientHandler("b" + idBase, idBase + 1);
            ReconnectRegistry.ReconnectResult r = ReconnectRegistry.shared().tryReconnect(idBase + 1, bAgain);
            check.check(name + ": reconnect returns a result naming game, slot and opponent",
                r != null && name.equals(r.gameId) && g.slotOneSymbol().equals(r.mySymbol) && ("a" + idBase).equals(r.opponentUsername));
            check.check(name + ": result carries the state", r != null && r.boardState != null && r.boardState.length() > 0);
            check.check(name + ": waiting player gets one fresh state on return", a.countOfType(update) == frozen + 1);
            Thread.sleep(700);
            check.check(name + ": still frozen during the resume delay", a.countOfType(update) == frozen + 1);
            check.check(name + ": the returning player has not been sent anything ahead of the login response", bAgain.countOfType(update) == 0);
        }
        else
        // --- drop (the player NOT on move), then return ---
        {
            FakeClientHandler a = new FakeClientHandler("a" + idBase, idBase);
            FakeClientHandler b = new FakeClientHandler("b" + idBase, idBase + 1);
            Object match = g.create(a, b);
            disconnect(match, b);
            check.check(name + ": remaining player gets the waiting notice", a.countOfType(MessageType.OPPONENT_DISCONNECTED_NOTICE) == 1);
            check.check(name + ": notice carries the state (where the game has a board) and the 30s text",
                (a.lastOfType(MessageType.OPPONENT_DISCONNECTED_NOTICE).getBoardState() != null || "typing-duel".equals(name))
                && a.lastOfType(MessageType.OPPONENT_DISCONNECTED_NOTICE).getErrorText().contains("30"));
            check.check(name + ": no result during the pause", a.countOfType(result) == 0);

            int updatesBefore = a.countOfType(update) + b.countOfType(update);
            g.moveByA(match, a);
            check.check(name + ": the remaining player's move is held during the pause", a.countOfType(update) + b.countOfType(update) == updatesBefore);

            FakeClientHandler bAgain = new FakeClientHandler("b" + idBase, idBase + 1);
            ReconnectRegistry.ReconnectResult r = ReconnectRegistry.shared().tryReconnect(idBase + 1, bAgain);
            check.check(name + ": reconnect returns a result", r != null);
            check.check(name + ": result names the game, slot, opponent and turn",
                r != null && name.equals(r.gameId) && g.slotOneSymbol().equals(r.mySymbol) && ("a" + idBase).equals(r.opponentUsername) && g.startTurn().equals(r.turnSymbol));
            check.check(name + ": opponent gets a fresh update", a.countOfType(update) == 1);

            g.moveByA(match, a);
            check.check(name + ": play resumes, and the new handler receives updates", bAgain.countOfType(update) >= 1);
        }

        // --- timeout ---
        {
            FakeClientHandler a = new FakeClientHandler("c" + idBase, idBase + 2);
            FakeClientHandler b = new FakeClientHandler("d" + idBase, idBase + 3);
            Object match = g.create(a, b);
            disconnect(match, b);
            fireTimeout(match);
            Message over = a.lastOfType(result);
            check.check(name + ": timeout -> OPPONENT_LEFT", over != null && "OPPONENT_LEFT".equals(over.getMatchResult()));
            fireTimeout(match);
            check.check(name + ": timeout reports once", a.countOfType(result) == 1);
            ReconnectRegistry.shared().cancel(idBase + 3);
        }

        // --- guest, deliberate leave ---
        {
            FakeClientHandler a = new FakeClientHandler("e" + idBase, idBase + 4);
            FakeClientHandler guest = new FakeClientHandler("guest", null);
            Object match = g.create(a, guest);
            disconnect(match, guest);
            Message over = a.lastOfType(result);
            check.check(name + ": guest drop forfeits immediately", over != null && "OPPONENT_LEFT".equals(over.getMatchResult()));
            check.check(name + ": no notice for a guest", a.countOfType(MessageType.OPPONENT_DISCONNECTED_NOTICE) == 0);
        }
        {
            FakeClientHandler a = new FakeClientHandler("f" + idBase, idBase + 5);
            FakeClientHandler b = new FakeClientHandler("g" + idBase, idBase + 6);
            Object match = g.create(a, b);
            setLeaving(b, true);
            disconnect(match, b);
            setLeaving(b, false);
            Message over = a.lastOfType(result);
            check.check(name + ": deliberate leave forfeits immediately", over != null && "OPPONENT_LEFT".equals(over.getMatchResult()));
            check.check(name + ": nothing to reconnect to after a deliberate leave", ReconnectRegistry.shared().tryReconnect(idBase + 6, new FakeClientHandler("g", idBase + 6)) == null);
        }

        // --- both drop ---
        {
            FakeClientHandler a = new FakeClientHandler("h" + idBase, idBase + 7);
            FakeClientHandler b = new FakeClientHandler("i" + idBase, idBase + 8);
            Object match = g.create(a, b);
            disconnect(match, a);
            disconnect(match, b);
            fireTimeout(match);
            check.check(name + ": both gone -> no result sent", a.countOfType(result) == 0 && b.countOfType(result) == 0);
            ReconnectRegistry.shared().cancel(idBase + 7);
            ReconnectRegistry.shared().cancel(idBase + 8);
        }
    }

    public static void main(String[] args) throws Exception
    {
        Check check = new Check();

        run(check, new Game()
        {
            public Object create(ClientHandler a, ClientHandler b)
            {
                DiceDuelMatchManager m = new DiceDuelMatchManager(economy(), new economy.GameHistoryManager(), new social.ChatManager(), null);
                DiceDuelMatch match = new DiceDuelMatch("dd-t", a, b, m, economy(), null);
                match.start();
                return match;
            }
            public void moveByA(Object match, ClientHandler a) { ((DiceDuelMatch) match).reroll(a, java.util.Arrays.asList(0, 1)); }
            public MessageType updateType() { return MessageType.DICEDUEL_UPDATE; }
            public MessageType resultType() { return MessageType.DICEDUEL_RESULT; }
            public String gameId() { return "dice-duel"; }
            public String startTurn() { return "0"; }
            public String slotOneSymbol() { return "1"; }
        }, 100);

        run(check, new Game()
        {
            public Object create(ClientHandler a, ClientHandler b)
            {
                SignalGridMatchManager m = new SignalGridMatchManager(economy(), new economy.GameHistoryManager(), new social.ChatManager(), null);
                SignalGridMatch match = new SignalGridMatch("sg-t", a, b, m, economy(), null);
                match.start();
                return match;
            }
            public void moveByA(Object match, ClientHandler a) { ((SignalGridMatch) match).placeAndFire(a, 0, 3); }
            public MessageType updateType() { return MessageType.SIGNALGRID_UPDATE; }
            public MessageType resultType() { return MessageType.SIGNALGRID_RESULT; }
            public String gameId() { return "signal-grid"; }
            public String startTurn() { return "0"; }
            public String slotOneSymbol() { return "1"; }
        }, 200);

        run(check, new Game()
        {
            public Object create(ClientHandler a, ClientHandler b)
            {
                FusionGridMatchManager m = new FusionGridMatchManager(economy(), new economy.GameHistoryManager(), new social.ChatManager(), null);
                FusionGridMatch match = new FusionGridMatch("fg-t", a, b, m, economy(), null);
                match.start();
                return match;
            }
            public void moveByA(Object match, ClientHandler a) { ((FusionGridMatch) match).placeTile(a, 0); }
            public MessageType updateType() { return MessageType.FUSIONGRID_UPDATE; }
            public MessageType resultType() { return MessageType.FUSIONGRID_RESULT; }
            public String gameId() { return "fusion-grid"; }
            public String startTurn() { return "0"; }
            public String slotOneSymbol() { return "1"; }
        }, 300);

        run(check, new Game()
        {
            public Object create(ClientHandler a, ClientHandler b)
            {
                MemoryMatchMatchManager m = new MemoryMatchMatchManager(economy(), new economy.GameHistoryManager(), new social.ChatManager(), null);
                MemoryMatchMatch match = new MemoryMatchMatch("mm-t", a, b, m, economy(), null);
                match.start();
                return match;
            }
            public void moveByA(Object match, ClientHandler a) { ((MemoryMatchMatch) match).flipCard(a, 0); }
            public MessageType updateType() { return MessageType.MEMORY_UPDATE; }
            public MessageType resultType() { return MessageType.MEMORY_RESULT; }
            public String gameId() { return "memory-match"; }
            public String startTurn() { return "0|0:0"; }
            public String slotOneSymbol() { return "1"; }
        }, 400);

        run(check, new Game()
        {
            public Object create(ClientHandler a, ClientHandler b)
            {
                TypingDuelMatchManager m = new TypingDuelMatchManager(economy(), new economy.GameHistoryManager(), new social.ChatManager(), null);
                TypingDuelMatch match = new TypingDuelMatch("td-t", a, b, m, economy(), null);
                match.start();
                return match;
            }
            public void moveByA(Object match, ClientHandler a) { ((TypingDuelMatch) match).reportProgress(a, ""); }
            public MessageType updateType() { return MessageType.TYPINGDUEL_UPDATE; }
            public MessageType resultType() { return MessageType.TYPINGDUEL_RESULT; }
            public String gameId() { return "typing-duel"; }
            public String startTurn() { return "0:0|0:0"; }
            public String slotOneSymbol() { return "B"; }
        }, 600);

        run(check, new Game()
        {
            public Object create(ClientHandler a, ClientHandler b)
            {
                CardRushMatchManager m = new CardRushMatchManager(economy(), new economy.GameHistoryManager(), new social.ChatManager(), null);
                CardRushMatch match = new CardRushMatch("cr-t", a, b, m, economy(), null);
                match.start();
                return match;
            }
            /** Forces a legal play (a 6 onto a 5) so the test doesn't depend on the random deal. */
            public void moveByA(Object match, ClientHandler a)
            {
                try
                {
                    Field hand = CardRushMatch.class.getDeclaredField("handA");
                    hand.setAccessible(true);
                    Field pile = CardRushMatch.class.getDeclaredField("centerPile1");
                    pile.setAccessible(true);
                    ((java.util.List<Integer>) hand.get(match)).set(0, 60);
                    pile.setInt(match, 50);
                }
                catch (Exception e) { throw new RuntimeException(e); }
                ((CardRushMatch) match).playCard(a, 60, 1);
            }
            public MessageType updateType() { return MessageType.CARDRUSH_UPDATE; }
            public MessageType resultType() { return MessageType.CARDRUSH_RESULT; }
            public String gameId() { return "card-rush"; }
            public String startTurn() { return "-"; }
            public String slotOneSymbol() { return "B"; }
        }, 800);

        run(check, new Game()
        {
            public Object create(ClientHandler a, ClientHandler b)
            {
                AirHockeyMatchManager m = new AirHockeyMatchManager(economy(), new economy.GameHistoryManager(), new social.ChatManager(), null);
                AirHockeyMatch match = new AirHockeyMatch("rt-t", a, b, m, economy(), null);
                match.start();
                return match;
            }
            public void moveByA(Object match, ClientHandler a) { }
            public MessageType updateType() { return MessageType.AIRHOCKEY_UPDATE; }
            public MessageType resultType() { return MessageType.AIRHOCKEY_RESULT; }
            public String gameId() { return "air-hockey"; }
            public String startTurn() { return "-"; }
            public String slotOneSymbol() { return "B"; }
            public boolean realTime() { return true; }
        }, 900);

        run(check, new Game()
        {
            public Object create(ClientHandler a, ClientHandler b)
            {
                SnakeArenaMatchManager m = new SnakeArenaMatchManager(economy(), new economy.GameHistoryManager(), new social.ChatManager(), null);
                SnakeArenaMatch match = new SnakeArenaMatch("rt-t", a, b, m, economy(), null);
                match.start();
                return match;
            }
            public void moveByA(Object match, ClientHandler a) { }
            public MessageType updateType() { return MessageType.SNAKEARENA_UPDATE; }
            public MessageType resultType() { return MessageType.SNAKEARENA_RESULT; }
            public String gameId() { return "snake-arena"; }
            public String startTurn() { return "-"; }
            public String slotOneSymbol() { return "B"; }
            public boolean realTime() { return true; }
        }, 1000);

        run(check, new Game()
        {
            public Object create(ClientHandler a, ClientHandler b)
            {
                TetrisDuelMatchManager m = new TetrisDuelMatchManager(economy(), new economy.GameHistoryManager(), new social.ChatManager(), null);
                TetrisDuelMatch match = new TetrisDuelMatch("rt-t", a, b, m, economy(), null);
                match.start();
                return match;
            }
            public void moveByA(Object match, ClientHandler a) { }
            public MessageType updateType() { return MessageType.TETRISDUEL_UPDATE; }
            public MessageType resultType() { return MessageType.TETRISDUEL_RESULT; }
            public String gameId() { return "tetris-duel"; }
            public String startTurn() { return "-"; }
            public String slotOneSymbol() { return "B"; }
            public boolean realTime() { return true; }
        }, 1100);

        // --- Typing Duel only: a round that comes due while a player is away starts when they return ---
        {
            FakeClientHandler a = new FakeClientHandler("td-a", 700);
            FakeClientHandler b = new FakeClientHandler("td-b", 701);
            TypingDuelMatchManager m = new TypingDuelMatchManager(economy(), new economy.GameHistoryManager(), new social.ChatManager(), null);
            TypingDuelMatch match = new TypingDuelMatch("td-t2", a, b, m, economy(), null);
            match.start();
            Field cs = TypingDuelMatch.class.getDeclaredField("currentSentence");
            cs.setAccessible(true);
            String firstSentence = (String) cs.get(match);
            match.reportProgress(a, firstSentence);       // A wins round 1; next round is due in 2s
            disconnect(match, b);
            Thread.sleep(2600);
            check.check("typing-duel: no round starts while a player is away", a.countOfType(MessageType.TYPINGDUEL_ROUND_START) == 1);
            FakeClientHandler bAgain = new FakeClientHandler("td-b", 701);
            ReconnectRegistry.ReconnectResult r = ReconnectRegistry.shared().tryReconnect(701, bAgain);
            check.check("typing-duel: returning player is told the score (1:0) and the new sentence",
                r != null && r.turnSymbol.startsWith("1:0|") && r.boardState != null && r.boardState.length() > 0);
            check.check("typing-duel: the waiting player is sent the new round on return", a.countOfType(MessageType.TYPINGDUEL_ROUND_START) == 2);
            check.check("typing-duel: the new sentence matches what the returning player was given",
                r != null && r.boardState.equals(a.lastOfType(MessageType.TYPINGDUEL_ROUND_START).getTriviaQuestion()));
        }

        // --- Memory Match only: a mismatch clears on a timer, which must not fake "play resumed" mid-pause ---
        {
            FakeClientHandler a = new FakeClientHandler("mm-a", 500);
            FakeClientHandler b = new FakeClientHandler("mm-b", 501);
            MemoryMatchMatchManager m = new MemoryMatchMatchManager(economy(), new economy.GameHistoryManager(), new social.ChatManager(), null);
            MemoryMatchMatch match = new MemoryMatchMatch("mm-t2", a, b, m, economy(), null);
            match.start();
            Field cv = MemoryMatchMatch.class.getDeclaredField("cardValues");
            cv.setAccessible(true);
            char[] values = (char[]) cv.get(match);
            int other = 1;
            while (values[other] == values[0]) other++;
            match.flipCard(a, 0);
            match.flipCard(a, other);                 // mismatch: both revealed, clears in ~1.2s
            disconnect(match, b);
            int updatesAtDrop = a.countOfType(MessageType.MEMORY_UPDATE);
            int noticesAtDrop = a.countOfType(MessageType.OPPONENT_DISCONNECTED_NOTICE);
            Thread.sleep(1700);
            check.check("memory-match: the timer's clear during a pause sends no ordinary update", a.countOfType(MessageType.MEMORY_UPDATE) == updatesAtDrop);
            check.check("memory-match: ...it refreshes the waiting notice instead", a.countOfType(MessageType.OPPONENT_DISCONNECTED_NOTICE) == noticesAtDrop + 1);
            String board = a.lastOfType(MessageType.OPPONENT_DISCONNECTED_NOTICE).getBoardState();
            check.check("memory-match: refreshed board has the mismatched cards hidden again", board.charAt(0) == '.' && board.charAt(other) == '.');
            FakeClientHandler bAgain = new FakeClientHandler("mm-b", 501);
            ReconnectRegistry.ReconnectResult r = ReconnectRegistry.shared().tryReconnect(501, bAgain);
            check.check("memory-match: returning after the clear finds it player B's turn", r != null && r.turnSymbol.startsWith("1|"));
        }

        check.finish();
        System.exit(0);
    }
}
