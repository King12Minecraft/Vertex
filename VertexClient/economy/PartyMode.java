package economy;

import java.util.prefs.Preferences;

/**
 * PartyMode
 * ---------
 * A single opt-in switch for purely cosmetic fun - a silly cursor
 * trail (CursorTrailOverlay, attached once in MainMenu) and confetti
 * bursts (ConfettiOverlay.burst(...), called from a win moment).
 * Off by default. Never touches matchmaking, scoring, or any real
 * game state - see both overlay classes' own javadoc for the same
 * promise repeated where it actually matters.
 *
 * Persisted locally via Preferences (same pattern as PerformanceMode)
 * - a per-computer display preference, not account data.
 */
public class PartyMode
{
    private static final Preferences PREFS = Preferences.userNodeForPackage(PartyMode.class);
    private static final String KEY = "partyMode";

    private PartyMode()
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
}
