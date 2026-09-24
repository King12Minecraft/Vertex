package games;

import ai.BotStrategy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * FusionGridBotStrategy
 * -----------------------
 * Fusion Grid's practice-mode "opponent" - not built on ai.search
 * (like Word Duel/Dice Duel) since the tile after this placement is
 * randomly drawn, not chosen - there's no deterministic future state
 * a search could look ahead through, only the one placement decision
 * actually in front of the player right now. A plain ai.BotStrategy
 * fits fine here though (unlike Dice Duel's two-decision turn): one
 * placement is genuinely one chooseMove(state) call.
 *
 * Scores every empty cell by: the immediate cascade-merge points a
 * placement there would score right now (FusionGridMatch's own
 * mechanic, reimplemented here in a pure/simulatable form since
 * FusionGridMatch's own cascadeMerge mutates its live instance
 * arrays directly); a bonus for sitting next to more of the player's
 * own tiles (building a denser cluster for future merges); and a
 * penalty for sitting next to an opponent tile of the SAME value
 * being placed (that neighbor can never merge with this one - the
 * whole "opponent tiles are permanent blockers" twist the game's own
 * javadoc describes - so it's a wasted cell in that direction).
 */
public class FusionGridBotStrategy implements BotStrategy<FusionGridState, Integer>
{
    private static final int GRID_SIZE = FusionGridMatch.GRID_SIZE;

    @Override
    public Integer chooseMove(FusionGridState state)
    {
        int best = -1;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < state.values.length; i++)
        {
            if (state.owners[i] != -1) continue;
            int score = scoreForPlacement(state, i);
            if (score > bestScore)
            {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private int scoreForPlacement(FusionGridState state, int index)
    {
        int myNeighbors = 0;
        int blockedByOpponentSameValue = 0;
        for (int neighbor : neighborsOf(index))
        {
            if (state.owners[neighbor] == state.forPlayerIndex)
            {
                myNeighbors++;
            }
            else if (state.owners[neighbor] != -1 && state.owners[neighbor] != state.forPlayerIndex
                && state.values[neighbor] == state.currentTileValue)
            {
                blockedByOpponentSameValue++;
            }
        }

        int[] values = state.values.clone();
        int[] owners = state.owners.clone();
        values[index] = state.currentTileValue;
        owners[index] = state.forPlayerIndex;
        int gained = simulateCascade(values, owners, index, state.forPlayerIndex);

        return gained * 100 + myNeighbors * 5 - blockedByOpponentSameValue * 3;
    }

    /** Mirrors FusionGridMatch.cascadeMerge exactly, but on caller-supplied arrays instead of live instance fields, so it can be run hypothetically for every candidate cell without touching the real board. */
    static int simulateCascade(int[] values, int[] owners, int startIndex, int playerIndex)
    {
        int totalGained = 0;
        Deque<Integer> toCheck = new ArrayDeque<Integer>();
        toCheck.add(startIndex);

        while (!toCheck.isEmpty())
        {
            int index = toCheck.poll();
            int value = values[index];

            for (int neighbor : neighborsOf(index))
            {
                if (owners[neighbor] == playerIndex && values[neighbor] == value)
                {
                    owners[neighbor] = -1;
                    values[neighbor] = 0;
                    values[index] = value * 2;
                    totalGained += values[index];
                    toCheck.add(index);
                    break;
                }
            }
        }
        return totalGained;
    }

    private static List<Integer> neighborsOf(int index)
    {
        int row = index / GRID_SIZE, col = index % GRID_SIZE;
        List<Integer> result = new ArrayList<Integer>();
        if (row > 0) result.add(index - GRID_SIZE);
        if (row < GRID_SIZE - 1) result.add(index + GRID_SIZE);
        if (col > 0) result.add(index - 1);
        if (col < GRID_SIZE - 1) result.add(index + 1);
        return result;
    }
}
