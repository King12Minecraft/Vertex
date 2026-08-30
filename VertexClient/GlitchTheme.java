import java.awt.Color;

/**
 * GlitchTheme
 * -----------
 * Deliberately jarring "digital corruption" palette - near-black
 * background, clashing neon magenta/cyan/acid-green accents instead of
 * one coherent accent gradient, pushing contrast harder than any other
 * theme on purpose. Pairs with GlitchEffectOverlay, which layers brief
 * animated distortion bursts on top of every window when this theme is
 * active - the color palette alone is "loud," but the actual glitch
 * motion effect lives in that separate class since Theme is colors
 * only (see the Theme interface's own javadoc).
 */
public class GlitchTheme implements Theme
{
    public String getName() { return "Glitch"; }

    public Color bgApp()        { return new Color(6, 6, 8); }
    public Color bgSidebar()    { return new Color(10, 8, 12); }
    public Color bgTopbar()     { return new Color(10, 8, 12); }
    public Color bgPanel()      { return new Color(16, 13, 18); }
    public Color bgPanelHover() { return new Color(24, 18, 26); }

    public Color accent()      { return new Color(255, 0, 200); }
    public Color accentHover() { return new Color(0, 255, 210); }
    public Color accentDim()   { return new Color(255, 0, 200, 45); }
    public Color accentGradientStart() { return new Color(255, 0, 200); }
    public Color accentGradientEnd()   { return new Color(0, 255, 210); }

    public Color success() { return new Color(140, 255, 60); }

    public Color textPrimary()   { return new Color(240, 250, 245); }
    public Color textSecondary() { return new Color(200, 190, 210); }
    public Color textMuted()     { return new Color(130, 120, 140); }

    public Color border() { return new Color(60, 20, 55); }
}
