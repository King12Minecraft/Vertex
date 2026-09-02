import java.awt.Color;

/** Warm orange-to-pink gradient theme. */
public class SunsetOrangeTheme implements Theme
{
    public String getName() { return "Sunset Orange"; }

    public Color bgApp()        { return new Color(20, 12, 8); }
    public Color bgSidebar()    { return new Color(27, 16, 11); }
    public Color bgTopbar()     { return new Color(27, 16, 11); }
    public Color bgPanel()      { return new Color(36, 21, 14); }
    public Color bgPanelHover() { return new Color(47, 28, 18); }

    public Color accent()      { return new Color(251, 146, 60); }
    public Color accentHover() { return new Color(253, 186, 116); }
    public Color accentDim()   { return new Color(251, 146, 60, 45); }
    public Color accentGradientStart() { return new Color(251, 146, 60); }
    public Color accentGradientEnd()   { return new Color(236, 72, 153); }

    public Color success() { return new Color(74, 222, 128); }

    public Color textPrimary()   { return new Color(253, 245, 238); }
    public Color textSecondary() { return new Color(201, 172, 150); }
    public Color textMuted()     { return new Color(135, 110, 92); }

    public Color border() { return new Color(75, 45, 28); }
}
