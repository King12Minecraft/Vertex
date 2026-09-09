package admin;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * AdminLog
 * --------
 * A simple append-only audit trail of admin/moderator actions -
 * custom game approvals/removals, role changes, and anything else
 * worth being able to answer "who did this and when" about later.
 * Same "genuinely human-readable plain text file" choice as
 * FeedbackManager (gamehub_adminlog.txt) rather than the
 * pipe-delimited .dat format, since this is exactly the kind of file
 * an admin might want to open directly, not just view in-app.
 *
 * This is a record of what happened, not an access-control mechanism -
 * every write here happens only after the caller (ClientHandler) has
 * already verified the actor was actually an admin/moderator.
 */
public class AdminLog
{
    private static final String LOG_FILE = "gamehub_adminlog.txt";
    private static final int MAX_ENTRIES_RETURNED = 200;

    private final List<String> entries = new ArrayList<String>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy h:mm a");

    public AdminLog()
    {
        load();
    }

    public synchronized void log(String actorUsername, String action)
    {
        String line = "[" + dateFormat.format(new Date()) + "] " + actorUsername + ": " + action;
        entries.add(line);
        append(line);
    }

    /** Most recent first, capped at MAX_ENTRIES_RETURNED so a very old server install doesn't send back a huge history on every request. */
    public synchronized List<String> getRecent()
    {
        List<String> copy = new ArrayList<String>(entries);
        Collections.reverse(copy);
        if (copy.size() > MAX_ENTRIES_RETURNED)
        {
            return copy.subList(0, MAX_ENTRIES_RETURNED);
        }
        return copy;
    }

    private void append(String line)
    {
        try
        {
            PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true));
            writer.println(line);
            writer.close();
        }
        catch (IOException e)
        {
            System.err.println("Could not write to admin log: " + e.getMessage());
        }
    }

    private void load()
    {
        File file = new File(LOG_FILE);
        if (!file.exists())
        {
            return;
        }
        try
        {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (!line.trim().isEmpty())
                {
                    entries.add(line);
                }
            }
            reader.close();
        }
        catch (IOException e)
        {
            System.err.println("Could not load admin log: " + e.getMessage());
        }
    }
}
