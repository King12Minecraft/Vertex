package engine.pseudo3d;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * RayCaster
 * ---------
 * Classic Wolfenstein-3D-style raycasting: renders a first-person view
 * of a 2D grid map as vertical wall slices, one ray per screen column,
 * using the standard DDA (digital differential analysis) grid-traversal
 * algorithm. Plain Java2D, no textures beyond a flat per-cell color
 * (darkened on one wall orientation for a cheap "shading" effect) -
 * the fastest path to an actual pseudo-3D look without a texture
 * pipeline, matching this engine's "plain Java2D tricks, not a real 3D
 * renderer" scope (see ROADMAP.md's engine package entry).
 *
 * The caller owns the camera: posX/posY (position in map cells),
 * dirX/dirY (a unit-length view direction), planeX/planeY (the camera
 * plane, perpendicular to dir, whose length sets the field of view -
 * roughly 0.66x dir's length for a ~66-degree FOV, the traditional
 * raycasting convention). Rotating the view is just rotating both dir
 * and plane by the same angle - see Vector2.rotate.
 */
public final class RayCaster
{
    private RayCaster()
    {
        // Static utility class - never instantiated.
    }

    public interface WallColorProvider
    {
        /** mapValue is the hit cell's value from the map grid (whatever a game uses to mean "wall type"); sideHit is true for an east/west-facing wall, false for north/south - callers typically darken one to fake simple directional shading. */
        Color colorFor(int mapValue, boolean sideHit);
    }

    public static void render(Graphics2D g2, int screenWidth, int screenHeight, int[][] map,
        double posX, double posY, double dirX, double dirY, double planeX, double planeY,
        WallColorProvider colors)
    {
        int mapRows = map.length;
        int mapCols = mapRows == 0 ? 0 : map[0].length;

        for (int screenColumn = 0; screenColumn < screenWidth; screenColumn++)
        {
            double cameraX = 2 * screenColumn / (double) screenWidth - 1;
            double rayDirX = dirX + planeX * cameraX;
            double rayDirY = dirY + planeY * cameraX;

            int mapX = (int) posX;
            int mapY = (int) posY;

            double deltaDistX = rayDirX == 0 ? Double.MAX_VALUE : Math.abs(1 / rayDirX);
            double deltaDistY = rayDirY == 0 ? Double.MAX_VALUE : Math.abs(1 / rayDirY);

            int stepX;
            int stepY;
            double sideDistX;
            double sideDistY;

            if (rayDirX < 0)
            {
                stepX = -1;
                sideDistX = (posX - mapX) * deltaDistX;
            }
            else
            {
                stepX = 1;
                sideDistX = (mapX + 1.0 - posX) * deltaDistX;
            }
            if (rayDirY < 0)
            {
                stepY = -1;
                sideDistY = (posY - mapY) * deltaDistY;
            }
            else
            {
                stepY = 1;
                sideDistY = (mapY + 1.0 - posY) * deltaDistY;
            }

            boolean hitWall = false;
            boolean sideHit = false;
            int hitMapValue = 1;

            // A ray that never finds a wall would loop forever - capping steps at the
            // map's own diagonal is a generous bound that still always terminates.
            int maxSteps = mapRows + mapCols + 2;
            for (int step = 0; step < maxSteps && !hitWall; step++)
            {
                if (sideDistX < sideDistY)
                {
                    sideDistX += deltaDistX;
                    mapX += stepX;
                    sideHit = false;
                }
                else
                {
                    sideDistY += deltaDistY;
                    mapY += stepY;
                    sideHit = true;
                }

                if (mapX < 0 || mapX >= mapCols || mapY < 0 || mapY >= mapRows)
                {
                    hitWall = true;
                    hitMapValue = 1;
                }
                else if (map[mapY][mapX] > 0)
                {
                    hitWall = true;
                    hitMapValue = map[mapY][mapX];
                }
            }

            double perpWallDist = sideHit
                ? (mapY - posY + (1 - stepY) / 2.0) / rayDirY
                : (mapX - posX + (1 - stepX) / 2.0) / rayDirX;
            perpWallDist = Math.max(perpWallDist, 1e-6);

            int lineHeight = (int) (screenHeight / perpWallDist);
            int drawStart = Math.max(0, screenHeight / 2 - lineHeight / 2);
            int drawEnd = Math.min(screenHeight - 1, screenHeight / 2 + lineHeight / 2);

            g2.setColor(colors.colorFor(hitMapValue, sideHit));
            g2.drawLine(screenColumn, drawStart, screenColumn, drawEnd);
        }
    }
}
