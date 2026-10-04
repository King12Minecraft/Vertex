package ui;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;

import javax.swing.JButton;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * ThemedButton
 * ------------
 * A theme-aware button. "Primary" buttons get the full Aurora Glass
 * treatment: a softly rounded outline in the accent color over a
 * faint accent-tinted fill, with an ambient glow that's always
 * present at rest and brightens further on hover - not a solid
 * gradient fill, so the glow (not a block of color) is what reads as
 * "the button." "Secondary" buttons stay simple (flat, rounded,
 * outlined in the neutral border color) so the accent treatment is
 * reserved for primary actions.
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

    /** Primary: a solid accent fill with text that is readable on it. No glow, no outline - the colour does the work. */
    private void paintPrimary(Graphics2D g2)
    {
        int w = getWidth();
        int h = getHeight();
        int radius = UITheme.RADIUS_BUTTON;

        if (!isEnabled())
        {
            g2.setColor(ThemeManager.getColor(ThemeColor.BG_PANEL_HOVER));
            g2.fillRoundRect(0, 0, w, h, radius, radius);
            setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            return;
        }

        Color fill = ThemeManager.getColor(ThemeColor.ACCENT);
        if (pressed)
        {
            fill = fill.darker();
        }
        else if (hover)
        {
            fill = ThemeManager.getColor(ThemeColor.ACCENT_HOVER);
        }
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, w, h, radius, radius);
        setForeground(ThemeManager.onAccent());
    }

    /** Secondary: the card colour with a hairline border; hover tints it. */
    private void paintSecondary(Graphics2D g2)
    {
        int w = getWidth();
        int h = getHeight();

        Color base = (isEnabled() && (hover || pressed))
            ? ThemeManager.getColor(ThemeColor.BG_PANEL_HOVER)
            : ThemeManager.getColor(ThemeColor.BG_PANEL);

        g2.setColor(base);
        g2.fillRoundRect(0, 0, w, h, UITheme.RADIUS_BUTTON, UITheme.RADIUS_BUTTON);

        g2.setColor(ThemeManager.getColor(ThemeColor.BORDER));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(0, 0, w - 1, h - 1, UITheme.RADIUS_BUTTON, UITheme.RADIUS_BUTTON);

        setForeground(ThemeManager.getColor(isEnabled() ? ThemeColor.TEXT_PRIMARY : ThemeColor.TEXT_MUTED));
    }

    /**
     * Never narrower than its own label (plus padding). Callers often fix a width that was right for one font or one
     * label and then truncate to "Report a Pl..." under another; asking for at least the text width means a
     * button can be wider than the caller guessed but never cuts its label.
     */
    @Override
    public java.awt.Dimension getPreferredSize()
    {
        java.awt.Dimension d = super.getPreferredSize();
        String text = getText();
        if (text == null || text.isEmpty())
        {
            return d;
        }
        java.awt.Insets in = getInsets();
        int needed = getFontMetrics(getFont()).stringWidth(text) + in.left + in.right + 4;
        return needed > d.width ? new java.awt.Dimension(needed, d.height) : d;
    }
}
