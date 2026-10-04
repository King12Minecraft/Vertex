package theme;

import java.awt.Color;

/**
 * "Claude Light" - the calm, warm light look: an off-white page, white cards with thin warm-grey borders, near-black
 * text and one terracotta accent used sparingly. No glow, no gradients (the two gradient roles are the same two
 * close terracotta shades, so anything that still asks for a gradient gets a barely-there one).
 */
public class ClaudeLightTheme implements Theme
{
    public String getName() { return "Claude Light"; }

    public Color bgApp()        { return new Color(250, 249, 245); }
    public Color bgSidebar()    { return new Color(243, 241, 234); }
    public Color bgTopbar()     { return new Color(250, 249, 245); }
    public Color bgPanel()      { return new Color(255, 255, 255); }
    public Color bgPanelHover() { return new Color(245, 243, 236); }

    public Color accent()      { return new Color(193, 95, 60); }
    public Color accentHover() { return new Color(168, 78, 46); }
    public Color accentDim()   { return new Color(193, 95, 60, 28); }
    public Color accentGradientStart() { return new Color(204, 104, 68); }
    public Color accentGradientEnd()   { return new Color(193, 95, 60); }
    public Color success()     { return new Color(74, 129, 64); }

    public Color textPrimary()   { return new Color(20, 20, 19); }
    public Color textSecondary() { return new Color(86, 85, 80); }
    public Color textMuted()     { return new Color(128, 127, 120); }

    public Color border() { return new Color(231, 228, 217); }
}
