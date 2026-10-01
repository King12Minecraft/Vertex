package games;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CaptionChaosPrompts
 * -------------------
 * The silly prompts Caption Chaos shows ("The worst thing to say on a first date"). All written for this game -
 * nothing taken from any party game's prompt packs. Kept family-friendly on purpose: the chat/moderation rules
 * apply to what players type, so the prompts shouldn't be the thing that invites trouble.
 */
public final class CaptionChaosPrompts
{
    private static final String[] PROMPTS =
    {
        "The worst thing to say on a first date",
        "A terrible name for a pet goldfish",
        "What the dog is really thinking during a walk",
        "A rejected slogan for a toothpaste brand",
        "The least helpful thing a GPS could say",
        "A surprising item in a wizard's backpack",
        "The worst superpower to have on a Monday",
        "A bad title for a cooking show",
        "What the moon says when nobody is looking",
        "The last thing you want to hear from the pilot",
        "A weird rule at the world's strangest school",
        "The title of a sequel nobody asked for",
        "What your fridge would say at 3 a.m.",
        "A bad name for a rollercoaster",
        "The worst way to start a speech",
        "A secret ingredient that ruins any soup",
        "A strange thing to find in a treasure chest",
        "What the cat says to the vacuum cleaner",
        "The worst advice from a fortune cookie",
        "An awkward thing to say to an astronaut",
        "A rejected name for a new colour",
        "What a robot says when it burns the toast",
        "The worst thing to bring to a picnic",
        "A bad excuse for being late to your own party",
        "A very unhelpful sign on a hiking trail",
        "What the snowman would do if he came to life for a day",
        "A questionable pizza topping that should never exist",
        "The first line of a very boring adventure movie",
        "What the pigeons are planning",
        "An odd thing to hear from the back seat of a car",
        "The worst theme for a birthday party",
        "A terrible thing to yell in a library",
        "What the teacher's secret hobby really is",
        "A bad product for a company to invent",
        "What a dragon would complain about at the dentist",
        "The worst possible name for a sports team",
        "A weird sound the haunted house makes",
        "What your phone says when the battery hits 1%",
        "An awful gift to give a grandparent",
        "The slogan on the world's worst T-shirt",
        "A surprising talent of the school janitor",
        "What the scarecrow does after dark",
        "The most dramatic way to order a sandwich",
        "A bad name for a boat",
        "What a ghost says when it stubs its toe",
        "A rule every cat would add to the house",
        "The worst thing to find in your cereal",
        "The title of a very sad song about homework",
        "What the pyramid builders left in the group chat",
        "A useless app that somehow has a million downloads",
        "The worst thing to whisper during a quiet movie",
        "What the penguin is hiding under its coat",
        "A suspiciously specific warning label",
        "The worst name for a bakery",
        "How a knight would answer a phone call",
        "A terrible theme song for a weather report",
        "What the garden gnomes do at night",
        "The most boring superhero origin story",
        "An unexpected ingredient in grandma's famous cake",
        "What the mirror wishes it could say",
    };

    private CaptionChaosPrompts()
    {
        // Static utility class - never instantiated.
    }

    /** A fresh shuffled copy of the whole list, so a match can deal prompts without repeats. */
    public static List<String> shuffled()
    {
        List<String> copy = new ArrayList<String>(PROMPTS.length);
        Collections.addAll(copy, PROMPTS);
        Collections.shuffle(copy);
        return copy;
    }

    public static int count()
    {
        return PROMPTS.length;
    }
}
