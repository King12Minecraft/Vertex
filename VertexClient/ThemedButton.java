import javax.swing.JButton;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;

/**
 * ThemedButton
 * ------------
 * A theme-aware button. "Primary" buttons get the full reskin
 * treatment: gradient fill (theme's accentGradientStart/End), angular
 * chamfered corners, and an animated glow on hover. "Secondary"
 * buttons stay simple (flat, rounded, outlined) - the loud gradient
 * treatment is reserved for primary actions so it doesn't visually
 * compete with itself when several buttons share a screen.
 */
public class ThemedButton extends JButton
{
    private boolean primary;
    private boolean hover = false;
    private boolean pressed = false;
    private final HoverGlowAnimator glow = new HoverGlowAnimator(this);

    public ThemedButton(String text, boolean primary)
    {
        super(text);
        this.primary = primary;
        setFont(UITheme.FONT_NAV_BOLD);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setHorizontalAlignment(SwingConstants.CENTER);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter()
        {
            public void mouseEntered(MouseEvent e) { hover = true; glow.animateIn(); repaint(); }
            public void mouseExited(MouseEvent e)  { hover = false; glow.animateOut(); repaint(); }
            public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
            public void mouseReleased(MouseEvent e){ pressed = false; repaint(); }
        });

        ThemeManager.addListener(new Runnable()
        {
            public void run() { repaint(); }
        });
    }

    /** Toggles between primary (gradient/filled) and secondary (flat/outlined) styling after construction - used for tab/filter-chip active states. */
    public void setPrimary(boolean primary)
    {
        this.primary = primary;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        if (primary)
        {
            paintPrimary(g2);
        }
        else
        {
            paintSecondary(g2);
        }

        g2.dispose();
        super.paintComponent(g);
    }

    private void paintPrimary(Graphics2D g2)
    {
        int w = getWidth();
        int h = getHeight();
        int cut = Math.min(10, h / 3);

        float glowIntensity = glow.getIntensity();
        if (glowIntensity > 0f)
        {
            Color glowColor = ThemeManager.getColor(ThemeColor.ACCENT);
            int alpha = (int) (90 * glowIntensity);
            g2.setColor(new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), alpha));
            GeneralPath glowShape = chamferedRect(w + 8, h + 8, cut + 4);
            g2.translate(-4, -4);
            g2.fill(glowShape);
            g2.translate(4, 4);
        }

        GeneralPath shape = chamferedRect(w, h, cut);

        Color start;
        Color end;
        if (!isEnabled())
        {
            Color dim = ThemeManager.getColor(ThemeColor.ACCENT_DIM);
            start = dim;
            end = dim;
        }
        else
        {
            start = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START);
            end = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_END);
            if (pressed)
            {
                start = start.darker();
                end = end.darker();
            }
        }

        LinearGradientPaint paint = new LinearGradientPaint(
            0, 0, Math.max(w, 1), Math.max(h, 1), new float[] {0f, 1f}, new Color[] {start, end});
        g2.setPaint(paint);
        g2.fill(shape);

        setForeground(ThemeManager.getColor(ThemeColor.BG_APP));
    }

    private void paintSecondary(Graphics2D g2)
    {
        int w = getWidth();
        int h = getHeight();

        Color base;
        if (!isEnabled())
        {
            base = ThemeManager.getColor(ThemeColor.BG_PANEL);
        }
        else
        {
            base = (hover || pressed)
                ? ThemeManager.getColor(ThemeColor.BG_PANEL_HOVER)
                : ThemeManager.getColor(ThemeColor.BG_SIDEBAR);
        }

        g2.setColor(base);
        g2.fillRoundRect(0, 0, w, h, UITheme.RADIUS_BUTTON, UITheme.RADIUS_BUTTON);

        g2.setColor(ThemeManager.getColor(ThemeColor.BORDER));
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(0, 0, w - 1, h - 1, UITheme.RADIUS_BUTTON, UITheme.RADIUS_BUTTON);

        setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
    }

    /** An octagon-like chamfered rectangle - cut corners instead of rounded ones. */
    private GeneralPath chamferedRect(int w, int h, int cut)
    {
        GeneralPath path = new GeneralPath();
        path.moveTo(cut, 0);
        path.lineTo(w - cut, 0);
        path.lineTo(w, cut);
        path.lineTo(w, h - cut);
        path.lineTo(w - cut, h);
        path.lineTo(cut, h);
        path.lineTo(0, h - cut);
        path.lineTo(0, cut);
        path.closePath();
        return path;
    }
}
