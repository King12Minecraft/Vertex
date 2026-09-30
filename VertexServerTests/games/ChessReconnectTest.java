package games;

import mechanics.ReconnectRegistry;
import net.ClientHandler;
import net.Message;
import net.MessageType;
import support.Check;
import support.FakeClientHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * ChessReconnectTest
 * -------------------
 * Chess adopting the shared reconnect mechanic (mechanics.ReconnectRegistry / ReconnectPolicy):
 * a dropped player gets a pause instead of an instant forfeit, moves and draw offers are held
 * during it, the returning player takes over their slot on a brand-new handler, and the
 * pre-existing OPPONENT_LEFT outcome is preserved for the cases that still forfeit (guest,
 * deliberate leave, timeout). Uses the real shared registry; the 30-second timer is exercised by
 * invoking the match's timeout callback directly instead of waiting it out.
 */
public class ChessReconnectTest
{
    private static ChessMatch newMatch(FakeClientHandler white, FakeClientHandler black)
    {
        ChessMatchManager manager = new ChessMatchManager(null, null, null, null, null);
        ChessMatch match = new ChessMatch("chess-t", white, black, manager, null, null, null);
        white.setCurrentChessMatch(match);
        black.setCurrentChessMatch(match);
        match.start();
        return match;
    }

    private static void fireTimeout(ChessMatch match) throws Exception
    {
        Method m = ChessMatch.class.getDeclaredMethod("onReconnectTimeout");
        m.setAccessible(true);
        m.invoke(match);
    }

    private static void setLeaving(ClientHandler h, boolean value) throws Exception
    {
        Field f = ClientHandler.class.getDeclaredField("leavingVoluntarily");
        f.setAccessible(true);
        f.setBoolean(h, value);
    }

