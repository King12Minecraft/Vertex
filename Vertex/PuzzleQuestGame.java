import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * PuzzleQuestGame
 * ---------------
 * The model for Puzzle Quest - a classic 4x4 sliding tile puzzle
 * (the "15-puzzle"). Tiles 1-15 plus one blank (represented as 0),
 * arranged in a 4x4 grid stored as a flat 16-element array. Shuffled
 * by performing random valid slides from the solved state - this
 * guarantees the result is always solvable (randomizing tile
 * positions directly can land in an unsolvable configuration for this
 * exact class of puzzle - sliding from solved avoids that entirely).
 */
public class PuzzleQuestGame
{
    public static final int SIZE = 4;
    private static final int SHUFFLE_MOVES = 150;

    private final int[] tiles = new int[SIZE * SIZE];
    private final Random random = new Random();
    private int moves = 0;
    private boolean solved = false;

    public PuzzleQuestGame()
    {
        reset();
    }

    private void reset()
    {
        for (int i = 0; i < tiles.length - 1; i++)
        {
            tiles[i] = i + 1;
        }
        tiles[tiles.length - 1] = 0;

        int lastBlank = tiles.length - 1;
        for (int i = 0; i < SHUFFLE_MOVES; i++)
        {
            List<Integer> movable = movableIndices();
            movable.remove(Integer.valueOf(lastBlank));
            if (movable.isEmpty())
            {
                movable = movableIndices();
            }
            int chosen = movable.get(random.nextInt(movable.size()));
            lastBlank = indexOf(0);
            swap(chosen, indexOf(0));
        }

        moves = 0;
        solved = false;
    }

    /** Attempts to slide the tile at index into the blank space - only works if adjacent. Returns true if the move happened. */
    public boolean tryMove(int index)
    {
        if (solved)
        {
            return false;
        }
        int blankIndex = indexOf(0);
        if (!isAdjacent(index, blankIndex))
        {
            return false;
        }

        swap(index, blankIndex);
        moves++;
        checkSolved();
        return true;
    }

    private void checkSolved()
    {
        for (int i = 0; i < tiles.length - 1; i++)
        {
            if (tiles[i] != i + 1)
            {
                return;
            }
        }
        solved = tiles[tiles.length - 1] == 0;
    }

    private boolean isAdjacent(int a, int b)
    {
        int ar = a / SIZE, ac = a % SIZE;
        int br = b / SIZE, bc = b % SIZE;
        return (ar == br && Math.abs(ac - bc) == 1) || (ac == bc && Math.abs(ar - br) == 1);
    }

    private List<Integer> movableIndices()
    {
        int blankIndex = indexOf(0);
        List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < tiles.length; i++)
        {
            if (isAdjacent(i, blankIndex))
            {
                result.add(i);
            }
        }
        return result;
    }

    private int indexOf(int value)
    {
        for (int i = 0; i < tiles.length; i++)
        {
            if (tiles[i] == value) return i;
        }
        return -1;
    }

    private void swap(int a, int b)
    {
        int tmp = tiles[a];
        tiles[a] = tiles[b];
        tiles[b] = tmp;
    }

    public int getTile(int index) { return tiles[index]; }
    public int getMoves() { return moves; }
    public boolean isSolved() { return solved; }

    public void shuffleAgain()
    {
        reset();
    }
}
