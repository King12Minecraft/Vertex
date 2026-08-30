import java.awt.Color;

/** Dark teal "ocean" theme option. */
public class OceanTheme implements Theme
{
    public String getName() { return "Ocean Teal"; }

    public Color bgApp()        { return new Color(8, 24, 32); }
    public Color bgSidebar()    { return new Color(11, 30, 40); }
    public Color bgTopbar()     { return new Color(11, 30, 40); }
    public Color bgPanel()      { return new Color(15, 41, 53); }
    public Color bgPanelHover() { return new Color(20, 53, 68); }

    public Color accent()      { return new Color(45, 212, 191); }
    public Color accentHover() { return new Color(94, 234, 212); }
    public Color accentDim()   { return new Color(45, 212, 191, 45); }
    public Color accentGradientStart() { return new Color(45, 212, 191); }
    public Color accentGradientEnd()   { return new Color(34, 211, 238); }
    public Color success()     { return new Color(74, 222, 128); }

    public Color textPrimary()   { return new Color(230, 245, 244); }
    public Color textSecondary() { return new Color(140, 178, 175); }
    public Color textMuted()     { return new Color(90, 122, 120); }

    public Color border() { return new Color(30, 66, 74); }
}
