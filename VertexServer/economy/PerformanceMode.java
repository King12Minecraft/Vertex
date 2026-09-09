package economy;

import java.util.prefs.Preferences;

/**
 * PerformanceMode
 * ---------------
 * A single opt-in switch for lower-end computers, checked in a
 * handful of high-leverage spots rather than scattered everywhere:
 *
 *   - UITheme.applyAntialiasing() - every panel in the app calls this
 *     one shared method before drawing, so this one check cuts render
 *     cost across the entire app (all games, all UI) in a single place.
 *   - GlitchEffectOverlay.attach() - skips the per-window idle timer
 *     entirely (cheap already, but every bit counts on weak hardware).
 *   - Every real-time game's own Timer interval, via
 *     getTickIntervalMs() - halves the frame rate (60fps -> 30fps)
 *     rather than turning games choppy in an uncontrolled way.
 *
 * Persisted locally via Preferences (same pattern as
 * LastGameModeStore/PinnedGamesStore) - this is a per-computer display
 * preference, not account data, so it deliberately doesn't sync
 * between the sync of coins/ELO/achievements or across servers/devices.
 */
public class PerformanceMode
{
    private static final Preferences PREFS = Preferences.userNodeForPackage(PerformanceMode.class);
    private static final String KEY = "performanceMode";

    private PerformanceMode()
    {
        // Static utility class - never instantiated.
    }

    public static boolean isEnabled()
    {
        return PREFS.getBoolean(KEY, false);
    }

    public static void setEnabled(boolean enabled)
    {
        PREFS.putBoolean(KEY, enabled);
    }

    /** Doubles the given tick interval (e.g. 16ms/60fps -> 32ms/~30fps) when Performance Mode is on, otherwise returns it unchanged. */
    public static int getTickIntervalMs(int normalIntervalMs)
    {
        return isEnabled() ? normalIntervalMs * 2 : normalIntervalMs;
    }
}
