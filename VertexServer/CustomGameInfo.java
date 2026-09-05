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
    private final boolean approved;

    private CustomGameInfo(String gameId, String name, String authorUsername, String entryClassName,
                            long uploadedAt, String hash, long sizeBytes, boolean approved)
    {
        this.gameId = gameId;
        this.name = name;
        this.authorUsername = authorUsername;
        this.entryClassName = entryClassName;
        this.uploadedAt = uploadedAt;
        this.hash = hash;
        this.sizeBytes = sizeBytes;
        this.approved = approved;
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
            boolean approved = parts.length < 8 || "1".equals(parts[7]);
            return new CustomGameInfo(parts[0], parts[1], parts[2], parts[3],
                Long.parseLong(parts[4]), parts[5], Long.parseLong(parts[6]), approved);
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
    /** True once an admin has reviewed and approved this game - see CUSTOM_GAME_APPROVE_REQUEST. Unapproved entries are only ever sent back to their own uploader (pending) or to an admin (for review); everyone else's CUSTOM_GAME_LIST_RESPONSE simply omits them. */
    public boolean isApproved() { return approved; }
}
