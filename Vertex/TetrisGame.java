import java.util.Random;

/**
 * TetrisGame
 * ----------
 * Classic falling-block puzzle - 10x20 grid, the 7 standard
 * tetrominoes (I/O/T/S/Z/J/L), basic rotation (naive - rotate if the
 * new orientation doesn't collide, no full SRS wall-kick system).
 * Purely single-player, no server involvement in gameplay.
 */
public class TetrisGame
{
    public static final int COLS = 10;
    public static final int ROWS = 20;

    /** [pieceType][rotation][cellIndex] = {row, col} within a 4x4 box. */
    private static final int[][][][] PIECES = {
        // I
        {
            {{1,0},{1,1},{1,2},{1,3}},
            {{0,2},{1,2},{2,2},{3,2}},
            {{2,0},{2,1},{2,2},{2,3}},
            {{0,1},{1,1},{2,1},{3,1}}
        },
        // O
        {
            {{0,1},{0,2},{1,1},{1,2}},
            {{0,1},{0,2},{1,1},{1,2}},
            {{0,1},{0,2},{1,1},{1,2}},
            {{0,1},{0,2},{1,1},{1,2}}
        },
        // T
        {
            {{0,1},{1,0},{1,1},{1,2}},
            {{0,1},{1,1},{1,2},{2,1}},
            {{1,0},{1,1},{1,2},{2,1}},
            {{0,1},{1,0},{1,1},{2,1}}
        },
        // S
        {
            {{0,1},{0,2},{1,0},{1,1}},
            {{0,1},{1,1},{1,2},{2,2}},
            {{1,1},{1,2},{2,0},{2,1}},
            {{0,0},{1,0},{1,1},{2,1}}
        },
        // Z
        {
            {{0,0},{0,1},{1,1},{1,2}},
            {{0,2},{1,1},{1,2},{2,1}},
            {{1,0},{1,1},{2,1},{2,2}},
            {{0,1},{1,0},{1,1},{2,0}}
        },
        // J
        {
            {{0,0},{1,0},{1,1},{1,2}},
            {{0,1},{0,2},{1,1},{2,1}},
            {{1,0},{1,1},{1,2},{2,2}},
            {{0,1},{1,1},{2,0},{2,1}}
        },
        // L
        {
            {{0,2},{1,0},{1,1},{1,2}},
            {{0,1},{1,1},{2,1},{2,2}},
            {{1,0},{1,1},{1,2},{2,0}},
            {{0,0},{0,1},{1,1},{2,1}}
        }
    };

    private final int[][] grid = new int[ROWS][COLS];
    private final Random random = new Random();

    private int currentType;
    private int currentRotation;
    private int currentX;
    private int currentY;
    private int nextType;

    private int score = 0;
    private int linesCleared = 0;
    private boolean gameOver = false;
    private int frameCount = 0;
    private int dropInterval = 40;

    public TetrisGame()
    {
        nextType = random.nextInt(7);
        spawnPiece();
    }

    /** Used for the next-piece preview - callers outside this class never touch rotation state. */
    public static int[][] shapeFor(int type, int rotation)
    {
        return PIECES[type][rotation];
    }

    private void spawnPiece()
    {
        currentType = nextType;
        nextType = random.nextInt(7);
        currentRotation = 0;
        currentX = 3;
        currentY = 0;

        if (collides(currentX, currentY, currentRotation))
        {
            gameOver = true;
        }
    }

    private boolean collides(int x, int y, int rotation)
    {
        int[][] cells = PIECES[currentType][rotation];
        for (int i = 0; i < cells.length; i++)
        {
            int r = y + cells[i][0];
            int c = x + cells[i][1];
            if (c < 0 || c >= COLS || r >= ROWS) return true;
            if (r >= 0 && grid[r][c] != 0) return true;
        }
        return false;
    }

    public void moveLeft()
    {
        if (!gameOver && !collides(currentX - 1, currentY, currentRotation)) currentX--;
    }

    public void moveRight()
    {
        if (!gameOver && !collides(currentX + 1, currentY, currentRotation)) currentX++;
    }

    public void rotate()
    {
        if (gameOver) return;
        int newRotation = (currentRotation + 1) % 4;
        if (!collides(currentX, currentY, newRotation))
        {
            currentRotation = newRotation;
        }
    }

    public void softDrop()
    {
        if (gameOver) return;
        if (!collides(currentX, currentY + 1, currentRotation))
        {
            currentY++;
            score++;
        }
        else
        {
            lockPiece();
        }
    }

    public void hardDrop()
    {
        if (gameOver) return;
        while (!collides(currentX, currentY + 1, currentRotation))
        {
            currentY++;
            score += 2;
        }
        lockPiece();
    }

    private void lockPiece()
    {
        int[][] cells = PIECES[currentType][currentRotation];
        for (int i = 0; i < cells.length; i++)
        {
            int r = currentY + cells[i][0];
            int c = currentX + cells[i][1];
            if (r >= 0 && r < ROWS && c >= 0 && c < COLS)
            {
                grid[r][c] = currentType + 1;
            }
        }
        clearLines();
        spawnPiece();
    }

    private void clearLines()
    {
        int cleared = 0;
        for (int r = ROWS - 1; r >= 0; r--)
        {
            boolean full = true;
            for (int c = 0; c < COLS; c++)
            {
                if (grid[r][c] == 0) { full = false; break; }
            }
            if (full)
            {
                cleared++;
                for (int rr = r; rr > 0; rr--)
                {
                    grid[rr] = grid[rr - 1].clone();
                }
                grid[0] = new int[COLS];
                r++;
            }
        }
        if (cleared > 0)
        {
            linesCleared += cleared;
            score += cleared == 1 ? 100 : cleared == 2 ? 300 : cleared == 3 ? 500 : 800;
            dropInterval = Math.max(10, 40 - linesCleared / 2);
        }
    }

    public void tick()
    {
        if (gameOver)
        {
            return;
        }
        frameCount++;
        if (frameCount >= dropInterval)
        {
            frameCount = 0;
            if (!collides(currentX, currentY + 1, currentRotation))
            {
                currentY++;
            }
            else
            {
                lockPiece();
            }
        }
    }

    public int[][] getGrid() { return grid; }
    public int getCurrentType() { return currentType; }
    public int getCurrentX() { return currentX; }
    public int getCurrentY() { return currentY; }
    public int[][] getCurrentCells() { return PIECES[currentType][currentRotation]; }
    public int getNextType() { return nextType; }
    public int getScore() { return score; }
    public int getLinesCleared() { return linesCleared; }
    public boolean isGameOver() { return gameOver; }
}
