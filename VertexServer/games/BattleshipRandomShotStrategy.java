package games;

import ai.BotStrategy;

import java.util.Random;

/**
 * BattleshipRandomShotStrategy
 * ------------------------------
 * The fallback AiKernel.applySafely(...) falls back to if
 * BattleshipBotStrategy's hunt/target logic ever throws. Deliberately
 * as simple as possible - tracks only which cells it has already
 * fired at (its own minimal local bookkeeping) and picks uniformly at
 * random among the rest, using the CALLER'S ground-truth "already
 * fired" array directly rather than any private tracking of its own -
 * that's what guarantees it can never pick an already-fired cell, even
 * on the very first time it's ever called mid-match (it has no history
 * to have missed).
 */
public class BattleshipRandomShotStrategy implements BotStrategy<boolean[], Integer>
{
    private static final int SIZE = 10;

    private final Random random = new Random();

    public Integer chooseMove(boolean[] alreadyFired)
    {
        if (alreadyFired == null)
        {
            return random.nextInt(SIZE * SIZE);
        }

        // A bounded number of random attempts handles the common case (plenty of free cells left)
        // cheaply, but must NOT be the only mechanism: with very few free cells remaining, random
        // guessing can plausibly exhaust the attempt budget by bad luck alone and end up returning
        // an already-fired cell, which is a correctness bug, not just an inefficiency. So if random
        // guessing doesn't find one, fall through to a guaranteed linear scan for the first free
        // cell instead of ever returning one known to be already fired.
        for (int attempt = 0; attempt < 50; attempt++)
        {
            int candidate = random.nextInt(alreadyFired.length);
            if (!alreadyFired[candidate])
            {
                return candidate;
            }
        }
        for (int i = 0; i < alreadyFired.length; i++)
        {
            if (!alreadyFired[i])
            {
                return i;
            }
        }
        return -1; // every cell has been fired at - shouldn't be reachable in a real match
    }
}
