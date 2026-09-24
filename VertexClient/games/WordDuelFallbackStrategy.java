package games;

import ai.BotStrategy;

/**
 * WordDuelFallbackStrategy
 * -------------------------
 * The deliberately-trivial fallback AiKernel requires alongside
 * WordDuelBotStrategy - the first word that fits the draw at all,
 * rather than searching for the longest one. Distinct and cheaper
 * than the primary strategy, matching the "bulletproof, not smart"
 * fallback every other game on this engine registers.
 */
public class WordDuelFallbackStrategy implements BotStrategy<String, String>
{
    @Override
    public String chooseMove(String letters)
    {
        String any = WordDuelWordList.anyWordFrom(letters);
        return any != null ? any : "";
    }
}
