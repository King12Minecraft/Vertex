package ai.knowledge;

/**
 * CapitalSource
 * -------------
 * The live-lookup boundary CachingCapitalLookup calls on a cache miss.
 * Its own small interface, separate from any specific HTTP client, so
 * the caching/retry/fallback logic around it (the part with actual
 * decisions to get right) can be verified with a fake source - see
 * RestCountriesCapitalSource's Javadoc for why the REAL network call
 * could only be sanity-checked, not exercised end-to-end, from inside
 * this dev session.
 */
public interface CapitalSource
{
    /**
     * Returns the capital of countryName, or null if it couldn't be
     * determined for any reason (unknown country, no network, timeout,
     * malformed response, ...). Every failure reason collapses to
     * null - the caller's fallback behavior (retry once, then give up
     * and let the caller fall back to a bundled question) is the same
     * regardless of WHY a lookup failed, so there's no reason for this
     * interface to distinguish them.
     */
    String lookupCapital(String countryName);
}
