import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * RacingGame
 * ----------
 * The model for Racing - a lane-based dodge runner with a set finish
 * line (FINISH_FRAMES) rather than endless play. Purely single-player
 * from the model's own perspective - no server involvement in gameplay
 * itself, matching Snake's pattern.
 *
 * Power-ups and near-misses were added on top of the original plain
 * dodge-and-survive loop to give it more to actually do round to
 * round, without touching the online protocol at all: everything here
 * is fully deterministic from the shared seed, so every racer in an
 * online match sees the identical sequence of obstacles AND power-ups,
 * and the final score (which now reflects power-up pickups and
 * near-misses too) is still the one number already being compared for
 * placement - no new fields needed anywhere.
 *
 * Entities are {lane, y, type} - type 0 is a dangerous obstacle;
 * SHIELD/BOOST/COIN are collectible power-ups, not hazards.
 */
public class RacingGame
{
    public static final int LANES = 3;
    public static final int BOARD_HEIGHT = 480;
    public static final int PLAYER_Y = 400;
    /** ~30 seconds at the 16ms tick rate RacingPanel runs at - the shared finish line for every racer, whether Practice or Online. */
    public static final int FINISH_FRAMES = 1800;

    public static final int TYPE_OBSTACLE = 0;
    public static final int TYPE_SHIELD = 1;
    public static final int TYPE_BOOST = 2;
    public static final int TYPE_COIN = 3;

    private static final int SPAWN_INTERVAL_START = 55;
    private static final int SPEED_RAMP_TICKS = 150;
    private static final int POWERUP_INTERVAL = 130;
    private static final int BOOST_DURATION_TICKS = 180;
    private static final int NEAR_MISS_BONUS = 5;
    private static final int COIN_BONUS = 10;
    private static final int NEAR_MISS_COOLDOWN_TICKS = 20;

    /** One entity: {lane, yPosition, type}. */
    private final List<int[]> entities = new ArrayList<int[]>();
    private final Random random;

    private int playerLane = 1;
    private int score = 0;
    private int frameCount = 0;
    private double speed = 3.0;
    private boolean gameOver = false;
    private boolean finished = false;

    private boolean hasShield = false;
    private int boostTicksRemaining = 0;
    private int nearMissCooldown = 0;
    private int lastPickup = -1;

    /** Practice mode - unseeded, every playthrough is different. */
    public RacingGame()
    {
        this.random = new Random();
    }

    /** Online mode - every racer gets the identical obstacle and power-up sequence from the same seed, so comparing final scores stays a fair comparison without needing to broadcast live positions between clients. */
    public RacingGame(long seed)
    {
        this.random = new Random(seed);
    }

    public void moveLeft()
    {
        if (playerLane > 0) playerLane--;
    }

    public void moveRight()
    {
        if (playerLane < LANES - 1) playerLane++;
    }

    public void tick()
    {
        if (gameOver || finished)
        {
            return;
        }
        frameCount++;
        lastPickup = -1;

        if (frameCount >= FINISH_FRAMES)
        {
            finished = true;
            return;
        }

        double effectiveSpeed = boostTicksRemaining > 0 ? speed * 1.6 : speed;
        if (boostTicksRemaining > 0)
        {
            boostTicksRemaining--;
        }
        if (nearMissCooldown > 0)
        {
            nearMissCooldown--;
        }

        for (int i = 0; i < entities.size(); i++)
        {
            entities.get(i)[1] += (int) effectiveSpeed;
        }

        Iterator<int[]> it = entities.iterator();
        while (it.hasNext())
        {
            int[] entity = it.next();
            if (entity[1] > BOARD_HEIGHT)
            {
                it.remove();
                if (entity[2] == TYPE_OBSTACLE)
                {
                    score++;
                }
            }
        }

        int spawnInterval = Math.max(24, SPAWN_INTERVAL_START - frameCount / 40);
        if (frameCount % spawnInterval == 0)
        {
            entities.add(new int[] { random.nextInt(LANES), -40, TYPE_OBSTACLE });
        }

        if (frameCount % POWERUP_INTERVAL == 0)
        {
            int type = TYPE_SHIELD + random.nextInt(3);
            entities.add(new int[] { random.nextInt(LANES), -40, type });
        }

        if (frameCount % SPEED_RAMP_TICKS == 0)
        {
            speed += 0.4;
        }

        checkCollisionsAndPickups();
        checkNearMisses();
    }

    private void checkCollisionsAndPickups()
    {
        Iterator<int[]> it = entities.iterator();
        while (it.hasNext())
        {
            int[] entity = it.next();
            boolean inPlayerLane = entity[0] == playerLane;
            boolean overlapsPlayer = entity[1] > PLAYER_Y - 34 && entity[1] < PLAYER_Y + 34;

            if (!inPlayerLane || !overlapsPlayer)
            {
                continue;
            }

            if (entity[2] == TYPE_OBSTACLE)
            {
                if (hasShield)
                {
                    hasShield = false;
                    it.remove();
                }
                else
                {
                    gameOver = true;
                }
                return;
            }
            else
            {
                applyPickup(entity[2]);
                it.remove();
            }
        }
    }

    private void applyPickup(int type)
    {
        lastPickup = type;
        if (type == TYPE_SHIELD)
        {
            hasShield = true;
        }
        else if (type == TYPE_BOOST)
        {
            boostTicksRemaining = BOOST_DURATION_TICKS;
        }
        else if (type == TYPE_COIN)
        {
            score += COIN_BONUS;
        }
    }

    /** A near-miss is an obstacle in an ADJACENT lane passing close to the player's row - rewards staying near danger instead of always retreating to the safest lane. Cooldown prevents one slow-moving obstacle from scoring the bonus many ticks in a row. */
    private void checkNearMisses()
    {
        if (nearMissCooldown > 0)
        {
            return;
        }
        for (int i = 0; i < entities.size(); i++)
        {
            int[] entity = entities.get(i);
            if (entity[2] != TYPE_OBSTACLE)
            {
                continue;
            }
            boolean adjacentLane = Math.abs(entity[0] - playerLane) == 1;
            boolean closeToPlayer = entity[1] > PLAYER_Y - 20 && entity[1] < PLAYER_Y + 20;
            if (adjacentLane && closeToPlayer)
            {
                score += NEAR_MISS_BONUS;
                nearMissCooldown = NEAR_MISS_COOLDOWN_TICKS;
                return;
            }
        }
    }

    public int getPlayerLane() { return playerLane; }
    public List<int[]> getEntities() { return entities; }
    public int getScore() { return score; }
    public int getFrameCount() { return frameCount; }
    public boolean isGameOver() { return gameOver; }
    public boolean isFinished() { return finished; }
    public boolean hasShield() { return hasShield; }
    public boolean isBoosting() { return boostTicksRemaining > 0; }

    /** The power-up type just collected this tick (TYPE_SHIELD/BOOST/COIN), or -1 if none - lets the panel show a brief pickup flash without needing its own state tracking. */
    public int getLastPickup() { return lastPickup; }

    /** True once the run has ended one way or another - crashed or reached the finish line. */
    public boolean isOver() { return gameOver || finished; }
}