    public static void main(String[] args) throws Exception
    {
        Check check = new Check();

        // --- a drop pauses the match; nothing is decided yet ---
        {
            FakeClientHandler white = new FakeClientHandler("alice", 1);
            FakeClientHandler black = new FakeClientHandler("bob", 2);
            ChessMatch match = newMatch(white, black);
            match.offerDraw(white);
            check.check("draw offer reached black", black.countOfType(MessageType.CHESS_DRAW_OFFERED) == 1);

            match.handleDisconnect(white);
            check.check("remaining player gets the waiting notice", black.countOfType(MessageType.OPPONENT_DISCONNECTED_NOTICE) == 1);
            check.check("notice mentions the wait", black.lastOfType(MessageType.OPPONENT_DISCONNECTED_NOTICE).getErrorText().contains("30"));
            check.check("no result during the pause", black.countOfType(MessageType.CHESS_MATCH_OVER) == 0);

            int rejectedBefore = black.countOfType(MessageType.CHESS_MOVE_REJECTED);
            match.makeMove(black, 52, 36);
            check.check("moves are held during the pause", black.countOfType(MessageType.CHESS_MOVE_REJECTED) == rejectedBefore + 1);
            match.offerDraw(black);
            check.check("draw offers are held during the pause", white.countOfType(MessageType.CHESS_DRAW_OFFERED) == 0);

            // --- reconnect: same account, new handler ---
            FakeClientHandler whiteAgain = new FakeClientHandler("alice", 1);
            ReconnectRegistry.ReconnectResult result = ReconnectRegistry.shared().tryReconnect(1, whiteAgain);
            check.check("reconnect returns a result", result != null);
            check.check("result is chess / WHITE / vs bob",
                result != null && "chess".equals(result.gameId) && "WHITE".equals(result.mySymbol) && "bob".equals(result.opponentUsername));
            check.check("result carries the board and turn", result != null && result.boardState.length() == 64 && "WHITE".equals(result.turnSymbol));
            check.check("opponent gets a fresh update (clears their waiting notice)", black.countOfType(MessageType.CHESS_UPDATE) >= 2);

            int updatesBefore = black.countOfType(MessageType.CHESS_UPDATE);
            match.makeMove(whiteAgain, 12, 28);
            check.check("the new handler can move", black.countOfType(MessageType.CHESS_UPDATE) == updatesBefore + 1);
            match.makeMove(white, 11, 27);
            check.check("the old handler no longer controls white", black.countOfType(MessageType.CHESS_UPDATE) == updatesBefore + 1);

            match.respondToDraw(black, true);
            check.check("the pre-drop draw offer did not survive the pause", black.countOfType(MessageType.CHESS_MATCH_OVER) == 0);
        }

        // --- timeout keeps the old outcome ---
        {
            FakeClientHandler white = new FakeClientHandler("carol", 3);
            FakeClientHandler black = new FakeClientHandler("dave", 4);
            ChessMatch match = newMatch(white, black);
            match.handleDisconnect(black);
            fireTimeout(match);
            Message over = white.lastOfType(MessageType.CHESS_MATCH_OVER);
            check.check("timeout -> OPPONENT_LEFT for the remaining player", over != null && "OPPONENT_LEFT".equals(over.getMatchResult()));
            fireTimeout(match);
            check.check("timeout reports once", white.countOfType(MessageType.CHESS_MATCH_OVER) == 1);
            ReconnectRegistry.shared().cancel(4);
        }

        // --- guest: immediate ---
        {
            FakeClientHandler white = new FakeClientHandler("guest1", null);
            FakeClientHandler black = new FakeClientHandler("erin", 5);
            ChessMatch match = newMatch(white, black);
            match.handleDisconnect(white);
            Message over = black.lastOfType(MessageType.CHESS_MATCH_OVER);
            check.check("guest drop forfeits immediately", over != null && "OPPONENT_LEFT".equals(over.getMatchResult()));
            check.check("no waiting notice for a guest", black.countOfType(MessageType.OPPONENT_DISCONNECTED_NOTICE) == 0);
        }

        // --- deliberate leave: immediate, even for a logged-in player ---
        {
            FakeClientHandler white = new FakeClientHandler("frank", 6);
            FakeClientHandler black = new FakeClientHandler("gina", 7);
            ChessMatch match = newMatch(white, black);
            setLeaving(white, true);
            match.handleDisconnect(white);
            setLeaving(white, false);
            Message over = black.lastOfType(MessageType.CHESS_MATCH_OVER);
            check.check("deliberate leave forfeits immediately", over != null && "OPPONENT_LEFT".equals(over.getMatchResult()));
            check.check("no waiting notice for a deliberate leave", black.countOfType(MessageType.OPPONENT_DISCONNECTED_NOTICE) == 0);
            check.check("nothing left to reconnect to", ReconnectRegistry.shared().tryReconnect(6, new FakeClientHandler("frank", 6)) == null);
        }

        // --- both drop: match ends, a later timeout is a no-op ---
        {
            FakeClientHandler white = new FakeClientHandler("hal", 8);
            FakeClientHandler black = new FakeClientHandler("ivy", 9);
            ChessMatch match = newMatch(white, black);
            match.handleDisconnect(white);
            match.handleDisconnect(black);
            fireTimeout(match);
            check.check("both gone: no result sent to anyone", white.countOfType(MessageType.CHESS_MATCH_OVER) == 0 && black.countOfType(MessageType.CHESS_MATCH_OVER) == 0);
            check.check("both gone: a reconnect finds nothing", ReconnectRegistry.shared().tryReconnect(8, new FakeClientHandler("hal", 8)) == null);
            ReconnectRegistry.shared().cancel(8);
            ReconnectRegistry.shared().cancel(9);
        }

        // --- takeover: a new login of the same account while the old session is still registered (a dead connection the server hasn't noticed) ---
        {
            FakeClientHandler white = new FakeClientHandler("jo", 30);
            FakeClientHandler black = new FakeClientHandler("kay", 31);
            ChessMatch match = newMatch(white, black);
            white.releaseMatchesForTakeover();
            check.check("takeover starts the grace period (opponent is told)", black.countOfType(MessageType.OPPONENT_DISCONNECTED_NOTICE) == 1);
            FakeClientHandler whiteNew = new FakeClientHandler("jo", 30);
            ReconnectRegistry.ReconnectResult r = ReconnectRegistry.shared().tryReconnect(30, whiteNew);
            check.check("the new login resumes the match straight away", r != null && "chess".equals(r.gameId) && "WHITE".equals(r.mySymbol));
            white.releaseMatchesForTakeover();
            check.check("the old session's later cleanup no longer touches the match", black.countOfType(MessageType.OPPONENT_DISCONNECTED_NOTICE) == 1 && black.countOfType(MessageType.CHESS_MATCH_OVER) == 0);
            match.makeMove(whiteNew, 12, 28);
            check.check("the new session can play on", black.countOfType(MessageType.CHESS_UPDATE) >= 2);
        }

        check.finish();
        System.exit(0);
    }
}
