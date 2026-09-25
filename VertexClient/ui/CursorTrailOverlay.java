package ui;
import economy.PartyMode;

import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * CursorTrailOverlay
 * -------------------
 * Party Mode's cursor trail - a short-lived rainbow trail of dots
 * following the mouse. Purely decorative: attached once, whole-app,
 * from MainMenu; does nothing at all (no new points get recorded)
 * whenever PartyMode.isEnabled() is false, so turning it off in
 * Settings takes effect immediately - any already-recorded points
 * simply finish fading out over MAX_AGE_MS instead of needing a
 * restart.
 *
 * Each point remembers when it was added and fades/expires by real
 * elapsed time (MAX_AGE_MS), not by a fixed "remove one per tick"
 * countdown - that first approach looked right in isolation but was a
 * real bug: whenever points arrive slower than the tick rate removes
 * them (a plausible mouse-movement speed, not just a test artifact),
 * the trail can empty out while the cursor is still moving. Age-based
 * expiry ties trail length to actual elapsed time instead, so it's
 * correct regardless of how fast the mouse moves or how it happens to
 * align with the tick timer.
 *
 * Click-through by construction, not by accident: the painted panel
 * overrides contains(x, y) to always return false, the standard
 * lightweight Swing trick for a non-interactive overlay - real clicks
 * hit-test straight through it to whatever's actually underneath,
 * without needing manual mouse-event redispatching. The panel itself
 * never registers any mouse listeners; the frame does, purely as an
 * observer, which is why this never blocks a single click anywhere.
 */
public class CursorTrailOverlay
{
    private static final int MAX_AGE_MS = 400;
    private static final int TICK_MS = 30;

    private static final Color[] PALETTE = {
        new Color(255, 99, 132), new Color(255, 159, 64), new Color(255, 205, 86),
        new Color(75, 192, 192), new Color(54, 162, 235), new Color(153, 102, 255)
    };

    private static class TrailPoint
    {
        final Point location;
        final long addedAt;

        TrailPoint(Point location, long addedAt)
        {
            this.location = location;
            this.addedAt = addedAt;
        }
    }

    private CursorTrailOverlay()
    {
        // Static utility class - never instantiated.
    }

    public static void attach(final JFrame frame, final JLayeredPane layeredPane)
    {
        final List<TrailPoint> points = new ArrayList<TrailPoint>();

        final JPanel trailPanel = new JPanel()
        {
            @Override
            public boolean contains(int x, int y)
            {
                return false;
            }

            @Override
            protected void paintComponent(Graphics g)
            {
                if (points.isEmpty())
                {
                    return;
                }
                long now = System.currentTimeMillis();
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                for (int i = 0; i < points.size(); i++)
                {
                    TrailPoint tp = points.get(i);
                    float age = (now - tp.addedAt) / (float) MAX_AGE_MS;
                    float fraction = Math.max(0f, 1f - age);
                    int size = (int) (4 + fraction * 10);
                    Color base = PALETTE[i % PALETTE.length];
                    int alpha = (int) (fraction * 180);
                    g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
                    g2.fillOval(tp.location.x - size / 2, tp.location.y - size / 2, size, size);
                }
                g2.dispose();
            }
        };
        trailPanel.setOpaque(false);
        trailPanel.setFocusable(false);

        layeredPane.add(trailPanel, JLayeredPane.POPUP_LAYER);
        layeredPane.addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent e)
            {
                trailPanel.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
            }
        });
        trailPanel.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());

        // A plain listener on the frame only ever sees events that land on
        // the frame itself, never on any child component inside it (Swing
        // routes each event straight to the deepest component under the
        // cursor, it doesn't bubble up) - a global AWTEventListener is the
        // correct, standard way to observe mouse motion regardless of which
        // component within the app actually received it. This purely
        // monitors events; it never consumes or redirects them, so it can't
        // interfere with normal clicks/drags anywhere in the app.
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener()
        {
            public void eventDispatched(AWTEvent event)
            {
                if (!PartyMode.isEnabled() || !(event instanceof MouseEvent))
                {
                    return;
                }
                MouseEvent me = (MouseEvent) event;
                if (me.getID() != MouseEvent.MOUSE_MOVED && me.getID() != MouseEvent.MOUSE_DRAGGED)
                {
                    return;
                }
                Component source = (Component) me.getSource();
                if (SwingUtilities.getWindowAncestor(source) != frame)
                {
                    return;
                }
                Point p = SwingUtilities.convertPoint(source, me.getPoint(), layeredPane);
                points.add(new TrailPoint(p, System.currentTimeMillis()));
            }
        }, AWTEvent.MOUSE_MOTION_EVENT_MASK);

        Timer timer = new Timer(TICK_MS, new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                if (!points.isEmpty())
                {
                    long now = System.currentTimeMillis();
                    Iterator<TrailPoint> it = points.iterator();
                    while (it.hasNext())
                    {
                        if (now - it.next().addedAt > MAX_AGE_MS)
                        {
                            it.remove();
                        }
                    }
                    trailPanel.repaint();
                }
            }
        });
        timer.start();
    }
}
