import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

/**
 * SplashScreen
 * ------------
 * A brief animated loading screen shown at app startup, before the
 * login window appears - undecorated, centered, with the logo, an
 * animated fill progress bar, and a soft glow. Uses fixed brand colors
 * rather than the live theme (same reasoning as GameLogo.renderIcon):
 * this shows before any theme is loaded, and shouldn't flicker or
 * depend on load order.
 *
 * Layout uses fixed pixel offsets rather than percentages of the
 * window size - the earlier version positioned the logo and wordmark
 * with percentage math that left them almost touching (logo bottom
 * and wordmark baseline landed within a few pixels of each other).
 * Since the window size here is always a fixed constant anyway,
 * percentages bought nothing but fragility.
 *
 * Used identically by both entry points - Vertex.main() (client-only)
 * and ServerMain.main() (combined server+client) - via
 * showThenRun(Runnable), which displays the splash for a fixed
 * duration and then runs the given callback (normally: open
 * AuthWindow).
 */
public class SplashScreen extends JWindow
{
    private static final int DURATION_MS = 1400;
    private static final int WIDTH = 420;
    private static final int HEIGHT = 420;

    private static final int LOGO_SIZE = 108;
    private static final int LOGO_TOP = 56;
    private static final int WORDMARK_BASELINE = 208;
    private static final int TAGLINE_BASELINE = 232;
    private static final int BAR_TOP = 276;
    private static final int BAR_WIDTH = 240;
    private static final int BAR_HEIGHT = 4;
    private static final int STATUS_BASELINE = BAR_TOP + 28;

    private float progress = 0f;

    private SplashScreen()
    {
        setSize(WIDTH, HEIGHT);
        setLocationRelativeTo(null);
        getContentPane().add(new SplashPanel());
    }

    /** Shows the splash for a fixed duration, then disposes it and runs onComplete. */
    public static void showThenRun(final Runnable onComplete)
    {
        final SplashScreen splash = new SplashScreen();
        splash.setVisible(true);

        final long start = System.currentTimeMillis();
        final Timer timer = new Timer(16, null);
        timer.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                long elapsed = System.currentTimeMillis() - start;
                float t = Math.min(1f, elapsed / (float) DURATION_MS);
                splash.progress = t;
                splash.repaint();

                if (t >= 1f)
                {
                    timer.stop();
                    splash.dispose();
                    onComplete.run();
                }
            }
        });
        timer.start();
    }

    private class SplashPanel extends JPanel
    {
        SplashPanel()
        {
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            int w = getWidth();
            int h = getHeight();

            Color bgTop = new Color(8, 9, 12);
            Color bgBottom = new Color(14, 16, 20);
            g2.setPaint(new GradientPaint(0, 0, bgTop, 0, h, bgBottom));
            g2.fillRect(0, 0, w, h);

            Color accent = new Color(34, 227, 238);
            RadialGradientPaint glow = new RadialGradientPaint(
                w / 2f, LOGO_TOP + LOGO_SIZE / 2f, Math.max(w, h) * 0.45f,
                new float[] {0f, 1f},
                new Color[] {
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 75),
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0)
                });
            g2.setPaint(glow);
            g2.fillRect(0, 0, w, h);

            BufferedImage logo = GameLogo.renderIcon(LOGO_SIZE);
            g2.drawImage(logo, (w - LOGO_SIZE) / 2, LOGO_TOP, null);

            g2.setColor(Color.WHITE);
            g2.setFont(UITheme.FONT_HEADING.deriveFont(Font.BOLD, 26f));
            FontMetrics fmWord = g2.getFontMetrics();
            String word = "VERTEX";
            g2.drawString(word, (w - fmWord.stringWidth(word)) / 2, WORDMARK_BASELINE);

            g2.setColor(new Color(255, 255, 255, 120));
            g2.setFont(UITheme.FONT_SMALL.deriveFont(11f));
            FontMetrics fmTag = g2.getFontMetrics();
            String tagline = "G A M E   L A U N C H E R";
            g2.drawString(tagline, (w - fmTag.stringWidth(tagline)) / 2, TAGLINE_BASELINE);

            int barX = (w - BAR_WIDTH) / 2;

            g2.setColor(new Color(255, 255, 255, 30));
            g2.fillRoundRect(barX, BAR_TOP, BAR_WIDTH, BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT);
            g2.setColor(accent);
            g2.fillRoundRect(barX, BAR_TOP, (int) (BAR_WIDTH * progress), BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT);

            g2.setColor(new Color(255, 255, 255, 130));
            g2.setFont(UITheme.FONT_SMALL);
            FontMetrics fmStatus = g2.getFontMetrics();
            String status = "Loading...";
            g2.drawString(status, (w - fmStatus.stringWidth(status)) / 2, STATUS_BASELINE);

            g2.setColor(new Color(255, 255, 255, 20));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(0, 0, w - 1, h - 1);

            g2.dispose();
        }
    }
}
