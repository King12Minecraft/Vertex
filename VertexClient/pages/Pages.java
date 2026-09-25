package pages;

/**
 * Pages
 * -----
 * Central place for the page/screen keys used by the main CardLayout
 * navigation. Admin/Moderation page keys get added here in Phase 4 once
 * roles exist.
 */
public class Pages
{
    public static final String HOME       = "HOME";
    public static final String GAMES      = "GAMES";
    public static final String ALL_GAMES  = "ALL_GAMES";
    /** The single slot an embedded game (see games.EmbeddedGamePanel) occupies while being played - MainMenu.showGame(...)/returnToGames() swap its one child in and out. */
    public static final String GAME_HOST  = "GAME_HOST";
    public static final String GAME_SUGGESTIONS = "GAME_SUGGESTIONS";
    public static final String QUESTS      = "QUESTS";
    public static final String LEADERBOARDS = "LEADERBOARDS";
    public static final String ACHIEVEMENTS = "ACHIEVEMENTS";
    public static final String TOURNAMENTS = "TOURNAMENTS";
    public static final String FRIENDS    = "FRIENDS";
    public static final String CHAT       = "CHAT";
    public static final String SHOP       = "SHOP";
    public static final String PROFILE    = "PROFILE";
    public static final String SETTINGS   = "SETTINGS";
    public static final String MODERATION = "MODERATION";
    public static final String ADMIN      = "ADMIN";

    private Pages()
    {
        // Static constants holder - never instantiated.
    }
}
