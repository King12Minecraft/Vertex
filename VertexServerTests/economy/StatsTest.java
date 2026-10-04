package economy;

import account.Role;
import account.ServerAccountStore;
import net.ClientHandler;
import net.Message;
import net.MessageType;
import support.Check;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * StatsTest
 * ----------
 * The data behind the Stats page: per-game play counts (most played first), rating and win/loss/draw
 * rows, best scores, all recorded by the server - plus the request handler that serves them: public by
 * player name like a profile, "yourself" needing a login, unknown names refused, and a player with no
 * history getting empty lists rather than an error.
 */
public class StatsTest
{
    static class Handler extends ClientHandler
    {
        Handler(ServerAccountStore accounts, GameHistoryManager history, LeaderboardManager leaderboard)
        {
            super(null, accounts, null, null, null, null, null, history, null, null,
                null, null, null, null, null, null, leaderboard, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        }
    }

    private static Message ask(Handler h, String username) throws Exception
    {
        Message r = new Message();
        r.setType(MessageType.STATS_REQUEST);
        r.setUsername(username);
        Method m = ClientHandler.class.getDeclaredMethod("handle", Message.class);
        m.setAccessible(true);
        return (Message) m.invoke(h, r);
    }

    private static void loginAs(Handler h, String username) throws Exception
    {
        Field f = ClientHandler.class.getDeclaredField("loggedInUsername");
        f.setAccessible(true);
        f.set(h, username);
    }

    public static void main(String[] args) throws Exception
    {
        Check check = new Check();
        ServerAccountStore accounts = new ServerAccountStore();
        int a = accounts.createAccount("aaa", "password1", Role.PLAYER).getAccountId();
        int b = accounts.createAccount("bbb", "password1", Role.PLAYER).getAccountId();
        accounts.createAccount("newbie", "password1", Role.PLAYER);
        GameHistoryManager history = new GameHistoryManager();
        LeaderboardManager leaderboard = new LeaderboardManager(accounts);

        history.recordPlay(a, "chess"); history.recordPlay(a, "chess"); history.recordPlay(a, "chess");
        history.recordPlay(a, "dice-duel");
        history.recordPlay(a, "snake"); history.recordPlay(a, "snake");
        history.recordPlay(b, "chess");
        leaderboard.recordRatedMatch("chess", a, b, 1.0);
        leaderboard.recordRatedMatch("chess", a, b, 1.0);
        leaderboard.recordRatedMatch("chess", a, b, 0.5);
        leaderboard.recordScore("snake", a, 120);
        leaderboard.recordScore("snake", a, 90);     // lower: best stays 120

        // manager level
        List<String> counts = history.getPlayCountsByGame(a);
        check.check("play counts are per game, most played first, ties by name: " + counts,
            counts.equals(java.util.Arrays.asList("chess:3", "snake:2", "dice-duel:1")));
        check.check("another player's plays are not mixed in", history.getPlayCountsByGame(b).equals(java.util.Arrays.asList("chess:1")));
        check.check("a player with no history has no counts", history.getPlayCountsByGame(999).isEmpty());

        List<String> rows = leaderboard.getStatsRowsForAccount(a);
        String chessRow = null, snakeRow = null;
        for (String r : rows) { if (r.startsWith("chess|")) chessRow = r; if (r.startsWith("snake|")) snakeRow = r; }
        check.check("a rated game row carries rating, 2 wins, 0 losses, 1 draw: " + chessRow,
            chessRow != null && chessRow.split("\\|")[2].equals("2") && chessRow.split("\\|")[3].equals("0") && chessRow.split("\\|")[4].equals("1")
            && Integer.parseInt(chessRow.split("\\|")[1]) > 1000);
        check.check("a score-only game row has no rating and the best score: " + snakeRow,
            snakeRow != null && snakeRow.equals("snake||0|0|0|120"));
        check.check("a game with nothing on record is left out (dice-duel)", rows.size() == 2);
        check.check("the opponent's row shows the loss", leaderboard.getStatsRowsForAccount(b).get(0).split("\\|")[3].equals("2"));
        check.check("a player with no record has no rows", leaderboard.getStatsRowsForAccount(999).isEmpty());

        // handler level
        Handler guest = new Handler(accounts, history, leaderboard);
        Message r = ask(guest, "aaa");
        check.check("anyone can look up a player by name, even a guest", r.isSuccess() && "aaa".equals(r.getUsername()));
        check.check("total plays is the sum", r.getStatsTotalPlays() == 6);
        check.check("play counts travel in the response", r.getStatsPlayCounts().get(0).equals("chess:3"));
        check.check("game rows travel in the response", r.getStatsGameRows().size() == 2);
        check.check("with no achievement manager the count is 0, not an error", r.getStatsAchievementCount() == 0);

        Message notLoggedIn = ask(guest, "");
        check.check("asking for yourself while logged out is refused with a message", !notLoggedIn.isSuccess() && notLoggedIn.getErrorText().contains("Log in"));
        Message unknown = ask(guest, "nobody-here");
        check.check("an unknown player is refused", !unknown.isSuccess() && unknown.getErrorText().contains("No such player"));

        Handler me = new Handler(accounts, history, leaderboard);
        loginAs(me, "aaa");
        Message self = ask(me, null);
        check.check("asking for yourself while logged in returns your own stats", self.isSuccess() && "aaa".equals(self.getUsername()) && self.getStatsTotalPlays() == 6);

        Message empty = ask(guest, "newbie");
        check.check("a player with no history gets empty lists and zero, not an error",
            empty.isSuccess() && empty.getStatsTotalPlays() == 0 && empty.getStatsPlayCounts().isEmpty() && empty.getStatsGameRows().isEmpty());

        check.finish();
    }
}
