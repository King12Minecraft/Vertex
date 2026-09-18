package games;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * BubbleShooterGame
 * ------------------
 * An original implementation of the well-known, uncopyrightable "aim and
 * shoot a colored ball into a hex grid, clear groups of 3+ of the same
 * color, drop anything left floating" mechanic - decades old across
 * countless independent clones, no relation to any specific existing
 * game's code, art, or level layout. Continuous pixel-space movement for
 * the flying bubble (aim angle, wall bounces) but the settled bubbles
 * live on a hex-offset grid (the classic "odd-r" layout: odd rows are
 * shifted right by half a cell). BubbleShooterWindow drives tick() on a
 * fixed-rate timer and handles rendering/input.
 *
 * Fairness: the bubble you're given to shoot is always a color that
 * still exists somewhere on the board (see nextRandomColor) - same
 * spirit as the pipe-gap fairness fix in FlappyBirdGame - so the board
 * is always fully clearable, never stuck with a color no longer present.
 */
public class BubbleShooterGame
{
    public static final int BOARD_WIDTH = 480;
    public static final int BOARD_HEIGHT = 640;

    public static final int BUBBLE_RADIUS = 18;
    public static final int BUBBLE_DIAMETER = BUBBLE_RADIUS * 2;
    private static final int MARGIN = 24;
    public static final int COLS = 11;
    public static final int MAX_ROWS = 14;
    private static final int ROW_HEIGHT = 31;
    private static final int TOP_MARGIN = 22;

    public static final int STARTING_ROWS = 8;
    public static final int COLOR_COUNT = 5;
    /** A bubble snapping this far down (row index, 0 = top) or deeper ends the run - it's gotten too close to the shooter. */
    private static final int LOSE_ROW = MAX_ROWS - 3;

    private static final double SHOT_SPEED = 11.0;
    public static final double MIN_AIM_DEG = -70;
    public static final double MAX_AIM_DEG = 70;

    private final Random random = new Random();
    private final int[][] grid = new int[MAX_ROWS][COLS]; // -1 = empty

    private double aimAngleDeg;
    private int currentColor;
    private int nextColor;

    private boolean inFlight;
    private double flyingX, flyingY, flyingVx, flyingVy;
    private int flyingColor;

    private double shooterX;
    private double shooterY;

    private int score;
    private boolean won;
    private boolean lost;

    public BubbleShooterGame()
    {
        for (int r = 0; r < MAX_ROWS; r++)
        {
            for (int c = 0; c < COLS; c++)
            {
                grid[r][c] = -1;
            }
        }
        for (int r = 0; r < STARTING_ROWS; r++)
        {
            for (int c = 0; c < COLS; c++)
            {
                grid[r][c] = random.nextInt(COLOR_COUNT);
            }
        }

        shooterX = BOARD_WIDTH / 2.0;
        shooterY = BOARD_HEIGHT - 40;
        currentColor = nextRandomColor();
        nextColor = nextRandomColor();
    }

    /** Only ever hands out a color that's still somewhere on the board, so the board can never get stuck holding a color that no longer exists (falls back to any color once the board is empty, which only matters after the win is already set). */
    private int nextRandomColor()
    {
        Set<Integer> present = new HashSet<Integer>();
        for (int r = 0; r < MAX_ROWS; r++)
        {
            for (int c = 0; c < COLS; c++)
            {
                if (grid[r][c] >= 0) present.add(grid[r][c]);
            }
        }
        if (present.isEmpty())
        {
            return random.nextInt(COLOR_COUNT);
        }
        List<Integer> options = new ArrayList<Integer>(present);
        return options.get(random.nextInt(options.size()));
    }

    public void setAimAngle(double deg)
    {
        aimAngleDeg = Math.max(MIN_AIM_DEG, Math.min(MAX_AIM_DEG, deg));
    }

    public void adjustAim(double deltaDeg)
    {
        setAimAngle(aimAngleDeg + deltaDeg);
    }

    public void shoot()
    {
        if (inFlight || isOver()) return;

        double rad = Math.toRadians(aimAngleDeg);
        flyingVx = SHOT_SPEED * Math.sin(rad);
        flyingVy = -SHOT_SPEED * Math.cos(rad);
        flyingX = shooterX;
        flyingY = shooterY;
        flyingColor = currentColor;
        inFlight = true;

        currentColor = nextColor;
        nextColor = nextRandomColor();
    }

    public void tick()
    {
        if (isOver() || !inFlight) return;

        flyingX += flyingVx;
        flyingY += flyingVy;

        if (flyingX - BUBBLE_RADIUS < 0)
        {
            flyingX = BUBBLE_RADIUS;
            flyingVx = -flyingVx;
        }
        else if (flyingX + BUBBLE_RADIUS > BOARD_WIDTH)
        {
            flyingX = BOARD_WIDTH - BUBBLE_RADIUS;
            flyingVx = -flyingVx;
        }

        if (flyingY - BUBBLE_RADIUS <= TOP_MARGIN - BUBBLE_RADIUS)
        {
            snapAndResolve();
            return;
        }

        for (int r = 0; r < MAX_ROWS; r++)
        {
            for (int c = 0; c < COLS; c++)
            {
                if (grid[r][c] < 0) continue;
                double cx = xCenter(r, c), cy = yCenter(r);
                double dx = flyingX - cx, dy = flyingY - cy;
                if (dx * dx + dy * dy < BUBBLE_DIAMETER * BUBBLE_DIAMETER * 0.9)
                {
                    snapAndResolve();
                    return;
                }
            }
        }
    }

