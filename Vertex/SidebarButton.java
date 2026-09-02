import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * SidebarButton
 * -------------
 * A single navigation entry in the left sidebar. Always shows its icon
 * (drawn via NavIcons, fixed in a left-hand zone); the text label only
 * takes up space once the sidebar is expanded (hover) - see
 * setExpanded(). Handles its own hover/selected visual states, fully
 * theme-aware. Sidebar listens for clicks and switches pages.
 */
public class SidebarButton extends RoundedPanel
{
    private static final int ICON_ZONE = 40;
    private static final int ICON_SIZE = 20;

    private final JLabel label;
    private final String pageKey;
    private boolean selected = false;
    private boolean hovering = false;
    private boolean showBadge = false;

    /** Text-only button, no icon zone reserved - used by contexts like ChatPanel's own channel list. */
    public SidebarButton(String text)
    {
        this(text, null);
    }

    /** Icon + expandable-label button - used by the main navigation Sidebar. */
    public SidebarButton(String text, String pageKey)
    {
        super(ThemeColor.BG_SIDEBAR, UITheme.RADIUS_BUTTON);
        this.pageKey = pageKey;
        setLayout(new BorderLayout());
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        label = new JLabel(text);
        label.setFont(UITheme.FONT_NAV);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        label.setBorder(BorderFactory.createEmptyBorder(10, pageKey != null ? ICON_ZONE : 16, 10, 16));
        label.setVisible(pageKey == null);
        add(label, BorderLayout.CENTER);

        addMouseListener(new MouseAdapter()
        {
            public void mouseEntered(MouseEvent e) { hovering = true; updateVisual(); }
            public void mouseExited(MouseEvent e)  { hovering = false; updateVisual(); }
        });

        ThemeManager.addListener(new Runnable()
        {
            public void run() { updateVisual(); }
        });

        updateVisual();
    }

    public void setSelected(boolean selected)
    {
        this.selected = selected;
        updateVisual();
    }

    public boolean isSelected()
    {
        return selected;
    }

    /** Called by Sidebar as it expands/collapses - shows or hides the text label. */
    public void setExpanded(boolean expanded)
    {
        label.setVisible(expanded);
        revalidate();
    }

    /** A small colored dot in the corner of the icon - used for "something new here" indicators (e.g. a friend just came online) without needing a numeric count. */
    public void setShowBadge(boolean show)
    {
        this.showBadge = show;
        repaint();
    }

    private Color currentIconColor()
    {
        if (selected)  return ThemeManager.getColor(ThemeColor.ACCENT);
        if (hovering)  return ThemeManager.getColor(ThemeColor.TEXT_PRIMARY);
        return ThemeManager.getColor(ThemeColor.TEXT_SECONDARY);
    }

    private void updateVisual()
    {
        if (selected)
        {
            setBackgroundRole(ThemeColor.ACCENT_DIM);
            label.setForeground(ThemeManager.getColor(ThemeColor.ACCENT));
            label.setFont(UITheme.FONT_NAV_BOLD);
        }
        else if (hovering)
        {
            setBackgroundRole(ThemeColor.BG_PANEL_HOVER);
            label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
            label.setFont(UITheme.FONT_NAV);
        }
        else
        {
            setBackgroundRole(ThemeColor.BG_SIDEBAR);
            label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
            label.setFont(UITheme.FONT_NAV);
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        if (pageKey != null)
        {
            int iconY = (getHeight() - ICON_SIZE) / 2;
            int iconX = (ICON_ZONE - ICON_SIZE) / 2;
            Graphics2D iconG2 = (Graphics2D) g2.create();
            iconG2.translate(iconX, iconY);
            NavIcons.draw(iconG2, pageKey, ICON_SIZE, currentIconColor());
            iconG2.dispose();

            if (showBadge)
            {
                int dotSize = 8;
                int dotX = iconX + ICON_SIZE - dotSize + 2;
                int dotY = iconY - 2;
                g2.setColor(ThemeManager.getColor(ThemeColor.SUCCESS));
                g2.fillOval(dotX, dotY, dotSize, dotSize);
            }
        }

        if (selected)
        {
            Color start = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START);
            Color end = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_END);
            int barHeight = Math.max(getHeight() - 12, 1);
            LinearGradientPaint gradient = new LinearGradientPaint(
                0, 6, 0, 6 + barHeight, new float[] {0f, 1f}, new Color[] {start, end});
            g2.setPaint(gradient);
            g2.fillRoundRect(0, 6, 4, barHeight, 4, 4);
        }

        g2.dispose();
    }
}
