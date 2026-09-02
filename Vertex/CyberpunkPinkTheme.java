import java.awt.Color;

/** Hot pink/violet synthwave theme. */
public class CyberpunkPinkTheme implements Theme
{
    public String getName() { return "Cyberpunk Pink"; }

    public Color bgApp()        { return new Color(14, 8, 20); }
    public Color bgSidebar()    { return new Color(19, 11, 27); }
    public Color bgTopbar()     { return new Color(19, 11, 27); }
    public Color bgPanel()      { return new Color(26, 15, 36); }
    public Color bgPanelHover() { return new Color(34, 20, 47); }

    public Color accent()      { return new Color(236, 72, 153); }
    public Color accentHover() { return new Color(244, 114, 182); }
    public Color accentDim()   { return new Color(236, 72, 153, 45); }
    public Color accentGradientStart() { return new Color(236, 72, 153); }
    public Color accentGradientEnd()   { return new Color(139, 92, 246); }

    public Color success() { return new Color(74, 222, 128); }

    public Color textPrimary()   { return new Color(250, 240, 250); }
    public Color textSecondary() { return new Color(195, 165, 200); }
    public Color textMuted()     { return new Color(125, 100, 135); }

    public Color border() { return new Color(65, 35, 78); }
}
