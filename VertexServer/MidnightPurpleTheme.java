import java.awt.Color;

/** Dark purple theme option, lavender accent. */
public class MidnightPurpleTheme implements Theme
{
    public String getName() { return "Midnight Purple"; }

    public Color bgApp()        { return new Color(23, 13, 43); }
    public Color bgSidebar()    { return new Color(29, 18, 53); }
    public Color bgTopbar()     { return new Color(29, 18, 53); }
    public Color bgPanel()      { return new Color(39, 26, 72); }
    public Color bgPanelHover() { return new Color(51, 36, 92); }

    public Color accent()      { return new Color(167, 139, 250); }
    public Color accentHover() { return new Color(196, 174, 255); }
    public Color accentDim()   { return new Color(167, 139, 250, 45); }
    public Color accentGradientStart() { return new Color(167, 139, 250); }
    public Color accentGradientEnd()   { return new Color(139, 92, 246); }
    public Color success()     { return new Color(74, 222, 128); }

    public Color textPrimary()   { return new Color(241, 235, 255); }
    public Color textSecondary() { return new Color(175, 160, 201); }
    public Color textMuted()     { return new Color(115, 100, 140); }

    public Color border() { return new Color(74, 59, 112); }
}
