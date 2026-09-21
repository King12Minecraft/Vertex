package games;

import ai.BotStrategy;
import ai.grid.GridDirection;
import ai.grid.GridPathfinder;

import java.util.List;
import java.util.Random;

/**
 * MazeChaseChaserBotStrategy
 * ----------------------------
 * The "smart" AI for Maze Chase's chasers, wrapped as an ai.BotStrategy
 * so it's registered with AiKernel instead of the ad hoc greedy
 * Manhattan-distance heuristic MazeChaseGame used to compute inline.
 * Uses the shared ai.grid.GridPathfinder for a genuine BFS shortest
 * path instead of a one-step-lookahead Manhattan-distance guess -
 * which fixes a real correctness gap the old heuristic had: a chaser
 * one step from the player in straight-line distance but on the far
 * side of a wall pillar would still greedily walk toward the wall
 * ("closer" by Manhattan distance, but not actually reachable that
 * way), rather than routing around it. Same 75%-optimal/25%-random
 * split as the original, so chasers still aren't perfectly
 * predictable - just with the "optimal" 75% now genuinely optimal
 * (true shortest path) instead of a myopic approximation of it. When
 * frightened, chases whichever open cell MAXIMIZES true graph distance
 * from the player (via GridPathfinder.distanceField) instead of the
 * old one-step Manhattan-distance approximation of "away," for the
 * same reason.
 */
public class MazeChaseChaserBotStrategy implements BotStrategy<MazeChaseChaserState, MazeChaseGame.Direction>
{
    private final Random random = new Random();

    public MazeChaseGame.Direction chooseMove(MazeChaseChaserState state)
    {
        List<MazeChaseGame.Direction> options = state.openDirections();
        if (options.isEmpty())
        {
            return MazeChaseGame.Direction.NONE;
        }

        if (random.nextInt(100) < 75)
        {
            MazeChaseGame.Direction best = state.fleeing() ? bestByDistanceField(state) : bestByShortestPath(state);
            if (best != null)
            {
                return best;
            }
        }
        return options.get(random.nextInt(options.size()));
    }

    private MazeChaseGame.Direction bestByShortestPath(MazeChaseChaserState state)
    {
        GridDirection step = GridPathfinder.firstStepTowards(
            state.rows(), state.cols(), state.obstacle(),
            state.row(), state.col(), state.targetRow(), state.targetCol(),
            toGrid(opposite(state.currentDirection())));
        return toGame(step);
    }

    private MazeChaseGame.Direction bestByDistanceField(MazeChaseChaserState state)
    {
        int[][] distance = GridPathfinder.distanceField(
            state.rows(), state.cols(), state.obstacle(), state.targetRow(), state.targetCol());

        MazeChaseGame.Direction best = null;
        int bestDistance = -1;
        for (MazeChaseGame.Direction d : state.openDirections())
        {
            int[] next = state.step(d);
            int dist = distance[next[0]][next[1]];
            if (dist != Integer.MAX_VALUE && dist > bestDistance)
            {
                bestDistance = dist;
                best = d;
            }
        }
        return best;
    }

    private MazeChaseGame.Direction opposite(MazeChaseGame.Direction d)
    {
        if (d == MazeChaseGame.Direction.UP) return MazeChaseGame.Direction.DOWN;
        if (d == MazeChaseGame.Direction.DOWN) return MazeChaseGame.Direction.UP;
        if (d == MazeChaseGame.Direction.LEFT) return MazeChaseGame.Direction.RIGHT;
        if (d == MazeChaseGame.Direction.RIGHT) return MazeChaseGame.Direction.LEFT;
        return MazeChaseGame.Direction.NONE;
    }

    private GridDirection toGrid(MazeChaseGame.Direction d)
    {
        if (d == MazeChaseGame.Direction.UP) return GridDirection.UP;
        if (d == MazeChaseGame.Direction.DOWN) return GridDirection.DOWN;
        if (d == MazeChaseGame.Direction.LEFT) return GridDirection.LEFT;
        if (d == MazeChaseGame.Direction.RIGHT) return GridDirection.RIGHT;
        return null;
    }

    private MazeChaseGame.Direction toGame(GridDirection d)
    {
        if (d == null) return null;
        if (d == GridDirection.UP) return MazeChaseGame.Direction.UP;
        if (d == GridDirection.DOWN) return MazeChaseGame.Direction.DOWN;
        if (d == GridDirection.LEFT) return MazeChaseGame.Direction.LEFT;
        return MazeChaseGame.Direction.RIGHT;
    }
}
