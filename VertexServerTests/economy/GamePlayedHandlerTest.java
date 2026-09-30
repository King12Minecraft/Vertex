package economy;

import account.Account;
import account.Role;
import account.ServerAccountStore;
import net.ClientHandler;
import net.Message;
import net.MessageType;
import support.Check;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * GamePlayedHandlerTest
 * ----------------------
 * Drives the REAL ClientHandler.handleGamePlayed - the message an offline game sends when a round ends - to pin
 * two server-side rules: the game id is only accepted if it looks like a game id (it is written into the
 * "account|game|time" history file, so a "|" or newline in it would forge or break a record), and the coins it can
 * earn are rate limited, because the server cannot verify an offline round happened.
 */
public class GamePlayedHandlerTest
{
    static class Handler extends ClientHandler
    {
        Handler(ServerAccountStore accounts, EconomyManager economy, GameHistoryManager history, LeaderboardManager leaderboard)
        {
            super(null, accounts, null, null, null, null, economy, history, null, null,
                null, null, null, null, null, null, leaderboard, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        }

        /** No socket: swallow what would be sent (wallet updates, notices). */
        @Override
        public void sendMessage(Message message) { }
    }

    private static void send(Handler h, String gameId, int score) throws Exception
    {
        Message m = new Message();
        m.setType(MessageType.GAME_PLAYED_REQUEST);
        m.setGameId(gameId);
        m.setScore(score);
        Method handle = ClientHandler.class.getDeclaredMethod("handle", Message.class);
        handle.setAccessible(true);
        handle.invoke(h, m);
    }

    private static int historyLines() throws Exception
    {
        java.io.File f = new java.io.File("gamehub_play_history.dat");
        if (!f.exists()) return 0;
        int n = 0;
        BufferedReader r = new BufferedReader(new FileReader(f));
        while (r.readLine() != null) n++;
        r.close();
        return n;
    }

    public static void main(String[] args) throws Exception
    {
        Check check = new Check();
        ServerAccountStore accounts = new ServerAccountStore();
        Account account = accounts.createAccount("player1", "password1", Role.PLAYER);
        EconomyManager economy = new EconomyManager(accounts, new TransactionManager());
        GameHistoryManager history = new GameHistoryManager();
        LeaderboardManager leaderboard = new LeaderboardManager(accounts);
        Handler h = new Handler(accounts, economy, history, leaderboard);

        Field u = ClientHandler.class.getDeclaredField("loggedInUsername");
        u.setAccessible(true);
        u.set(h, "player1");
        Field id = ClientHandler.class.getDeclaredField("loggedInAccountId");
        id.setAccessible(true);
        id.set(h, account.getAccountId());

        int startCoins = account.getCoins();

        // hostile ids never reach the history file
        String[] bad = { "snake|999|5\n1|chess|1", "snake\nx", "Snake", "", "a b", "x".repeat(41), "../etc", null };
        for (String gameId : bad) send(h, gameId, 500);
        check.check("hostile or malformed game ids record nothing", history.getTotalPlayCount(account.getAccountId()) == 0 && historyLines() == 0);
        check.check("...and pay nothing", account.getCoins() == startCoins);

        // a normal claim is recorded and paid (Snake: score/5 capped at 25)
        send(h, "snake", 500);
        check.check("a proper game id is recorded", history.getTotalPlayCount(account.getAccountId()) == 1 && historyLines() == 1);
        check.check("its reward is paid once (25 coins)", account.getCoins() == startCoins + 25);

        // claiming again straight away records the play but pays nothing more
        for (int i = 0; i < 20; i++) send(h, "snake", 500);
        check.check("repeated claims are still recorded as plays", history.getTotalPlayCount(account.getAccountId()) == 21);
        check.check("...but the rate limit holds the coins at the first payout", account.getCoins() == startCoins + 25);

        // flat-reward games are limited the same way
        send(h, "minesweeper", 0);
        int afterFirstMine = account.getCoins();
        check.check("a flat-reward game pays the first time", afterFirstMine == startCoins + 25 + EconomyConfig.MINESWEEPER_REWARD);
        send(h, "minesweeper", 0);
        check.check("...and not the second time in a row", account.getCoins() == afterFirstMine);

        check.finish();
    }
}
