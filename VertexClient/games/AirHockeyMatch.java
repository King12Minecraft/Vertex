package games;

import net.ClientHandler;
import net.Message;
import net.MessageType;
import economy.EconomyManager;
import economy.LeaderboardManager;

import java.util.Timer;
import java.util.TimerTask;

/**
 * AirHockeyMatch
 * --------------
 * Real-time 1v1 air hockey, an original implementation. Unlike every
 * other "real-time" game on this platform (Racing/Zombie Survival/
 * Space Battle all give each player the same seed and let them
 * simulate independently, only syncing the final result) this one
 * genuinely needs a single shared physics object both players affect
 * live - you can't simulate "the puck" independently when both
 * players are hitting the same one. So this is server-authoritative:
 * a fixed-rate physics tick (TICK_MS, ~30/sec) runs the puck's
 * position/velocity, reflects it off walls and paddles, and
 * broadcasts the full state to both clients every tick. Clients only
 * ever report "move my paddle to this position" - they never touch
 * the puck's physics themselves, which is what keeps both players
 * seeing the same puck instead of two different ones.
 *
 * Coordinate space: TABLE_WIDTH x TABLE_HEIGHT virtual units. Player
 * A defends the bottom goal, Player B defends the top goal. First to
 * WIN_SCORE goals wins.
 */
public class AirHockeyMatch
{
    public static final double TABLE_WIDTH = 300;
    public static final double TABLE_HEIGHT = 500;
    public static final double PUCK_RADIUS = 12;
    public static final double PADDLE_RADIUS = 20;
    public static final double GOAL_HALF_WIDTH = 50;
    private static final int WIN_SCORE = 7;
    private static final long TICK_MS = 33;
    private static final double WALL_BOUNCE_DAMPING = 0.92;
    private static final double MAX_PUCK_SPEED = 14;
    private static final String GAME_ID = "air-hockey";

    private final String matchId;
    private final ClientHandler playerA;
    private final ClientHandler playerB;
    private final AirHockeyMatchManager matchManager;
    private final EconomyManager economyManager;
    private final LeaderboardManager leaderboardManager;

    private double puckX = TABLE_WIDTH / 2, puckY = TABLE_HEIGHT / 2;
    private double puckVX = 0, puckVY = 0;
    private double paddleAX = TABLE_WIDTH / 2, paddleAY = TABLE_HEIGHT - 60;
    private double paddleBX = TABLE_WIDTH / 2, paddleBY = 60;
    private int scoreA = 0, scoreB = 0;
    private boolean over = false;
    private Timer physicsTimer;

    public AirHockeyMatch(String matchId, ClientHandler playerA, ClientHandler playerB,
                           AirHockeyMatchManager matchManager, EconomyManager economyManager,
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

        physicsTimer = new Timer(true);
        physicsTimer.scheduleAtFixedRate(new TimerTask()
        {
            public void run() { tick(); }
        }, TICK_MS, TICK_MS);
    }

    private void sendMatchFound(ClientHandler to, String symbol, String opponentUsername)
    {
        Message msg = new Message();
        msg.setType(MessageType.AIRHOCKEY_MATCH_FOUND);
        msg.setMatchId(matchId);
        msg.setSymbol(symbol);
        msg.setOpponentUsername(opponentUsername);
        to.sendMessage(msg);
    }

    /** Reports where a player wants their paddle - clamped to their own half of the table plus a small buffer over the centerline, so paddles can contest the middle without crossing all the way into the opponent's territory. */
    public synchronized void movePaddle(ClientHandler requester, double x, double y)
    {
        if (over) return;
        double clampedX = Math.max(PADDLE_RADIUS, Math.min(TABLE_WIDTH - PADDLE_RADIUS, x));

        if (requester == playerA)
        {
            double minY = TABLE_HEIGHT / 2 - 20;
            paddleAX = clampedX;
            paddleAY = Math.max(minY, Math.min(TABLE_HEIGHT - PADDLE_RADIUS, y));
        }
        else if (requester == playerB)
        {
            double maxY = TABLE_HEIGHT / 2 + 20;
            paddleBX = clampedX;
            paddleBY = Math.max(PADDLE_RADIUS, Math.min(maxY, y));
        }
    }

