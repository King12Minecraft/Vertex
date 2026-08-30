import java.awt.Color;

/**
 * The default Vertex theme - restyled to match the reference: near-
 * black backgrounds, one bold flat neon cyan accent (not a blue
 * gradient), matching the Opera GX-esque games-hub reference.
 */
public class DarkNavyTheme implements Theme
{
    public String getName() { return "Dark Navy"; }

    public Color bgApp()        { return new Color(8, 9, 12); }
    public Color bgSidebar()    { return new Color(11, 12, 16); }
    public Color bgTopbar()     { return new Color(11, 12, 16); }
    public Color bgPanel()      { return new Color(17, 18, 23); }
    public Color bgPanelHover() { return new Color(24, 26, 32); }

    public Color accent()      { return new Color(34, 227, 238); }
    public Color accentHover() { return new Color(110, 240, 246); }
    public Color accentDim()   { return new Color(34, 227, 238, 40); }
    public Color accentGradientStart() { return new Color(34, 227, 238); }
    public Color accentGradientEnd()   { return new Color(16, 180, 200); }
    public Color success()     { return new Color(74, 222, 128); }

    public Color textPrimary()   { return new Color(245, 247, 250); }
    public Color textSecondary() { return new Color(168, 176, 188); }
    public Color textMuted()     { return new Color(108, 115, 128); }

    public Color border() { return new Color(26, 28, 34); }
}

