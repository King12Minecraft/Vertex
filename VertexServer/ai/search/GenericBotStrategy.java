package ai.search;

import ai.BotStrategy;

/**
 * GenericBotStrategy
 * -------------------
 * The one BotStrategy every search-based game registers with AiKernel
 * as its "smart" primary strategy - wraps Minimax over that game's own
 * GameModel. No game ever writes its own `<Name>BotStrategy` class for
 * this anymore; only its GameModel (rules) and a registration line are
 * game-specific.
 *
 * Stateless and safe to share as one long-lived instance per game
 * (like TicTacToePracticeBotStrategy) - it only ever reads the state
 * it's handed, per BotStrategy's contract.
 */
public class GenericBotStrategy<S, M> implements BotStrategy<S, M>
{
    private final GameModel<S, M> model;
    private final int playerIndex;
    private final int depth;

    public GenericBotStrategy(GameModel<S, M> model, int playerIndex, int depth)
    {
        this.model = model;
        this.playerIndex = playerIndex;
        this.depth = depth;
    }

    @Override
    public M chooseMove(S state)
    {
        return Minimax.bestMove(model, state, playerIndex, depth);
    }
}
