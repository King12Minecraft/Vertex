package economy;

import java.util.prefs.Preferences;

/**
 * FpsCounterSetting
 * ------------------
 * Whether to draw a live FPS readout in the corner of every real-time
 * game - lets someone actually see whether Performance Mode (or their
 * hardware) is giving them a smooth 60/30fps or something worse,
 * rather than just guessing from how it feels. Same
 * Preferences-backed, per-computer pattern as PerformanceMode - not
 * account data, doesn't sync anywhere.
 */
public class FpsCounterSetting
{
    private static final Preferences PREFS = Preferences.userNodeForPackage(FpsCounterSetting.class);
    private static final String KEY = "showFpsCounter";

    private FpsCounterSetting()
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
