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
    private static final String FONT_FAMILY = "Segoe UI";

    public static final Font FONT_LOGO     = new Font(FONT_FAMILY, Font.BOLD, 20);
    public static final Font FONT_HEADING  = new Font(FONT_FAMILY, Font.BOLD, 24);
    public static final Font FONT_SUBHEAD  = new Font(FONT_FAMILY, Font.PLAIN, 14);
    public static final Font FONT_NAV      = new Font(FONT_FAMILY, Font.PLAIN, 15);
    public static final Font FONT_NAV_BOLD = new Font(FONT_FAMILY, Font.BOLD, 15);
    public static final Font FONT_BODY     = new Font(FONT_FAMILY, Font.PLAIN, 13);
    public static final Font FONT_SMALL    = new Font(FONT_FAMILY, Font.PLAIN, 12);

    public static final int RADIUS_PANEL  = 14;
    public static final int RADIUS_BUTTON = 10;
    public static final int SIDEBAR_WIDTH = 220;
    public static final int TOPBAR_HEIGHT = 64;

    private UITheme()
    {
        // Static utility class - never instantiated.
    }

    /** Enables smooth edges for any custom-painted component. */
    public static void applyAntialiasing(Graphics2D g2)
    {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }
}
