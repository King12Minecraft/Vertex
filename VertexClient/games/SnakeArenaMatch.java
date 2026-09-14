package games;

import net.ClientHandler;
import net.Message;
import net.MessageType;
import economy.EconomyManager;
import economy.LeaderboardManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * SnakeArenaMatch
 * ----------------
 * Real-time 1v1 competitive Snake, an original implementation - two
 * snakes sharing one live arena at once, unlike the existing
 * single-player Snake which is just one snake against an empty grid.
 * Server-authoritative, same pattern as AirHockeyMatch: a fixed-rate
 * tick moves both snakes forward one cell, checks food/wall/self/
 * opponent collisions, and broadcasts the full arena state every
 * tick. Clients only ever report "turn this direction" - the actual
 * movement and collision detection only ever happens once, server-
 * side, so both players always see the identical arena rather than
 * two independently-simulated ones that could drift apart.
 *
 * A snake dies by hitting a wall, itself, or the other snake (head or
 * body) - the other snake wins immediately. If both die on the same
 * tick (a head-on collision), it's a draw.
 */
public class SnakeArenaMatch
{
    public static final int GRID_SIZE = 20;
    private static final long TICK_MS = 150;
    private static final String GAME_ID = "snake-arena";

    private static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;

    private final String matchId;
    private final ClientHandler playerA;
    private final ClientHandler playerB;
    private final SnakeArenaMatchManager matchManager;
    private final EconomyManager economyManager;
    private final LeaderboardManager leaderboardManager;
    private final Random random = new Random();

    private final Deque<int[]> snakeA = new ArrayDeque<int[]>();
    private final Deque<int[]> snakeB = new ArrayDeque<int[]>();
    private int directionA = RIGHT, directionB = LEFT;
    private int pendingDirectionA = RIGHT, pendingDirectionB = LEFT;
    private int[] food;
    private boolean aliveA = true, aliveB = true;
    private boolean over = false;
    private Timer tickTimer;

    public SnakeArenaMatch(String matchId, ClientHandler playerA, ClientHandler playerB,
                            SnakeArenaMatchManager matchManager, EconomyManager economyManager,
                            LeaderboardManager leaderboardManager)
    {
        this.matchId = matchId;
        this.playerA = playerA;
        this.playerB = playerB;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.leaderboardManager = leaderboardManager;

        snakeA.add(new int[] { 3, GRID_SIZE / 2 });
        snakeB.add(new int[] { GRID_SIZE - 4, GRID_SIZE / 2 });
        spawnFood();
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
        msg.setType(MessageType.SNAKEARENA_MATCH_FOUND);
        msg.setMatchId(matchId);
        msg.setSymbol(symbol);
        msg.setOpponentUsername(opponentUsername);
        msg.setBoardState(stateString());
        to.sendMessage(msg);
    }

    /** Queues a direction change for the next tick - can't reverse directly into your own neck (e.g. going RIGHT then immediately queuing LEFT), the same restriction every real Snake game has, since that would just be an instant, uninteresting self-collision. */
    public synchronized void turn(ClientHandler requester, int direction)
    {
        if (over || direction < 0 || direction > 3) return;
        if (requester == playerA && !isOpposite(directionA, direction)) pendingDirectionA = direction;
        else if (requester == playerB && !isOpposite(directionB, direction)) pendingDirectionB = direction;
    }

    private boolean isOpposite(int current, int next)
    {
        return (current == UP && next == DOWN) || (current == DOWN && next == UP)
            || (current == LEFT && next == RIGHT) || (current == RIGHT && next == LEFT);
    }

    private synchronized void tick()
    {
        if (over) return;

        directionA = pendingDirectionA;
        directionB = pendingDirectionB;

        int[] nextHeadA = aliveA ? nextCell(snakeA.peekFirst(), directionA) : null;
        int[] nextHeadB = aliveB ? nextCell(snakeB.peekFirst(), directionB) : null;

        boolean eatFoodA = aliveA && sameCell(nextHeadA, food);
        boolean eatFoodB = aliveB && sameCell(nextHeadB, food);

        if (aliveA)
        {
            snakeA.addFirst(nextHeadA);
            if (!eatFoodA) snakeA.removeLast();
        }
        if (aliveB)
        {
            snakeB.addFirst(nextHeadB);
            if (!eatFoodB) snakeB.removeLast();
        }

        if (aliveA && collides(nextHeadA, snakeA, snakeB, true)) aliveA = false;
        if (aliveB && collides(nextHeadB, snakeB, snakeA, false)) aliveB = false;
        if (aliveA && aliveB && sameCell(nextHeadA, nextHeadB))
        {
            aliveA = false;
            aliveB = false;
        }

        if (eatFoodA || eatFoodB) spawnFood();

        if (!aliveA || !aliveB)
        {
            over = true;
            tickTimer.cancel();
            finish();
            return;
        }

        broadcastUpdate();
    }

