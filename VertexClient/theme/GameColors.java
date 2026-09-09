package theme;

import java.awt.Color;

/**
 * GameColors
 * ----------
 * A fixed color palette for game rendering (boards, canvases, in-game
 * text/borders), deliberately NOT sourced from ThemeManager. Every
 * game used to pull its board/canvas colors from the active app
 * theme, which meant switching your theme in Settings would also
 * repaint the inside of every game - Snake's board, Tetris's grid,
 * Zombie Survival's arena, etc. - to match, even though none of those
 * are meant to be "themed" the way buttons and panels are. Games now
 * always render with this one consistent look regardless of which of
 * the 10 app themes is active; only the surrounding UI chrome (menus,
 * buttons, dialogs) still follows the user's theme choice.
 *
 * Values match DarkNavyTheme (the app default) exactly, so nothing
 * visually changes for anyone already on that theme - this only
 * changes behavior for people who'd picked a different theme.
 */
public class GameColors
{
    private GameColors()
    {
        // Static constants class - never instantiated.
    }

    public static final Color BG_APP     = new Color(8, 9, 12);
    public static final Color BG_BOARD   = new Color(17, 18, 23);
    public static final Color BG_HOVER   = new Color(24, 26, 32);

    public static final Color ACCENT       = new Color(34, 227, 238);
    public static final Color ACCENT_HOVER = new Color(110, 240, 246);
    public static final Color SUCCESS      = new Color(74, 222, 128);

    public static final Color TEXT_PRIMARY   = new Color(245, 247, 250);
    public static final Color TEXT_SECONDARY = new Color(168, 176, 188);
    public static final Color TEXT_MUTED     = new Color(108, 115, 128);

    public static final Color BORDER = new Color(26, 28, 34);
}
