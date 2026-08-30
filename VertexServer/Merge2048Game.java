import java.util.Random;

/**
 * Merge2048Game
 * -------------
 * Model for 2048 - a 4x4 grid (flat 16-element array, 0 = empty),
 * standard merge rules: sliding a direction merges equal adjacent
 * tiles once per move, then spawns a new 2 or 4 in a random empty
 * cell. Game over when no move is possible in any direction.
 */
public class Merge2048Game
{
    public static final int SIZE = 4;

    private final int[] tiles = new int[SIZE * SIZE];
    private final Random random = new Random();
    private int score = 0;
    private boolean gameOver = false;

    public Merge2048Game()
    {
        spawnTile();
        spawnTile();
    }

    public int getTile(int row, int col) { return tiles[row * SIZE + col]; }
    public int getScore() { return score; }
    public boolean isGameOver() { return gameOver; }

    public boolean moveLeft()  { return move(0, -1); }
    public boolean moveRight() { return move(0, 1); }
    public boolean moveUp()    { return move(-1, 0); }
    public boolean moveDown()  { return move(1, 0); }

    /** Slides every row/column in the given direction, merging equal tiles. Returns true if anything actually moved (so the caller knows whether to spawn a new tile). */
    private boolean move(int rowDir, int colDir)
    {
        boolean moved = false;
        boolean[] merged = new boolean[tiles.length];

        int start = (rowDir == 1 || colDir == 1) ? SIZE - 1 : 0;
        int end = (rowDir == 1 || colDir == 1) ? -1 : SIZE;
        int step = (rowDir == 1 || colDir == 1) ? -1 : 1;

        for (int line = 0; line < SIZE; line++)
        {
            for (int i = start; i != end; i += step)
            {
                int row = (rowDir != 0) ? i : line;
                int col = (colDir != 0) ? i : line;
                if (tiles[row * SIZE + col] == 0)
                {
                    continue;
                }

                int newRow = row;
                int newCol = col;
                while (true)
                {
                    int nextRow = newRow + rowDir;
                    int nextCol = newCol + colDir;
                    if (nextRow < 0 || nextRow >= SIZE || nextCol < 0 || nextCol >= SIZE)
                    {
                        break;
                    }
                    int nextIndex = nextRow * SIZE + nextCol;
                    int curIndex = newRow * SIZE + newCol;

                    if (tiles[nextIndex] == 0)
                    {
                        tiles[nextIndex] = tiles[curIndex];
                        tiles[curIndex] = 0;
                        newRow = nextRow;
                        newCol = nextCol;
                        moved = true;
                    }
                    else if (tiles[nextIndex] == tiles[curIndex] && !merged[nextIndex] && !merged[curIndex])
                    {
                        tiles[nextIndex] *= 2;
                        score += tiles[nextIndex];
                        tiles[curIndex] = 0;
                        merged[nextIndex] = true;
                        moved = true;
                        break;
                    }
                    else
                    {
                        break;
                    }
                }
            }
        }

        if (moved)
        {
            spawnTile();
            if (!hasAnyMove())
            {
                gameOver = true;
            }
        }
        return moved;
    }

    private void spawnTile()
    {
        java.util.List<Integer> empty = new java.util.ArrayList<Integer>();
        for (int i = 0; i < tiles.length; i++)
        {
            if (tiles[i] == 0) empty.add(i);
        }
        if (empty.isEmpty())
        {
            return;
        }
        int index = empty.get(random.nextInt(empty.size()));
        tiles[index] = random.nextInt(10) == 0 ? 4 : 2;
    }

    private boolean hasAnyMove()
    {
        for (int i = 0; i < tiles.length; i++)
        {
            if (tiles[i] == 0)
            {
                return true;
            }
        }
        for (int row = 0; row < SIZE; row++)
        {
            for (int col = 0; col < SIZE; col++)
            {
                int value = tiles[row * SIZE + col];
                if (col < SIZE - 1 && tiles[row * SIZE + col + 1] == value) return true;
                if (row < SIZE - 1 && tiles[(row + 1) * SIZE + col] == value) return true;
            }
        }
        return false;
    }
}
