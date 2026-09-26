package admin;

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

/**
 * FeedbackManager
 * ----------------
 * Bug reports and suggestions about Vertex itself - separate from
 * ModerationManager's player-conduct reports (REPORT_*), which are
 * about other PLAYERS, not the app. Persisted as a genuinely
 * human-readable plain text file (gamehub_feedback.txt) - open it in
 * any text editor and it reads the same way it renders in-app - rather
 * than the pipe-delimited .dat format the rest of this class's siblings
 * use, since being a real, readable txt file was specifically asked for.
 *
 * Every submission is visible to admins (see ClientHandler.isAdmin());
 * everyone else only ever sees their own, matching the moderation
 * reports' "who can see what" pattern one level down (a player can't see
 * other players' reports either).
 */
public class FeedbackManager
{
    private static final String FEEDBACK_FILE = "gamehub_feedback.txt";
    private static final String DELIMITER = "----------------------------------------------------------------";
    private static final String TITLE_PREFIX = "Title: ";
    private static final String STEPS_MARKER = "\n\nSteps to reproduce:\n";

    private static class Entry
    {
        String id;
        String type;
        String submitterUsername;
        long timestamp;
        /** A short one-line summary - required, same as any bug tracker's "title" field. Empty string for entries saved before this field existed (see parseBlock's fallback). */
        String title;
        String text;
        /** Bug reports only - null/empty for a suggestion, and optional even for a bug. */
        String stepsToReproduce;
    }

    private final List<Entry> entries = new ArrayList<Entry>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy h:mm a");

    public FeedbackManager()
    {
        load();
    }

    /** type is "BUG" or "SUGGESTION". stepsToReproduce may be null/blank (always for a suggestion; optional for a bug report too). Appends to the in-memory list and rewrites the txt file. */
    public synchronized void submit(String submitterUsername, String type, String title, String text, String stepsToReproduce)
    {
        Entry entry = new Entry();
        entry.id = "feedback-" + System.currentTimeMillis() + "-" + entries.size();
        entry.type = "BUG".equals(type) ? "BUG" : "SUGGESTION";
        entry.submitterUsername = submitterUsername;
        entry.timestamp = System.currentTimeMillis();
        entry.title = title == null ? "" : title.trim();
        entry.text = text.trim();
        entry.stepsToReproduce = ("BUG".equals(entry.type) && stepsToReproduce != null) ? stepsToReproduce.trim() : "";
        entries.add(entry);
        save();
    }

    /** Every submission, most recent first - for an admin. */
    public synchronized List<String> getAllDescriptions()
    {
        return describe(null);
    }

    /** Just this one person's own submissions, most recent first. */
    public synchronized List<String> getDescriptionsFor(String username)
    {
        return describe(username);
    }

    private List<String> describe(String onlyUsername)
    {
        List<String> result = new ArrayList<String>();
        for (int i = entries.size() - 1; i >= 0; i--)
        {
            Entry entry = entries.get(i);
            if (onlyUsername != null && !entry.submitterUsername.equalsIgnoreCase(onlyUsername))
            {
                continue;
            }
            StringBuilder display = new StringBuilder();
            display.append('[').append(entry.type).append("] ")
                .append(dateFormat.format(new Date(entry.timestamp)))
                .append(" - ").append(entry.submitterUsername);
            // Entries saved before the title field existed have an empty title -
            // fall back to the old "no title, just the text after a colon" look
            // rather than showing a jarring empty ": ".
            if (!entry.title.isEmpty())
            {
                display.append(": ").append(entry.title).append('\n').append(entry.text);
            }
            else
            {
                display.append(": ").append(entry.text);
            }
            if (entry.stepsToReproduce != null && !entry.stepsToReproduce.isEmpty())
            {
                display.append("\n\nSteps to reproduce:\n").append(entry.stepsToReproduce);
            }
            result.add(display.toString());
        }
        return result;
    }

