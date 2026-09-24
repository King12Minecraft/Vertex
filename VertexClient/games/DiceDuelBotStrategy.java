package games;

import java.util.Set;

/**
 * DiceDuelBotStrategy
 * ---------------------
 * Dice Duel's practice-mode "opponent" - not built on ai.search (like
 * Word Duel, and unlike Connect Four through Signal Grid) since a turn
 * here has real randomness IN THE MIDDLE of the decision (each reroll
 * changes the dice), not just at setup - there's no single
 * deterministic state transition a search could explore hypothetical
 * futures through. Also not a plain ai.BotStrategy: one turn is two
 * different kinds of decision (which dice to hold across up to 2
 * rerolls, then which category to lock), not one chooseMove(state)
 * call, so this is a small standalone class with two named methods
 * instead, called directly by DiceDuelWindow's practice-mode turn
 * loop rather than through AiKernel.
 *
 * Both decisions share one greedy idea: score every still-open
 * category against the CURRENT dice via DiceDuelMatch.scoreFor
 * (deliberately not trying to project what a reroll might turn into -
 * that's the "real randomness" this class doesn't try to search
 * through), and act on whichever category currently scores highest.
 */
public class DiceDuelBotStrategy
{
    /** Which die indices to hold (leave everything else to be rerolled) so the dice already contributing to the best currently-achievable open category are kept, in hopes the reroll improves the rest. */
    public boolean[] chooseDiceToHold(int[] dice, Set<String> openCategories)
    {
        boolean[] hold = new boolean[dice.length];
        String best = chooseCategory(dice, openCategories);
        if (best == null)
        {
            return hold;
        }

        if ("THREE_OF_A_KIND".equals(best))
        {
            int[] counts = new int[7];
            for (int value : dice) counts[value]++;
            int bestFace = -1, bestCount = 0;
            for (int face = 1; face <= 6; face++)
            {
                if (counts[face] > bestCount)
                {
                    bestCount = counts[face];
                    bestFace = face;
                }
            }
            for (int i = 0; i < dice.length; i++)
            {
                if (dice[i] == bestFace) hold[i] = true;
            }
        }
        else
        {
            int face = faceFor(best);
            for (int i = 0; i < dice.length; i++)
            {
                if (dice[i] == face) hold[i] = true;
            }
        }
        return hold;
    }

    /** Whichever open category scores highest for the current dice - null only if openCategories is empty, which shouldn't happen mid-match. */
    public String chooseCategory(int[] dice, Set<String> openCategories)
    {
        String best = null;
        int bestScore = -1;
        for (String category : openCategories)
        {
            int score = DiceDuelMatch.scoreFor(category, dice);
            if (score > bestScore)
            {
                bestScore = score;
                best = category;
            }
        }
        return best;
    }

    private int faceFor(String faceCategory)
    {
        for (int face = 1; face <= 6; face++)
        {
            if (DiceDuelMatch.faceCategoryName(face).equals(faceCategory))
            {
                return face;
            }
        }
        return -1;
    }
}
