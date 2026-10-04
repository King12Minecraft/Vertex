package games;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * SkyHopperGame
 * -------------
 * An original implementation of the well-known, uncopyrightable
 * "bounce upward off a procedurally generated field of platforms,
 * camera scrolls up forever, game ends when you fall off the bottom
 * of the screen" mechanic (the `Doodle Jump` genre named in the games
 * backlog) - no relation to any specific existing game's code, art, or
 * level data. The games backlog specifically called this out as the
 * platform's first vertical endless climber, distinct from every
 * other single-player game here.
 *
 * World coordinates use a screen-like y-down convention throughout
 * (y decreases going up, matching how the player actually climbs) so
 * the physics and the renderer share the same numbers with no flip -
 * "up" velocity is negative, gravity adds a positive vy each tick.
 * The camera (cameraTop) only ever moves up (decreases) once the
 * player climbs past a fixed fraction of the screen - falling back
 * down never scrolls it back, so a missed landing is genuinely
 * punished, same design intent as the genre's whole risk/reward
 * shape. Offline, single-player, tick-driven, same overall shape as
 * HillClimbGame - SkyHopperWindow drives tick() on a fixed-rate
 * GameLoop and handles rendering/input.
 */
public class SkyHopperGame
{
    public static final int WORLD_WIDTH = 420;
    public static final int VIEW_HEIGHT = 600;
    public static final double GRAVITY = 1500.0;
    public static final double BOUNCE_VELOCITY = -640.0;
    public static final double SPRING_BOUNCE_VELOCITY = -980.0;
    public static final double HORIZONTAL_ACCEL = 1100.0;
    public static final double MAX_HORIZONTAL_SPEED = 260.0;
    public static final double FOLLOW_FRACTION = 0.42;

    public static final int PLAYER_SIZE = 36;
    public static final int PLATFORM_WIDTH = 70;
    public static final int PLATFORM_HEIGHT = 14;
    private static final double MIN_GAP = 70;
    private static final double MAX_GAP = 105;
    private static final double MOVING_SPEED_MIN = 40;
    private static final double MOVING_SPEED_MAX = 90;
    private static final double METERS_PER_WORLD_UNIT = 0.1;

    public enum PlatformType { NORMAL, MOVING, BREAKABLE, SPRING }

    public static class Platform
    {
        public double x;
        public final double y;
        public final PlatformType type;
        public double vx;
        public boolean broken;

        Platform(double x, double y, PlatformType type)
        {
            this.x = x;
            this.y = y;
            this.type = type;
        }
    }

    private final Random random = new Random();
    private final List<Platform> platforms = new ArrayList<Platform>();

    private double playerX = WORLD_WIDTH / 2.0 - PLAYER_SIZE / 2.0;
    private double playerY = 0;
    private double playerVx = 0;
    private double playerVy = 0;
    private boolean movingLeft;
    private boolean movingRight;

    private double cameraTop = 0;
    private double lastGeneratedY;
    private double maxHeightClimbed = 0;
    private boolean over = false;

    public SkyHopperGame()
    {
        // A guaranteed first platform right under the starting position - gravity
        // carries the player onto it within the first fraction of a second, so the
        // very first bounce happens through the same collision logic as every
        // later one rather than a special-cased "start already bouncing" hack.
        Platform first = new Platform(WORLD_WIDTH / 2.0 - PLATFORM_WIDTH / 2.0, 50, PlatformType.NORMAL);
        platforms.add(first);
        lastGeneratedY = first.y;
        generateAhead();
    }

    public void setMovingLeft(boolean value) { movingLeft = value; }
    public void setMovingRight(boolean value) { movingRight = value; }

    public double getPlayerX() { return playerX; }
    public double getPlayerY() { return playerY; }
    public double getCameraTop() { return cameraTop; }
    public List<Platform> getPlatforms() { return platforms; }
    public boolean isOver() { return over; }
    public int getScore() { return (int) (maxHeightClimbed * METERS_PER_WORLD_UNIT); }

