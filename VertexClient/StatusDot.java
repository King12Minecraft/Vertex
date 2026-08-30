import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * StatusDot
 * ---------
 * A small filled circle, used wherever a status needs a colored dot
 * (online/offline indicators, status pills). Takes a literal Color
 * rather than a ThemeColor role, since callers pick the specific status
 * color (usually resolved from ThemeManager ahead of time).
 */
public class StatusDot extends JComponent
{
    private Color color;
    private final int size;

    public StatusDot(Color color, int size)
    {
        this.color = color;
        this.size = size;
        setPreferredSize(new Dimension(size, size));
        setMaximumSize(new Dimension(size, size));
        setOpaque(false);
    }

    public void setColor(Color color)
    {
        this.color = color;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);
        g2.setColor(color);
        g2.fillOval(0, (getHeight() - size) / 2, size, size);
        g2.dispose();
    }
}
