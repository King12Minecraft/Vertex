import java.awt.Color;

/** Aggressive red/orange theme - the most iconic gamer-brand color pairing. */
public class CrimsonRedTheme implements Theme
{
    public String getName() { return "Crimson Red"; }

    public Color bgApp()        { return new Color(18, 10, 10); }
    public Color bgSidebar()    { return new Color(24, 13, 13); }
    public Color bgTopbar()     { return new Color(24, 13, 13); }
    public Color bgPanel()      { return new Color(32, 17, 17); }
    public Color bgPanelHover() { return new Color(42, 22, 22); }

    public Color accent()      { return new Color(239, 68, 68); }
    public Color accentHover() { return new Color(248, 113, 113); }
    public Color accentDim()   { return new Color(239, 68, 68, 45); }
    public Color accentGradientStart() { return new Color(239, 68, 68); }
    public Color accentGradientEnd()   { return new Color(249, 115, 22); }

    public Color success() { return new Color(74, 222, 128); }

    public Color textPrimary()   { return new Color(253, 242, 242); }
    public Color textSecondary() { return new Color(198, 160, 160); }
    public Color textMuted()     { return new Color(130, 100, 100); }

    public Color border() { return new Color(74, 35, 35); }
}
