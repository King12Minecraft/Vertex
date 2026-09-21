package ai.knowledge;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RestCountriesCapitalSource
 * ----------------------------
 * Live "what's the capital of X" lookup against restcountries.com's
 * free public REST API - no API key or account needed, no per-call
 * cost, and generous enough rate limits that a trivia game's actual
 * call volume (a handful of NEW countries per match, everything else
 * hitting FactCache) will never come close to a limit. Chosen over an
 * LLM/search-API-based approach specifically because Bipin wants this
 * genuinely unlimited with no account to set up first.
 *
 * Deliberately hand-rolled, minimal JSON extraction rather than
 * pulling in a JSON library dependency the rest of Vertex doesn't
 * otherwise have - the response shape here is fixed and narrow (a
 * one-field array response when queried with ?fields=capital, e.g.
 * `[{"capital":["Astana"]}]`), so a small regex is honest and
 * sufficient. If a future ai.knowledge source needs to parse richer
 * JSON, that's the point to reconsider adding a real JSON library
 * rather than growing ad hoc regexes further.
 *
 * NOTE for whoever runs this next: this dev session's own network
 * access is restricted to package registries/GitHub (its sandbox
 * proxy doesn't allow arbitrary outbound HTTPS), so the actual HTTP
 * call here could NOT be exercised end-to-end from inside this
 * session - only the surrounding cache/retry/fallback orchestration
 * (CachingCapitalLookup) was verified, using a fake CapitalSource.
 * Test this specific class for real on a machine with normal internet
 * access (e.g. via BlueJ) before relying on it in a live match - if
 * restcountries.com's response shape has changed, or the endpoint
 * moves, only this one class needs to change (CapitalSource is the
 * seam that isolates it).
 */
public class RestCountriesCapitalSource implements CapitalSource
{
    private static final Pattern CAPITAL_FIELD = Pattern.compile("\"capital\"\\s*:\\s*\\[\\s*\"([^\"]+)\"");
    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .build();

    public String lookupCapital(String countryName)
    {
        try
        {
            String encoded = URLEncoder.encode(countryName, StandardCharsets.UTF_8.toString());
            URI uri = URI.create("https://restcountries.com/v3.1/name/" + encoded + "?fields=capital");
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
            {
                return null;
            }

            Matcher matcher = CAPITAL_FIELD.matcher(response.body());
            return matcher.find() ? matcher.group(1) : null;
        }
        catch (Exception e)
        {
            // Any failure (no network, timeout, interrupted, malformed response, unknown
            // country, ...) collapses to null here - see CapitalSource's Javadoc for why.
            return null;
        }
    }
}
