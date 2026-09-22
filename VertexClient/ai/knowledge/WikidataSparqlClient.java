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
 * WikidataSparqlClient
 * ---------------------
 * Generic client for Wikidata's free public SPARQL endpoint
 * (query.wikidata.org/sparql) - no API key/account, no per-call cost,
 * no meaningful rate limit at trivia-game volume (same "unlimited"
 * requirement RestCountriesCapitalSource was chosen for, extended
 * beyond countries to companies, inventions, historical events, and
 * cities - Wikidata has structured facts about all of these). Runs
 * any SPARQL SELECT query text handed to it and extracts the first
 * result row's value for a named variable.
 *
 * Deliberately a minimal, format-specific extraction rather than a
 * full JSON library dependency - SPARQL's standard JSON results shape
 * (`results.bindings[0].<var>.value`) is a fixed W3C-specified format
 * that doesn't vary between queries, so ONE small parser here is
 * shared by every WikidataFactSource, however many categories are
 * built on top of it - unlike RestCountriesCapitalSource, which reads
 * one fixed, narrow response shape with its own one-off pattern.
 *
 * Building the actual SPARQL query text (matching a subject by label,
 * choosing which property to read) is WikidataFactSource's job - this
 * class only knows how to run a query string and pull one value back.
 *
 * NOTE for whoever runs this next: same caveat as
 * RestCountriesCapitalSource - this dev sandbox's own network access
 * doesn't reach query.wikidata.org (outbound HTTPS here is restricted
 * to package registries/GitHub), so this class's real HTTP call has
 * only been exercised with a fake FactSource standing in for the
 * network, never against the live endpoint. Test for real on a
 * machine with normal internet access before trusting any
 * WikidataFactSource in a live match - in particular, confirm the
 * exact rdfs:label text Wikidata stores for each subject used in
 * TriviaMatch's live task list actually matches (an English label
 * that differs even slightly just fails to resolve and is skipped,
 * per FactSource's null-on-failure contract - not a crash, but it
 * does mean that subject silently never produces a live question
 * until its label is corrected).
 */
public class WikidataSparqlClient
{
    private static final Duration TIMEOUT = Duration.ofSeconds(6);
    private static final String ENDPOINT = "https://query.wikidata.org/sparql";

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .build();

    /**
     * Runs query and returns the first result row's value for
     * resultVariable, or null if there were no results, the request
     * failed, or that variable wasn't bound in the first row.
     */
    public String queryFirstValue(String query, String resultVariable)
    {
        try
        {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
            URI uri = URI.create(ENDPOINT + "?format=json&query=" + encoded);
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Accept", "application/sparql-results+json")
                // Wikidata's own etiquette guidelines ask anonymous callers to identify themselves
                // with a real User-Agent - a generic default gets throttled more aggressively.
                .header("User-Agent", "Vertex-GameHub-TriviaEngine/1.0 (educational hobby project)")
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
            {
                return null;
            }
            return extractFirstBindingValue(response.body(), resultVariable);
        }
        catch (Exception e)
        {
            // Any failure (no network, timeout, interrupted, malformed response, ...) collapses
            // to null here - see FactSource's Javadoc for why.
            return null;
        }
    }

    /**
     * Pulls "value" out of the first binding for the given SPARQL
     * variable name, from SPARQL's standard JSON results format:
     * {"results":{"bindings":[{"varName":{"type":"literal","value":"1976"}}]}}
     * Package-visible (not private) so a verification harness can
     * exercise this parsing logic directly against canned response
     * text, without needing a real network call.
     */
    static String extractFirstBindingValue(String json, String variableName)
    {
        int bindingsIdx = json.indexOf("\"bindings\"");
        if (bindingsIdx < 0)
        {
            return null;
        }

        Pattern pattern = Pattern.compile(
            "\"" + Pattern.quote(variableName) + "\"\\s*:\\s*\\{[^}]*?\"value\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json.substring(bindingsIdx));
        return matcher.find() ? matcher.group(1) : null;
    }
}
