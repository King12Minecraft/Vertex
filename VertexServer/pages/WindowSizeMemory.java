package pages;

import java.awt.Dimension;
import java.awt.Point;
import java.util.prefs.Preferences;

/**
 * WindowSizeMemory
 * -----------------
 * Remembers MainMenu's size and position between launches - a real
 * desktop app expectation that was missing (the window always opened
 * at a fixed 1280x800, centered, no matter how you'd last resized or
 * moved it). Same Preferences-backed, per-computer pattern as
 * PerformanceMode/LastGameModeStore - a local display preference, not
 * account data, so it doesn't sync anywhere.
 */
public class WindowSizeMemory
{
    private static final Preferences PREFS = Preferences.userNodeForPackage(WindowSizeMemory.class);

    private WindowSizeMemory()
    {
        // Static utility class - never instantiated.
    }

    /** Null if nothing's been saved yet - callers should fall back to their own default size/position in that case. */
    public static Dimension loadSize()
    {
        int w = PREFS.getInt("windowWidth", -1);
        int h = PREFS.getInt("windowHeight", -1);
        return (w > 0 && h > 0) ? new Dimension(w, h) : null;
    }

    public static Point loadPosition()
    {
        int x = PREFS.getInt("windowX", Integer.MIN_VALUE);
        int y = PREFS.getInt("windowY", Integer.MIN_VALUE);
        return (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE) ? new Point(x, y) : null;
    }

    /** Also true if the window was maximized when closed - restored as maximized next time rather than at its pre-maximize size, matching what most desktop apps do. */
    public static boolean loadMaximized()
    {
        return PREFS.getBoolean("windowMaximized", false);
    }

    public static void save(Dimension size, Point position, boolean maximized)
    {
        if (size != null)
        {
            PREFS.putInt("windowWidth", size.width);
            PREFS.putInt("windowHeight", size.height);
        }
        if (position != null)
        {
            PREFS.putInt("windowX", position.x);
            PREFS.putInt("windowY", position.y);
        }
        PREFS.putBoolean("windowMaximized", maximized);
    }
}
