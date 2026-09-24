package games;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * MemoryMatchBotStrategy
 * ------------------------
 * Memory Match's practice-mode "opponent" - the one game on this
 * engine with genuinely hidden information: which symbol sits at a
 * still-face-down index is unknown to both players until it's
 * flipped, unlike every other game here (all perfect-information,
 * whether they use ai.search or a bespoke heuristic). A bot built on
 * full board knowledge would be omniscient and unbeatable, which
 * isn't "a proper AI," it's cheating - so this bot only ever knows
 * what it has personally watched get revealed, exactly like a real
 * player relying on memory.
 *
 * Deliberately NOT registered through AiKernel/ai.search at all
 * (unlike Word Duel/Fusion Grid, which are still plain ai.BotStrategy
 * even without ai.search): a bot turn here is a sequence of two
 * flips with a real information update in between (what the first
 * flip revealed informs the second), and - critically - this class
 * needs a fresh, private memory per match, not a shared/stateless
 * instance, the same reasoning BattleshipBotStrategy already
 * established for its own hunt/target memory.
 */
public class MemoryMatchBotStrategy
{
    private final Map<Integer, Character> known = new HashMap<Integer, Character>();
    private final Random random = new Random();

    /** Records that index was seen showing symbol - called for EVERY flip in the match, the bot's own or the human's, so the bot "remembers" what it has watched happen exactly like a real opponent would, not just its own moves. */
    public void observe(int index, char symbol)
    {
        known.put(index, symbol);
    }

    /** The first flip of a bot turn: plays a remembered pair if one exists among still-face-down cards, otherwise explores a card it hasn't seen yet (falling back to any remaining card if everything left is already known). */
    public int chooseFirstFlip(boolean[] matched)
    {
        Integer pairStart = findKnownPairIndex(matched, -1);
        if (pairStart != null)
        {
            return pairStart;
        }
        Integer unknown = randomUnmatchedUnknown(matched, -1);
        if (unknown != null)
        {
            return unknown;
        }
        return randomUnmatched(matched, -1);
    }

    /** The second flip, now that firstIndex/firstSymbol are known (from the flip that just happened): completes the match if a remembered twin exists, otherwise explores an unseen card, otherwise picks anything left. */
    public int chooseSecondFlip(int firstIndex, char firstSymbol, boolean[] matched)
    {
        for (Map.Entry<Integer, Character> entry : known.entrySet())
        {
            int index = entry.getKey();
            if (index != firstIndex && !matched[index] && entry.getValue() == firstSymbol)
            {
                return index;
            }
        }
        Integer unknown = randomUnmatchedUnknown(matched, firstIndex);
        if (unknown != null)
        {
            return unknown;
        }
        return randomUnmatched(matched, firstIndex);
    }

    /** Any still-face-down index whose symbol the bot remembers, that also has a remembered, unmatched twin elsewhere - the actual "I know where a pair is" case. */
    private Integer findKnownPairIndex(boolean[] matched, int exclude)
    {
        for (Map.Entry<Integer, Character> a : known.entrySet())
        {
            if (a.getKey() == exclude || matched[a.getKey()]) continue;
            for (Map.Entry<Integer, Character> b : known.entrySet())
            {
                if (b.getKey() == a.getKey() || b.getKey() == exclude || matched[b.getKey()]) continue;
                if (a.getValue().equals(b.getValue()))
                {
                    return a.getKey();
                }
            }
        }
        return null;
    }

    private Integer randomUnmatchedUnknown(boolean[] matched, int exclude)
    {
        java.util.List<Integer> candidates = new java.util.ArrayList<Integer>();
        for (int i = 0; i < matched.length; i++)
        {
            if (i != exclude && !matched[i] && !known.containsKey(i))
            {
                candidates.add(i);
            }
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    private Integer randomUnmatched(boolean[] matched, int exclude)
    {
        java.util.List<Integer> candidates = new java.util.ArrayList<Integer>();
        for (int i = 0; i < matched.length; i++)
        {
            if (i != exclude && !matched[i])
            {
                candidates.add(i);
            }
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }
}
