package games;

import java.util.Random;

/**
 * MinesweeperGame
 * ---------------
 * Standard Minesweeper rules, an original implementation (well-known,
 * decades-old, uncopyrightable game mechanics - no lockout on
 * building this from scratch). 9x9 board, 10 mines (classic
 * "Beginner" difficulty). Left-click reveals a cell - revealing a
 * cell with zero adjacent mines flood-fills outward automatically,
 * same as the original. Right-click toggles a flag. Win by revealing
 * every non-mine cell; lose immediately on revealing a mine.
 *
 * Mines are placed AFTER the first click, never under it or its
 * neighbors - a first-move loss feels unfair since there's no
 * information to have avoided it, and every real Minesweeper
 * implementation avoids this the same way.
 */
public class MinesweeperGame
{
    public static final int COLS = 9;
    public static final int ROWS = 9;
    public static final int MINE_COUNT = 10;

    public static final int HIDDEN = 0;
    public static final int REVEALED = 1;
    public static final int FLAGGED = 2;

    private final boolean[] mines = new boolean[COLS * ROWS];
    private final int[] state = new int[COLS * ROWS];
    private final int[] adjacentCounts = new int[COLS * ROWS];
    private boolean minesPlaced = false;
    private boolean gameOver = false;
    private boolean won = false;
    private int revealedCount = 0;

    public MinesweeperGame()
    {
        java.util.Arrays.fill(state, HIDDEN);
    }

    public boolean isGameOver() { return gameOver; }
    public boolean isWon() { return won; }
    public int getState(int index) { return state[index]; }
    public boolean isMine(int index) { return mines[index]; }
    public int getAdjacentCount(int index) { return adjacentCounts[index]; }

    /** Left-click - reveals a cell, flood-filling outward if it has no adjacent mines. First click of the game places the mines (never under this cell or its neighbors). No-op on an already-revealed or flagged cell, or after the game has ended. */
    public void reveal(int index)
    {
        if (gameOver || state[index] != HIDDEN) return;

        if (!minesPlaced)
        {
            placeMines(index);
            minesPlaced = true;
        }

        if (mines[index])
        {
            state[index] = REVEALED;
            gameOver = true;
            won = false;
            return;
        }

        floodReveal(index);

        if (revealedCount == (COLS * ROWS - MINE_COUNT))
        {
            gameOver = true;
            won = true;
        }
    }

    /** Right-click - toggles a flag on a hidden cell, no-op once revealed or after the game ends. */
    public void toggleFlag(int index)
    {
        if (gameOver || state[index] == REVEALED) return;
        state[index] = (state[index] == FLAGGED) ? HIDDEN : FLAGGED;
    }

    private void floodReveal(int startIndex)
    {
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<Integer>();
        queue.add(startIndex);

        while (!queue.isEmpty())
        {
            int index = queue.poll();
            if (state[index] == REVEALED) continue;

            state[index] = REVEALED;
            revealedCount++;

            if (adjacentCounts[index] == 0)
            {
                for (int neighbor : neighborsOf(index))
                {
                    if (state[neighbor] == HIDDEN && !mines[neighbor])
                    {
                        queue.add(neighbor);
                    }
                }
            }
        }
    }

    private void placeMines(int excludeIndex)
    {
        java.util.Set<Integer> excluded = new java.util.HashSet<Integer>();
        excluded.add(excludeIndex);
        for (int neighbor : neighborsOf(excludeIndex)) excluded.add(neighbor);

        Random random = new Random();
        int placed = 0;
        while (placed < MINE_COUNT)
        {
            int index = random.nextInt(COLS * ROWS);
            if (mines[index] || excluded.contains(index)) continue;
            mines[index] = true;
            placed++;
        }

        for (int i = 0; i < COLS * ROWS; i++)
        {
            if (mines[i]) continue;
            int count = 0;
            for (int neighbor : neighborsOf(i))
            {
                if (mines[neighbor]) count++;
            }
            adjacentCounts[i] = count;
        }
    }

    private int[] neighborsOf(int index)
    {
        int row = index / COLS, col = index % COLS;
        java.util.List<Integer> result = new java.util.ArrayList<Integer>();
        for (int dr = -1; dr <= 1; dr++)
        {
            for (int dc = -1; dc <= 1; dc++)
            {
                if (dr == 0 && dc == 0) continue;
                int r = row + dr, c = col + dc;
                if (r >= 0 && r < ROWS && c >= 0 && c < COLS)
                {
                    result.add(r * COLS + c);
                }
            }
        }
        int[] array = new int[result.size()];
        for (int i = 0; i < array.length; i++) array[i] = result.get(i);
        return array;
    }
}
