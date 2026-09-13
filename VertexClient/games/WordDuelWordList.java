package games;

import java.util.HashSet;
import java.util.Set;

/**
 * WordDuelWordList
 * -----------------
 * A hand-curated list of common English words for validating Word
 * Duel submissions - plain dictionary words (not copyrightable),
 * written out directly rather than sourced from any particular
 * dictionary product or word-list file. Not exhaustive - a real
 * dictionary has hundreds of thousands of entries; this covers a few
 * hundred common words across a range of lengths, chosen to give
 * reasonable coverage for whatever letters a match happens to draw,
 * not to catch every possible valid word. A submission not in this
 * list is rejected even if it's a "real" word - a known, deliberate
 * limitation of doing this without a bundled dictionary file.
 */
public class WordDuelWordList
{
    private static final Set<String> WORDS = new HashSet<String>();
    static
    {
        String[] words = {
            "cat", "dog", "run", "sit", "eat", "top", "pot", "pit", "sit", "tap", "rat", "art", "tar",
            "ear", "era", "are", "sea", "eat", "ate", "tea", "set", "test", "rest", "star", "part",
            "cart", "care", "race", "rice", "ice", "nice", "dice", "site", "rise", "wise", "size",
            "time", "mine", "line", "wine", "nine", "dine", "fine", "pine", "sine", "vine", "tone",
            "note", "vote", "rote", "rode", "code", "mode", "node", "pose", "rose", "nose", "hose",
            "dose", "lose", "hope", "rope", "cope", "dope", "pope", "sole", "role", "hole", "pole",
            "mole", "cole", "dole", "tale", "male", "pale", "sale", "wale", "gale", "bale", "vale",
            "care", "bare", "dare", "fare", "hare", "mare", "pare", "rare", "ware", "core", "bore",
            "gore", "lore", "more", "pore", "sore", "tore", "wore", "cure", "lure", "pure", "sure",
            "date", "gate", "late", "mate", "rate", "fate", "hate", "plate", "state", "grate", "crate",
            "trade", "grade", "blade", "spade", "shade", "stage", "wage", "cage", "page", "rage",
            "sage", "stone", "shone", "phone", "prone", "drone", "crone", "alone", "atone", "clone",
            "smile", "while", "child", "mild", "wild", "field", "yield", "shield", "world", "word",
            "work", "worm", "worn", "corn", "born", "torn", "morn", "form", "storm", "norm", "farm",
            "harm", "warm", "charm", "alarm", "start", "smart", "chart", "shark", "spark", "dark",
            "park", "bark", "mark", "lark", "hark", "cart", "part", "dart", "tart", "mart", "wart",
            "heart", "earth", "hearth", "learn", "yearn", "burn", "turn", "urn", "fun", "run", "sun",
            "bun", "gun", "nun", "pun", "stun", "spun", "shun", "trust", "crust", "burst", "thrust",
            "must", "dust", "gust", "just", "rust", "bust", "list", "fist", "mist", "wrist", "twist",
            "exist", "hoist", "moist", "point", "joint", "paint", "faint", "saint", "quaint", "print",
            "sprint", "flint", "mint", "hint", "tint", "lint", "stint", "sound", "round", "ground",
            "found", "bound", "mound", "wound", "pound", "hound", "count", "mount", "amount", "fount",
            "house", "mouse", "blouse", "louse", "rouse", "grouse", "spouse", "arouse", "clock", "block",
            "flock", "shock", "stock", "smock", "rock", "sock", "lock", "dock", "mock", "knock",
            "black", "track", "crack", "stack", "shack", "smack", "snack", "attack", "quack", "back",
            "pack", "rack", "sack", "tack", "jack", "hack", "lack", "black", "brick", "trick", "click",
            "chick", "thick", "quick", "stick", "flick", "slick", "kick", "lick", "pick", "sick",
            "tick", "wick", "brave", "grave", "crave", "shave", "slave", "wave", "cave", "gave", "pave",
            "save", "have", "gate", "plane", "crane", "flame", "frame", "blame", "shame", "game",
            "fame", "lame", "name", "same", "tame", "came", "dame", "story", "glory", "ivory",
            "history", "victory", "factory", "memory", "theory", "dairy", "fairy", "hairy", "chair",
            "stair", "flair", "repair", "affair", "table", "cable", "fable", "gable", "stable", "unable",
            "able", "apple", "maple", "staple", "purple", "simple", "sample", "temple", "people",
            "couple", "double", "trouble", "bubble", "rubble", "stumble", "tumble", "humble", "jungle",
            "bundle", "handle", "candle", "sandal", "vandal", "medal", "petal", "metal", "total",
            "vital", "fatal", "capital", "hospital", "animal", "final", "signal", "normal", "formal",
            "moral", "coral", "choral", "mural", "plural", "rural", "sonic", "music", "magic", "logic",
            "tragic", "attic", "static", "topic", "public", "epic", "toxic", "basic", "panic",
            "manic", "ethnic", "picnic", "clinic", "cynic", "planet", "market", "basket", "jacket",
            "packet", "racket", "ticket", "pocket", "rocket", "socket", "bucket", "budget", "target",
            "carpet", "trumpet", "helmet", "planet", "orange", "change", "danger", "ranger", "anger",
            "finger", "singer", "linger", "ginger", "hunger", "longer", "stronger", "cottage", "voyage",
            "manage", "damage", "salvage", "average", "storage", "message", "passage", "village",
            "package", "sausage", "garbage", "acreage",
        };
        for (String word : words) WORDS.add(word.toLowerCase());
    }

    private WordDuelWordList()
    {
        // Static utility class - never instantiated.
    }

    public static boolean isValidWord(String word)
    {
        return word != null && WORDS.contains(word.toLowerCase());
    }
}
