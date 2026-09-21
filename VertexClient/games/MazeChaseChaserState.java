package games;

import ai.grid.GridPathfinder;

import java.util.ArrayList;
import java.util.List;

/**
 * MazeChaseChaserState
 * ----------------------
 * The read-only snapshot MazeChaseChaserBotStrategy needs to decide a
 * single chaser's next move: the maze's open/blocked cells (via the
 * game's own isWall(), never a copy of the wall grid), this chaser's
 * position and current heading, the target cell to chase (the player -
 * used directly when hunting, and as the source for the "maximize
 * distance from" field when fleeing), and whether frightened mode is
 * on. Built fresh once per chaser per tick by MazeChaseGame; nothing
 * here mutates the game (matches ai.BotStrategy's contract that a
 * strategy must not mutate the state it's handed).
 */
public class MazeChaseChaserState
{
    private static final MazeChaseGame.Direction[] ALL_DIRECTIONS = {
        MazeChaseGame.Direction.UP, MazeChaseGame.Direction.DOWN,
        MazeChaseGame.Direction.LEFT, MazeChaseGame.Direction.RIGHT
    };

    private final MazeChaseGame game;
    private final MazeChaseGame.Chaser chaser;
    private final boolean fleeing;

    MazeChaseChaserState(MazeChaseGame game, MazeChaseGame.Chaser chaser, boolean fleeing)
    {
        this.game = game;
        this.chaser = chaser;
        this.fleeing = fleeing;
    }

    int rows() { return MazeChaseGame.HEIGHT; }
    int cols() { return MazeChaseGame.WIDTH; }
    int row() { return chaser.row; }
    int col() { return chaser.col; }
    int targetRow() { return game.getPlayerRow(); }
    int targetCol() { return game.getPlayerCol(); }
    boolean fleeing() { return fleeing; }
    MazeChaseGame.Direction currentDirection() { return chaser.direction; }

    GridPathfinder.GridObstacle obstacle()
    {
        return new GridPathfinder.GridObstacle()
        {
            public boolean isBlocked(int row, int col)
            {
                return game.isWall(row, col);
            }
        };
    }

    int[] step(MazeChaseGame.Direction d)
    {
        return game.step(chaser.row, chaser.col, d);
    }

    /** Every direction that doesn't walk into a wall, excluding a straight reversal unless that's the only option left - same rule the original inline heuristic used. */
    List<MazeChaseGame.Direction> openDirections()
    {
        MazeChaseGame.Direction reverse = game.opposite(currentDirection());
        List<MazeChaseGame.Direction> options = new ArrayList<MazeChaseGame.Direction>();
        for (MazeChaseGame.Direction d : ALL_DIRECTIONS)
        {
            int[] next = step(d);
            if (game.isOpen(next[0], next[1]) && d != reverse)
            {
                options.add(d);
            }
        }
        if (options.isEmpty())
        {
            for (MazeChaseGame.Direction d : ALL_DIRECTIONS)
            {
                int[] next = step(d);
                if (game.isOpen(next[0], next[1]))
                {
                    options.add(d);
                }
            }
        }
        return options;
    }
}
