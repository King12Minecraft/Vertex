package ai.grid;

/**
 * GridDirection
 * --------------
 * The four-directional movement convention every ai.grid utility
 * shares. Deliberately its own type rather than reusing any one
 * game's own direction enum (e.g. MazeChaseGame.Direction) - the ai
 * package never depends on games, so a caller in games translates
 * between its own direction type and this one at the adapter layer
 * (see MazeChaseChaserBotStrategy for an example).
 */
public enum GridDirection
{
    UP(-1, 0), DOWN(1, 0), LEFT(0, -1), RIGHT(0, 1);

    public final int dRow;
    public final int dCol;

    GridDirection(int dRow, int dCol)
    {
        this.dRow = dRow;
        this.dCol = dCol;
    }
}
