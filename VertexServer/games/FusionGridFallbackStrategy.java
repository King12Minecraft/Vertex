package games;

import ai.BotStrategy;

/**
 * FusionGridFallbackStrategy
 * ----------------------------
 * The deliberately-trivial fallback AiKernel requires alongside
 * FusionGridBotStrategy - the first empty cell found, no scoring at
 * all. Distinct and cheaper than the primary strategy, matching the
 * "bulletproof, not smart" fallback every other game on this engine
 * registers.
 */
public class FusionGridFallbackStrategy implements BotStrategy<FusionGridState, Integer>
{
    @Override
    public Integer chooseMove(FusionGridState state)
    {
        for (int i = 0; i < state.owners.length; i++)
        {
            if (state.owners[i] == -1) return i;
        }
        return -1;
    }
}
