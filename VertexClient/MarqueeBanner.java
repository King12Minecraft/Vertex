import javax.swing.Timer;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * MarqueeBanner
 * -------------
 * A single line of text that continuously scrolls right-to-left,
 * classic news-ticker style. Used by HomePanel to turn "recent games,
 * leaderboard leaders, messages" into one dynamically-moving feed
 * instead of a static list.
 *
 * setItems() replaces the content and restarts the scroll from the
 * right edge; the caller is responsible for calling it again whenever
 * the underlying data changes (HomePanel does this from its own
 * periodic refresh and from a NotificationCenter listener). The
 * animation Timer itself just keeps nudging an x offset and wrapping
 * once the text has fully scrolled past the left edge - no images, no
 * extra libraries, same "cheap and robust" approach MainMenu's own
 * fade transition uses.
 */
public class MarqueeBanner extends RoundedPanel
{
    private static final int SPEED_PX = 2;
    private static final int TICK_MS = 30;

    private String text = "Welcome to Vertex.";
    private int x = 0;

    public MarqueeBanner()
    {
        super(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        setPreferredSize(new Dimension(200, 44));

        Timer animTimer = new Timer(TICK_MS, new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                x -= SPEED_PX;
                repaint();
            }
        });
        animTimer.start();
    }

    /** Joins the given lines with a bullet separator into one scrolling string; a null/empty list falls back to a friendly default. */
    public void setItems(List<String> items)
    {
        if (items == null || items.isEmpty())
        {
            text = "Welcome to Vertex - jump into a game from the sidebar to get started.";
        }
        else
        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < items.size(); i++)
            {
                if (i > 0)
                {
                    sb.append("      •      ");
                }
                sb.append(items.get(i));
            }
            text = sb.toString();
        }
        x = getWidth();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);
        g2.setFont(UITheme.FONT_BODY);
        g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));

        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int baseline = (getHeight() + fm.getAscent()) / 2 - 2;
        g2.drawString(text, x, baseline);

        if (x < -textWidth)
        {
            x = getWidth();
        }

        g2.dispose();
    }
}
