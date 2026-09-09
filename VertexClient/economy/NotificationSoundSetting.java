package economy;

import java.util.prefs.Preferences;

/**
 * NotificationSoundSetting
 * -------------------------
 * Whether a system beep plays for new-message notifications - a
 * plain Toolkit.beep(), not real audio playback, so this doesn't
 * touch the "no JavaFX sound design" boundary the project's own
 * roadmap set (that's about actual sound effects/music for games,
 * not a one-bit "something happened" cue). On by default, same
 * Preferences-backed per-computer pattern as PerformanceMode/
 * FpsCounterSetting.
 */
public class NotificationSoundSetting
{
    private static final Preferences PREFS = Preferences.userNodeForPackage(NotificationSoundSetting.class);
    private static final String KEY = "notificationSound";

    private NotificationSoundSetting()
    {
        // Static utility class - never instantiated.
    }

    public static boolean isEnabled()
    {
        return PREFS.getBoolean(KEY, true);
    }

    public static void setEnabled(boolean enabled)
    {
        PREFS.putBoolean(KEY, enabled);
    }

    /** No-op if the setting is off - callers can call this unconditionally. */
    public static void playIfEnabled()
    {
        if (isEnabled())
        {
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }
}
