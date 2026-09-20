package games;

import ai.BotStrategy;

/**
 * BattleshipBotStrategy
 * ----------------------
 * Wraps the existing BattleshipAI (hunt/target shot-picking, unchanged)
 * as an ai.BotStrategy, so BattleshipWindow can ask for its next shot
 * through AiKernel's safety net (AiKernel.applySafely) instead of
 * calling chooseNextShot() directly. State type is Void since
 * BattleshipAI needs no external state passed in - its memory (which
 * cells were already fired at, which cells are queued to finish off a
 * hit ship) lives entirely inside this instance, which is exactly why
 * a FRESH one is created per match (same as before this migration:
 * BattleshipWindow still constructs exactly one per match) rather than
 * being shared across matches the way Tic-Tac-Toe's genuinely
 * stateless strategy safely is via AiKernel's registry - two matches
 * sharing one instance here would let their shot histories corrupt
 * each other.
 *
 * reportResult(...) is exposed directly (not part of the BotStrategy
 * interface, which only has chooseMove) since it's feedback about a
 * PAST shot, not a request for a new decision - BattleshipWindow calls
 * it directly on this same instance, exactly as it called it on a raw
 * BattleshipAI before this migration.
 *
 * State type is boolean[] - the caller's ground-truth "already fired
 * at" tracking for every cell (10x10, indexed row*10+col), not just
 * this AI's own private copy of it. On every call, any externally-true
 * cell this AI doesn't yet know about is reconciled in via markFired()
 * before picking a move. In ordinary play this is a no-op (this AI's
 * own tracking already matches reality, since it's the one making
 * every shot) - it only matters in the rare case where AiKernel had to
 * fall back to BattleshipRandomShotStrategy for one shot: without this
 * reconciliation, this AI wouldn't know that cell was taken and could
 * pick it again later, double-firing at an already-fired cell.
 */
public class BattleshipBotStrategy implements BotStrategy<boolean[], Integer>
{
    private final BattleshipAI ai = new BattleshipAI();

    public Integer chooseMove(boolean[] alreadyFired)
    {
        if (alreadyFired != null)
        {
            for (int i = 0; i < alreadyFired.length; i++)
            {
                if (alreadyFired[i])
                {
                    ai.markFired(i);
                }
            }
        }
        return ai.chooseNextShot();
    }

    public void reportResult(int cellIndex, String result)
    {
        ai.reportResult(cellIndex, result);
    }
}
