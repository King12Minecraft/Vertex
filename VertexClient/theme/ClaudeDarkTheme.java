package theme;

import java.awt.Color;

/**
 * "Claude Dark" - warm charcoal rather than blue-black: a soft dark-brown-grey page, slightly lighter cards with thin
 * borders, off-white text and the same terracotta accent as Claude Light. No glow, no gradients.
 */
public class ClaudeDarkTheme implements Theme
{
    public String getName() { return "Claude Dark"; }

    public Color bgApp()        { return new Color(38, 38, 36); }
    public Color bgSidebar()    { return new Color(31, 30, 29); }
    public Color bgTopbar()     { return new Color(38, 38, 36); }
    public Color bgPanel()      { return new Color(48, 48, 46); }
    public Color bgPanelHover() { return new Color(58, 58, 55); }

    public Color accent()      { return new Color(217, 119, 87); }
    public Color accentHover() { return new Color(228, 140, 110); }
    public Color accentDim()   { return new Color(217, 119, 87, 40); }
    public Color accentGradientStart() { return new Color(224, 126, 94); }
    public Color accentGradientEnd()   { return new Color(217, 119, 87); }
    public Color success()     { return new Color(122, 184, 106); }

    public Color textPrimary()   { return new Color(250, 249, 245); }
    public Color textSecondary() { return new Color(194, 192, 182); }
    public Color textMuted()     { return new Color(139, 138, 130); }

    public Color border() { return new Color(70, 69, 65); }
}
