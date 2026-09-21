package ai.grid;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * GridPathfinder
 * --------------
 * Generic breadth-first-search utilities for four-directional grid
 * games - the shared "pathfinding" layer for roadmap item 4. Doesn't
 * know anything about a specific game's board representation: the
 * caller supplies a GridObstacle callback ("is this cell blocked?")
 * plus the grid's dimensions, and gets back either the single best
 * next step toward a target (firstStepTowards) or a full distance
 * field from a source cell (distanceField - useful for "flee toward
 * whichever open cell is FARTHEST from X," using true graph distance
 * rather than a straight-line approximation that can be wrong around
 * obstacles).
 *
 * Deliberately advisory-only, same convention as ai.BotStrategy - this
 * never touches game state, it only answers questions about the grid
 * it's handed. Recomputing a full BFS from scratch on every call is
 * the honest, simplest-that-works choice for the grid sizes Vertex's
 * games actually use (Maze Chase's board is 19x15 = 285 cells) - a
 * few hundred cells, tens of times a second, is nothing to worry
 * about. If a much bigger grid game is ever added, revisit before
 * assuming this scales as-is.
 */
public final class GridPathfinder
{
    private GridPathfinder()
    {
        // Static utility class - never instantiated.
    }

    public interface GridObstacle
    {
        boolean isBlocked(int row, int col);
    }

    private static final GridDirection[] DIRECTIONS = GridDirection.values();

    /**
     * Returns the direction to step NOW to make progress along a
     * shortest path from (startRow,startCol) to (targetRow,targetCol),
     * or null if the target is unreachable, blocked, or already
     * reached.
     *
     * preferNotFirst, if non-null, is a direction the caller would
     * rather not take as the very first step (e.g. "don't reverse
     * straight back the way you came") - only influences which
     * equally-short first step is chosen when more than one exists;
     * never trades away a strictly shorter path, and is still used if
     * it's genuinely the only way to make progress.
     */
    public static GridDirection firstStepTowards(int rows, int cols, GridObstacle obstacle,
        int startRow, int startCol, int targetRow, int targetCol, GridDirection preferNotFirst)
    {
        if (rows <= 0 || cols <= 0) return null;
        if (startRow == targetRow && startCol == targetCol) return null;
        if (obstacle.isBlocked(targetRow, targetCol)) return null;

        int[][] parentRow = new int[rows][cols];
        int[][] parentCol = new int[rows][cols];
        GridDirection[][] arrivedVia = new GridDirection[rows][cols];
        boolean[][] visited = new boolean[rows][cols];

        Deque<int[]> queue = new ArrayDeque<int[]>();
        visited[startRow][startCol] = true;
        queue.add(new int[] { startRow, startCol });

        boolean found = false;
        while (!queue.isEmpty() && !found)
        {
            int[] cur = queue.poll();
            boolean isStart = cur[0] == startRow && cur[1] == startCol;

            for (GridDirection d : orderFor(isStart, preferNotFirst))
            {
                int nr = cur[0] + d.dRow;
                int nc = cur[1] + d.dCol;
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                if (visited[nr][nc] || obstacle.isBlocked(nr, nc)) continue;

                visited[nr][nc] = true;
                parentRow[nr][nc] = cur[0];
                parentCol[nr][nc] = cur[1];
                arrivedVia[nr][nc] = d;
                if (nr == targetRow && nc == targetCol)
                {
                    found = true;
                    break;
                }
                queue.add(new int[] { nr, nc });
            }
        }

        if (!visited[targetRow][targetCol]) return null;

        // Walk the parent chain backward from the target until we reach the
        // cell adjacent to start - that cell's arrival direction is the
        // shortest path's FIRST step.
        int row = targetRow, col = targetCol;
        while (!(parentRow[row][col] == startRow && parentCol[row][col] == startCol))
        {
            int pr = parentRow[row][col];
            int pc = parentCol[row][col];
            row = pr;
            col = pc;
        }
        return arrivedVia[row][col];
    }

    /**
     * Full BFS distance (in steps) from (sourceRow,sourceCol) to every
     * reachable cell; unreachable cells (and the source itself, if it's
     * blocked) come back as Integer.MAX_VALUE.
     */
    public static int[][] distanceField(int rows, int cols, GridObstacle obstacle, int sourceRow, int sourceCol)
    {
        int[][] distance = new int[Math.max(rows, 0)][Math.max(cols, 0)];
        for (int[] row : distance)
        {
            Arrays.fill(row, Integer.MAX_VALUE);
        }
        if (rows <= 0 || cols <= 0 || obstacle.isBlocked(sourceRow, sourceCol))
        {
            return distance;
        }

        distance[sourceRow][sourceCol] = 0;
        Deque<int[]> queue = new ArrayDeque<int[]>();
        queue.add(new int[] { sourceRow, sourceCol });

        while (!queue.isEmpty())
        {
            int[] cur = queue.poll();
            for (GridDirection d : DIRECTIONS)
            {
                int nr = cur[0] + d.dRow;
                int nc = cur[1] + d.dCol;
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                if (obstacle.isBlocked(nr, nc) || distance[nr][nc] != Integer.MAX_VALUE) continue;

                distance[nr][nc] = distance[cur[0]][cur[1]] + 1;
                queue.add(new int[] { nr, nc });
            }
        }
        return distance;
    }

    private static GridDirection[] orderFor(boolean isStartNode, GridDirection preferNotFirst)
    {
        if (!isStartNode || preferNotFirst == null) return DIRECTIONS;

        GridDirection[] ordered = new GridDirection[DIRECTIONS.length];
        int i = 0;
        for (GridDirection d : DIRECTIONS)
        {
            if (d != preferNotFirst)
            {
                ordered[i++] = d;
            }
        }
        ordered[i] = preferNotFirst;
        return ordered;
    }
}
