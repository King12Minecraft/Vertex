package ai.search;

import java.util.List;

/**
 * Minimax
 * -------
 * Generic depth-limited minimax search with alpha-beta pruning over
 * any GameModel - the one search algorithm every board-game AI in
 * Vertex shares, written once instead of once per game. A game brings
 * only its GameModel (its rules); this class never knows anything
 * game-specific.
 *
 * Static utility, like GridPathfinder - no per-search state is kept
 * between calls, so it's safe to call from any number of concurrent
 * practice matches without needing an instance per game.
 */
public final class Minimax
{
    private Minimax()
    {
        // Static utility class - never instantiated.
    }

    /** Picks the best move for playerIndex to play from state, searching depth plies ahead. */
    public static <S, M> M bestMove(GameModel<S, M> model, S state, int playerIndex, int depth)
    {
        List<M> moves = model.legalMoves(state, playerIndex);
        if (moves.isEmpty())
        {
            throw new IllegalStateException("GameModel.legalMoves returned no moves for a non-terminal state");
        }

        M best = moves.get(0);
        int bestScore = Integer.MIN_VALUE;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        for (M move : moves)
        {
            S next = model.applyMove(state, move, playerIndex);
            int nextToMove = model.nextPlayer(next, playerIndex);
            int score = search(model, next, nextToMove, playerIndex, depth - 1, alpha, beta);

            if (score > bestScore)
            {
                bestScore = score;
                best = move;
            }
            alpha = Math.max(alpha, bestScore);
        }

        return best;
    }

    /** Alpha-beta value of state, from forPlayer's perspective, with toMove next to act. */
    private static <S, M> int search(GameModel<S, M> model, S state, int toMove, int forPlayer,
                                      int depth, int alpha, int beta)
    {
        if (depth <= 0 || model.isTerminal(state))
        {
            return model.evaluate(state, forPlayer);
        }

        List<M> moves = model.legalMoves(state, toMove);
        boolean maximizing = toMove == forPlayer;
        int value = maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (M move : moves)
        {
            S next = model.applyMove(state, move, toMove);
            int nextToMove = model.nextPlayer(next, toMove);
            int score = search(model, next, nextToMove, forPlayer, depth - 1, alpha, beta);

            if (maximizing)
            {
                value = Math.max(value, score);
                alpha = Math.max(alpha, value);
            }
            else
            {
                value = Math.min(value, score);
                beta = Math.min(beta, value);
            }

            if (beta <= alpha)
            {
                break;
            }
        }

        return value;
    }
}
