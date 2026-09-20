package games;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * BattleshipAI
 * ------------
 * Local opponent for vs-AI Battleship - simple hunt/target behavior.
 * Fires randomly at untried cells until it lands a hit, then switches
 * to "target mode" and tries the four adjacent cells to finish off
 * that ship before returning to random hunting. Not a sophisticated
 * probability-based AI (which real Battleship solvers use), but a
 * genuinely competent opponent for casual play.
 */
public class BattleshipAI
{
    private static final int SIZE = 10;

    private final Random random = new Random();
    private final boolean[] fired = new boolean[SIZE * SIZE];
    private final List<Integer> targetQueue = new ArrayList<Integer>();

    /**
     * Reconciles external ground truth into this AI's own "already fired"
     * tracking - a no-op if it already knew about the cell. Exists purely
     * so a caller that sometimes has to fall back to a DIFFERENT strategy
     * (see BattleshipBotStrategy/AiKernel.applySafely) can keep this AI's
     * internal state from drifting out of sync with what actually
     * happened, without needing to touch chooseNextShot()'s own algorithm.
     */
    public void markFired(int cellIndex)
    {
        fired[cellIndex] = true;
    }

    public int chooseNextShot()
    {
        while (!targetQueue.isEmpty())
        {
            int candidate = targetQueue.remove(0);
            if (!fired[candidate])
            {
                fired[candidate] = true;
                return candidate;
            }
        }

        int candidate;
        do
        {
            candidate = random.nextInt(SIZE * SIZE);
        }
        while (fired[candidate]);

        fired[candidate] = true;
        return candidate;
    }

    /** Call after each shot so the AI can react - adds adjacent cells to the target queue on a hit, clears the queue once a ship is confirmed sunk (a simplification: it doesn't track which hits belonged to which ship). */
    public void reportResult(int cellIndex, String result)
    {
        if ("HIT".equals(result))
        {
            addAdjacent(cellIndex);
        }
        else if ("SUNK".equals(result))
        {
            targetQueue.clear();
        }
    }

    private void addAdjacent(int cellIndex)
    {
        int row = cellIndex / SIZE;
        int col = cellIndex % SIZE;
        tryAdd(row - 1, col);
        tryAdd(row + 1, col);
        tryAdd(row, col - 1);
        tryAdd(row, col + 1);
    }

    private void tryAdd(int row, int col)
    {
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE)
        {
            return;
        }
        int cell = row * SIZE + col;
        if (!fired[cell] && !targetQueue.contains(cell))
        {
            targetQueue.add(cell);
        }
    }
}
