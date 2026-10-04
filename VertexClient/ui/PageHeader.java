package ui;
import theme.ThemeManager;
import theme.ThemeColor;
import theme.UITheme;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;

/**
 * PageHeader
 * ----------
 * A consistent page-title header used across the app's pages, with an
 * accent-gradient underline matching the same chrome treatment already
 * on the TopBar and Sidebar - part of the launcher-style pass. Supports
 * an optional right-aligned action area (e.g. GamesPanel's Refresh
 * button) and a settable title for pages whose header text changes at
 * runtime (e.g. ChatPanel's current channel name).
 */
public class PageHeader extends JPanel
{
    private final JLabel titleLabel;
    private JLabel subtitleLabel;
    private final JPanel titleBox;

    public PageHeader(String title, String subtitle)
    {
        this(title);
        setSubtitle(subtitle);
    }

    public PageHeader(String title)
    {
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(28, 0, 22, 0));

        titleLabel = new JLabel(TextCase.sentence(title));
        titleLabel.setFont(UITheme.FONT_HEADING);
        titleLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new javax.swing.BoxLayout(titleBox, javax.swing.BoxLayout.Y_AXIS));
        titleBox.add(titleLabel);
        add(titleBox, BorderLayout.WEST);

        ThemeManager.addListener(new Runnable()
        {
            public void run()
            {
                titleLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
                if (subtitleLabel != null)
                {
                    subtitleLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
                }
                repaint();
            }
        });
    }

    public void setTitle(String title)
    {
        titleLabel.setText(TextCase.sentence(title));
    }

    /** A muted one-line description under the title; null or empty removes it. */
    public void setSubtitle(String text)
    {
        if (subtitleLabel != null)
        {
            titleBox.remove(subtitleLabel);
            subtitleLabel = null;
        }
        if (text != null && !text.isEmpty())
        {
            subtitleLabel = new JLabel(text);
            subtitleLabel.setFont(UITheme.FONT_SUBHEAD);
            subtitleLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            subtitleLabel.setBorder(new EmptyBorder(6, 0, 0, 0));
            titleBox.add(subtitleLabel);
        }
        titleBox.revalidate();
        repaint();
    }

    /** Adds a right-aligned action area (e.g. a Refresh button) alongside the title. */
    public void setRightComponent(Component component)
    {
        add(component, BorderLayout.EAST);
    }
}
