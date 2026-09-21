package games;

import ai.BotStrategy;

/**
 * RockPaperScissorsFixedMoveStrategy
 * ------------------------------------
 * The fallback AiKernel falls back to if RockPaperScissorsBotStrategy
 * ever throws. Deliberately simpler than even a single Random.nextInt
 * call - no randomness, no array indexing, nothing left in it that
 * could itself have a bug. Always plays Rock. Every game that
 * registers with AiKernel should have a fallback this trivial; the
 * whole point is that it's fundamentally simpler than the strategy
 * it's backing up.
 */
public class RockPaperScissorsFixedMoveStrategy implements BotStrategy<Void, String>
{
    public String chooseMove(Void state)
    {
        return "Rock";
    }
}
