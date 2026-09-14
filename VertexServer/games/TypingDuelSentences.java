package games;

import java.util.List;
import java.util.Random;

/**
 * TypingDuelSentences
 * --------------------
 * A bank of short sentences for Typing Duel to race on - all
 * originally written for this game, not sourced from any book,
 * article, or existing typing-test product. Varied lengths and
 * vocabulary so no single round always favors the same typing style.
 */
public class TypingDuelSentences
{
    private static final List<String> SENTENCES = java.util.Arrays.asList(
        "The quick fox jumped over the lazy dog near the old wooden fence.",
        "Bright stars filled the night sky above the quiet mountain village.",
        "She carried the heavy box up three flights of narrow stairs.",
        "A gentle breeze moved through the tall grass by the river bank.",
        "The chef added fresh basil to the simmering pot of tomato soup.",
        "Loud thunder rolled across the valley just before the storm arrived.",
        "He fixed the broken bicycle chain with a small metal wrench.",
        "The library was silent except for the soft rustle of turning pages.",
        "Golden leaves drifted slowly down onto the empty park bench.",
        "The little robot rolled across the floor and beeped twice.",
        "Warm sunlight poured through the kitchen window every morning.",
        "The old clock on the wall ticked steadily through the night.",
        "A curious cat watched the goldfish swim in the glass bowl.",
        "The train pulled into the station exactly on time today.",
        "Fresh snow covered the rooftops of the small mountain town.",
        "The garden was full of bees buzzing around the purple flowers.",
        "He typed the report quickly before the deadline at noon.",
        "The bakery smelled of warm bread and sweet cinnamon rolls.",
        "Two children raced their bikes down the quiet suburban street.",
        "The lighthouse beam swept slowly across the dark ocean water.",
        "A small dog barked happily as its owner returned home.",
        "The scientist carefully labeled each sample before the experiment.",
        "Rain tapped gently against the window throughout the evening.",
        "The orchestra tuned their instruments before the concert began.",
        "A rainbow appeared briefly after the short afternoon shower.",
        "The farmer loaded fresh vegetables onto the old wooden cart.",
        "Kids laughed loudly while playing tag in the school yard.",
        "The pilot announced a smooth landing just before sunset.",
        "Steam rose from the hot cup of coffee on the table.",
        "The old bridge creaked slightly as the truck rolled across it."
    );

    private static final Random RANDOM = new Random();

    private TypingDuelSentences()
    {
        // Static utility class - never instantiated.
    }

    public static String randomSentence()
    {
        return SENTENCES.get(RANDOM.nextInt(SENTENCES.size()));
    }
}