    private void snapAndResolve()
    {
        int[] cell = nearestEmptyCell(flyingX, flyingY);
        inFlight = false;
        if (cell == null)
        {
            // Grid is entirely full near the shot - treat as an overflow loss rather than dropping the shot silently.
            lost = true;
            return;
        }

        int row = cell[0], col = cell[1];
        grid[row][col] = flyingColor;
        resolvePops(row, col);

        if (!won && row >= LOSE_ROW)
        {
            lost = true;
        }
    }

    private int[] nearestEmptyCell(double x, double y)
    {
        int approxRow = Math.max(0, Math.min(MAX_ROWS - 1, (int) Math.round((y - yCenter(0)) / ROW_HEIGHT)));

        int[] best = null;
        double bestDist = Double.MAX_VALUE;
        for (int r = Math.max(0, approxRow - 2); r <= Math.min(MAX_ROWS - 1, approxRow + 2); r++)
        {
            for (int c = 0; c < COLS; c++)
            {
                if (grid[r][c] >= 0) continue;
                double cx = xCenter(r, c), cy = yCenter(r);
                double dx = x - cx, dy = y - cy;
                double dist = dx * dx + dy * dy;
                if (dist < bestDist)
                {
                    bestDist = dist;
                    best = new int[] { r, c };
                }
            }
        }
        return best;
    }

    /** Standard "odd-r" offset-grid neighbors: odd rows are shifted right by half a cell, so their up/down neighbor columns differ from even rows'. */
    private List<int[]> neighborsOf(int row, int col)
    {
        List<int[]> result = new ArrayList<int[]>();
        int[][] deltas = (row % 2 == 0)
            ? new int[][] { {0, -1}, {0, 1}, {-1, -1}, {-1, 0}, {1, -1}, {1, 0} }
            : new int[][] { {0, -1}, {0, 1}, {-1, 0}, {-1, 1}, {1, 0}, {1, 1} };

        for (int[] d : deltas)
        {
            int nr = row + d[0], nc = col + d[1];
            if (nr >= 0 && nr < MAX_ROWS && nc >= 0 && nc < COLS)
            {
                result.add(new int[] { nr, nc });
            }
        }
        return result;
    }

    private void resolvePops(int row, int col)
    {
        int color = grid[row][col];
        Set<Long> group = new HashSet<Long>();
        Deque<int[]> queue = new ArrayDeque<int[]>();
        queue.add(new int[] { row, col });
        group.add(key(row, col));

        while (!queue.isEmpty())
        {
            int[] cur = queue.poll();
            for (int[] n : neighborsOf(cur[0], cur[1]))
            {
                long k = key(n[0], n[1]);
                if (group.contains(k)) continue;
                if (grid[n[0]][n[1]] == color)
                {
                    group.add(k);
                    queue.add(n);
                }
            }
        }

        if (group.size() < 3)
        {
            return;
        }

        for (long k : group)
        {
            int r = (int) (k >> 16), c = (int) (k & 0xFFFF);
            grid[r][c] = -1;
        }
        score += group.size() * 10;

        dropFloatingBubbles();
        checkWin();
    }

    /** Anything not connected, directly or indirectly, back to the ceiling row is floating and falls - the classic bonus-scoring cascade every bubble shooter has. */
    private void dropFloatingBubbles()
    {
        Set<Long> connected = new HashSet<Long>();
        Deque<int[]> queue = new ArrayDeque<int[]>();
        for (int c = 0; c < COLS; c++)
        {
            if (grid[0][c] >= 0)
            {
                connected.add(key(0, c));
                queue.add(new int[] { 0, c });
            }
        }
        while (!queue.isEmpty())
        {
            int[] cur = queue.poll();
            for (int[] n : neighborsOf(cur[0], cur[1]))
            {
                long k = key(n[0], n[1]);
                if (connected.contains(k)) continue;
                if (grid[n[0]][n[1]] >= 0)
                {
                    connected.add(k);
                    queue.add(n);
                }
            }
        }

        int dropped = 0;
        for (int r = 0; r < MAX_ROWS; r++)
        {
            for (int c = 0; c < COLS; c++)
            {
                if (grid[r][c] >= 0 && !connected.contains(key(r, c)))
                {
                    grid[r][c] = -1;
                    dropped++;
                }
            }
        }
        score += dropped * 20;
    }

    private void checkWin()
    {
        for (int r = 0; r < MAX_ROWS; r++)
        {
            for (int c = 0; c < COLS; c++)
            {
                if (grid[r][c] >= 0) return;
            }
        }
        won = true;
        score += 200;
    }

    private static long key(int row, int col)
    {
        return ((long) row << 16) | (col & 0xFFFF);
    }

    public double xCenter(int row, int col)
    {
        double base = MARGIN + BUBBLE_RADIUS + col * BUBBLE_DIAMETER;
        return (row % 2 == 0) ? base : base + BUBBLE_RADIUS;
    }

    public double yCenter(int row)
    {
        return TOP_MARGIN + BUBBLE_RADIUS + row * ROW_HEIGHT;
    }

    public int colorAt(int row, int col) { return grid[row][col]; }
    public double getShooterX() { return shooterX; }
    public double getShooterY() { return shooterY; }
    public double getAimAngleDeg() { return aimAngleDeg; }
    public int getCurrentColor() { return currentColor; }
    public int getNextColor() { return nextColor; }
    public boolean isInFlight() { return inFlight; }
    public double getFlyingX() { return flyingX; }
    public double getFlyingY() { return flyingY; }
    public int getFlyingColor() { return flyingColor; }
    public int getScore() { return score; }
    public boolean isWon() { return won; }
    public boolean isLost() { return lost; }
    public boolean isOver() { return won || lost; }
}
