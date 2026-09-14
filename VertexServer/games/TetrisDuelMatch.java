package games;

import net.ClientHandler;
import net.Message;
import net.MessageType;
import economy.EconomyManager;
import economy.LeaderboardManager;

import java.util.Timer;
import java.util.TimerTask;

/**
 * TetrisDuelMatch
 * ---------------
 * Competitive 1v1 Tetris - two independent boards, each running the
 * existing single-player TetrisGame engine (reused as-is rather than
 * rewritten - it was already a clean, UI-free logic class). What
 * makes this competitive rather than just "two people happen to play
 * Tetris at the same time": clearing 2+ lines at once sends garbage
 * rows to the opponent's board (TetrisGame.addGarbageLines - added
 * for this feature). Last board still standing wins; if both top out
 * on the same tick, it's a draw.
 *
 * Server-authoritative, same shape as AirHockeyMatch/SnakeArenaMatch:
 * a fixed-rate tick advances both boards' gravity and broadcasts
 * state. Runs its tick loop at TICK_MS (50ms) rather than the
 * single-player window's 16ms - broadcasting two full 10x20 grids
 * every 16ms to two clients is a lot of unnecessary bandwidth for a
 * LAN-hosted app, and gravity at this coarser rate is still a
 * perfectly playable pace (just gentler than single-player's, since
 * TetrisGame's own dropInterval-in-frames stays the same regardless
 * of how often a frame actually ticks) - a deliberate, documented
 * tradeoff rather than an attempt to reproduce single-player's exact
 * feel. Move/rotate/drop inputs are still applied immediately when
 * they arrive, not queued to the next tick.
 */
public class TetrisDuelMatch
{
    private static final long TICK_MS = 50;
    private static final String GAME_ID = "tetris-duel";

    private final String matchId;
    private final ClientHandler playerA;
    private final ClientHandler playerB;
    private final TetrisDuelMatchManager matchManager;
    private final EconomyManager economyManager;
    private final LeaderboardManager leaderboardManager;

    private final TetrisGame gameA = new TetrisGame();
    private final TetrisGame gameB = new TetrisGame();
    private int linesClearedSoFarA = 0, linesClearedSoFarB = 0;
    private boolean over = false;
    private Timer tickTimer;

    public TetrisDuelMatch(String matchId, ClientHandler playerA, ClientHandler playerB,
                            TetrisDuelMatchManager matchManager, EconomyManager economyManager,
                            LeaderboardManager leaderboardManager)
    {
        this.matchId = matchId;
        this.playerA = playerA;
        this.playerB = playerB;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.leaderboardManager = leaderboardManager;
    }

    public void start()
    {
        sendMatchFound(playerA, "A", playerB.getLoggedInUsername());
        sendMatchFound(playerB, "B", playerA.getLoggedInUsername());

        tickTimer = new Timer(true);
        tickTimer.scheduleAtFixedRate(new TimerTask()
        {
            public void run() { tick(); }
        }, TICK_MS, TICK_MS);
    }

    private void sendMatchFound(ClientHandler to, String symbol, String opponentUsername)
    {
        Message msg = new Message();
        msg.setType(MessageType.TETRISDUEL_MATCH_FOUND);
        msg.setMatchId(matchId);
        msg.setSymbol(symbol);
        msg.setOpponentUsername(opponentUsername);
        to.sendMessage(msg);
    }

    /** action: "LEFT", "RIGHT", "ROTATE", "SOFT_DROP", or "HARD_DROP" - applied immediately, not queued to the next gravity tick, so input feels responsive rather than laggy. */
    public synchronized void applyAction(ClientHandler requester, String action)
    {
        if (over) return;
        TetrisGame game = requester == playerA ? gameA : requester == playerB ? gameB : null;
        if (game == null || game.isGameOver()) return;

        if ("LEFT".equals(action)) game.moveLeft();
        else if ("RIGHT".equals(action)) game.moveRight();
        else if ("ROTATE".equals(action)) game.rotate();
        else if ("SOFT_DROP".equals(action)) game.softDrop();
        else if ("HARD_DROP".equals(action)) game.hardDrop();

        checkForGarbageToSend(requester == playerA);
        broadcastUpdate();
    }

    private synchronized void tick()
    {
        if (over) return;

        gameA.tick();
        gameB.tick();
        checkForGarbageToSend(true);
        checkForGarbageToSend(false);

        if (gameA.isGameOver() || gameB.isGameOver())
        {
            over = true;
            tickTimer.cancel();
            finish();
            return;
        }

        broadcastUpdate();
    }

