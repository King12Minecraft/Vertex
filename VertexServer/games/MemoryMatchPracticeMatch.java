package games;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MemoryMatchPracticeMatch
 * -------------------------
 * A local, offline Memory Match against MemoryMatchBotStrategy -
 * mirrors MemoryMatchMatch's own rules exactly (a match earns another
 * turn, a miss passes it), but unlike the ai.search-backed games'
 * shared PracticeMatch, this is its own small class: Memory Match has
 * hidden information (see MemoryMatchBotStrategy's javadoc), so it
 * doesn't fit that shared wrapper's single-visible-state-to-both-
 * players design at all.
 *
 * Every flip - the human's or the bot's - is fed to the bot's
 * observe() so it "remembers" what actually got revealed on screen,
 * exactly like a real opponent watching the board. The real game's
 * brief "show both mismatched cards, then flip them back" pause is
 * deliberately NOT modeled here (flip() resolves instantly) - the
 * calling Window is expected to hold the mismatched pair's symbols on
 * screen itself for a moment before re-rendering, same as it already
 * would for an online match's equivalent server message.
 */
public class MemoryMatchPracticeMatch
{
    public static final int CARD_COUNT = MemoryMatchMatch.CARD_COUNT;
    private static final char[] SYMBOLS = { 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H' };

    public static final int HUMAN = 0;
    public static final int BOT = 1;

    public static class FlipResult
    {
        public final int firstIndex;
        public final int secondIndex;
        public final char firstSymbol;
        public final char secondSymbol;
        public final boolean isMatch;
        public final boolean over;
        public final int scoreHuman;
        public final int scoreBot;

        FlipResult(int firstIndex, int secondIndex, char firstSymbol, char secondSymbol,
                   boolean isMatch, boolean over, int scoreHuman, int scoreBot)
        {
            this.firstIndex = firstIndex;
            this.secondIndex = secondIndex;
            this.firstSymbol = firstSymbol;
            this.secondSymbol = secondSymbol;
            this.isMatch = isMatch;
            this.over = over;
            this.scoreHuman = scoreHuman;
            this.scoreBot = scoreBot;
        }
    }

    private final char[] cardValues = new char[CARD_COUNT];
    private final boolean[] matched = new boolean[CARD_COUNT];
    private final int[] scores = new int[2];
    private final MemoryMatchBotStrategy botStrategy = new MemoryMatchBotStrategy();

    private int turnPlayerIndex = HUMAN;
    private Integer firstFlipIndex;
    private boolean over;

    public MemoryMatchPracticeMatch()
    {
        List<Character> deck = new ArrayList<Character>();
        for (char symbol : SYMBOLS)
        {
            deck.add(symbol);
            deck.add(symbol);
        }
        Collections.shuffle(deck);
        for (int i = 0; i < CARD_COUNT; i++)
        {
            cardValues[i] = deck.get(i);
        }
    }

    public boolean[] getMatched() { return matched; }
    public int getScoreHuman() { return scores[HUMAN]; }
    public int getScoreBot() { return scores[BOT]; }
    public boolean isHumanTurn() { return !over && turnPlayerIndex == HUMAN; }
    public boolean isOver() { return over; }
    public Integer getFirstFlipIndex() { return firstFlipIndex; }
    public MemoryMatchBotStrategy getBotStrategy() { return botStrategy; }

    /** Only meaningful while isHumanTurn() (or right after the bot's own first flip) - the symbol at a still-face-down index, revealed only to whoever is calling this after actually flipping it. Callers must not peek at unflipped cards. */
    public char symbolAt(int index)
    {
        return cardValues[index];
    }

    public boolean isFaceDown(int index)
    {
        return !matched[index] && (firstFlipIndex == null || firstFlipIndex != index);
    }

    /** Flips index for whoever's turn it is. Returns null if this was the first flip of the pair (waiting on a second); otherwise the resolved FlipResult - always feeds both flips to the bot's memory regardless of who made them. */
    public FlipResult flip(int index)
    {
        char symbol = cardValues[index];
        botStrategy.observe(index, symbol);

        if (firstFlipIndex == null)
        {
            firstFlipIndex = index;
            return null;
        }

        int firstIndex = firstFlipIndex;
        char firstSymbol = cardValues[firstIndex];
        boolean isMatch = firstSymbol == symbol;
        int mover = turnPlayerIndex;

        firstFlipIndex = null;
        if (isMatch)
        {
            matched[firstIndex] = true;
            matched[index] = true;
            scores[mover]++;
            if (allMatched()) over = true;
            // turnPlayerIndex intentionally unchanged - a match earns another turn.
        }
        else
        {
            turnPlayerIndex = 1 - turnPlayerIndex;
        }

        return new FlipResult(firstIndex, index, firstSymbol, symbol, isMatch, over, scores[HUMAN], scores[BOT]);
    }

    private boolean allMatched()
    {
        for (boolean m : matched)
        {
            if (!m) return false;
        }
        return true;
    }
}
