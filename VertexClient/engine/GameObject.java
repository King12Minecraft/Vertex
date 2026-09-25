package engine;

import java.awt.geom.Rectangle2D;

/**
 * GameObject
 * ----------
 * Optional base class for a moving, collidable thing in a game - a
 * ball, a paddle, an enemy, a bullet. Plain double x/y/vx/vy fields
 * (not Vector2) on purpose, matching every existing game's own
 * convention (BrickBreakerGame.ballX/ballVx, AirHockeyWindow's
 * puckX/puckY, etc.) - a game already comfortable hand-rolling that
 * style loses nothing by extending this instead, and gains bounds()
 * for Collision's helpers plus a default update(dt) for free.
 *
 * Entirely optional: a game can keep using its own plain fields
 * without extending GameObject at all, and Collision's static methods
 * work directly against raw coordinates or Rectangle2D.Double too.
 */
public abstract class GameObject
{
    public double x;
    public double y;
    public double vx;
    public double vy;
    public double width;
    public double height;
    public boolean alive = true;

    protected GameObject(double x, double y, double width, double height)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** Integrates velocity into position, scaled by dt (seconds) - see GameLoop's javadoc on dt. Override to add gravity, AI-driven steering, or scripted motion; call super.update(dt) first if plain velocity integration should still happen underneath. */
    public void update(double dt)
    {
        x += vx * dt;
        y += vy * dt;
    }

    /** This object's axis-aligned bounding box in world space, for Collision's overlap checks. */
    public Rectangle2D.Double bounds()
    {
        return new Rectangle2D.Double(x, y, width, height);
    }

    public double centerX()
    {
        return x + width / 2.0;
    }

    public double centerY()
    {
        return y + height / 2.0;
    }
}
