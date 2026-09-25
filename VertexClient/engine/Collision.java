package engine;

import java.awt.geom.Rectangle2D;

/**
 * Collision
 * ---------
 * Generic overlap tests and bounce responses - the same handful of
 * checks every physics-y game in this codebase already hand-rolls
 * per-game (BrickBreakerGame's checkPaddleCollision/checkBrickCollision,
 * AirHockeyMatch's puck-vs-paddle, SquareWarsWindow's cell-click math),
 * written once instead of once per game. Static utility, like
 * ai.search.Minimax and ai.grid.GridPathfinder - no per-check state,
 * safe to call from any number of games at once.
 *
 * Circle-vs-rectangle uses the standard "clamp the circle's center
 * into the rectangle, then check distance to that clamped point"
 * approach rather than approximating the circle as its own bounding
 * box (which BrickBreakerGame does today) - genuinely more accurate
 * near corners, at negligible extra cost.
 */
public final class Collision
{
    private Collision()
    {
        // Static utility class - never instantiated.
    }

    public static boolean aabbOverlap(Rectangle2D a, Rectangle2D b)
    {
        return a.intersects(b);
    }

    public static boolean circleOverlap(double x1, double y1, double r1, double x2, double y2, double r2)
    {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double radiusSum = r1 + r2;
        return dx * dx + dy * dy <= radiusSum * radiusSum;
    }

    public static boolean circleRectOverlap(double cx, double cy, double radius, Rectangle2D rect)
    {
        double closestX = clamp(cx, rect.getMinX(), rect.getMaxX());
        double closestY = clamp(cy, rect.getMinY(), rect.getMaxY());
        double dx = cx - closestX;
        double dy = cy - closestY;
        return dx * dx + dy * dy <= radius * radius;
    }

    private static double clamp(double value, double min, double max)
    {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * Which side of rect a circle centered at (cx,cy) is overlapping
     * from - useful for deciding whether a bounce should flip vx or vy,
     * the exact question BrickBreakerGame.checkBrickCollision answers
     * by hand today. Only meaningful when the circle and rect actually
     * overlap; ties (a corner hit) resolve to Side.TOP, the common case
     * for a ball falling onto a brick from above.
     */
    public enum Side { TOP, BOTTOM, LEFT, RIGHT }

    public static Side overlapSide(double cx, double cy, double radius, Rectangle2D rect)
    {
        double overlapLeft = (cx + radius) - rect.getMinX();
        double overlapRight = rect.getMaxX() - (cx - radius);
        double overlapTop = (cy + radius) - rect.getMinY();
        double overlapBottom = rect.getMaxY() - (cy - radius);

        double minX = Math.min(overlapLeft, overlapRight);
        double minY = Math.min(overlapTop, overlapBottom);

        if (minY <= minX)
        {
            return overlapTop <= overlapBottom ? Side.TOP : Side.BOTTOM;
        }
        return overlapLeft <= overlapRight ? Side.LEFT : Side.RIGHT;
    }

    /** Reflects a velocity off an axis-aligned surface hit on the given side - flips vy for TOP/BOTTOM, vx for LEFT/RIGHT. The common "bounce a ball" response; a game wanting an angled bounce (e.g. paddle-position-based steering, see BrickBreakerGame's checkPaddleCollision) should compute that itself instead of using this helper. */
    public static Vector2 bounceOffSide(Vector2 velocity, Side side)
    {
        if (side == Side.TOP || side == Side.BOTTOM)
        {
            return new Vector2(velocity.x, -velocity.y);
        }
        return new Vector2(-velocity.x, velocity.y);
    }
}
