package ai.knowledge;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * FactCache
 * ---------
 * A small persistent key -> value cache for facts a live lookup (see
 * CachingCapitalLookup) has already resolved once, so the same
 * question never needs a second network round-trip. Same flat
 * pipe-delimited-file convention every other "gamehub_*.dat" store in
 * VertexServer uses (see account.ServerAccountStore) - lives outside
 * source control and outside the jars, loaded once on construction,
 * and every new entry is appended to disk immediately so a crash never
 * loses more than the single in-flight lookup.
 *
 * Keys are pre-normalized by the caller (e.g. lowercased country
 * name) - this class doesn't know or care what a key "means," it's
 * purely a generic string->string store any ai.knowledge lookup can
 * share (not just capitals - a future word-definition lookup, say,
 * can construct its own FactCache with its own file name).
 */
public class FactCache
{
    private final String storeFile;
    private final Map<String, String> facts = new HashMap<String, String>();

    public FactCache(String storeFile)
    {
        this.storeFile = storeFile;
        load();
    }

    private void load()
    {
        File file = new File(storeFile);
        if (!file.exists())
        {
            return;
        }

        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null)
            {
                int sep = line.indexOf('|');
                if (sep <= 0)
                {
                    continue;
                }
                facts.put(line.substring(0, sep), line.substring(sep + 1));
            }
        }
        catch (IOException e)
        {
            // Best-effort - an unreadable cache just means starting cold, not a crash.
        }
        finally
        {
            if (reader != null)
            {
                try { reader.close(); } catch (IOException ignored) { }
            }
        }
    }

    public synchronized String get(String key)
    {
        return facts.get(key);
    }

    /** Stores a newly-resolved fact and immediately persists it - a fresh live lookup should never be lost to a crash before some later full save. */
    public synchronized void put(String key, String value)
    {
        facts.put(key, value);
        appendToFile(key, value);
    }

    private void appendToFile(String key, String value)
    {
        PrintWriter writer = null;
        try
        {
            writer = new PrintWriter(new FileWriter(storeFile, true));
            writer.println(key + "|" + value);
        }
        catch (IOException e)
        {
            // Best-effort - a failed persist just means this entry gets re-looked-up next time,
            // not a crash mid-match.
        }
        finally
        {
            if (writer != null)
            {
                writer.close();
            }
        }
    }
}
