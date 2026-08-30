import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;

/**
 * RoundedPanel
 * ------------
 * A JPanel with rounded corners whose background color comes from the
 * active Theme (looked up by role) rather than a fixed Color. Repaints
 * automatically when the theme changes.
 *
 * Pass radius 0 for a plain square-edged themed panel (e.g. a sidebar or
 * top bar spanning the full width/height of its container).
 *
 * Two opt-in reskin extras, used by interactive/content cards:
 *   - glow() exposes a HoverGlowAnimator - wire it to a mouse or focus
 *     listener to get a soft animated accent glow around the panel.
 *   - enableTopAccent() adds a thin gradient bar along the top edge,
 *     an Opera GX-style card accent.
 */
public class RoundedPanel extends JPanel
{
    private ThemeColor backgroundRole;
    private final int radius;
    private HoverGlowAnimator glowAnimator;
    private boolean topAccentEnabled = false;

    public RoundedPanel(ThemeColor backgroundRole, int radius)
    {
        this.backgroundRole = backgroundRole;
        this.radius = radius;
        setOpaque(false);

        ThemeManager.addListener(new Runnable()
        {
            public void run() { repaint(); }
        });
    }

    /** Changes which theme color role this panel paints, e.g. for hover states. */
    public void setBackgroundRole(ThemeColor role)
    {
        this.backgroundRole = role;
        repaint();
    }

    /** Lazily-created glow animator - wire external hover/focus listeners to its animateIn()/animateOut(). */
    public HoverGlowAnimator glow()
    {
        if (glowAnimator == null)
        {
            glowAnimator = new HoverGlowAnimator(this);
        }
        return glowAnimator;
    }

    /** Adds a thin gradient accent bar along the top edge. */
    public void enableTopAccent()
    {
        topAccentEnabled = true;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        if (glowAnimator != null && glowAnimator.getIntensity() > 0f)
        {
            Color accent = ThemeManager.getColor(ThemeColor.ACCENT);
            int alpha = (int) (70 * glowAnimator.getIntensity());
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), alpha));
            g2.fillRoundRect(-3, -3, getWidth() + 6, getHeight() + 6, radius + 4, radius + 4);
        }

        g2.setColor(ThemeManager.getColor(backgroundRole));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);

        if (topAccentEnabled && getWidth() > 0)
        {
            Color start = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START);
            Color end = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_END);
            LinearGradientPaint gradient = new LinearGradientPaint(
                0, 0, getWidth(), 0, new float[] {0f, 1f}, new Color[] {start, end});
            g2.setPaint(gradient);
            g2.fillRoundRect(0, 0, getWidth(), 4, radius, radius);
        }

        g2.dispose();
        super.paintComponent(g);
    }
}
