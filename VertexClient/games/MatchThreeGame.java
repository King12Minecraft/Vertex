package games;

import java.util.Random;

/**
 * MatchThreeGame
 * --------------
 * An original implementation of the well-known, uncopyrightable
 * match-3 puzzle mechanic (swap two adjacent gems, matches of 3+
 * same-colored gems in a row or column clear and score points, gems
 * above fall to fill the gap, new random gems drop in from the top).
 * No relation to any specific existing match-3 game's code, art, or
 * level design - just the generic mechanic itself.
 *
 * Single-player, offline, move-limited: the player gets a fixed
 * number of swaps and is scored on total gems cleared (with a bonus
 * for bigger single matches and for chain reactions), same "score in
 * a limited attempt" shape as WhackAMoleGame/AimTrainer.
 */
public class MatchThreeGame
{
    public static final int SIZE = 8;
    public static final int COLORS = 6;
    public static final int STARTING_MOVES = 20;

    private final int[] grid = new int[SIZE * SIZE];
    private final Random random = new Random();
    private int score;
    private int movesLeft;
    private boolean lastSwapValid;

    public MatchThreeGame()
    {
        movesLeft = STARTING_MOVES;
        fillBoardNoMatches();
    }

    public int getCell(int row, int col) { return grid[row * SIZE + col]; }
    public int getScore() { return score; }
    public int getMovesLeft() { return movesLeft; }
    public boolean isOver() { return movesLeft <= 0; }
    public boolean wasLastSwapValid() { return lastSwapValid; }

    /** Fills every cell with a random color, re-rolling any cell that would form an immediate 3-in-a-row with its already-placed left/up neighbors - so the very first board the player sees has no free matches sitting on it. */
    private void fillBoardNoMatches()
    {
        for (int row = 0; row < SIZE; row++)
        {
            for (int col = 0; col < SIZE; col++)
            {
                int color;
                do
                {
                    color = random.nextInt(COLORS);
                }
                while (createsMatchAt(row, col, color));
                grid[row * SIZE + col] = color;
            }
        }
    }

    private boolean createsMatchAt(int row, int col, int color)
    {
        if (col >= 2 && grid[row * SIZE + col - 1] == color && grid[row * SIZE + col - 2] == color)
        {
            return true;
        }
        if (row >= 2 && grid[(row - 1) * SIZE + col] == color && grid[(row - 2) * SIZE + col] == color)
        {
            return true;
        }
        return false;
    }

    /**
     * Attempts to swap two adjacent cells. Returns true if the swap was
     * accepted (formed at least one match, so it's applied, resolved, and
     * costs a move) - false if it didn't form a match (swapped back,
     * still costs a move, same as a real match-3's "invalid move" swap-
     * and-bounce-back). Non-adjacent cells are rejected without costing
     * a move at all.
     */
    public boolean trySwap(int row1, int col1, int row2, int col2)
    {
        if (isOver()) return false;
        if (!isAdjacent(row1, col1, row2, col2)) return false;

        swap(row1, col1, row2, col2);

        if (hasAnyMatch())
        {
            movesLeft--;
            lastSwapValid = true;
            resolveMatchesWithCascade();
            return true;
        }
        else
        {
            swap(row1, col1, row2, col2);
            movesLeft--;
            lastSwapValid = false;
            return false;
        }
    }

    private boolean isAdjacent(int row1, int col1, int row2, int col2)
    {
        int dr = Math.abs(row1 - row2), dc = Math.abs(col1 - col2);
        return (dr == 1 && dc == 0) || (dr == 0 && dc == 1);
    }

    private void swap(int row1, int col1, int row2, int col2)
    {
        int i1 = row1 * SIZE + col1, i2 = row2 * SIZE + col2;
        int tmp = grid[i1];
        grid[i1] = grid[i2];
        grid[i2] = tmp;
    }

    /** Clears every matched run, drops the columns, refills from the top, and repeats as long as the refill itself produced new matches - a chain reaction. Each successive chain step scores extra (chainMultiplier), rewarding cascades over a single flat clear. */
    private void resolveMatchesWithCascade()
    {
        int chainMultiplier = 1;
        boolean[] toClear = findMatches();
        while (anyTrue(toClear))
        {
            int cleared = clearAndScore(toClear, chainMultiplier);
            score += cleared;
            dropAndRefill();
            chainMultiplier++;
            toClear = findMatches();
        }
    }

    private boolean anyTrue(boolean[] arr)
    {
        for (boolean b : arr) if (b) return true;
        return false;
    }

    /** Marks every cell that's part of a run of 3+ same-colored cells, horizontally or vertically. */
    private boolean[] findMatches()
    {
        boolean[] toClear = new boolean[SIZE * SIZE];

        for (int row = 0; row < SIZE; row++)
        {
            int runStart = 0;
            for (int col = 1; col <= SIZE; col++)
            {
                boolean sameAsPrev = col < SIZE && grid[row * SIZE + col] == grid[row * SIZE + col - 1];
                if (!sameAsPrev)
                {
                    if (col - runStart >= 3)
                    {
                        for (int c = runStart; c < col; c++) toClear[row * SIZE + c] = true;
                    }
                    runStart = col;
                }
            }
        }

        for (int col = 0; col < SIZE; col++)
        {
            int runStart = 0;
            for (int row = 1; row <= SIZE; row++)
            {
                boolean sameAsPrev = row < SIZE && grid[row * SIZE + col] == grid[(row - 1) * SIZE + col];
                if (!sameAsPrev)
                {
                    if (row - runStart >= 3)
                    {
                        for (int r = runStart; r < row; r++) toClear[r * SIZE + col] = true;
                    }
                    runStart = row;
                }
            }
        }

        return toClear;
    }

    private boolean hasAnyMatch()
    {
        return anyTrue(findMatches());
    }

    /** Marked cells become empty (-1); returns how many were cleared, weighted by the chain multiplier so later cascade steps are worth more. */
    private int clearAndScore(boolean[] toClear, int chainMultiplier)
    {
        int count = 0;
        for (int i = 0; i < grid.length; i++)
        {
            if (toClear[i])
            {
                grid[i] = -1;
                count++;
            }
        }
        return count * 10 * chainMultiplier;
    }

    /** Column by column: compact the surviving gems downward (gravity), then fill every remaining empty cell above them with a fresh random color. */
    private void dropAndRefill()
    {
        for (int col = 0; col < SIZE; col++)
        {
            int writeRow = SIZE - 1;
            for (int row = SIZE - 1; row >= 0; row--)
            {
                int value = grid[row * SIZE + col];
                if (value != -1)
                {
                    grid[writeRow * SIZE + col] = value;
                    writeRow--;
                }
            }
            for (int row = writeRow; row >= 0; row--)
            {
                grid[row * SIZE + col] = random.nextInt(COLORS);
            }
        }
    }
}
