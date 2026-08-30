import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Random;

/**
 * GlitchEffectOverlay
 * -------------------
 * The actual animated distortion behind "Glitch mode" - GlitchTheme
 * itself is just a color palette (Theme is colors only, see its own
 * javadoc), so the visible "glitchiness" lives here instead: a
 * translucent overlay on every window, checking a few times a second
 * whether GlitchTheme is the active theme, and when it is, randomly
 * flashing 2-4 short-lived offset colored bands (a cheap stand-in for
 * chromatic-aberration/scan-tear glitch art) for ~120ms before clearing.
 *
 * Deliberately does NOT touch the real content underneath - no pixel
 * capturing/shifting of the actual UI, just translucent bars drawn on
 * top and cleared again. Cheap, and importantly can't ever make a
 * button un-clickable: it never intercepts mouse events (same
 * click-through behavior as SignatureOverlay, which uses the identical
 * layered-pane placement with no listeners registered), and the
 * flashes are brief and intermittent by design so the UI stays fully
 * usable throughout - "mostly glitchy but still usable," not a
 * strobing mess.
 *
 * Same idempotent-attach pattern as SignatureOverlay - safe to call
 * more than once on the same frame.
 */
public class GlitchEffectOverlay
{
    private static final String ATTACHED_KEY = "glitchEffectOverlay.attached";
    private static final int CHECK_INTERVAL_MS = 220;
    private static final double TRIGGER_CHANCE = 0.10;
    private static final int FLASH_DURATION_MS = 120;

    private static final Color[] GLITCH_COLORS = {
        new Color(255, 0, 200, 70),
        new Color(0, 255, 210, 70),
        new Color(140, 255, 60, 55),
    };

    private GlitchEffectOverlay()
    {
        // Static utility class - never instantiated.
    }

    public static void attach(final JFrame frame)
    {
        if (Boolean.TRUE.equals(frame.getRootPane().getClientProperty(ATTACHED_KEY)))
        {
            return;
        }
        frame.getRootPane().putClientProperty(ATTACHED_KEY, Boolean.TRUE);

        final GlitchPanel glitchPanel = new GlitchPanel();
        glitchPanel.setOpaque(false);

        final JLayeredPane layeredPane = frame.getLayeredPane();
        layeredPane.add(glitchPanel, JLayeredPane.DRAG_LAYER);

        final Runnable resize = new Runnable()
        {
            public void run()
            {
                Dimension paneSize = layeredPane.getSize();
                glitchPanel.setBounds(0, 0, paneSize.width, paneSize.height);
            }
        };
        javax.swing.SwingUtilities.invokeLater(resize);
        frame.addComponentListener(new ComponentAdapter()
        {
            public void componentResized(ComponentEvent e) { resize.run(); }
        });

        Timer checkTimer = new Timer(CHECK_INTERVAL_MS, new ActionListener()
        {
            private final Random random = new Random();

            public void actionPerformed(ActionEvent e)
            {
                if (!(ThemeManager.getCurrentTheme() instanceof GlitchTheme))
                {
                    if (glitchPanel.isFlashing())
                    {
                        glitchPanel.clearFlash();
                    }
                    return;
                }
                if (random.nextDouble() < TRIGGER_CHANCE)
                {
                    glitchPanel.triggerFlash(random);
                }
            }
        });
        checkTimer.start();
    }

    /** Renders the current flash (if any) as a handful of translucent offset bands, then self-clears after FLASH_DURATION_MS via its own short-lived Timer - no persistent per-frame animation loop needed since each flash is a one-shot burst. */
    private static class GlitchPanel extends JPanel
    {
        private int[][] bands; // {y, height, xOffset, colorIndex}
        private Timer clearTimer;

        boolean isFlashing()
        {
            return bands != null;
        }

        void triggerFlash(Random random)
        {
            int h = Math.max(1, getHeight());
            int w = Math.max(1, getWidth());
            int count = 2 + random.nextInt(3);
            bands = new int[count][4];
            for (int i = 0; i < count; i++)
            {
                bands[i][0] = random.nextInt(h);
                bands[i][1] = 4 + random.nextInt(14);
                bands[i][2] = (random.nextInt(21) - 10) * Math.max(1, w / 200);
                bands[i][3] = random.nextInt(GLITCH_COLORS.length);
            }
            repaint();

            if (clearTimer != null)
            {
                clearTimer.stop();
            }
            clearTimer = new Timer(FLASH_DURATION_MS, new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { clearFlash(); }
            });
            clearTimer.setRepeats(false);
            clearTimer.start();
        }

        void clearFlash()
        {
            bands = null;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            if (bands == null)
            {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            for (int i = 0; i < bands.length; i++)
            {
                int y = bands[i][0];
                int bandHeight = bands[i][1];
                int xOffset = bands[i][2];
                Color color = GLITCH_COLORS[bands[i][3]];
                g2.setColor(color);
                g2.fillRect(xOffset, y, w, bandHeight);
            }
            g2.dispose();
        }
    }
}
