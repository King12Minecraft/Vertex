package ai.steering;

/**
 * Steering
 * --------
 * Small, pure continuous-space movement utilities shared across
 * Vertex's real-time games - the non-grid counterpart to
 * ai.grid.GridPathfinder for roadmap item 4. "Seek" (move directly
 * toward a target at a given speed) needs no primary/fallback safety
 * net the way ai.BotStrategy/AiKernel provide for genuinely fallible
 * decisions: it's a closed-form vector calculation that can't throw,
 * so it's a plain static utility rather than something registered
 * with AiKernel - the same reasoning that found Checkers/Chess/
 * Connect Four had nothing to migrate in roadmap item 3 applies here
 * to the arithmetic itself, even though the BEHAVIOR (zombies chasing
 * the player) is exactly the kind of thing this shared AI layer is
 * for. Games that DO need a real decision on top of this (which
 * target to pick, whether to flee, a smarter-vs-fallback split) still
 * register that decision through AiKernel/BotStrategy - this only
 * covers the "given a target, how do I move toward/away from it"
 * arithmetic underneath such a decision.
 */
public final class Steering
{
    private Steering()
    {
        // Static utility class - never instantiated.
    }

    /**
     * Returns the {dx, dy} velocity to move from (fromX,fromY) directly
     * toward (towardX,towardY) at the given speed. {0, 0} if already
     * at (or extremely close to) the target, to avoid a divide-by-zero
     * on a zero-length direction vector.
     */
    public static double[] seek(double fromX, double fromY, double towardX, double towardY, double speed)
    {
        double dx = towardX - fromX;
        double dy = towardY - fromY;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.0001)
        {
            return new double[] { 0, 0 };
        }
        return new double[] { (dx / len) * speed, (dy / len) * speed };
    }

    /** The opposite of seek() - moves directly AWAY from (fromX,fromY), as if fleeing (awayFromX,awayFromY), at the given speed. */
    public static double[] flee(double fromX, double fromY, double awayFromX, double awayFromY, double speed)
    {
        double[] towards = seek(fromX, fromY, awayFromX, awayFromY, speed);
        return new double[] { -towards[0], -towards[1] };
    }
}
