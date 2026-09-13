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
 * dictionary has hundreds of thousands of entries; this covers
 * roughly a thousand common words across a range of lengths (3-9
 * letters), chosen to give solid coverage for whatever letters a
 * match happens to draw, not to catch every possible valid word. A
 * submission not in this list is rejected even if it's a "real" word
 * - a known, deliberate limitation of doing this without a bundled
 * dictionary file.
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
            // Common short words (3-4 letters) - high letter-reuse value
            "bat", "bag", "big", "bit", "boy", "buy", "can", "cap", "car", "cop", "cow", "cry",
            "cup", "cut", "day", "die", "dig", "dip", "dry", "dye", "egg", "end", "eye", "fan",
            "far", "fat", "few", "fix", "fly", "fog", "for", "fox", "fun", "gap", "gas", "get",
            "gym", "hat", "hen", "hid", "him", "hip", "hit", "hot", "how", "hub", "hug", "hut",
            "jam", "jaw", "jet", "job", "jog", "joy", "jug", "key", "kid", "kit", "lap", "law",
            "lay", "leg", "let", "lid", "lie", "lip", "log", "low", "lot", "lug", "man", "map",
            "mat", "may", "mix", "mob", "mud", "mug", "nap", "net", "new", "nod", "nor", "not",
            "now", "nut", "odd", "off", "oil", "old", "one", "our", "out", "owl", "own", "pad",
            "pan", "paw", "pay", "pea", "pen", "pet", "pie", "pig", "pin", "pod", "pop", "pug",
            "pup", "put", "raw", "ray", "red", "rib", "rid", "rim", "rip", "rob", "rod", "row",
            "rub", "rug", "sad", "saw", "say", "sea", "see", "she", "shy", "sip", "six", "sky",
            "sly", "sob", "sod", "spy", "sum", "tag", "tan", "tap", "tax", "the", "tie", "tin",
            "tip", "toe", "ton", "top", "toy", "try", "tub", "tug", "van", "vet", "via", "vow",
            "wag", "wax", "way", "wet", "who", "why", "wig", "win", "wit", "yes", "yet", "you",
            "zip", "zoo", "able", "acid", "aged", "also", "area", "army", "away", "baby", "back",
            "ball", "band", "bank", "base", "bath", "bear", "beat", "beer", "bell", "belt", "bend",
            "best", "bike", "bill", "bird", "bite", "blue", "boat", "body", "bomb", "bond", "bone",
            "book", "boot", "boss", "both", "bowl", "boys", "bulk", "burn", "bush", "busy", "call",
            "calm", "came", "camp", "card", "care", "case", "cash", "cast", "cell", "chat", "chip",
            "city", "clay", "club", "coal", "coat", "cook", "cool", "cope", "copy", "corn", "cost",
            "crew", "crop", "dark", "data", "dawn", "days", "dead", "deal", "dear", "debt", "deep",
            "deny", "desk", "dial", "diet", "disc", "disk", "does", "done", "door", "down", "draw",
            "drop", "drug", "drum", "dual", "duke", "dust", "duty", "each", "earn", "ease", "east",
            "easy", "edge", "else", "even", "ever", "evil", "exam", "exit", "face", "fact", "fail",
            "fair", "fall", "farm", "fast", "fear", "feed", "feel", "feet", "fell", "felt", "file",
            "fill", "film", "find", "fine", "fire", "firm", "fish", "fist", "five", "flag", "flat",
            "flow", "food", "foot", "ford", "fork", "form", "fort", "four", "free", "from", "fuel",
            "full", "fund", "gain", "game", "gate", "gave", "gear", "gift", "girl", "give", "glad",
            "goal", "goes", "gold", "golf", "gone", "good", "gray", "grew", "grey", "grow", "gulf",
            "hair", "half", "hall", "hand", "hang", "hard", "harm", "hate", "have", "head", "hear",
            "heat", "held", "hell", "help", "here", "hero", "high", "hill", "hire", "hold", "hole",
            "holy", "home", "hope", "host", "hour", "huge", "hung", "hunt", "hurt", "idea", "inch",
            "into", "iron", "item", "jail", "join", "joke", "jump", "jury", "just", "keen", "keep",
            "kept", "kick", "kill", "kind", "king", "knee", "knew", "know", "lack", "lady", "lake",
            "land", "lane", "last", "late", "lawn", "lead", "leaf", "lean", "left", "lens", "less",
            "life", "lift", "like", "line", "link", "lion", "list", "live", "load", "loan", "lock",
            "logo", "long", "look", "lord", "lose", "loss", "lost", "loud", "love", "luck", "lung",
            "made", "mail", "main", "make", "male", "mall", "many", "mark", "mask", "mass", "meal",
            "mean", "meat", "meet", "melt", "mild", "mile", "milk", "mind", "mine", "mint", "miss",
            "mode", "mood", "moon", "more", "most", "move", "much", "must", "nail", "name", "navy",
            "near", "neck", "need", "news", "next", "nice", "nine", "none", "noon", "nose", "note",
            "okay", "once", "only", "onto", "open", "oral", "over", "pace", "pack", "page", "paid",
            "pain", "pair", "palm", "park", "part", "pass", "past", "path", "peak", "pick", "pink",
            "pipe", "plan", "play", "plot", "plug", "plus", "poem", "poet", "pole", "poll", "pool",
            "poor", "pork", "port", "pose", "post", "pull", "pump", "pure", "push", "race", "rail",
            "rain", "rank", "rare", "rate", "read", "real", "rear", "rely", "rent", "rest", "rice",
            "rich", "ride", "ring", "rise", "risk", "road", "rock", "role", "roll", "roof", "room",
            "root", "rope", "rose", "rule", "rush", "safe", "said", "sail", "sake", "sale", "salt",
            "same", "sand", "save", "seal", "seat", "seed", "seek", "seem", "seen", "self", "sell",
            "send", "sent", "shed", "ship", "shoe", "shop", "shot", "show", "shut", "sick", "side",
            "sign", "silk", "sing", "sink", "site", "size", "skin", "skip", "slip", "slow", "snap",
            "snow", "soft", "soil", "sold", "sole", "some", "song", "soon", "sort", "soul", "soup",
            "spin", "spot", "star", "stay", "step", "stop", "such", "suit", "sure", "swim", "tail",
            "take", "tale", "talk", "tall", "tank", "tape", "task", "team", "tell", "tend", "tent",
            "term", "test", "text", "than", "that", "them", "then", "they", "thin", "this", "thus",
            "tide", "tile", "till", "time", "tiny", "told", "toll", "tone", "tool", "tour", "town",
            "trap", "tray", "tree", "trip", "true", "tune", "turn", "twin", "type", "unit", "upon",
            "used", "user", "vary", "vast", "very", "view", "vote", "wage", "wait", "wake", "walk",
            "wall", "want", "ward", "warm", "wash", "wave", "ways", "weak", "wear", "week", "well",
            "went", "were", "west", "what", "when", "wide", "wife", "wild", "will", "wind", "wine",
            "wing", "wire", "wise", "wish", "with", "wolf", "wood", "wool", "word", "wore", "work",
            "worn", "yard", "yeah", "year", "your", "zero", "zone",
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
