/**
 * CustomGameInfo
 * --------------
 * Client-side parsed view of one line from Message.getCustomGameEntries()
 * ("gameId|name|authorUsername|entryClassName|uploadedAtMillis|hash|sizeBytes"),
 * the same "format a pipe-delimited String on the server, parse it back
 * into an object on the client" shape LeaderboardPanel/TournamentsPanel
 * already use for their own list responses.
 */
public class CustomGameInfo
{
    private final String gameId;
    private final String name;
    private final String authorUsername;
    private final String entryClassName;
    private final long uploadedAt;
    private final String hash;
    private final long sizeBytes;

    private CustomGameInfo(String gameId, String name, String authorUsername, String entryClassName,
                            long uploadedAt, String hash, long sizeBytes)
    {
        this.gameId = gameId;
        this.name = name;
        this.authorUsername = authorUsername;
        this.entryClassName = entryClassName;
        this.uploadedAt = uploadedAt;
        this.hash = hash;
        this.sizeBytes = sizeBytes;
    }

    /** Null if the line is malformed (defensive - a server/client protocol mismatch shouldn't crash the whole list). */
    public static CustomGameInfo parse(String line)
    {
        if (line == null)
        {
            return null;
        }
        String[] parts = line.split("\\|", -1);
        if (parts.length < 7)
        {
            return null;
        }
        try
        {
            return new CustomGameInfo(parts[0], parts[1], parts[2], parts[3],
                Long.parseLong(parts[4]), parts[5], Long.parseLong(parts[6]));
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    public String getGameId() { return gameId; }
    public String getName() { return name; }
    public String getAuthorUsername() { return authorUsername; }
    public String getEntryClassName() { return entryClassName; }
    public long getUploadedAt() { return uploadedAt; }
    public String getHash() { return hash; }
    public long getSizeBytes() { return sizeBytes; }
}
