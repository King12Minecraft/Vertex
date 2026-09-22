package ui;
import theme.ThemeColor;
import theme.ThemeManager;

import javax.swing.JLabel;
import java.awt.Font;

/**
 * ThemedLabel
 * -----------
 * A JLabel whose foreground color comes from the active Theme (looked
 * up by role) and stays live - repaints in the new color automatically
 * when the theme changes, the same live-update guarantee RoundedPanel/
 * ThemedButton/ThemedTextField already give their own colors. A plain
 * JLabel with setForeground(ThemeManager.getColor(role)) only applies
 * that role once, at construction time, and has no way to pick up a
 * later theme switch - which is why so much body text across the app
 * used to stay stuck in whatever theme was active at login.
 */
public class ThemedLabel extends JLabel
{
    private ThemeColor role;

    public ThemedLabel(String text, ThemeColor role)
    {
        super(text);
        this.role = role;
        setForeground(ThemeManager.getColor(role));

        ThemeManager.addListener(new Runnable()
        {
            public void run() { setForeground(ThemeManager.getColor(ThemedLabel.this.role)); }
        });
    }

    public ThemedLabel(String text, Font font, ThemeColor role)
    {
        this(text, role);
        setFont(font);
    }

    /** Changes which theme color role this label paints (e.g. a selected/unselected state) - takes effect immediately, and stays live through any later theme switch too. */
    public void setRole(ThemeColor role)
    {
        this.role = role;
        setForeground(ThemeManager.getColor(role));
    }
}
