import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TransactionManager
{
    private static final String TRANSACTIONS_FILE = "gamehub_transactions.dat";
    private static final int MAX_RETURNED = 25;

    private static class Entry
    {
        int accountId;
        long timestamp;
        String description;
    }

    private final List<Entry> entries = new ArrayList<Entry>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, h:mm a");

    public TransactionManager()
    {
        load();
    }

    public synchronized void log(int accountId, int delta, String reason)
    {
        String sign = delta >= 0 ? "+" : "";
        String description = reason + " (" + sign + delta + " coins)";

        Entry entry = new Entry();
        entry.accountId = accountId;
        entry.timestamp = System.currentTimeMillis();
        entry.description = description;
        entries.add(entry);

        appendToFile(entry);
    }

    public synchronized List<String> getRecentDescriptions(int accountId)
    {
        List<String> result = new ArrayList<String>();
        for (int i = entries.size() - 1; i >= 0 && result.size() < MAX_RETURNED; i--)
        {
            Entry entry = entries.get(i);
            if (entry.accountId == accountId)
            {
                result.add(dateFormat.format(new Date(entry.timestamp)) + " - " + entry.description);
            }
        }
        return result;
    }

    private void load()
    {
        File file = new File(TRANSACTIONS_FILE);
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
                String[] parts = line.split("\\|", 3);
                if (parts.length < 3)
                {
                    continue;
                }
                Entry entry = new Entry();
                entry.accountId = Integer.parseInt(parts[0]);
                entry.timestamp = Long.parseLong(parts[1]);
                entry.description = parts[2];
                entries.add(entry);
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not load transactions: " + e.getMessage());
        }
        finally
        {
            if (reader != null) { try { reader.close(); } catch (IOException ignored) { } }
        }
    }

    private void appendToFile(Entry entry)
    {
        PrintWriter writer = null;
        try
        {
            writer = new PrintWriter(new FileWriter(TRANSACTIONS_FILE, true));
            writer.println(entry.accountId + "|" + entry.timestamp + "|" + entry.description);
        }
        catch (IOException e)
        {
            System.err.println("Could not save transaction: " + e.getMessage());
        }
        finally
        {
            if (writer != null) writer.close();
        }
    }
}
