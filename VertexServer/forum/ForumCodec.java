package forum;

import java.util.ArrayList;
import java.util.List;

/**
 * ForumCodec
 * -----------
 * One-line, tab-separated records with every field escaped - used both for the
 * on-disk store and for the lists sent to clients. Escaping backslash, tab, newline
 * and carriage return means a field can never contain a raw tab or line break, so a
 * post body (or title, or username) can't split into a second record or forge extra
 * fields when the file is reloaded - the same class of bug fixed in the game
 * suggestion store's flat file (an embedded newline forging another player's entry).
 */
public final class ForumCodec
{
    private ForumCodec()
    {
        // Static utility class - never instantiated.
    }

    public static String escape(String text)
    {
        if (text == null)
        {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (c == '\\') out.append("\\\\");
            else if (c == '\t') out.append("\\t");
            else if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else out.append(c);
        }
        return out.toString();
    }

    public static String unescape(String text)
    {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length())
            {
                char next = text.charAt(++i);
                if (next == 't') out.append('\t');
                else if (next == 'n') out.append('\n');
                else if (next == 'r') out.append('\r');
                else if (next == '\\') out.append('\\');
                else out.append(c).append(next);
            }
            else
            {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Escapes each field and joins them with tabs - always exactly one physical line. */
    public static String join(String... fields)
    {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < fields.length; i++)
        {
            if (i > 0) out.append('\t');
            out.append(escape(fields[i]));
        }
        return out.toString();
    }

    /** Inverse of join(): splits on raw tabs (escaped fields contain none) and unescapes each field. */
    public static List<String> split(String line)
    {
        List<String> fields = new ArrayList<String>();
        String[] parts = line.split("\t", -1);
        for (int i = 0; i < parts.length; i++)
        {
            fields.add(unescape(parts[i]));
        }
        return fields;
    }

    /** Wire form of a thread in a board's thread list: id, board, title, author, reply count, last activity (epoch ms), locked (0/1). */
    public static String threadSummaryLine(ForumThread thread)
    {
        return join(thread.getId(), thread.getBoardId(), thread.getTitle(), thread.getAuthor(),
            String.valueOf(thread.getReplyCount()), String.valueOf(thread.getLastActivityMs()),
            thread.isLocked() ? "1" : "0");
    }

    /** Wire form of one post in a thread view: id, author, created (epoch ms), body. */
    public static String postLine(ForumPost post)
    {
        return join(post.getId(), post.getAuthor(), String.valueOf(post.getCreatedMs()), post.getBody());
    }
}
