package ai.knowledge;

/**
 * CachingFactLookup
 * -------------------
 * The cache-first behavior Bipin asked for, generalized across every
 * trivia category (was CachingCapitalLookup, capital-only, in the
 * first slice of this feature - generalized once a second category
 * was needed, same "prove it on one case, then generalize" discipline
 * as every other ai package phase). Checks the local FactCache first;
 * on a miss, calls the live FactSource, retrying once if the first
 * attempt fails ("wait/retry briefly, then skip" - agreed fallback
 * behavior), and persists a successful result so the same subject
 * never needs a second network round-trip. Returns null (never
 * throws) if the cache misses AND both live attempts fail - the
 * caller (TriviaMatch) falls back to a bundled question in that case.
 *
 * keyPrefix namespaces this category's entries within the shared
 * FactCache - e.g. "capital" vs "founded" vs "inventor" - so two
 * categories that happen to share a subject string (unlikely, but
 * possible) never collide on the same cache key.
 *
 * Deliberately takes its FactCache and FactSource as constructor
 * arguments rather than constructing them itself, so this
 * orchestration logic is fully verifiable with a fake source,
 * independent of whether the real network call succeeds.
 */
public class CachingFactLookup
{
    private final FactCache cache;
    private final FactSource source;
    private final String keyPrefix;

    public CachingFactLookup(FactCache cache, FactSource source, String keyPrefix)
    {
        this.cache = cache;
        this.source = source;
        this.keyPrefix = keyPrefix;
    }

    public String lookup(String subject)
    {
        String key = keyPrefix + ":" + subject.trim().toLowerCase();
        String cached = cache.get(key);
        if (cached != null)
        {
            return cached;
        }

        String result = source.lookup(subject);
        if (result == null)
        {
            result = source.lookup(subject); // one retry, per the agreed fallback behavior
        }
        if (result != null)
        {
            cache.put(key, result);
        }
        return result;
    }
}
