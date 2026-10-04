package ui;
import theme.UITheme;
import theme.ThemeManager;
import theme.ThemeColor;

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
 Flat by design (the Claude-style restart, 2026-10): a solid themed fill and a one-pixel
 * border - no glow, no gradient bar. glow() and enableTopAccent() are kept so existing callers
 * still compile, but they no longer draw anything.
 */
public class RoundedPanel extends JPanel
{
    private ThemeColor backgroundRole;
    private final int radius;
    private HoverGlowAnimator glowAnimator;
    private boolean borderVisible = true;

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

    /** Turn the hairline border off for panels that sit inside other chrome (sidebar items, pills). */
    public void setBorderVisible(boolean visible)
    {
        this.borderVisible = visible;
        repaint();
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

    /** No longer draws anything (the flat redesign has no accent bars); kept so callers still compile. */
    public void enableTopAccent()
    {
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        g2.setColor(ThemeManager.getColor(backgroundRole));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);

        // A hairline border in the theme's border colour is what makes cards read as cards on the flat page.
        if (borderVisible && radius > 0 && getWidth() > 1 && getHeight() > 1)
        {
            g2.setColor(ThemeManager.getColor(ThemeColor.BORDER));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
        }

        g2.dispose();
        super.paintComponent(g);
    }
}