    /** Advances the simulation by dtSeconds - see GameLoop's javadoc on dt; frame-rate-independent motion matters here the same way it does for HillClimbGame. */
    public void tick(double dtSeconds)
    {
        if (over) return;

        if (movingLeft && !movingRight)
        {
            playerVx -= HORIZONTAL_ACCEL * dtSeconds;
        }
        else if (movingRight && !movingLeft)
        {
            playerVx += HORIZONTAL_ACCEL * dtSeconds;
        }
        else
        {
            // No input held - a little drag so the player coasts to a stop rather
            // than sliding forever, without feeling icy.
            playerVx *= Math.max(0, 1 - 3.0 * dtSeconds);
        }
        playerVx = clamp(playerVx, -MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED);

        playerX += playerVx * dtSeconds;
        // Wrap-around at the world edges - a genre-standard trait (not
        // copyrightable), and it keeps a fast horizontal dash from just running
        // the player off into unplayable empty space.
        if (playerX < -PLAYER_SIZE) playerX += WORLD_WIDTH + PLAYER_SIZE;
        if (playerX > WORLD_WIDTH) playerX -= WORLD_WIDTH + PLAYER_SIZE;

        playerVy += GRAVITY * dtSeconds;
        double prevFootY = playerY + PLAYER_SIZE;
        playerY += playerVy * dtSeconds;
        double newFootY = playerY + PLAYER_SIZE;

        for (int i = 0; i < platforms.size(); i++)
        {
            Platform p = platforms.get(i);
            if (p.type == PlatformType.MOVING)
            {
                p.x += p.vx * dtSeconds;
                if (p.x <= 0) { p.x = 0; p.vx = Math.abs(p.vx); }
                if (p.x >= WORLD_WIDTH - PLATFORM_WIDTH) { p.x = WORLD_WIDTH - PLATFORM_WIDTH; p.vx = -Math.abs(p.vx); }
            }
        }

        if (playerVy > 0)
        {
            for (int i = 0; i < platforms.size(); i++)
            {
                Platform p = platforms.get(i);
                if (p.broken) continue;

                // Swept check: the player's feet were above the platform last tick
                // and at-or-below it this tick, so a fast fall can't tunnel straight
                // through a thin platform between two ticks.
                boolean crossed = prevFootY <= p.y && newFootY >= p.y;
                boolean overlapsX = playerX + PLAYER_SIZE > p.x && playerX < p.x + PLATFORM_WIDTH;
                if (crossed && overlapsX)
                {
                    playerVy = p.type == PlatformType.SPRING ? SPRING_BOUNCE_VELOCITY : BOUNCE_VELOCITY;
                    playerY = p.y - PLAYER_SIZE;
                    if (p.type == PlatformType.BREAKABLE)
                    {
                        p.broken = true;
                    }
                    break;
                }
            }
        }

        double screenY = playerY - cameraTop;
        double followLine = VIEW_HEIGHT * FOLLOW_FRACTION;
        if (screenY < followLine)
        {
            cameraTop = playerY - followLine;
        }

        maxHeightClimbed = Math.max(maxHeightClimbed, -playerY);

        if (playerY - cameraTop > VIEW_HEIGHT)
        {
            over = true;
        }

        generateAhead();
        removeStalePlatforms();
    }

    /** Keeps generating platforms upward until there's a comfortable margin above the camera's current top edge - called every tick so climbing never outruns generation, but cheap enough to no-op almost every call since it only adds anything once the margin actually shrinks. */
    private void generateAhead()
    {
        double targetY = cameraTop - VIEW_HEIGHT;
        while (lastGeneratedY > targetY)
        {
            double gap = MIN_GAP + random.nextDouble() * (MAX_GAP - MIN_GAP);
            double y = lastGeneratedY - gap;
            double x = random.nextDouble() * (WORLD_WIDTH - PLATFORM_WIDTH);
            PlatformType type = rollPlatformType();

            Platform platform = new Platform(x, y, type);
            if (type == PlatformType.MOVING)
            {
                double speed = MOVING_SPEED_MIN + random.nextDouble() * (MOVING_SPEED_MAX - MOVING_SPEED_MIN);
                platform.vx = random.nextBoolean() ? speed : -speed;
            }
            platforms.add(platform);
            lastGeneratedY = y;
        }
    }

    private PlatformType rollPlatformType()
    {
        double roll = random.nextDouble();
        if (roll < 0.55) return PlatformType.NORMAL;
        if (roll < 0.75) return PlatformType.MOVING;
        if (roll < 0.90) return PlatformType.BREAKABLE;
        return PlatformType.SPRING;
    }

    /** Drops platforms once they're well below the visible viewport - unbounded climbing would otherwise grow this list forever. */
    private void removeStalePlatforms()
    {
        double removeBelow = cameraTop + VIEW_HEIGHT * 1.5;
        for (int i = platforms.size() - 1; i >= 0; i--)
        {
            if (platforms.get(i).y > removeBelow)
            {
                platforms.remove(i);
            }
        }
    }

    private static double clamp(double value, double min, double max)
    {
        return value < min ? min : (value > max ? max : value);
    }
}
