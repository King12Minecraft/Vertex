import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SatelliteRegistry
 * ------------------
 * Only meaningful on the main server - a record of every satellite
 * that's ever registered itself here, with when it was last heard
 * from. Only exists so an admin can actually see, in one place, every
 * server that's syncing data through this one - registration happens
 * once per satellite startup (see ClientHandler's SATELLITE_REGISTER_REQUEST
 * handling), keyed by "host:port" so a satellite restarting on the
 * same address just updates its own entry rather than duplicating it.
 */
public class SatelliteRegistry
{
    private static final String STORE_FILE = "gamehub_satellite_registry.dat";
    private final Map<String, Long> lastSeenByAddress = new LinkedHashMap<String, Long>();

    public SatelliteRegistry()
    {
        load();
    }

    public synchronized void register(String host, int port)
    {
        String address = host + ":" + port;
        lastSeenByAddress.put(address, System.currentTimeMillis());
        save();
    }

    /** Every known satellite, "host:port|lastSeenEpochMillis" per entry, most-recently-seen first. */
    public synchronized List<String> listAll()
    {
        List<Map.Entry<String, Long>> entries = new ArrayList<Map.Entry<String, Long>>(lastSeenByAddress.entrySet());
        entries.sort(new java.util.Comparator<Map.Entry<String, Long>>()
        {
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b)
            {
                return Long.compare(b.getValue(), a.getValue());
            }
        });

        List<String> result = new ArrayList<String>();
        for (Map.Entry<String, Long> entry : entries)
        {
            result.add(entry.getKey() + "|" + entry.getValue());
        }
        return result;
    }

    private void save()
    {
        PrintWriter writer = null;
        try
        {
            writer = new PrintWriter(new FileWriter(STORE_FILE));
            for (Map.Entry<String, Long> entry : lastSeenByAddress.entrySet())
            {
                writer.println(entry.getKey() + "|" + entry.getValue());
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not save satellite registry: " + e.getMessage());
        }
        finally
        {
            if (writer != null) writer.close();
        }
    }

    private void load()
    {
        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(STORE_FILE));
            String line;
            while ((line = reader.readLine()) != null)
            {
                String[] parts = line.split("\\|", -1);
                if (parts.length == 2)
                {
                    try
                    {
                        lastSeenByAddress.put(parts[0], Long.parseLong(parts[1]));
                    }
                    catch (NumberFormatException e)
                    {
                        // Malformed line - skip rather than crash the whole load.
                    }
                }
            }
        }
        catch (IOException e)
        {
            // No file yet - a fresh main server with no satellites registered, nothing to load.
        }
        finally
        {
            if (reader != null) { try { reader.close(); } catch (IOException ignored) { } }
        }
    }
}
