import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * AuthHeader
 * ----------
 * Branded header shown once at the top of AuthWindow (above whichever
 * of Login/Create Account is currently showing): the logo with a
 * continuously pulsing glow backdrop, plus the GAMEHUB wordmark. Purely
 * decorative - no login logic lives here.
 *
 * The pulse is a genuine continuous animation (not hover-triggered),
 * so callers MUST call stopAnimation() when the containing window
 * closes, or the Timer leaks and keeps ticking forever.
 */
public class AuthHeader extends JPanel
{
    private float pulse = 0f;
    private final Timer pulseTimer;
    private final JLabel wordmark;
    private final JPanel logoArea;

    public AuthHeader()
    {
        setOpaque(false);
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(100, 150));
        setAlignmentX(Component.CENTER_ALIGNMENT);

        logoArea = new JPanel()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.applyAntialiasing(g2);

                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                Color accent = ThemeManager.getColor(ThemeColor.ACCENT);
                float radius = 55f + pulse * 12f;
                int alpha = (int) (60 + pulse * 30);

                RadialGradientPaint glowPaint = new RadialGradientPaint(
                    cx, cy, radius,
                    new float[] {0f, 1f},
                    new Color[] {
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), alpha),
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0)
                    });
                g2.setPaint(glowPaint);
                g2.fillOval((int) (cx - radius), (int) (cy - radius), (int) (radius * 2), (int) (radius * 2));

                g2.dispose();
                super.paintComponent(g);
            }
        };
        logoArea.setOpaque(false);
        logoArea.setLayout(new GridBagLayout());
        logoArea.add(new GameLogo(56));
        add(logoArea, BorderLayout.CENTER);

        wordmark = new JLabel("VERTEX", SwingConstants.CENTER);
        wordmark.setFont(UITheme.FONT_HEADING);
        wordmark.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        wordmark.setBorder(new EmptyBorder(8, 0, 0, 0));
        add(wordmark, BorderLayout.SOUTH);

        ThemeManager.addListener(new Runnable()
        {
            public void run()
            {
                wordmark.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
                logoArea.repaint();
            }
        });

        pulseTimer = new Timer(40, new ActionListener()
        {
            private float t = 0f;

            public void actionPerformed(ActionEvent e)
            {
                t += 0.05f;
                pulse = (float) (Math.sin(t) * 0.5 + 0.5);
                logoArea.repaint();
            }
        });
        pulseTimer.start();
    }

    /** Stops the continuous pulse animation. Call when the containing window is disposed. */
    public void stopAnimation()
    {
        pulseTimer.stop();
    }
}
