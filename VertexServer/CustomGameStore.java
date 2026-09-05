import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * CustomGameStore
 * ---------------
 * User-uploaded games - the "Roblox-style" catalog. Anyone can upload a
 * compiled game jar (see CustomGamesPanel/CodeEditorWindow on the
 * client) and it becomes playable by everyone connected to this
 * server, right alongside the built-in games.
 *
 * Deliberately NO sandboxing: an uploaded game runs with exactly the
 * same JVM permissions Vertex itself has (see CustomGameLoader on the
 * client, which does the actual class-loading). Building a real
 * security boundary around arbitrary uploaded code is a much bigger
 * undertaking than this feature itself - acceptable here because this
 * is for a closed group of friends, same trust level as running any
 * other program a friend sends you, not the public internet.
 *
 * Metadata lives in a small pipe-delimited index file, same convention
 * as LeaderboardManager/ModerationManager; the jar bytes themselves are
 * stored one file per game under custom_games/ rather than inline in
 * that index, same reasoning as ClientUpdatePackage keeping Vertex.jar
 * as a real file instead of some serialized blob.
 */
public class CustomGameStore
{
    private static final String INDEX_FILE = "gamehub_custom_games.dat";
    private static final String GAMES_DIR = "custom_games";
    /** Generous for a school-project-scale game, small enough not to choke a slow connection or fill up a friend's disk. */
    private static final long MAX_JAR_BYTES = 8L * 1024 * 1024;

    public static class Entry
    {
        public String gameId;
        public String name;
        public String authorUsername;
        public String entryClassName;
        public long uploadedAt;
        public String hash;
        public long sizeBytes;
        /** New uploads start unapproved - see approve(). An admin has to sign off before everyone else can see/play it (Section: review queue), same "hidden until reviewed" idea as a moderation hold. */
        public boolean approved;
    }

    private final List<Entry> entries = new ArrayList<Entry>();

    public CustomGameStore()
    {
        new File(GAMES_DIR).mkdirs();
        load();
    }

    /** Stores a newly-uploaded jar and returns its assigned entry (id included), or null if rejected (empty/oversized/couldn't write to disk). */
    public synchronized Entry upload(String name, String authorUsername, String entryClassName, byte[] jarBytes)
    {
        if (jarBytes == null || jarBytes.length == 0 || jarBytes.length > MAX_JAR_BYTES)
        {
            return null;
        }

        Entry entry = new Entry();
        entry.gameId = "custom-" + System.currentTimeMillis() + "-" + entries.size();
        entry.name = sanitize(name);
        entry.authorUsername = sanitize(authorUsername);
        entry.entryClassName = sanitize(entryClassName);
        entry.uploadedAt = System.currentTimeMillis();
        entry.hash = FileHash.sha256Hex(jarBytes);
        entry.sizeBytes = jarBytes.length;
        entry.approved = false;

        if (!writeJar(entry.gameId, jarBytes))
        {
            return null;
        }

        entries.add(entry);
        save();
        return entry;
    }

    /** Newest first - matches ticker/feed ordering conventions used elsewhere (e.g. FeedbackManager). */
    public synchronized List<Entry> getAll()
    {
        List<Entry> copy = new ArrayList<Entry>(entries);
        Collections.sort(copy, new Comparator<Entry>()
        {
            public int compare(Entry a, Entry b) { return Long.valueOf(b.uploadedAt).compareTo(Long.valueOf(a.uploadedAt)); }
        });
        return copy;
    }

    public synchronized Entry findById(String gameId)
    {
        for (int i = 0; i < entries.size(); i++)
        {
            if (entries.get(i).gameId.equals(gameId))
            {
                return entries.get(i);
            }
        }
        return null;
    }

    /** Only the uploader or an admin may remove a game - same "own it or moderate it" rule ClientHandler already applies to feedback/reports. */
    public synchronized boolean removeById(String gameId, String requestingUsername, boolean isAdmin)
    {
        Entry entry = findById(gameId);
        if (entry == null)
        {
            return false;
        }
        if (!isAdmin && !entry.authorUsername.equalsIgnoreCase(requestingUsername))
        {
            return false;
        }
        entries.remove(entry);
        new File(GAMES_DIR, entry.gameId + ".jar").delete();
        save();
        return true;
    }

    /** Admin-only review action - marks an uploaded game visible to everyone. Returns false if the game no longer exists (e.g. it was removed while sitting in the review queue). */
    public synchronized boolean approve(String gameId)
    {
        Entry entry = findById(gameId);
        if (entry == null)
        {
            return false;
        }
        entry.approved = true;
        save();
        return true;
    }

    /** Null if the entry's jar is missing on disk (shouldn't normally happen, but a download request racing a delete is possible). */
    public byte[] readJarBytes(String gameId)
    {
        File file = new File(GAMES_DIR, gameId + ".jar");
        if (!file.isFile())
        {
            return null;
        }
        try
        {
            return java.nio.file.Files.readAllBytes(file.toPath());
        }
        catch (IOException e)
        {
            return null;
        }
    }

    private boolean writeJar(String gameId, byte[] jarBytes)
    {
        FileOutputStream out = null;
        try
        {
            out = new FileOutputStream(new File(GAMES_DIR, gameId + ".jar"));
            out.write(jarBytes);
            return true;
        }
        catch (IOException e)
        {
            System.err.println("Could not save custom game jar: " + e.getMessage());
            return false;
        }
        finally
        {
            if (out != null) { try { out.close(); } catch (IOException ignored) { } }
        }
    }

    /** Keeps free-text fields (game name especially) from breaking the pipe-delimited index format. */
    private static String sanitize(String s)
    {
        if (s == null)
        {
            return "";
        }
        return s.replace("|", "/").replace("\n", " ").replace("\r", " ").trim();
    }

    private void save()
    {
        PrintWriter writer = null;
        try
        {
            writer = new PrintWriter(new FileWriter(INDEX_FILE));
            for (int i = 0; i < entries.size(); i++)
            {
                Entry e = entries.get(i);
                writer.println(e.gameId + "|" + e.name + "|" + e.authorUsername + "|" + e.entryClassName
                    + "|" + e.uploadedAt + "|" + e.hash + "|" + e.sizeBytes + "|" + (e.approved ? "1" : "0"));
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not save custom game index: " + e.getMessage());
        }
        finally
        {
            if (writer != null) writer.close();
        }
    }

    private void load()
    {
        File file = new File(INDEX_FILE);
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
                String[] parts = line.split("\\|", -1);
                if (parts.length < 7)
                {
                    continue;
                }
                Entry entry = new Entry();
                entry.gameId = parts[0];
                entry.name = parts[1];
                entry.authorUsername = parts[2];
                entry.entryClassName = parts[3];
                entry.uploadedAt = Long.parseLong(parts[4]);
                entry.hash = parts[5];
                entry.sizeBytes = Long.parseLong(parts[6]);
                // Field 7 (approved) is new - older index lines without it default to
                // already-approved, so games uploaded before this review queue existed
                // don't silently vanish from everyone's catalog.
                entry.approved = parts.length < 8 || "1".equals(parts[7]);
                entries.add(entry);
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not load custom game index: " + e.getMessage());
        }
        finally
        {
            if (reader != null) { try { reader.close(); } catch (IOException ignored) { } }
        }
    }
}
