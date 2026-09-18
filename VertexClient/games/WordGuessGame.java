package games;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * WordGuessGame
 * -------------
 * An original implementation of the well-known, uncopyrightable
 * "guess the hidden word, get per-letter position feedback" word-puzzle
 * mechanic (the same family as Mastermind-for-words and Jotto, going
 * back decades before any one branded game) - no relation to any
 * specific existing game's code, word list, or visual design. Turn-
 * based, not real-time, so unlike the arcade games there's no tick
 * loop: WordGuessWindow just repaints after each key press. Guesses
 * only need to be 5 alphabetic letters here - not checked against a
 * dictionary, since shipping a full word-validity list is out of scope
 * for a from-scratch implementation; the target itself is always drawn
 * from a curated common-word list below.
 */
public class WordGuessGame
{
    public static final int WORD_LENGTH = 5;
    public static final int MAX_GUESSES = 6;

    /** Absent from the word. */
    public static final int ABSENT = 0;
    /** In the word, but not in this position. */
    public static final int PRESENT = 1;
    /** Correct letter, correct position. */
    public static final int CORRECT = 2;

    private static final String[] WORDS = new String[]
    {
        "ABOUT", "ABOVE", "ACTOR", "ADMIT", "ADULT", "AFTER", "AGAIN", "AGENT", "AGREE", "AHEAD",
        "ALARM", "ALIVE", "ALLOW", "ALONE", "ALONG", "ALTER", "AMONG", "ANGER", "ANGLE", "ANGRY",
        "APPLE", "APPLY", "ARENA", "ARGUE", "ARROW", "ASIDE", "ASSET", "AUDIO", "AVOID", "AWAKE",
        "AWARD", "AWARE", "BADGE", "BAKER", "BASIC", "BEACH", "BEGIN", "BEING", "BELOW", "BENCH",
        "BIRTH", "BLACK", "BLADE", "BLAME", "BLANK", "BLAST", "BLEND", "BLESS", "BLIND", "BLOCK",
        "BLOOD", "BOARD", "BOAST", "BONUS", "BOOST", "BOUND", "BRAIN", "BRAND", "BRAVE", "BREAD",
        "BREAK", "BRIEF", "BRING", "BROAD", "BROWN", "BRUSH", "BUILD", "BUYER", "CABIN", "CABLE",
        "CANDY", "CARGO", "CARRY", "CATCH", "CAUSE", "CHAIN", "CHAIR", "CHALK", "CHARM", "CHART",
        "CHASE", "CHEAP", "CHECK", "CHESS", "CHEST", "CHIEF", "CHILD", "CHOSE", "CIVIL", "CLAIM",
        "CLASS", "CLEAN", "CLEAR", "CLICK", "CLIFF", "CLIMB", "CLOCK", "CLOSE", "CLOUD", "COACH",
        "COAST", "COLOR", "COUCH", "COULD", "COUNT", "COURT", "COVER", "CRAFT", "CRASH", "CRAZY",
        "CREAM", "CRIME", "CROSS", "CROWD", "CROWN", "CRUSH", "CURVE", "CYCLE", "DAILY", "DANCE",
        "DEALT", "DEATH", "DELAY", "DEPTH", "DIRTY", "DOUBT", "DRAFT", "DRAMA", "DRANK", "DRAWN",
        "DREAM", "DRESS", "DRIED", "DRILL", "DRINK", "DRIVE", "DROVE", "DUSTY", "EAGER", "EARLY",
        "EARTH", "EIGHT", "ELDER", "ELECT", "EMPTY", "ENEMY", "ENJOY", "ENTER", "ENTRY", "EQUAL",
        "ERROR", "EVENT", "EVERY", "EXACT", "EXIST", "EXTRA", "FAITH", "FALSE", "FAULT", "FAVOR",
        "FENCE", "FIELD", "FIFTH", "FIGHT", "FINAL", "FIRST", "FIXED", "FLAME", "FLASH", "FLEET",
        "FLOOR", "FLOUR", "FOCUS", "FORCE", "FORTH", "FORUM", "FOUND", "FRAME", "FRESH", "FRONT",
        "FROST", "FRUIT", "FUNNY", "GHOST", "GIANT", "GIVEN", "GLASS", "GLOBE", "GRACE", "GRADE",
        "GRAIN", "GRAND", "GRANT", "GRAPH", "GRASP", "GRASS", "GREAT", "GREEN", "GROUP", "GROWN",
        "GUARD", "GUESS", "GUEST", "GUIDE", "HAPPY", "HARSH", "HEART", "HEAVY", "HELLO", "HENCE",
        "HOBBY", "HORSE", "HOTEL", "HOUSE", "HUMAN", "IDEAL", "IMAGE", "INDEX", "INNER", "INPUT",
        "ISSUE", "JOINT", "JUDGE", "JUICE", "KNIFE", "KNOCK", "KNOWN", "LABEL", "LARGE", "LASER",
        "LATER", "LAUGH", "LAYER", "LEARN", "LEAST", "LEAVE", "LEGAL", "LEMON", "LEVEL", "LIGHT",
        "LIMIT", "LOCAL", "LOGIC", "LOOSE", "LOWER", "LOYAL", "LUCKY", "LUNCH", "MAGIC", "MAJOR",
        "MAKER", "MARCH", "MATCH", "MAYBE", "MAYOR", "MEDAL", "MEDIA", "MERGE", "METAL", "MIGHT",
        "MINOR", "MINUS", "MODEL", "MONEY", "MONTH", "MORAL", "MOTOR", "MOUNT", "MOUSE", "MOUTH",
        "MOVIE", "MUSIC", "NAKED", "NERVE", "NEVER", "NIGHT", "NOISE", "NORTH", "NOVEL", "NURSE",
        "OCCUR", "OCEAN", "OFFER", "OFTEN", "ORDER", "OUTER", "OWNER", "PAINT", "PANEL", "PANIC",
        "PAPER", "PARTY", "PEACE", "PHASE", "PHOTO", "PIANO", "PIECE", "PILOT", "PITCH", "PLACE",
        "PLAIN", "PLANE", "PLANT", "PLATE", "POINT", "POUND", "POWER", "PRESS", "PRICE", "PRIDE",
        "PRIME", "PRINT", "PRIOR", "PRIZE", "PROOF", "PROUD", "PROVE", "QUEEN", "QUICK", "QUIET",
        "QUITE", "RADIO", "RAISE", "RANGE", "RAPID", "REACH", "READY", "REFER", "RIGHT", "RIVAL",
        "RIVER", "ROBOT", "ROUGH", "ROUND", "ROUTE", "ROYAL", "RURAL", "SADLY", "SALAD", "SAUCE",
        "SCALE", "SCENE", "SCOPE", "SCORE", "SENSE", "SERVE", "SEVEN", "SHADE", "SHAKE", "SHALL",
        "SHAPE", "SHARE", "SHARP", "SHEET", "SHELF", "SHELL", "SHIFT", "SHINE", "SHIRT", "SHOCK",
        "SHOOT", "SHORT", "SHOWN", "SIGHT", "SILLY", "SINCE", "SIXTH", "SKILL", "SLEEP", "SLIDE",
        "SMALL", "SMART", "SMILE", "SMOKE", "SNAKE", "SOLID", "SOLVE", "SORRY", "SOUND", "SOUTH",
        "SPACE", "SPARE", "SPEAK", "SPEED", "SPELL", "SPEND", "SPLIT", "SPORT", "STAFF", "STAGE",
        "STAND", "START", "STATE", "STEAM", "STEEL", "STICK", "STILL", "STOCK", "STONE", "STORE",
        "STORM", "STORY", "STUDY", "STUFF", "STYLE", "SUGAR", "SUPER", "SWEET", "TABLE", "TASTE",
        "TEACH", "THANK", "THEME", "THICK", "THING", "THINK", "THREE", "THROW", "TIGHT", "TIRED",
        "TITLE", "TODAY", "TOTAL", "TOUCH", "TOUGH", "TOWER", "TRACK", "TRADE", "TRAIN", "TREAT",
        "TREND", "TRIAL", "TRUCK", "TRULY", "TRUST", "TRUTH", "TWICE", "UNDER", "UNION", "UNTIL",
        "UPPER", "URBAN", "USUAL", "VALID", "VALUE", "VIDEO", "VISIT", "VITAL", "VOICE", "WASTE",
        "WATCH", "WATER", "WHEEL", "WHERE", "WHICH", "WHILE", "WHITE", "WHOLE", "WOMAN", "WORLD",
        "WORRY", "WORSE", "WORST", "WORTH", "WOULD", "WRITE", "WRONG", "YIELD", "YOUNG", "YOUTH"
    };

