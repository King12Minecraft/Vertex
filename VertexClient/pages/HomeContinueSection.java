package pages;

/**
 * HomeContinueSection - PLACEHOLDER (design pending; see HomeSectionPanel).
 *
 * Meant to become: "continue playing" (your last few games, one click to launch) plus pinned games you chose
 * to keep handy.
 *
 * Data already available:
 *   - recent games: GAME_HISTORY_REQUEST -> response.getRecentGameIds() (most recent first, distinct, server
 *     records every play); HomePanel's existing "Recently Played" row already renders these as cards with a Play
 *     button - games.GameLauncher.launch(anchor, gameInfo) is the one launch entry point (it goes through the
 *     game detail page); games.GameManager.findCachedGame(id) turns an id into a GameInfo;
 *   - game art: games.GameCardArt(gameId).
 * Still to build: PINNED games have no storage yet - a small per-computer list in java.util.prefs (like
 * economy.FpsCounterSetting) is enough, plus a pin/unpin control on the game cards and the detail page.
 */
public class HomeContinueSection extends HomeSectionPanel
{
    public HomeContinueSection()
    {
        super("CONTINUE PLAYING", "Your recent and pinned games will show up here.");
    }
}
