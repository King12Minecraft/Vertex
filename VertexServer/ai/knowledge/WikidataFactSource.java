package ai.knowledge;

/**
 * WikidataFactSource
 * --------------------
 * A FactSource backed by a single SPARQL query template run against
 * WikidataSparqlClient - one small, reusable class instead of writing
 * a near-identical one-off class per trivia category (companies'
 * founding year, inventions' inventor, historical events' year,
 * cities' country, ...). Each category just supplies its own query
 * template (with a {subject} placeholder for the label to match) and
 * the SPARQL variable name that holds the answer - see TriviaMatch for
 * the actual query text used by each category.
 *
 * The subject is escaped (backslashes and double quotes) before being
 * substituted into the template, since it's placed inside a SPARQL
 * string literal (`rdfs:label "{subject}"@en`) - a subject containing
 * a stray quote could otherwise break the query's syntax.
 */
public class WikidataFactSource implements FactSource
{
    private final WikidataSparqlClient client;
    private final String queryTemplate;
    private final String resultVariable;

    public WikidataFactSource(WikidataSparqlClient client, String queryTemplate, String resultVariable)
    {
        this.client = client;
        this.queryTemplate = queryTemplate;
        this.resultVariable = resultVariable;
    }

    public String lookup(String subject)
    {
        String escaped = subject.replace("\\", "\\\\").replace("\"", "\\\"");
        String query = queryTemplate.replace("{subject}", escaped);
        return client.queryFirstValue(query, resultVariable);
    }
}