    private final Random random = new Random();
    private final String target;
    private final List<String> guesses = new ArrayList<String>();
    private final List<int[]> feedback = new ArrayList<int[]>();

    private final StringBuilder current = new StringBuilder();
    private boolean won;
    private boolean lost;
    private String lastError;

    public WordGuessGame()
    {
        target = WORDS[random.nextInt(WORDS.length)];
    }

    public void typeLetter(char c)
    {
        if (isOver()) return;
        lastError = null;
        char upper = Character.toUpperCase(c);
        if (upper < 'A' || upper > 'Z') return;
        if (current.length() < WORD_LENGTH)
        {
            current.append(upper);
        }
    }

    public void backspace()
    {
        if (isOver()) return;
        lastError = null;
        if (current.length() > 0)
        {
            current.deleteCharAt(current.length() - 1);
        }
    }

    public void submitGuess()
    {
        if (isOver()) return;
        lastError = null;

        if (current.length() != WORD_LENGTH)
        {
            lastError = "Not enough letters";
            return;
        }

        String guess = current.toString();
        int[] result = score(guess, target);
        guesses.add(guess);
        feedback.add(result);
        current.setLength(0);

        boolean allCorrect = true;
        for (int i = 0; i < WORD_LENGTH; i++)
        {
            if (result[i] != CORRECT) { allCorrect = false; break; }
        }

        if (allCorrect)
        {
            won = true;
        }
        else if (guesses.size() >= MAX_GUESSES)
        {
            lost = true;
        }
    }

