package theme;
import economy.PerformanceMode;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * UITheme
 * -------
 * Fonts and spacing constants shared by every screen. Colors are NOT
 * here - typography and layout stay constant no matter which color
 * theme is active (see Theme/ThemeManager for colors).
 */
public class UITheme
{
    /** First installed family from a preference list, else the logical fallback - so each platform gets its native-feeling UI font. */
    private static String pick(String fallback, String... preferred)
    {
        java.util.Set<String> installed = new java.util.HashSet<String>(java.util.Arrays.asList(
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String family : preferred)
        {
            if (installed.contains(family)) return family;
        }
        return fallback;
    }

    private static final String FONT_FAMILY = pick(Font.SANS_SERIF, "Segoe UI", "SF Pro Text", "Helvetica Neue", "Inter", "Noto Sans", "Ubuntu");
    /** Headings use a serif (Claude-style: calm, editorial) where the system has a good one. */
    private static final String SERIF_FAMILY = pick(Font.SERIF, "Georgia", "Iowan Old Style", "Charter", "Cambria", "Palatino Linotype", "Noto Serif");

    public static final Font FONT_LOGO     = new Font(SERIF_FAMILY, Font.PLAIN, 22);
    public static final Font FONT_HEADING  = new Font(SERIF_FAMILY, Font.PLAIN, 26);
    public static final Font FONT_SUBHEAD  = new Font(FONT_FAMILY, Font.PLAIN, 14);
    public static final Font FONT_NAV      = new Font(FONT_FAMILY, Font.PLAIN, 14);
    public static final Font FONT_NAV_BOLD = new Font(FONT_FAMILY, Font.BOLD, 14);
    public static final Font FONT_BODY     = new Font(FONT_FAMILY, Font.PLAIN, 14);
    public static final Font FONT_SMALL    = new Font(FONT_FAMILY, Font.PLAIN, 12);

    public static final int RADIUS_PANEL  = 14;
    public static final int RADIUS_BUTTON = 9;
    public static final int SIDEBAR_WIDTH = 240;
    public static final int TOPBAR_HEIGHT = 60;

    private UITheme()
    {
        // Static utility class - never instantiated.
    }

    /** Enables smooth edges for any custom-painted component. */
    /** Skips antialiasing/quality rendering hints entirely when Performance Mode is on - every panel in the app routes through this one method before drawing, so this single check is the highest-leverage place to cut render cost app-wide for weaker hardware. */
    public static void applyAntialiasing(Graphics2D g2)
    {
        if (PerformanceMode.isEnabled())
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
            return;
        }
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }
}