    /** Sends garbage to the OTHER board when this one has cleared 2+ lines since last checked - only the newly-cleared lines beyond what was already sent count, since getLinesCleared() is cumulative for the whole game, not per-action. A single line clear sends nothing (the standard Tetris-multiplayer convention: only multi-line clears punish the opponent, or single clears would make garbage come far too often to leave any room for skill in avoiding it). */
    private void checkForGarbageToSend(boolean isA)
    {
        TetrisGame mine = isA ? gameA : gameB;
        TetrisGame opponent = isA ? gameB : gameA;
        int totalCleared = mine.getLinesCleared();
        int previousTotal = isA ? linesClearedSoFarA : linesClearedSoFarB;
        int newlyCleared = totalCleared - previousTotal;

        if (isA) linesClearedSoFarA = totalCleared; else linesClearedSoFarB = totalCleared;

        if (newlyCleared >= 2)
        {
            opponent.addGarbageLines(newlyCleared - 1);
        }
    }

    private void broadcastUpdate()
    {
        Message toA = new Message();
        toA.setType(MessageType.TETRISDUEL_UPDATE);
        toA.setMatchId(matchId);
        toA.setBoardState(gridToString(gameA));
        toA.setChatText(gridToString(gameB));
        toA.setScore(gameA.getScore());
        playerA.sendMessage(toA);

        Message toB = new Message();
        toB.setType(MessageType.TETRISDUEL_UPDATE);
        toB.setMatchId(matchId);
        toB.setBoardState(gridToString(gameB));
        toB.setChatText(gridToString(gameA));
        toB.setScore(gameB.getScore());
        playerB.sendMessage(toB);
    }

    /** Flattens the 10x20 grid plus the falling piece's current cells into one comma-separated string of cell values (0=empty, 1-7=locked piece colors, 8=garbage) - the falling piece is merged in here rather than sent separately, since the client only ever needs to draw the board as it currently looks. */
    private String gridToString(TetrisGame game)
    {
        int[][] grid = game.getGrid();
        int[][] rows = new int[TetrisGame.ROWS][TetrisGame.COLS];
        for (int r = 0; r < TetrisGame.ROWS; r++)
        {
            System.arraycopy(grid[r], 0, rows[r], 0, TetrisGame.COLS);
        }

        if (!game.isGameOver())
        {
            int[][] cells = game.getCurrentCells();
            for (int[] cell : cells)
            {
                int r = game.getCurrentY() + cell[0];
                int c = game.getCurrentX() + cell[1];
                if (r >= 0 && r < TetrisGame.ROWS && c >= 0 && c < TetrisGame.COLS)
                {
                    rows[r][c] = game.getCurrentType() + 1;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < TetrisGame.ROWS; r++)
        {
            for (int c = 0; c < TetrisGame.COLS; c++)
            {
                if (sb.length() > 0) sb.append(",");
                sb.append(rows[r][c]);
            }
        }
        return sb.toString();
    }

    private void finish()
    {
        matchManager.endMatch(matchId);

        String winnerResult;
        if (gameA.isGameOver() && gameB.isGameOver()) winnerResult = "DRAW";
        else winnerResult = gameA.isGameOver() ? "B" : "A";

        recordRating(winnerResult);
        if (!"DRAW".equals(winnerResult))
        {
            economyManager.awardWin("A".equals(winnerResult) ? playerA : playerB, GAME_ID);
        }

        sendResult(playerA, winnerResult, gameA.getScore());
        sendResult(playerB, winnerResult, gameB.getScore());
    }

    private void recordRating(String winnerResult)
    {
        if (leaderboardManager == null || playerA.getAccountId() == null || playerB.getAccountId() == null)
        {
            return;
        }
        double outcomeForA = "DRAW".equals(winnerResult) ? 0.5 : "A".equals(winnerResult) ? 1.0 : 0.0;
        leaderboardManager.recordRatedMatch(GAME_ID, playerA.getAccountId(), playerB.getAccountId(), outcomeForA);
    }

    private void sendResult(ClientHandler to, String winnerResult, int myScore)
    {
        Message msg = new Message();
        msg.setType(MessageType.TETRISDUEL_RESULT);
        msg.setMatchId(matchId);
        msg.setScore(myScore);
        boolean toIsWinner = (to == playerA && "A".equals(winnerResult)) || (to == playerB && "B".equals(winnerResult));
        msg.setMatchResult("DRAW".equals(winnerResult) ? "DRAW" : (toIsWinner ? "WIN" : "LOSE"));
        to.sendMessage(msg);
    }

    public synchronized void handleDisconnect(ClientHandler who)
    {
        if (over) return;
        over = true;
        if (tickTimer != null) tickTimer.cancel();
        matchManager.endMatch(matchId);

        ClientHandler remaining = (who == playerA) ? playerB : playerA;
        Message msg = new Message();
        msg.setType(MessageType.TETRISDUEL_RESULT);
        msg.setMatchId(matchId);
        msg.setMatchResult("OPPONENT_LEFT");
        remaining.sendMessage(msg);

        economyManager.awardWin(remaining, GAME_ID);
    }
}
