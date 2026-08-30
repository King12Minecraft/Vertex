import java.awt.Color;

/** Warm gold/amber luxury theme. */
public class GoldRushTheme implements Theme
{
    public String getName() { return "Gold Rush"; }

    public Color bgApp()        { return new Color(16, 12, 6); }
    public Color bgSidebar()    { return new Color(22, 16, 8); }
    public Color bgTopbar()     { return new Color(22, 16, 8); }
    public Color bgPanel()      { return new Color(30, 22, 11); }
    public Color bgPanelHover() { return new Color(40, 29, 15); }

    public Color accent()      { return new Color(245, 197, 66); }
    public Color accentHover() { return new Color(250, 218, 122); }
    public Color accentDim()   { return new Color(245, 197, 66, 45); }
    public Color accentGradientStart() { return new Color(250, 204, 21); }
    public Color accentGradientEnd()   { return new Color(217, 119, 6); }

    public Color success() { return new Color(74, 222, 128); }

    public Color textPrimary()   { return new Color(253, 249, 237); }
    public Color textSecondary() { return new Color(198, 178, 138); }
    public Color textMuted()     { return new Color(130, 112, 78); }

    public Color border() { return new Color(70, 52, 24); }
}
