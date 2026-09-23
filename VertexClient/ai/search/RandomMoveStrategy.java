package ai.search;

import ai.BotStrategy;

import java.util.List;
import java.util.Random;

/**
 * RandomMoveStrategy
 * -------------------
 * The one deliberately-trivial fallback strategy every search-based
 * game registers with AiKernel alongside GenericBotStrategy - picks a
 * uniformly random legal move via the same GameModel, so it's
 * bulletproof by construction (anything GenericBotStrategy considers
 * legal, this can trivially pick from) and, per AiKernel's contract,
 * about as unlikely to itself fail as a fallback can be. Generic like
 * everything else in this package, so no game writes its own
 * `<Name>RandomMoveStrategy` class either.
 */
public class RandomMoveStrategy<S, M> implements BotStrategy<S, M>
{
    private final GameModel<S, M> model;
    private final int playerIndex;
    private final Random random = new Random();

    public RandomMoveStrategy(GameModel<S, M> model, int playerIndex)
    {
        this.model = model;
        this.playerIndex = playerIndex;
    }

    @Override
    public M chooseMove(S state)
    {
        List<M> moves = model.legalMoves(state, playerIndex);
        return moves.get(random.nextInt(moves.size()));
    }
}
