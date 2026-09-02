import java.awt.Color;

/**
 * Theme
 * -----
 * Defines the full set of colors a Vertex screen needs. Each concrete
 * theme implements this with its own palette. Components should never
 * hardcode colors - they always ask ThemeManager.getCurrentTheme() (or
 * ThemeManager.getColor(role)) for one of these.
 *
 * Fonts and spacing are NOT part of a Theme - those live in UITheme and
 * stay constant across themes.
 */
public interface Theme
{
    String getName();

    Color bgApp();
    Color bgSidebar();
    Color bgTopbar();
    Color bgPanel();
    Color bgPanelHover();

    Color accent();
    Color accentHover();
    Color accentDim();

    /** Two-color gradient used for primary buttons, the logo, and other "hero" accents. */
    Color accentGradientStart();
    Color accentGradientEnd();

    /** Used for "online"/positive status - kept distinct from accent so the two meanings don't blur. */
    Color success();

    Color textPrimary();
    Color textSecondary();
    Color textMuted();

    Color border();
}
