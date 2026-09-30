package pages;

/**
 * HomeQuickPlaySection - PLACEHOLDER (design pending; see HomeSectionPanel).
 *
 * Meant to become: one prominent "Quick play" action that drops you into a match.
 *
 * Data already available:
 *   - pages/QuickPlayDropdown (the "Play" control in the top bar) already lists quick-play choices and launches
 *     them - reuse its logic rather than inventing a second picker;
 *   - which online games have players waiting: GameInfo.getQueueCount() (games.GameManager.getCachedGames(), kept
 *     fresh by the GAME_LIST refresh) - a good way to suggest "3 players waiting in Connect Four";
 *   - games.GameLauncher.launch(anchor, gameInfo) to start one.
 * Still to build: a rule for what "quick play" picks (the game with players waiting? your most played?) - a design
 * decision, not a technical one.
 */
public class HomeQuickPlaySection extends HomeSectionPanel
{
    public HomeQuickPlaySection()
    {
        super("QUICK PLAY", "Jump straight into a match with one click - coming soon.");
    }
}
