import java.awt.Color;

/** Cool icy cyan-to-blue theme. */
public class IceBlueTheme implements Theme
{
    public String getName() { return "Ice Blue"; }

    public Color bgApp()        { return new Color(8, 16, 22); }
    public Color bgSidebar()    { return new Color(11, 22, 30); }
    public Color bgTopbar()     { return new Color(11, 22, 30); }
    public Color bgPanel()      { return new Color(15, 30, 40); }
    public Color bgPanelHover() { return new Color(20, 40, 52); }

    public Color accent()      { return new Color(56, 189, 248); }
    public Color accentHover() { return new Color(125, 211, 252); }
    public Color accentDim()   { return new Color(56, 189, 248, 45); }
    public Color accentGradientStart() { return new Color(34, 211, 238); }
    public Color accentGradientEnd()   { return new Color(59, 130, 246); }

    public Color success() { return new Color(74, 222, 128); }

    public Color textPrimary()   { return new Color(236, 249, 253); }
    public Color textSecondary() { return new Color(155, 195, 210); }
    public Color textMuted()     { return new Color(95, 130, 150); }

    public Color border() { return new Color(30, 65, 82); }
}
