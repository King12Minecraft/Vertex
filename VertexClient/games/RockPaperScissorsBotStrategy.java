package games;

import ai.BotStrategy;

import java.util.Random;

/**
 * RockPaperScissorsBotStrategy
 * -----------------------------
 * Rock-Paper-Scissors's vs-AI opponent, wrapped as an ai.BotStrategy so
 * it can be registered with AiKernel instead of being called directly
 * from RockPaperScissorsWindow. Behavior is unchanged from the
 * original inline implementation - each round is simultaneous and
 * blind, so there's no opponent information to react to and no move
 * history worth keeping; a uniform random pick among the three moves
 * is already the game-theoretically optimal strategy against an
 * unknown opponent. Nothing about the current match is needed to make
 * that choice, hence Void for the state type.
 */
public class RockPaperScissorsBotStrategy implements BotStrategy<Void, String>
{
    private static final String[] MOVES = { "Rock", "Paper", "Scissors" };

    private final Random random = new Random();

    public String chooseMove(Void state)
    {
        return MOVES[random.nextInt(MOVES.length)];
    }
}
