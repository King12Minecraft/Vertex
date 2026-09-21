package games;

import ai.BotStrategy;

import java.util.List;
import java.util.Random;

/**
 * MazeChaseChaserRandomStrategy
 * --------------------------------
 * The fallback AiKernel falls back to if MazeChaseChaserBotStrategy
 * ever throws. Picks uniformly among the directions that don't walk
 * into a wall (already computed by the state snapshot itself, so
 * there's no pathfinding math in here at all) - deliberately as
 * trivial and bulletproof as the fallback strategy for every other
 * migrated game.
 */
public class MazeChaseChaserRandomStrategy implements BotStrategy<MazeChaseChaserState, MazeChaseGame.Direction>
{
    private final Random random = new Random();

    public MazeChaseGame.Direction chooseMove(MazeChaseChaserState state)
    {
        List<MazeChaseGame.Direction> options = state.openDirections();
        if (options.isEmpty())
        {
            return MazeChaseGame.Direction.NONE;
        }
        return options.get(random.nextInt(options.size()));
    }
}