    private synchronized void tick()
    {
        if (over) return;

        puckX += puckVX;
        puckY += puckVY;

        // Side walls - bounce, unless within the goal mouth exactly at the top/bottom edge
        // (handled separately below as a goal, not a bounce).
        if (puckX - PUCK_RADIUS < 0)
        {
            puckX = PUCK_RADIUS;
            puckVX = -puckVX * WALL_BOUNCE_DAMPING;
        }
        else if (puckX + PUCK_RADIUS > TABLE_WIDTH)
        {
            puckX = TABLE_WIDTH - PUCK_RADIUS;
            puckVX = -puckVX * WALL_BOUNCE_DAMPING;
        }

        boolean inGoalMouth = Math.abs(puckX - TABLE_WIDTH / 2) < GOAL_HALF_WIDTH;

        if (puckY - PUCK_RADIUS < 0)
        {
            if (inGoalMouth)
            {
                scoreA++;
                resetPuck();
            }
            else
            {
                puckY = PUCK_RADIUS;
                puckVY = -puckVY * WALL_BOUNCE_DAMPING;
            }
        }
        else if (puckY + PUCK_RADIUS > TABLE_HEIGHT)
        {
            if (inGoalMouth)
            {
                scoreB++;
                resetPuck();
            }
            else
            {
                puckY = TABLE_HEIGHT - PUCK_RADIUS;
                puckVY = -puckVY * WALL_BOUNCE_DAMPING;
            }
        }

        resolvePaddleCollision(paddleAX, paddleAY);
        resolvePaddleCollision(paddleBX, paddleBY);

        double speed = Math.hypot(puckVX, puckVY);
        if (speed > MAX_PUCK_SPEED)
        {
            puckVX = puckVX / speed * MAX_PUCK_SPEED;
            puckVY = puckVY / speed * MAX_PUCK_SPEED;
        }

        if (scoreA >= WIN_SCORE || scoreB >= WIN_SCORE)
        {
            over = true;
            physicsTimer.cancel();
            finish();
            return;
        }

        broadcastState();
    }

    /** Simple circle-circle elastic-ish bounce - reflects the puck's velocity along the line between the paddle and puck centers, with a bit of extra push so a hit actually feels like a hit rather than a limp deflection. */
    private void resolvePaddleCollision(double paddleX, double paddleY)
    {
        double dx = puckX - paddleX, dy = puckY - paddleY;
        double dist = Math.hypot(dx, dy);
        double minDist = PUCK_RADIUS + PADDLE_RADIUS;
        if (dist < minDist && dist > 0.0001)
        {
            double nx = dx / dist, ny = dy / dist;
            puckX = paddleX + nx * minDist;
            puckY = paddleY + ny * minDist;

            double dot = puckVX * nx + puckVY * ny;
            puckVX = puckVX - 2 * dot * nx + nx * 3;
            puckVY = puckVY - 2 * dot * ny + ny * 3;
        }
    }

    private void resetPuck()
    {
        puckX = TABLE_WIDTH / 2;
        puckY = TABLE_HEIGHT / 2;
        puckVX = 0;
        puckVY = (Math.random() < 0.5 ? -1 : 1) * 3;
    }

    private void broadcastState()
    {
        String state = String.format(java.util.Locale.US, "%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%d,%d",
            puckX, puckY, paddleAX, paddleAY, paddleBX, paddleBY, scoreA, scoreB);

        for (ClientHandler player : new ClientHandler[] { playerA, playerB })
        {
            Message msg = new Message();
            msg.setType(MessageType.AIRHOCKEY_UPDATE);
            msg.setMatchId(matchId);
            msg.setBoardState(state);
            player.sendMessage(msg);
        }
    }

    private void finish()
    {
        matchManager.endMatch(matchId);

        String winnerSymbol = scoreA > scoreB ? "A" : "B";
        recordRating(winnerSymbol);
        economyManager.awardWin("A".equals(winnerSymbol) ? playerA : playerB, GAME_ID);

        sendResult(playerA, winnerSymbol, scoreA);
        sendResult(playerB, winnerSymbol, scoreB);
    }

    private void recordRating(String winnerSymbol)
    {
        if (leaderboardManager == null || playerA.getAccountId() == null || playerB.getAccountId() == null)
        {
            return;
        }
        double outcomeForA = "A".equals(winnerSymbol) ? 1.0 : 0.0;
        leaderboardManager.recordRatedMatch(GAME_ID, playerA.getAccountId(), playerB.getAccountId(), outcomeForA);
    }

    private void sendResult(ClientHandler to, String winnerSymbol, int myScore)
    {
        Message msg = new Message();
        msg.setType(MessageType.AIRHOCKEY_RESULT);
        msg.setMatchId(matchId);
        msg.setScore(myScore);
        boolean toIsWinner = (to == playerA && "A".equals(winnerSymbol)) || (to == playerB && "B".equals(winnerSymbol));
        msg.setMatchResult(toIsWinner ? "WIN" : "LOSE");
        to.sendMessage(msg);
    }

    public synchronized void handleDisconnect(ClientHandler who)
    {
        if (over) return;
        over = true;
        if (physicsTimer != null) physicsTimer.cancel();
        matchManager.endMatch(matchId);

        ClientHandler remaining = (who == playerA) ? playerB : playerA;
        Message msg = new Message();
        msg.setType(MessageType.AIRHOCKEY_RESULT);
        msg.setMatchId(matchId);
        msg.setMatchResult("OPPONENT_LEFT");
        remaining.sendMessage(msg);

        economyManager.awardWin(remaining, GAME_ID);
    }
}