    private void load()
    {
        File file = new File(FEEDBACK_FILE);
        if (!file.exists())
        {
            return;
        }

        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(file));
            String line;
            StringBuilder block = new StringBuilder();
            while ((line = reader.readLine()) != null)
            {
                if (line.equals(DELIMITER))
                {
                    parseBlock(block.toString());
                    block.setLength(0);
                }
                else
                {
                    block.append(line).append('\n');
                }
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not load feedback: " + e.getMessage());
        }
        finally
        {
            if (reader != null) { try { reader.close(); } catch (IOException ignored) { } }
        }
    }

    /**
     * Block format written by save(): one human-readable header line -
     * "[TYPE] date - username (id=feedback-<epoch millis>-<index>)" -
     * then a body that's optionally "Title: <title>", a blank line, the
     * description, and (bug reports with steps only) a "Steps to
     * reproduce:" section, up to (not including) the delimiter. The
     * timestamp isn't re-parsed from the printed date text (locale/format
     * round-tripping is fragile); it's pulled back out of the id itself,
     * which already embeds the millis it was created with. Entries
     * written before the title/steps fields existed have no "Title: "
     * line at all - treated as title="" and the whole body as the plain
     * description, rather than losing or misparsing pre-existing feedback.
     */
    private void parseBlock(String block)
    {
        int firstNewline = block.indexOf('\n');
        if (firstNewline < 0)
        {
            return;
        }
        String header = block.substring(0, firstNewline);
        String body = block.substring(firstNewline + 1);
        // Body was written with a trailing '\n' before the delimiter line - drop exactly that one.
        if (body.endsWith("\n"))
        {
            body = body.substring(0, body.length() - 1);
        }

        int idStart = header.indexOf("(id=");
        int idEnd = header.indexOf(')', idStart);
        int dashBeforeUsername = header.lastIndexOf(" - ", idStart < 0 ? header.length() : idStart);
        if (idStart < 0 || idEnd < 0 || dashBeforeUsername < 0 || !header.startsWith("["))
        {
            return;
        }

        int typeEnd = header.indexOf(']');
        if (typeEnd < 0)
        {
            return;
        }

        Entry entry = new Entry();
        entry.type = header.substring(1, typeEnd);
        entry.submitterUsername = header.substring(dashBeforeUsername + 3, idStart).trim();
        entry.id = header.substring(idStart + 4, idEnd);
        entry.timestamp = timestampFromId(entry.id);

        String title = "";
        String description = body;
        if (body.startsWith(TITLE_PREFIX))
        {
            int titleLineEnd = body.indexOf('\n');
            if (titleLineEnd < 0)
            {
                titleLineEnd = body.length();
            }
            title = body.substring(TITLE_PREFIX.length(), titleLineEnd);
            description = titleLineEnd < body.length() ? body.substring(titleLineEnd + 1) : "";
            if (description.startsWith("\n"))
            {
                description = description.substring(1);
            }
        }

        String steps = "";
        int stepsMarker = description.indexOf(STEPS_MARKER);
        if (stepsMarker >= 0)
        {
            steps = description.substring(stepsMarker + STEPS_MARKER.length());
            description = description.substring(0, stepsMarker);
        }

        entry.title = title;
        entry.text = description;
        entry.stepsToReproduce = steps;
        entries.add(entry);
    }

    private long timestampFromId(String id)
    {
        String[] parts = id.split("-");
        if (parts.length >= 2)
        {
            try { return Long.parseLong(parts[1]); }
            catch (NumberFormatException ignored) { }
        }
        return System.currentTimeMillis();
    }

    /**
     * Rewrites the whole file every time (same pattern as
     * ModerationManager) - feedback volume is small enough that this is
     * simpler and safer than an append-only log that could interleave
     * badly with a hand-edit. Each block is genuinely readable as plain
     * text - a header line, the free-text body, then a divider - not a
     * pipe-delimited record like the rest of this class's siblings,
     * since being a real, readable txt file was specifically asked for.
     */
    private void save()
    {
        PrintWriter writer = null;
        try
        {
            writer = new PrintWriter(new FileWriter(FEEDBACK_FILE));
            for (int i = 0; i < entries.size(); i++)
            {
                Entry entry = entries.get(i);
                writer.println("[" + entry.type + "] " + dateFormat.format(new Date(entry.timestamp))
                    + " - " + entry.submitterUsername + " (id=" + entry.id + ")");
                StringBuilder body = new StringBuilder();
                if (!entry.title.isEmpty())
                {
                    body.append(TITLE_PREFIX).append(entry.title).append("\n\n");
                }
                body.append(entry.text);
                if (entry.stepsToReproduce != null && !entry.stepsToReproduce.isEmpty())
                {
                    body.append(STEPS_MARKER).append(entry.stepsToReproduce);
                }
                writer.println(body.toString());
                writer.println(DELIMITER);
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not save feedback: " + e.getMessage());
        }
        finally
        {
            if (writer != null) writer.close();
        }
    }
}
