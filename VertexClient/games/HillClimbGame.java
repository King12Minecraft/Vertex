package games;

import engine.Vector2;

/**
 * HillClimbGame
 * -------------
 * An original implementation of the well-known, uncopyrightable
 * "drive a simple vehicle across procedurally rolling hills, managed
 * by a limited fuel tank, going as far as possible before running
 * dry" mechanic - no relation to any specific existing game's code,
 * art, or terrain data. Terrain is a closed-form height function
 * (layered sine waves), not copied from anywhere, and the car is a
 * simple two-wheel physics model, not a full rigid-body simulation.
 *
 * The first game in this codebase built on the `engine` package
 * (`Vector2` for position/velocity) rather than plain x/y/vx/vy
 * fields - genuinely useful here since the car's forward motion needs
 * to be resolved into a vector along the terrain's slope angle every
 * tick, which is exactly the kind of thing `Vector2.rotate` is for.
 *
 * Offline, single-player, tick-driven - same overall shape as
 * BrickBreakerGame (continuous physics, not grid-based); HillClimbWindow
 * drives tick() on a fixed-rate GameLoop and handles rendering/input.
 * Running out of fuel is the only way a run ends - an earlier version
 * also flipped the car over on a steep-enough slope at high speed, but
 * that triggered too readily during ordinary hard-throttle climbing (a
 * false "unfair death" rather than a genuine reckless-driving penalty)
 * and was cut rather than shipped half-tuned; the car's tilt to match
 * the terrain's slope is purely cosmetic now, not a failure condition.
 */
public class HillClimbGame
{
    public static final double GROUND_ACCEL = 260.0;
    public static final double GRAVITY = 900.0;
    public static final double MAX_SPEED = 260.0;
    public static final double STARTING_FUEL = 100.0;
    public static final double FUEL_BURN_PER_SECOND = 6.0;
    public static final double CAR_LENGTH = 46.0;

    private double distance;
    private double forwardSpeed;
    private double fuel = STARTING_FUEL;
    private boolean throttling;
    private boolean braking;
    private boolean outOfFuel;
    private double carAngle;

    public HillClimbGame()
    {
    }

    public void setThrottling(boolean value) { throttling = value; }
    public void setBraking(boolean value) { braking = value; }

    public double getDistance() { return distance; }
    public double getFuel() { return Math.max(0, fuel); }
    public double getFuelFraction() { return Math.max(0, fuel) / STARTING_FUEL; }
    public double getCarAngle() { return carAngle; }
    public int getScore() { return (int) distance; }
    public boolean isOver() { return outOfFuel; }
    public boolean isOutOfFuel() { return outOfFuel; }

    /** Terrain height at horizontal position x (world units) - layered sine waves for a rolling-but-never-perfectly-flat road, always smooth enough that the slope angle at any point is well-defined. */
    public static double terrainHeight(double x)
    {
        return 60 * Math.sin(x * 0.010)
            + 22 * Math.sin(x * 0.028 + 1.3)
            + 10 * Math.sin(x * 0.06 + 0.4);
    }

    /** Slope angle (radians) of the terrain at x, from its derivative - used both for rendering the car's tilt and for how much gravity fights forward motion. */
    public static double terrainSlope(double x)
    {
        double dx = 0.5;
        double rise = terrainHeight(x + dx) - terrainHeight(x - dx);
        return Math.atan2(rise, 2 * dx);
    }

    /** Advances the simulation by dtSeconds - see GameLoop's javadoc on dt; this game genuinely uses it for frame-rate-independent motion rather than ignoring it, since a fixed-rate Timer's actual interval can drift slightly under load and hill-climb physics feels off if speed doesn't track real elapsed time closely. */
    public void tick(double dtSeconds)
    {
        if (isOver()) return;

        double slope = terrainSlope(distance);

        if (throttling && fuel > 0)
        {
            forwardSpeed += GROUND_ACCEL * dtSeconds;
            fuel -= FUEL_BURN_PER_SECOND * dtSeconds;
        }
        if (braking)
        {
            forwardSpeed -= GROUND_ACCEL * 1.5 * dtSeconds;
        }

        // Gravity resolved along the slope: uphill (positive slope in travel
        // direction) fights forward motion, downhill assists it - the same
        // "gravity component along an incline" physics every hill-climb game
        // needs, done here via Vector2.rotate rather than hand-rolled trig.
        Vector2 gravityAlongSlope = new Vector2(0, GRAVITY).rotate(-slope);
        forwardSpeed += gravityAlongSlope.x * dtSeconds;

        forwardSpeed = clamp(forwardSpeed, -MAX_SPEED / 2, MAX_SPEED);
        // Rolling resistance so the car doesn't coast forever on the flat.
        forwardSpeed *= (1.0 - 0.35 * dtSeconds);

        distance += forwardSpeed * dtSeconds;
        if (distance < 0) distance = 0;

        carAngle = slope;

        if (fuel <= 0 && Math.abs(forwardSpeed) < 1.0)
        {
            fuel = 0;
            outOfFuel = true;
        }
    }

    private static double clamp(double value, double min, double max)
    {
        return value < min ? min : (value > max ? max : value);
    }
}
