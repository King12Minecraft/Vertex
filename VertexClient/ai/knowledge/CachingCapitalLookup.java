package ai.knowledge;

/**
 * CachingCapitalLookup
 * -----------------------
 * The cache-first behavior Bipin asked for: check the local FactCache
 * first; on a miss, call the live CapitalSource, retrying once if the
 * first attempt fails ("wait/retry briefly, then skip" - agreed
 * fallback behavior), and persist a successful result so the same
 * country never needs a second network round-trip. Returns null
 * (never throws) if the cache misses AND both live attempts fail - the
 * caller (TriviaMatch) falls back to a bundled question in that case,
 * exactly as agreed, rather than stalling a live match waiting on the
 * network.
 *
 * Deliberately takes its FactCache and CapitalSource as constructor
 * arguments rather than constructing them itself, so this
 * orchestration logic - the part with actual branching to get right -
 * is fully verifiable with a fake cache/source, independent of
 * whether the real network call succeeds (see RestCountriesCapitalSource's
 * Javadoc for why that part couldn't be exercised from this session).
 */
public class CachingCapitalLookup
{
    private final FactCache cache;
    private final CapitalSource source;

    public CachingCapitalLookup(FactCache cache, CapitalSource source)
    {
        this.cache = cache;
        this.source = source;
    }

    public String lookupCapital(String countryName)
    {
        String key = countryName.trim().toLowerCase();
        String cached = cache.get(key);
        if (cached != null)
        {
            return cached;
        }

        String result = source.lookupCapital(countryName);
        if (result == null)
        {
            result = source.lookupCapital(countryName); // one retry, per the agreed fallback behavior
        }
        if (result != null)
        {
            cache.put(key, result);
        }
        return result;
    }
}
