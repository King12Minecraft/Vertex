package engine;

/**
 * Vector2
 * -------
 * Immutable 2D vector - the math primitive the rest of the engine
 * package (GameObject motion, pseudo3d's camera direction/plane,
 * Collision's bounce responses) is built on. Every operation returns a
 * new Vector2 rather than mutating in place, so a Vector2 is safe to
 * share/reuse (e.g. as a GameObject's constant spawn point) without
 * defensive copying.
 *
 * Most existing games in this codebase move objects with plain double
 * x/y/vx/vy fields instead of a vector type (see BrickBreakerGame,
 * AirHockeyWindow) - that's still perfectly fine for simple motion, and
 * GameObject uses that same convention for its own position/velocity.
 * Vector2 exists for the cases plain doubles get awkward: pseudo3d's
 * raycasting needs real vector rotation for the camera direction/plane,
 * and any future game with angle-based aiming/steering benefits from
 * normalize()/rotate() rather than reimplementing the trig by hand.
 */
public final class Vector2
{
    public static final Vector2 ZERO = new Vector2(0, 0);

    public final double x;
    public final double y;

    public Vector2(double x, double y)
    {
        this.x = x;
        this.y = y;
    }

    public Vector2 add(Vector2 other)
    {
        return new Vector2(x + other.x, y + other.y);
    }

    public Vector2 subtract(Vector2 other)
    {
        return new Vector2(x - other.x, y - other.y);
    }

    public Vector2 scale(double factor)
    {
        return new Vector2(x * factor, y * factor);
    }

    public double dot(Vector2 other)
    {
        return x * other.x + y * other.y;
    }

    public double lengthSquared()
    {
        return x * x + y * y;
    }

    public double length()
    {
        return Math.sqrt(lengthSquared());
    }

    public double distanceTo(Vector2 other)
    {
        return subtract(other).length();
    }

    /** The zero vector normalizes to itself rather than dividing by zero. */
    public Vector2 normalize()
    {
        double len = length();
        return len < 1e-9 ? ZERO : scale(1.0 / len);
    }

    /** Rotates this vector by angleRadians counterclockwise (standard screen-space math convention; Graphics2D's y-down axis just flips which way "counterclockwise" looks on screen). */
    public Vector2 rotate(double angleRadians)
    {
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);
        return new Vector2(x * cos - y * sin, x * sin + y * cos);
    }

    /** This vector reflected off a surface with the given (normalized) normal - the standard bounce formula, v - 2*(v.n)*n. */
    public Vector2 reflect(Vector2 normal)
    {
        double d = 2 * dot(normal);
        return new Vector2(x - d * normal.x, y - d * normal.y);
    }

    public double angle()
    {
        return Math.atan2(y, x);
    }

    @Override
    public String toString()
    {
        return "(" + x + ", " + y + ")";
    }
}
