import java.awt.Color;

/** Neon green, hacker/matrix vibe. */
public class ToxicGreenTheme implements Theme
{
    public String getName() { return "Toxic Green"; }

    public Color bgApp()        { return new Color(8, 15, 10); }
    public Color bgSidebar()    { return new Color(11, 20, 14); }
    public Color bgTopbar()     { return new Color(11, 20, 14); }
    public Color bgPanel()      { return new Color(15, 28, 18); }
    public Color bgPanelHover() { return new Color(20, 38, 24); }

    public Color accent()      { return new Color(74, 222, 128); }
    public Color accentHover() { return new Color(110, 231, 155); }
    public Color accentDim()   { return new Color(74, 222, 128, 45); }
    public Color accentGradientStart() { return new Color(163, 230, 53); }
    public Color accentGradientEnd()   { return new Color(34, 197, 94); }

    public Color success() { return new Color(74, 222, 128); }

    public Color textPrimary()   { return new Color(232, 250, 236); }
    public Color textSecondary() { return new Color(150, 190, 160); }
    public Color textMuted()     { return new Color(95, 130, 105); }

    public Color border() { return new Color(35, 66, 42); }
}