    private int[] nextCell(int[] head, int direction)
    {
        int x = head[0], y = head[1];
        if (direction == UP) y--;
        else if (direction == DOWN) y++;
        else if (direction == LEFT) x--;
        else x++;
        return new int[] { x, y };
    }

    /** Checks wall bounds, self-collision (the segment behind the new head, since the tail itself just moved away this same tick), and collision with the OTHER snake's full body (checked against its state from before this tick's move, since bodySelf already includes the just-added head). */
    private boolean collides(int[] head, Deque<int[]> bodySelf, Deque<int[]> otherSnakeBody, boolean isA)
    {
        if (head[0] < 0 || head[0] >= GRID_SIZE || head[1] < 0 || head[1] >= GRID_SIZE)
        {
            return true;
        }
        int count = 0;
        for (int[] segment : bodySelf)
        {
            count++;
            if (count > 1 && sameCell(head, segment)) return true;
        }
        for (int[] segment : otherSnakeBody)
        {
            if (sameCell(head, segment)) return true;
        }
        return false;
    }

    private boolean sameCell(int[] a, int[] b)
    {
        return a != null && b != null && a[0] == b[0] && a[1] == b[1];
    }

    private void spawnFood()
    {
        int x, y;
        do
        {
            x = random.nextInt(GRID_SIZE);
            y = random.nextInt(GRID_SIZE);
        }
        while (occupiedByEitherSnake(x, y));
        food = new int[] { x, y };
    }

    private boolean occupiedByEitherSnake(int x, int y)
    {
        for (int[] segment : snakeA) if (segment[0] == x && segment[1] == y) return true;
        for (int[] segment : snakeB) if (segment[0] == x && segment[1] == y) return true;
        return false;
    }

    private void broadcastUpdate()
    {
        String state = stateString();
        for (ClientHandler player : new ClientHandler[] { playerA, playerB })
        {
            Message msg = new Message();
            msg.setType(MessageType.SNAKEARENA_UPDATE);
            msg.setMatchId(matchId);
            msg.setBoardState(state);
            player.sendMessage(msg);
        }
    }

    /** "food_x,food_y|Ax1,Ay1;Ax2,Ay2;...|Bx1,By1;Bx2,By2;..." */
    private String stateString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(food[0]).append(",").append(food[1]).append("|");
        appendSnake(sb, snakeA);
        sb.append("|");
        appendSnake(sb, snakeB);
        return sb.toString();
    }

    private void appendSnake(StringBuilder sb, Deque<int[]> snake)
    {
        boolean first = true;
        for (int[] segment : snake)
        {
            if (!first) sb.append(";");
            sb.append(segment[0]).append(",").append(segment[1]);
            first = false;
        }
    }

    private void finish()
    {
        matchManager.endMatch(matchId);

        String winnerResult;
        if (!aliveA && !aliveB) winnerResult = "DRAW";
        else winnerResult = aliveA ? "A" : "B";

        recordRating(winnerResult);
        if (!"DRAW".equals(winnerResult))
        {
            economyManager.awardWin("A".equals(winnerResult) ? playerA : playerB, GAME_ID);
        }

        sendResult(playerA, winnerResult, snakeA.size());
        sendResult(playerB, winnerResult, snakeB.size());
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

    private void sendResult(ClientHandler to, String winnerResult, int myLength)
    {
        Message msg = new Message();
        msg.setType(MessageType.SNAKEARENA_RESULT);
        msg.setMatchId(matchId);
        msg.setScore(myLength);
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
        msg.setType(MessageType.SNAKEARENA_RESULT);
        msg.setMatchId(matchId);
        msg.setMatchResult("OPPONENT_LEFT");
        remaining.sendMessage(msg);

        economyManager.awardWin(remaining, GAME_ID);
    }
}