    /** Classic duplicate-letter-safe scoring: exact matches are claimed first, then leftover letter counts decide yellow vs gray for the rest. */
    private int[] score(String guess, String answer)
    {
        int[] result = new int[WORD_LENGTH];
        int[] remaining = new int[26];

        for (int i = 0; i < WORD_LENGTH; i++)
        {
            if (guess.charAt(i) == answer.charAt(i))
            {
                result[i] = CORRECT;
            }
            else
            {
                remaining[answer.charAt(i) - 'A']++;
            }
        }
        for (int i = 0; i < WORD_LENGTH; i++)
        {
            if (result[i] == CORRECT) continue;
            int idx = guess.charAt(i) - 'A';
            if (remaining[idx] > 0)
            {
                result[i] = PRESENT;
                remaining[idx]--;
            }
            else
            {
                result[i] = ABSENT;
            }
        }
        return result;
    }

    /** Best status seen for a letter across every guess so far, for coloring the on-screen keyboard (CORRECT beats PRESENT beats ABSENT, and an untried letter reports -1). */
    public int bestStatusForLetter(char c)
    {
        int best = -1;
        char upper = Character.toUpperCase(c);
        for (int g = 0; g < guesses.size(); g++)
        {
            String guess = guesses.get(g);
            int[] result = feedback.get(g);
            for (int i = 0; i < WORD_LENGTH; i++)
            {
                if (guess.charAt(i) == upper && result[i] > best)
                {
                    best = result[i];
                }
            }
        }
        return best;
    }

    public String getCurrentGuess() { return current.toString(); }
    public List<String> getGuesses() { return guesses; }
    public List<int[]> getFeedback() { return feedback; }
    public String getTarget() { return target; }
    public String getLastError() { return lastError; }
    public boolean isWon() { return won; }
    public boolean isLost() { return lost; }
    public boolean isOver() { return won || lost; }
    public int getGuessesUsed() { return guesses.size(); }

    /** Fewer guesses used is worth more - the standard "efficiency" reward shape, zero if you never solved it. */
    public int getScore()
    {
        if (!won) return 0;
        return (MAX_GUESSES - guesses.size() + 1) * 20;
    }
}
