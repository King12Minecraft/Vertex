import java.io.Serializable;

/**
 * GameInfo
 * --------
 * SHARED (Common) class - identical copy lives in both VertexClient
 * and VertexServer. Describes one game in the registry: enough for the
 * Games page to render a card. The actual game logic/assets are NOT
 * part of this - that's what Game (client-side interface, Phase 7)
 * and later the download/update system (Phase 13) handle.
 */
public class GameInfo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String gameId;
    private String name;
    private String type;
    private String statusText;
    private boolean online;
    private boolean comingSoon;
    private String version;
    private int queueCount;

    public GameInfo(String gameId, String name, String type, String statusText,
                     boolean online, boolean comingSoon, String version)
    {
        this.gameId = gameId;
        this.name = name;
        this.type = type;
        this.statusText = statusText;
        this.online = online;
        this.comingSoon = comingSoon;
        this.version = version;
        this.queueCount = 0;
    }

    public String getGameId() { return gameId; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getStatusText() { return statusText; }
    public boolean isOnline() { return online; }
    public boolean isComingSoon() { return comingSoon; }
    public String getVersion() { return version; }

    /** Live count of players currently waiting in the matchmaking queue for this game - server-populated, 0 for games with no real matchmaking yet. */
    public int getQueueCount() { return queueCount; }
    public void setQueueCount(int queueCount) { this.queueCount = queueCount; }
}
