package mechanics;

import economy.EconomyManager;
import economy.TransactionManager;
import account.ServerAccountStore;
import games.DiceDuelMatch;
import games.DiceDuelMatchManager;
import games.FusionGridMatch;
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

        // --- drop (the player NOT on move), then return ---
        {
            FakeClientHandler a = new FakeClientHandler("a" + idBase, idBase);
            FakeClientHandler b = new FakeClientHandler("b" + idBase, idBase + 1);
            Object match = g.create(a, b);
            disconnect(match, b);
            check.check(name + ": remaining player gets the waiting notice", a.countOfType(MessageType.OPPONENT_DISCONNECTED_NOTICE) == 1);
            check.check(name + ": notice carries the state and the 30s text",
                a.lastOfType(MessageType.OPPONENT_DISCONNECTED_NOTICE).getBoardState() != null
                && a.lastOfType(MessageType.OPPONENT_DISCONNECTED_NOTICE).getErrorText().contains("30"));
            check.check(name + ": no result during the pause", a.countOfType(result) == 0);

            int updatesBefore = a.countOfType(update) + b.countOfType(update);
            g.moveByA(match, a);
            check.check(name + ": the remaining player's move is held during the pause", a.countOfType(update) + b.countOfType(update) == updatesBefore);

            FakeClientHandler bAgain = new FakeClientHandler("b" + idBase, idBase + 1);
            ReconnectRegistry.ReconnectResult r = ReconnectRegistry.shared().tryReconnect(idBase + 1, bAgain);
            check.check(name + ": reconnect returns a result", r != null);
            check.check(name + ": result names the game, slot, opponent and turn",
                r != null && name.equals(r.gameId) && "1".equals(r.mySymbol) && ("a" + idBase).equals(r.opponentUsername) && "0".equals(r.turnSymbol));
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
        }, 300);

        check.finish();
        System.exit(0);
    }
}
