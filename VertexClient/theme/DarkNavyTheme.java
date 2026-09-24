package theme;

import java.awt.Color;

/**
 * The default Vertex theme - "Aurora Glass": a richer near-black base
 * (not pure #000-adjacent) with one neon cyan accent used sparingly
 * for glow rather than flat fill, matching the reskin's softer,
 * layered-glass card language over the earlier flat Opera-GX look.
 */
public class DarkNavyTheme implements Theme
{
    public String getName() { return "Dark Navy"; }

    public Color bgApp()        { return new Color(11, 13, 18); }
    public Color bgSidebar()    { return new Color(14, 16, 22); }
    public Color bgTopbar()     { return new Color(14, 16, 22); }
    public Color bgPanel()      { return new Color(18, 21, 28); }
    public Color bgPanelHover() { return new Color(23, 26, 34); }

    public Color accent()      { return new Color(34, 227, 238); }
    public Color accentHover() { return new Color(110, 240, 246); }
    public Color accentDim()   { return new Color(34, 227, 238, 40); }
    public Color accentGradientStart() { return new Color(34, 227, 238); }
    public Color accentGradientEnd()   { return new Color(16, 180, 200); }
    public Color success()     { return new Color(74, 222, 128); }

    public Color textPrimary()   { return new Color(245, 247, 250); }
    public Color textSecondary() { return new Color(168, 176, 188); }
    public Color textMuted()     { return new Color(108, 115, 128); }

    public Color border() { return new Color(30, 34, 42); }
}

