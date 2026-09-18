package games;

import java.util.Random;

/**
 * YahtzeeGame
 * -----------
 * An original implementation of the classic public-domain dice game
 * generically known as "Yahtzee" / five-dice poker scoring - the dice
 * mechanic (roll five dice, keep some, reroll up to twice, score into
 * one of 13 fixed categories) and the category rules themselves (three
 * of a kind, full house, straights, "Yahtzee" for five of a kind, etc.)
 * are generic scoring-game rules, not anyone's copyrightable code or
 * expression. No code, art, or specific wording was copied from any
 * existing implementation.
 *
 * Turn-based like Word Guess/Klondike - no Swing Timer. A "turn" is:
 * roll (up to 3 times total, holding any dice in between), then commit
 * to exactly one of the 13 scoring categories. After 13 turns (one per
 * category) the game is over and the total is the score.
 */
public class YahtzeeGame
{
    public static final int NUM_DICE = 5;
    public static final int MAX_ROLLS_PER_TURN = 3;
    public static final int NUM_CATEGORIES = 13;

    public static final int ONES = 0;
    public static final int TWOS = 1;
    public static final int THREES = 2;
    public static final int FOURS = 3;
    public static final int FIVES = 4;
    public static final int SIXES = 5;
    public static final int THREE_KIND = 6;
    public static final int FOUR_KIND = 7;
    public static final int FULL_HOUSE = 8;
    public static final int SMALL_STRAIGHT = 9;
    public static final int LARGE_STRAIGHT = 10;
    public static final int YAHTZEE = 11;
    public static final int CHANCE = 12;

    public static final String[] CATEGORY_NAMES = new String[] {
        "Ones", "Twos", "Threes", "Fours", "Fives", "Sixes",
        "3 of a Kind", "4 of a Kind", "Full House",
        "Sm. Straight", "Lg. Straight", "YAHTZEE", "Chance"
    };

    private final Random random = new Random();
    private final int[] dice = new int[NUM_DICE];
    private final boolean[] held = new boolean[NUM_DICE];
    private final int[] categoryScore = new int[NUM_CATEGORIES];
    private final boolean[] categoryUsed = new boolean[NUM_CATEGORIES];

    private int rollsThisTurn;
    private int turnsCompleted;
    private boolean everRolledAllFive; // tracks whether the very first roll of the game has happened

    public YahtzeeGame()
    {
        rollDice(true);
    }

    public int[] getDice() { return dice.clone(); }
    public boolean isHeld(int index) { return held[index]; }
    public int getRollsThisTurn() { return rollsThisTurn; }
    public int getTurnsCompleted() { return turnsCompleted; }
    public boolean isCategoryUsed(int category) { return categoryUsed[category]; }
    public int getCategoryScore(int category) { return categoryScore[category]; }
    public boolean canRoll() { return rollsThisTurn < MAX_ROLLS_PER_TURN && !isOver(); }
    public boolean isOver() { return turnsCompleted >= NUM_CATEGORIES; }

    /** Toggling hold is only meaningful between rolls, and only once at least one roll has happened this turn. */
    public void toggleHold(int index)
    {
        if (index < 0 || index >= NUM_DICE) return;
        if (rollsThisTurn == 0 || isOver()) return;
        held[index] = !held[index];
    }

    /** Re-rolls every die that isn't held. First roll of a turn always rerolls everything (nothing can be held yet). */
    public void roll()
    {
        if (!canRoll()) return;
        rollDice(rollsThisTurn == 0);
        rollsThisTurn++;
    }

    private void rollDice(boolean forceAll)
    {
        for (int i = 0; i < NUM_DICE; i++)
        {
            if (forceAll || !held[i])
            {
                dice[i] = 1 + random.nextInt(6);
            }
        }
        everRolledAllFive = true;
    }

    /** Score the current dice into the given category (if not already used) and end the turn. */
    public void commitCategory(int category)
    {
        if (category < 0 || category >= NUM_CATEGORIES) return;
        if (categoryUsed[category] || isOver() || rollsThisTurn == 0) return;

        categoryScore[category] = scoreFor(category, dice);
        categoryUsed[category] = true;
        turnsCompleted++;

        // Reset for the next turn.
        rollsThisTurn = 0;
        for (int i = 0; i < NUM_DICE; i++) held[i] = false;
        if (!isOver()) rollDice(true);
    }

    public int getUpperSectionTotal()
    {
        int total = 0;
        for (int c = ONES; c <= SIXES; c++)
        {
            if (categoryUsed[c]) total += categoryScore[c];
        }
        return total;
    }

    public int getUpperBonus()
    {
        return getUpperSectionTotal() >= 63 ? 35 : 0;
    }

    public int getTotalScore()
    {
        int total = getUpperBonus();
        for (int c = 0; c < NUM_CATEGORIES; c++)
        {
            if (categoryUsed[c]) total += categoryScore[c];
        }
        return total;
    }

    /** Computes what a given category WOULD score for a given dice set, without mutating any state - used both by commitCategory and by the UI to preview scores before committing. */
    public static int scoreFor(int category, int[] d)
    {
        int[] counts = countsOf(d);
        int sum = 0;
        for (int v : d) sum += v;

        switch (category)
        {
            case ONES: return counts[1] * 1;
            case TWOS: return counts[2] * 2;
            case THREES: return counts[3] * 3;
            case FOURS: return counts[4] * 4;
            case FIVES: return counts[5] * 5;
            case SIXES: return counts[6] * 6;
            case THREE_KIND: return hasCountAtLeast(counts, 3) ? sum : 0;
            case FOUR_KIND: return hasCountAtLeast(counts, 4) ? sum : 0;
            case FULL_HOUSE: return isFullHouse(counts) ? 25 : 0;
            case SMALL_STRAIGHT: return isSmallStraight(counts) ? 30 : 0;
            case LARGE_STRAIGHT: return isLargeStraight(counts) ? 40 : 0;
            case YAHTZEE: return hasCountAtLeast(counts, 5) ? 50 : 0;
            case CHANCE: return sum;
            default: return 0;
        }
    }

    private static int[] countsOf(int[] d)
    {
        int[] counts = new int[7]; // index 1..6 used
        for (int v : d) counts[v]++;
        return counts;
    }

    private static boolean hasCountAtLeast(int[] counts, int n)
    {
        for (int i = 1; i <= 6; i++)
        {
            if (counts[i] >= n) return true;
        }
        return false;
    }

    private static boolean isFullHouse(int[] counts)
    {
        boolean hasThree = false, hasTwo = false;
        for (int i = 1; i <= 6; i++)
        {
            if (counts[i] == 3) hasThree = true;
            if (counts[i] == 2) hasTwo = true;
            if (counts[i] == 5) return true; // five of a kind also counts as a full house in this implementation
        }
        return hasThree && hasTwo;
    }

    private static boolean isSmallStraight(int[] counts)
    {
        return containsRun(counts, 1, 4) || containsRun(counts, 2, 5) || containsRun(counts, 3, 6);
    }

    private static boolean isLargeStraight(int[] counts)
    {
        return containsRun(counts, 1, 5) || containsRun(counts, 2, 6);
    }

    private static boolean containsRun(int[] counts, int from, int to)
    {
        for (int i = from; i <= to; i++)
        {
            if (counts[i] == 0) return false;
        }
        return true;
    }
}
