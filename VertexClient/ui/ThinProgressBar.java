package ui;

import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * ThinProgressBar
 * ---------------
 * A slim rounded progress bar: a track in the theme's border colour and a fill in a caller-chosen colour (default
 * the accent). Used wherever the app shows progress (quests, stats) so bars look the same on every page and
 * follow the theme - the stock JProgressBar draws a bulky outlined box that ignores it.
 */
public class ThinProgressBar extends JComponent
{
    private int value;
    private int max;
    private Color fill;

    public ThinProgressBar(int value, int max)
    {
        this.value = value;
        this.max = Math.max(max, 1);
        setOpaque(false);
        setPreferredSize(new Dimension(100, 8));
        setMinimumSize(new Dimension(20, 8));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
    }

    public ThinProgressBar fill(Color color)
    {
        this.fill = color;
        repaint();
        return this;
    }

    public ThinProgressBar height(int pixels)
    {
        setPreferredSize(new Dimension(100, pixels));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, pixels));
        return this;
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);
        int w = getWidth();
        int h = getHeight();
        g2.setColor(ThemeManager.getColor(ThemeColor.BORDER));
        g2.fillRoundRect(0, 0, w, h, h, h);
        int filled = (int) Math.round(w * Math.min(Math.max(value, 0), max) / (double) max);
        if (filled > 0)
        {
            g2.setColor(fill != null ? fill : ThemeManager.getColor(ThemeColor.ACCENT));
            g2.fillRoundRect(0, 0, Math.max(filled, h), h, h, h);
        }
        g2.dispose();
    }
}
