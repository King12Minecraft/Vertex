package ui;

import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * InitialBadge
 * ------------
 * A round badge with a person's first letter, tinted with the theme accent, and an optional presence dot
 * (green = online, muted = offline) in its corner. The stand-in for an avatar in lists (friends, requests) where
 * loading real avatar images for every row isn't worth it; colours come from the theme.
 */
public class InitialBadge extends JComponent
{
    private final String letter;
    private final int size;
    private Boolean online;

    public InitialBadge(String name, int size)
    {
        this.letter = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        this.size = size;
        setOpaque(false);
        Dimension d = new Dimension(size, size);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(d);
    }

    /** Shows a presence dot: true = online, false = offline; never called = no dot. */
    public InitialBadge presence(boolean online)
    {
        this.online = online;
        repaint();
        return this;
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);
        Color accent = ThemeManager.getColor(ThemeColor.ACCENT);
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 45));
        g2.fillOval(0, 0, size, size);
        g2.setColor(accent);
        g2.setFont(UITheme.FONT_NAV_BOLD.deriveFont(size * 0.42f));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(letter, (size - fm.stringWidth(letter)) / 2, (size + fm.getAscent() - fm.getDescent()) / 2);
        if (online != null)
        {
            int dot = Math.max(8, size / 4);
            g2.setColor(ThemeManager.getColor(ThemeColor.BG_PANEL));
            g2.fillOval(size - dot - 1, size - dot - 1, dot + 2, dot + 2);
            g2.setColor(ThemeManager.getColor(online ? ThemeColor.SUCCESS : ThemeColor.TEXT_MUTED));
            g2.fillOval(size - dot, size - dot, dot, dot);
        }
        g2.dispose();
    }
}
