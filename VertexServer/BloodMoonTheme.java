import java.awt.Color;

/** Darker, more sinister deep-red theme. */
public class BloodMoonTheme implements Theme
{
    public String getName() { return "Blood Moon"; }

    public Color bgApp()        { return new Color(10, 5, 6); }
    public Color bgSidebar()    { return new Color(15, 8, 9); }
    public Color bgTopbar()     { return new Color(15, 8, 9); }
    public Color bgPanel()      { return new Color(22, 11, 13); }
    public Color bgPanelHover() { return new Color(30, 15, 17); }

    public Color accent()      { return new Color(220, 38, 38); }
    public Color accentHover() { return new Color(239, 68, 68); }
    public Color accentDim()   { return new Color(220, 38, 38, 45); }
    public Color accentGradientStart() { return new Color(127, 29, 29); }
    public Color accentGradientEnd()   { return new Color(220, 38, 38); }

    public Color success() { return new Color(74, 222, 128); }

    public Color textPrimary()   { return new Color(248, 233, 233); }
    public Color textSecondary() { return new Color(175, 130, 130); }
    public Color textMuted()     { return new Color(110, 78, 78); }

    public Color border() { return new Color(58, 25, 27); }
}
