package pages;

import java.util.ArrayList;
import java.util.List;

/**
 * ChangelogParser
 * ---------------
 * Reads CHANGELOG.md (the one hand-written file shared with the website - format described in
 * the file's own header comment): "## " starts an entry, "- " starts a bullet, and a line
 * indented by two spaces continues the bullet above it. Everything else, including the "# "
 * title and the header comment, is ignored. Deliberately tiny and forgiving: an odd line is
 * skipped rather than failing the page.
 */
public final class ChangelogParser
{
    private ChangelogParser() { }

    /** One release/date block. */
    public static final class Entry
    {
        public final String heading;
        public final List<String> bullets = new ArrayList<String>();

        Entry(String heading) { this.heading = heading; }
    }

    public static List<Entry> parse(String text)
    {
        List<Entry> entries = new ArrayList<Entry>();
        if (text == null)
        {
            return entries;
        }
        Entry current = null;
        boolean inComment = false;
        for (String raw : text.split("\r?\n"))
        {
            String line = raw;
            if (inComment)
            {
                if (line.contains("-->")) inComment = false;
                continue;
            }
            if (line.trim().startsWith("<!--"))
            {
                if (!line.contains("-->")) inComment = true;
                continue;
            }
            if (line.startsWith("## "))
            {
                current = new Entry(line.substring(3).trim());
                entries.add(current);
            }
            else if (current != null && line.startsWith("- "))
            {
                current.bullets.add(line.substring(2).trim());
            }
            else if (current != null && !current.bullets.isEmpty() && line.startsWith("  ") && !line.trim().isEmpty())
            {
                int last = current.bullets.size() - 1;
                current.bullets.set(last, current.bullets.get(last) + " " + line.trim());
            }
        }
        return entries;
    }
}
