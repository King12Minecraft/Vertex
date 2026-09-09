package games;
import economy.EconomyConfig;
import net.MessageType;
import net.Message;
import economy.LeaderboardManager;
import economy.EconomyManager;
import net.ClientHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * SquareWarsMatch
 * ---------------
 * Real-time territory control, 2-4 players sharing one grid - the one
 * game on this platform where players genuinely compete over the same
 * live state, rather than each racing their own independent seeded
 * simulation the way Racing/Zombie Survival/Space Battle do. That's
 * fine here because claims are cheap, infrequent, discrete events
 * (click a cell, server validates and broadcasts the new grid) rather
 * than continuous position updates 60 times a second - the same
 * "broadcast state on each discrete move" shape Connect Four/Checkers
 * already use, just not turn-locked, so any player can claim any time.
 *
 * 12x9 grid (108 cells). Claiming a cell - whether empty or owned by
 * an opponent - assigns it to you instantly, no cooldown; that
 * back-and-forth is the "war" in Square Wars. After MATCH_DURATION_MS,
 * whoever owns the most cells wins (a coin reward, split evenly on a
 * tie for the top spot); everyone's final cell count also feeds the
 * score leaderboard.
 */
public class SquareWarsMatch
{
    public static final int COLS = 12;
    public static final int ROWS = 9;
    public static final int TOTAL_CELLS = COLS * ROWS;
    public static final long MATCH_DURATION_MS = 60_000;
    private static final String GAME_ID = "square-wars";

    private final String matchId;
    private final List<ClientHandler> players;
    private final SquareWarsMatchManager matchManager;
    private final EconomyManager economyManager;
    private final LeaderboardManager leaderboardManager;

    /** '.' = unclaimed, otherwise a digit '0'.. matching the claiming player's index in players. */
    private final char[] board = new char[TOTAL_CELLS];
    private boolean over = false;
    private Timer endTimer;

    public SquareWarsMatch(String matchId, List<ClientHandler> players, SquareWarsMatchManager matchManager,
                            EconomyManager economyManager, LeaderboardManager leaderboardManager)
    {
        this.matchId = matchId;
        this.players = players;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.leaderboardManager = leaderboardManager;
        java.util.Arrays.fill(board, '.');
    }

    public void start()
    {
        for (int i = 0; i < players.size(); i++)
        {
            Message msg = new Message();
            msg.setType(MessageType.SQWARS_MATCH_FOUND);
            msg.setMatchId(matchId);
            msg.setSymbol(String.valueOf(i));
            msg.setBoardState(boardString());
            players.get(i).sendMessage(msg);
        }

        endTimer = new Timer(true);
        endTimer.schedule(new TimerTask()
        {
            public void run() { finish(); }
        }, MATCH_DURATION_MS);
    }

    public synchronized void claim(ClientHandler requester, int cellIndex)
    {
        if (over || cellIndex < 0 || cellIndex >= TOTAL_CELLS)
        {
            return;
        }
        int playerIndex = players.indexOf(requester);
        if (playerIndex < 0)
        {
            return;
        }

        board[cellIndex] = (char) ('0' + playerIndex);
        broadcastUpdate();
    }

    private void broadcastUpdate()
    {
        String state = boardString();
        for (int i = 0; i < players.size(); i++)
        {
            Message msg = new Message();
            msg.setType(MessageType.SQWARS_UPDATE);
            msg.setMatchId(matchId);
            msg.setBoardState(state);
            players.get(i).sendMessage(msg);
        }
    }

    private synchronized void finish()
    {
        if (over)
        {
            return;
        }
        over = true;
        matchManager.endMatch(matchId);

        int[] counts = new int[players.size()];
        for (int i = 0; i < board.length; i++)
        {
            if (board[i] != '.')
            {
                int idx = board[i] - '0';
                if (idx >= 0 && idx < counts.length) counts[idx]++;
            }
        }

        int maxCount = 0;
        for (int c : counts) maxCount = Math.max(maxCount, c);

        List<Integer> winners = new ArrayList<Integer>();
        for (int i = 0; i < counts.length; i++)
        {
            if (maxCount > 0 && counts[i] == maxCount) winners.add(i);
        }

        int totalReward = EconomyConfig.getWinReward(GAME_ID);
        int perWinnerReward = winners.isEmpty() ? 0 : totalReward / winners.size();

        for (int i = 0; i < players.size(); i++)
        {
            ClientHandler player = players.get(i);
            boolean isWinner = winners.contains(i);

            if (isWinner && perWinnerReward > 0)
            {
                economyManager.awardCoins(player, perWinnerReward, "Won a Square Wars match");
            }

            if (player.getAccountId() != null)
            {
                leaderboardManager.recordScore(GAME_ID, player.getAccountId(), counts[i]);
            }

            Message msg = new Message();
            msg.setType(MessageType.SQWARS_RESULT);
            msg.setMatchId(matchId);
            msg.setBoardState(boardString());
            msg.setScore(counts[i]);
            msg.setMatchResult(isWinner ? "WIN" : "LOSE");
            msg.setSqWarsReward(isWinner ? perWinnerReward : 0);
            player.sendMessage(msg);
        }
    }

    public synchronized void handleDisconnect(ClientHandler who)
    {
        // Their already-claimed cells stay on the board (still "theirs" for scoring purposes
        // at the end) - they just can't claim any more since their connection is gone. The
        // match keeps running for whoever's left; finish() still fires on the same timer.
    }

    private String boardString()
    {
        return new String(board);
    }
}
