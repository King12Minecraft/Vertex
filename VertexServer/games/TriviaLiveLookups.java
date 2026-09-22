package games;

import ai.knowledge.CachingFactLookup;

/**
 * TriviaLiveLookups
 * -------------------
 * Bundles every live-lookup CachingFactLookup Trivia Blitz's live
 * question categories use, built once in GameServer and threaded
 * through TriviaMatchManager into each TriviaMatch - one shared
 * on-disk cache per category, not a fresh one per match. A plain,
 * explicitly-named holder rather than a generic map, matching the
 * rest of Vertex's constructor-injection style (see GameServer's own
 * manager fields) - there are only five of these and each is used by
 * name in TriviaMatch, so a map would just add an indirection with no
 * real benefit.
 */
public class TriviaLiveLookups
{
    public final CachingFactLookup capital;
    public final CachingFactLookup companyFoundingYear;
    public final CachingFactLookup inventor;
    public final CachingFactLookup historicalEventYear;
    public final CachingFactLookup cityCountry;

    public TriviaLiveLookups(CachingFactLookup capital, CachingFactLookup companyFoundingYear,
                              CachingFactLookup inventor, CachingFactLookup historicalEventYear,
                              CachingFactLookup cityCountry)
    {
        this.capital = capital;
        this.companyFoundingYear = companyFoundingYear;
        this.inventor = inventor;
        this.historicalEventYear = historicalEventYear;
        this.cityCountry = cityCountry;
    }
}
