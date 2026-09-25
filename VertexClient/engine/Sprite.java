package engine;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Sprite
 * ------
 * A single renderable image - one frame. Wraps a BufferedImage (loaded
 * from a resource via SpriteSheet, or handed a procedurally-drawn
 * image directly - useful for a game that renders complex vector art
 * once into an offscreen buffer and blits it every frame instead of
 * repainting the same shapes on every tick, the way GameLogo caches
 * its rendered icon today).
 *
 * No game in this codebase currently draws image-based sprites (every
 * board/board-piece is hand-painted with Graphics2D fillRect/fillOval/
 * etc. - see BrickBreakerWindow, ReversiWindow) - Sprite exists for
 * the games that will want actual bitmap art without every one of them
 * reinventing "load an image, draw it scaled at some position."
 */
public class Sprite
{
    private final BufferedImage image;

    public Sprite(BufferedImage image)
    {
        this.image = image;
    }

    public int width()
    {
        return image.getWidth();
    }

    public int height()
    {
        return image.getHeight();
    }

    public BufferedImage image()
    {
        return image;
    }

    /** Draws at the image's native size, top-left corner at (x,y). */
    public void draw(Graphics2D g2, double x, double y)
    {
        g2.drawImage(image, (int) Math.round(x), (int) Math.round(y), null);
    }

    /** Draws scaled to (width,height), top-left corner at (x,y). */
    public void draw(Graphics2D g2, double x, double y, double width, double height)
    {
        g2.drawImage(image, (int) Math.round(x), (int) Math.round(y),
            (int) Math.round(width), (int) Math.round(height), null);
    }

    /** Draws centered on a GameObject's own bounds, at the object's current size. */
    public void draw(Graphics2D g2, GameObject object)
    {
        draw(g2, object.x, object.y, object.width, object.height);
    }
}
