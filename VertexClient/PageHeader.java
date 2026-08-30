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

    public PageHeader(String title)
    {
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(24, 0, 18, 0));

        titleLabel = new JLabel(title);
        titleLabel.setFont(UITheme.FONT_NAV_BOLD);
        titleLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        add(titleLabel, BorderLayout.WEST);

        ThemeManager.addListener(new Runnable()
        {
            public void run()
            {
                titleLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
                repaint();
            }
        });
    }

    public void setTitle(String title)
    {
        titleLabel.setText(title);
    }

    /** Adds a right-aligned action area (e.g. a Refresh button) alongside the title. */
    public void setRightComponent(Component component)
    {
        add(component, BorderLayout.EAST);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        int w = getWidth();
        int titleWidth = Math.min(titleLabel.getPreferredSize().width + 40, w);

        Color accentStart = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START);
        Color accentEnd = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_END);
        LinearGradientPaint gradient = new LinearGradientPaint(
            0, 0, Math.max(titleWidth, 1), 0, new float[] {0f, 1f}, new Color[] {accentStart, accentEnd});
        g2.setPaint(gradient);
        g2.fillRect(0, getHeight() - 3, titleWidth, 2);

        g2.dispose();
    }
}
