package games;

import java.util.Random;

/**
 * NumberNestGame
 * --------------
 * Model for Number Nest - an original number-merge puzzle, deliberately
 * distinct from 2048's slide-everything mechanic (see ROADMAP.md's games
 * backlog: "a small grid where you place incoming numbered pieces rather
 * than 2048's slide-and-merge-everything mechanic"). A 5x5 grid (flat
 * 25-element array, 0 = empty). One piece at a time is offered (plus a
 * preview of the next one); the player places it into any empty cell of
 * their choosing - nothing slides, nothing moves but the placed piece
 * itself. If the newly-placed piece is orthogonally adjacent to exactly
 * one cell holding the same value, they merge into double that value at
 * the placed cell (deliberately at most one merge per placement, checked
 * in a fixed up/down/left/right order, with no cascade into a second
 * merge afterward - a placement is one deliberate, bounded action, not a
 * combo chain). Game ends when the grid is completely full - since
 * placement never requires adjacency, any empty cell is always a legal
 * move, so "full grid" is the only way to run out of moves.
 */
public class NumberNestGame
{
    public static final int SIZE = 5;

    /** Mostly 2s, sometimes 4s, rarely 8s - a gentle difficulty curve, same spirit as 2048's occasional-4 spawn but with a third tier since placements merge less often than a slide-everything move does. */
    private static final int[] PIECE_POOL = { 2, 2, 2, 2, 2, 4, 4, 4, 8 };

    private final int[] tiles = new int[SIZE * SIZE];
    private final Random random = new Random();
    private int score = 0;
    private int currentPiece;
    private int nextPiece;
    private boolean gameOver = false;

    public NumberNestGame()
    {
        currentPiece = drawPiece();
        nextPiece = drawPiece();
    }

    public int getTile(int row, int col) { return tiles[row * SIZE + col]; }
    public int getScore() { return score; }
    public int getCurrentPiece() { return currentPiece; }
    public int getNextPiece() { return nextPiece; }
    public boolean isGameOver() { return gameOver; }

    private int drawPiece()
    {
        return PIECE_POOL[random.nextInt(PIECE_POOL.length)];
    }

    /**
     * Places the current piece at (row, col). Returns false (no-op, nothing
     * changes) if the cell is occupied or out of bounds - the caller can
     * distinguish "illegal, try again" from "placed" this way, same shape as
     * Merge2048Game's move methods returning whether anything actually moved.
     */
    public boolean place(int row, int col)
    {
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE || gameOver)
        {
            return false;
        }
        int index = row * SIZE + col;
        if (tiles[index] != 0)
        {
            return false;
        }

        tiles[index] = currentPiece;
        mergeWithOneNeighbor(row, col);

        currentPiece = nextPiece;
        nextPiece = drawPiece();

        if (isFull())
        {
            gameOver = true;
        }
        return true;
    }

    /** Fixed up/right/down/left scan order - merges with the first matching neighbor found, then stops (see class javadoc for why this is deliberately not a cascading combo). */
    private void mergeWithOneNeighbor(int row, int col)
    {
        int[][] directions = { { -1, 0 }, { 0, 1 }, { 1, 0 }, { 0, -1 } };
        int placedIndex = row * SIZE + col;
        int value = tiles[placedIndex];

        for (int[] dir : directions)
        {
            int nRow = row + dir[0];
            int nCol = col + dir[1];
            if (nRow < 0 || nRow >= SIZE || nCol < 0 || nCol >= SIZE)
            {
                continue;
            }
            int nIndex = nRow * SIZE + nCol;
            if (tiles[nIndex] == value)
            {
                tiles[placedIndex] = value * 2;
                tiles[nIndex] = 0;
                score += tiles[placedIndex];
                return;
            }
        }
    }

    private boolean isFull()
    {
        for (int tile : tiles)
        {
            if (tile == 0) return false;
        }
        return true;
    }
}
