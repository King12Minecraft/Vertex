package ai.knowledge;

/**
 * FactSource
 * ----------
 * The live-lookup boundary CachingFactLookup calls on a cache miss -
 * generic across every kind of fact Vertex's trivia categories ask
 * about (a country's capital, a company's founding year, an
 * invention's inventor, ...), not just capitals. Kept as its own
 * small interface, separate from any specific HTTP client, so the
 * caching/retry/fallback logic around it (the part with actual
 * decisions to get right) can be verified with a fake source - see
 * RestCountriesCapitalSource and WikidataFactSource's own Javadoc for
 * why their REAL network calls could only be sanity-checked, not
 * exercised end-to-end, from inside this dev session.
 */
public interface FactSource
{
    /**
     * Returns the fact for the given subject (e.g. a country name, a
     * company name, an invention's name), or null if it couldn't be
     * determined for any reason (unknown subject, no network, timeout,
     * malformed response, ...). Every failure reason collapses to
     * null - the caller's fallback behavior (retry once, then give up
     * and let the caller fall back to a bundled question) is the same
     * regardless of WHY a lookup failed, so there's no reason for this
     * interface to distinguish them.
     */
    String lookup(String subject);
}
