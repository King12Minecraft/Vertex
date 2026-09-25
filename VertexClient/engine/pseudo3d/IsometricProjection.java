package engine.pseudo3d;

import java.awt.Point;

/**
 * IsometricProjection
 * --------------------
 * Converts between tile coordinates (col,row on a flat grid) and
 * on-screen pixel position for a 2:1 "dimetric"/isometric look (the
 * classic diamond-tile style) - the other half of the engine's
 * pseudo-3D toolkit alongside RayCaster's first-person raycasting.
 * Plain 2D math (no real depth buffer or 3D transform), same "plain
 * Java2D tricks" scope as the rest of this package.
 *
 * tileWidth/tileHeight are a single diamond tile's on-screen pixel
 * size; the traditional look uses tileHeight = tileWidth / 2. The
 * caller is responsible for adding their own screen-space origin
 * offset (e.g. to center the map, or to scroll a camera) on top of
 * whatever tileToScreen returns - this class only knows about the
 * tile grid's own coordinate space.
 */
public class IsometricProjection
{
    private final int tileWidth;
    private final int tileHeight;

    public IsometricProjection(int tileWidth, int tileHeight)
    {
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
    }

    /** Screen-space position of tile (col,row)'s top corner, relative to the grid's own origin (col=0,row=0). */
    public Point tileToScreen(double col, double row)
    {
        int screenX = (int) Math.round((col - row) * (tileWidth / 2.0));
        int screenY = (int) Math.round((col + row) * (tileHeight / 2.0));
        return new Point(screenX, screenY);
    }

    /** Inverse of tileToScreen - the fractional (col,row) a screen-space point falls on, for mouse picking. Returns {col, row}; floor both to get the actual tile under the cursor. */
    public double[] screenToTile(double screenX, double screenY)
    {
        double halfW = tileWidth / 2.0;
        double halfH = tileHeight / 2.0;
        double col = (screenX / halfW + screenY / halfH) / 2.0;
        double row = (screenY / halfH - screenX / halfW) / 2.0;
        return new double[] { col, row };
    }

    /**
     * The standard isometric painter's-algorithm sort key: draw tiles
     * in increasing order of this value (farthest from the camera
     * first) so closer tiles correctly paint over farther ones without
     * a real depth buffer. Ties (same col+row) are same-distance
     * tiles along the other diagonal and can draw in either order.
     */
    public static int drawOrder(int col, int row)
    {
        return col + row;
    }
}
