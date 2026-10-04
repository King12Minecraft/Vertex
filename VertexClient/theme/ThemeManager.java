package theme;

import java.awt.Color;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * ThemeManager
 * ------------
 * Static holder for the currently active Theme, and the registry of
 * every available theme (for the picker in Settings). Components ask
 * ThemeManager.getColor(ROLE) instead of hardcoding colors, and
 * register a listener (typically just "repaint myself") so calling
 * ThemeManager.setTheme(...) instantly updates the whole app.
 *
 * Listeners are held as WeakReferences rather than plain strong
 * references. Every game window/panel registers one of these (usually
 * "repaint myself" as an anonymous Runnable, which implicitly holds a
 * strong reference to its enclosing component), but almost none of
 * them ever called removeListener when closed - since this list was a
 * permanent static holder, that meant every game window a person ever
 * opened during a session stayed reachable forever afterward, unable
 * to be garbage collected, even after being disposed. Over a long
 * session of opening/closing many games, that's a real, accumulating
 * memory leak - exactly the kind of thing that causes an app to get
 * gradually laggier the longer it's been running. Using WeakReference
 * here means a listener whose owning component has otherwise become
 * unreachable is dropped automatically the next time the list is
 * walked, without requiring every caller to remember explicit cleanup.
 * Explicit removeListener() calls still work too, for callers that do
 * clean up - this doesn't replace that, it's a safety net underneath it.
 */
public class ThemeManager
{
    private static final java.util.prefs.Preferences PREFS = java.util.prefs.Preferences.userNodeForPackage(ThemeManager.class);
    /** Saved choice: a theme name, or SYSTEM (the default) = Claude Light or Claude Dark following the operating system. */
    public static final String SYSTEM = "SYSTEM";

    private static Theme currentTheme;
    private static final List<WeakReference<Runnable>> listeners = new ArrayList<WeakReference<Runnable>>();

    private static final List<Theme> AVAILABLE_THEMES = new ArrayList<Theme>();
    static
    {
        AVAILABLE_THEMES.add(new ClaudeLightTheme());
        AVAILABLE_THEMES.add(new ClaudeDarkTheme());
        AVAILABLE_THEMES.add(new DarkNavyTheme());
        AVAILABLE_THEMES.add(new MidnightPurpleTheme());
        AVAILABLE_THEMES.add(new OceanTheme());
        AVAILABLE_THEMES.add(new CrimsonRedTheme());
        AVAILABLE_THEMES.add(new ToxicGreenTheme());
        AVAILABLE_THEMES.add(new SunsetOrangeTheme());
        AVAILABLE_THEMES.add(new CyberpunkPinkTheme());
        AVAILABLE_THEMES.add(new IceBlueTheme());
        AVAILABLE_THEMES.add(new BloodMoonTheme());
        AVAILABLE_THEMES.add(new GoldRushTheme());
        AVAILABLE_THEMES.add(new GlitchTheme());
    
        currentTheme = resolve(PREFS.get("theme", SYSTEM));
    }

    /** The theme a saved choice stands for; SYSTEM (or an unknown name) follows the operating system's light/dark setting. */
    private static Theme resolve(String saved)
    {
        if (!SYSTEM.equals(saved))
        {
            for (Theme t : AVAILABLE_THEMES)
            {
                if (t.getName().equals(saved)) return t;
            }
        }
        return OsAppearance.isDark() ? new ClaudeDarkTheme() : new ClaudeLightTheme();
    }

    /** True while the user has not picked a specific theme (the app follows the operating system). */
    public static boolean isFollowingSystem()
    {
        return SYSTEM.equals(PREFS.get("theme", SYSTEM));
    }

    /** Back to following the operating system's light/dark setting. */
    public static void useSystem()
    {
        PREFS.put("theme", SYSTEM);
        applyTheme(resolve(SYSTEM));
    }

    private ThemeManager()
    {
        // Static utility class - never instantiated.
    }

    public static List<Theme> getAvailableThemes()
    {
        return AVAILABLE_THEMES;
    }

    public static Theme getCurrentTheme()
    {
        return currentTheme;
    }

    /** Switches the active theme and notifies every still-live registered listener, dropping any whose owner has been garbage collected along the way. */
    public static void setTheme(Theme theme)
    {
        PREFS.put("theme", theme.getName());
        applyTheme(theme);
    }

    private static void applyTheme(Theme theme)
    {
        currentTheme = theme;
        Iterator<WeakReference<Runnable>> it = listeners.iterator();
        while (it.hasNext())
        {
            Runnable listener = it.next().get();
            if (listener == null)
            {
                it.remove();
                continue;
            }
            listener.run();
        }
    }

    public static void addListener(Runnable listener)
    {
        listeners.add(new WeakReference<Runnable>(listener));
    }

    /** Still supported for callers that do clean up explicitly - removes the matching listener (and, opportunistically, any already-dead ones found along the way). */
    public static void removeListener(Runnable listener)
    {
        Iterator<WeakReference<Runnable>> it = listeners.iterator();
        while (it.hasNext())
        {
            Runnable existing = it.next().get();
            if (existing == null || existing == listener)
            {
                it.remove();
            }
        }
    }

    public static Color getColor(ThemeColor role)
    {
        Theme t = currentTheme;
        switch (role)
        {
            case BG_APP:         return t.bgApp();
            case BG_SIDEBAR:     return t.bgSidebar();
            case BG_TOPBAR:      return t.bgTopbar();
            case BG_PANEL:       return t.bgPanel();
            case BG_PANEL_HOVER: return t.bgPanelHover();
            case ACCENT:         return t.accent();
            case ACCENT_HOVER:   return t.accentHover();
            case ACCENT_DIM:     return t.accentDim();
            case ACCENT_GRADIENT_START: return t.accentGradientStart();
            case ACCENT_GRADIENT_END:   return t.accentGradientEnd();
            case SUCCESS:        return t.success();
            case TEXT_PRIMARY:   return t.textPrimary();
            case TEXT_SECONDARY: return t.textSecondary();
            case TEXT_MUTED:     return t.textMuted();
            case BORDER:         return t.border();
            default:             return t.textPrimary();
        }
    }

    /** A readable text colour (near-black or white) for content drawn ON the accent colour, whichever theme is active. */
    public static Color onAccent()
    {
        Color a = currentTheme.accent();
        double lum = (0.299 * a.getRed() + 0.587 * a.getGreen() + 0.114 * a.getBlue()) / 255.0;
        return lum > 0.62 ? new Color(20, 20, 19) : Color.WHITE;
    }

    /** A blend of two colours, t = 0 gives a, t = 1 gives b. */
    public static Color mix(Color a, Color b, double t)
    {
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
            (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /** True when the active theme's page background is dark. */
    public static boolean isDarkTheme()
    {
        Color bg = currentTheme.bgApp();
        return (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) < 128;
    }
}
