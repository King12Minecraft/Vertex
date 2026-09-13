package games;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * SudokuGame
 * ----------
 * Standard Sudoku rules, an original implementation of the
 * well-known, uncopyrightable puzzle. 9x9 grid, generated fresh each
 * game: a complete valid solution is built via randomized
 * backtracking, then a subset of cells is cleared to make the
 * puzzle - the cleared cells are what the player fills in, the rest
 * are fixed "given" digits. Win by correctly filling every cell
 * (checked against the generated solution, not just structural
 * validity, since the fastest way to confirm a finished grid is
 * correct is comparing it to the one true solution used to build it).
 */
public class SudokuGame
{
    public static final int SIZE = 9;
    public static final int BOX_SIZE = 3;
    private static final int CELLS_TO_REMOVE = 45;

    private final int[] solution = new int[SIZE * SIZE];
    private final int[] grid = new int[SIZE * SIZE];
    private final boolean[] fixed = new boolean[SIZE * SIZE];
    private boolean won = false;

    public SudokuGame()
    {
        generateSolution();
        System.arraycopy(solution, 0, grid, 0, solution.length);
        removeCells();
    }

    public boolean isFixed(int index) { return fixed[index]; }
    public int getValue(int index) { return grid[index]; }
    public boolean isWon() { return won; }

    /** No-op on a fixed (given) cell - those were never meant to be editable. value 0 clears the cell. */
    public void setValue(int index, int value)
    {
        if (fixed[index]) return;
        grid[index] = value;
        checkWin();
    }

    private void checkWin()
    {
        for (int i = 0; i < grid.length; i++)
        {
            if (grid[i] != solution[i])
            {
                won = false;
                return;
            }
        }
        won = true;
    }

    private void generateSolution()
    {
        java.util.Arrays.fill(solution, 0);
        fillCell(0);
    }

    /** Randomized backtracking - tries digits 1-9 in a shuffled order at each empty cell, which is what makes each generated puzzle different rather than always producing the same base solution. */
    private boolean fillCell(int index)
    {
        if (index == SIZE * SIZE) return true;

        int row = index / SIZE, col = index % SIZE;
        List<Integer> candidates = new ArrayList<Integer>();
        for (int v = 1; v <= 9; v++) candidates.add(v);
        Collections.shuffle(candidates);

        for (int value : candidates)
        {
            if (isSafe(row, col, value))
            {
                solution[index] = value;
                if (fillCell(index + 1)) return true;
                solution[index] = 0;
            }
        }
        return false;
    }

    private boolean isSafe(int row, int col, int value)
    {
        for (int c = 0; c < SIZE; c++)
        {
            if (solution[row * SIZE + c] == value) return false;
        }
        for (int r = 0; r < SIZE; r++)
        {
            if (solution[r * SIZE + col] == value) return false;
        }
        int boxRow = (row / BOX_SIZE) * BOX_SIZE, boxCol = (col / BOX_SIZE) * BOX_SIZE;
        for (int r = boxRow; r < boxRow + BOX_SIZE; r++)
        {
            for (int c = boxCol; c < boxCol + BOX_SIZE; c++)
            {
                if (solution[r * SIZE + c] == value) return false;
            }
        }
        return true;
    }

    private void removeCells()
    {
        java.util.Arrays.fill(fixed, true);
        List<Integer> indices = new ArrayList<Integer>();
        for (int i = 0; i < SIZE * SIZE; i++) indices.add(i);
        Collections.shuffle(indices);

        for (int i = 0; i < CELLS_TO_REMOVE; i++)
        {
            int index = indices.get(i);
            grid[index] = 0;
            fixed[index] = false;
        }
    }
}
