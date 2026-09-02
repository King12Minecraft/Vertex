import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * ThemedScrollBarUI
 * ------------------
 * A minimal, theme-aware scrollbar - flat track, rounded pill thumb,
 * no arrow buttons. Replaces the default OS-grey scrollbar, which
 * looks out of place against the dark themed UI. Use
 * ThemedScrollBarUI.apply(scrollPane) right after creating any
 * JScrollPane in the app.
 */
public class ThemedScrollBarUI extends BasicScrollBarUI
{
    /** Applies themed scrollbars to both axes of a scroll pane in one call. */
    public static void apply(JScrollPane scrollPane)
    {
        scrollPane.getVerticalScrollBar().setUI(new ThemedScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new ThemedScrollBarUI());
        scrollPane.getVerticalScrollBar().setPreferredSize(new java.awt.Dimension(10, 0));
        scrollPane.getHorizontalScrollBar().setPreferredSize(new java.awt.Dimension(0, 10));
    }

    @Override
    protected void configureScrollBarColors()
    {
        // Colors are painted directly below instead of via these fields.
    }

    @Override
    protected javax.swing.JButton createDecreaseButton(int orientation)
    {
        return zeroButton();
    }

    @Override
    protected javax.swing.JButton createIncreaseButton(int orientation)
    {
        return zeroButton();
    }

    private javax.swing.JButton zeroButton()
    {
        javax.swing.JButton button = new javax.swing.JButton();
        button.setPreferredSize(new java.awt.Dimension(0, 0));
        button.setMinimumSize(new java.awt.Dimension(0, 0));
        button.setMaximumSize(new java.awt.Dimension(0, 0));
        return button;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);
        g2.setColor(ThemeManager.getColor(ThemeColor.BG_APP));
        g2.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
        g2.dispose();
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds)
    {
        if (thumbBounds.isEmpty() || !c.isEnabled())
        {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        boolean horizontal = ((JScrollBar) c).getOrientation() == JScrollBar.HORIZONTAL;
        int pad = 2;
        int x = thumbBounds.x + (horizontal ? 0 : pad);
        int y = thumbBounds.y + (horizontal ? pad : 0);
        int w = thumbBounds.width - (horizontal ? 0 : pad * 2);
        int h = thumbBounds.height - (horizontal ? pad * 2 : 0);
        int arc = Math.min(w, h);

        Color thumbColor = isThumbRollover()
            ? ThemeManager.getColor(ThemeColor.ACCENT)
            : ThemeManager.getColor(ThemeColor.BORDER);

        g2.setColor(thumbColor);
        g2.fillRoundRect(x, y, Math.max(w, 1), Math.max(h, 1), arc, arc);

        g2.dispose();
    }
}
