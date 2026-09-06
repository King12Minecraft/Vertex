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
 * GameSuggestionStore
 * -------------------
 * A community wishlist of game ideas - replaces the old
 * upload-a-custom-game feature entirely. Nobody uploads or runs any
 * code here; it's just short text pitches ("a Connect Four game" /
 * "something like Pictionary but multiplayer"), visible to everyone so
 * people can see what's already been suggested. Same "genuinely
 * human-readable plain text file" choice as FeedbackManager/AdminLog
 * (gamehub_gamesuggestions.txt).
 */
public class GameSuggestionStore
{
    private static final String FILE_NAME = "gamehub_gamesuggestions.txt";
    private static final int MAX_ENTRIES_RETURNED = 100;
    private static final int MAX_SUGGESTION_LENGTH = 300;

    private final List<String> entries = new ArrayList<String>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy");

    public GameSuggestionStore()
    {
        load();
    }

    public synchronized void submit(String username, String text)
    {
        String trimmed = text.trim();
        if (trimmed.length() > MAX_SUGGESTION_LENGTH)
        {
            trimmed = trimmed.substring(0, MAX_SUGGESTION_LENGTH);
        }
        String line = "[" + dateFormat.format(new Date()) + "] " + username + ": " + trimmed;
        entries.add(line);
        append(line);
    }

    /** Newest first, capped so a long-running server doesn't send back its entire history every time someone opens the page. */
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
            PrintWriter writer = new PrintWriter(new FileWriter(FILE_NAME, true));
            writer.println(line);
            writer.close();
        }
        catch (IOException e)
        {
            System.err.println("Could not write to game suggestions file: " + e.getMessage());
        }
    }

    private void load()
    {
        File file = new File(FILE_NAME);
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
            System.err.println("Could not load game suggestions: " + e.getMessage());
        }
    }
}
