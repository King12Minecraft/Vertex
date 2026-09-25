package ui;
import economy.PartyMode;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ConfettiOverlay
 * ----------------
 * Party Mode's confetti burst - call ConfettiOverlay.burst(anchor)
 * from any real "you won" moment (see MemoryMatchWindow for the first
 * hookup) to celebrate for about 1.5 seconds, then it removes itself
 * completely. A no-op entirely when PartyMode.isEnabled() is false, so
 * every call site can call it unconditionally without its own if-check.
 *
 * Purely decorative - never reads or changes any game/match state, and
 * never blocks input: same click-through trick as CursorTrailOverlay
 * (contains(x, y) always false), and it's gone on its own well before
 * anyone would need to click through it anyway.
 */
public class ConfettiOverlay
{
    private static final int PARTICLE_COUNT = 60;
    private static final int DURATION_MS = 1500;
    private static final int TICK_MS = 20;

    private static final Color[] PALETTE = {
        new Color(255, 99, 132), new Color(255, 159, 64), new Color(255, 205, 86),
        new Color(75, 192, 192), new Color(54, 162, 235), new Color(153, 102, 255),
        new Color(74, 222, 128)
    };

    private ConfettiOverlay()
    {
        // Static utility class - never instantiated.
    }

    private static class Particle
    {
        float x, y, vx, vy, size, rotation, rotationSpeed;
        Color color;
    }

    public static void burst(Component anchor)
    {
        if (!PartyMode.isEnabled())
        {
            return;
        }

        JRootPane rootPane = SwingUtilities.getRootPane(anchor);
        if (rootPane == null)
        {
            return;
        }
        final JLayeredPane layeredPane = rootPane.getLayeredPane();

        final Random random = new Random();
        final List<Particle> particles = new ArrayList<Particle>();
        int width = Math.max(layeredPane.getWidth(), 200);
        for (int i = 0; i < PARTICLE_COUNT; i++)
        {
            Particle p = new Particle();
            p.x = random.nextFloat() * width;
            p.y = -20 - random.nextFloat() * 100;
            p.vx = (random.nextFloat() - 0.5f) * 2f;
            p.vy = 1f + random.nextFloat() * 2f;
            p.size = 5 + random.nextFloat() * 5;
            p.rotation = random.nextFloat() * 360;
            p.rotationSpeed = (random.nextFloat() - 0.5f) * 12f;
            p.color = PALETTE[random.nextInt(PALETTE.length)];
            particles.add(p);
        }

        final JComponent confettiPanel = new JComponent()
        {
            @Override
            public boolean contains(int x, int y)
            {
                return false;
            }

            @Override
            protected void paintComponent(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                for (int i = 0; i < particles.size(); i++)
                {
                    Particle p = particles.get(i);
                    Graphics2D pg = (Graphics2D) g2.create();
                    pg.translate(p.x, p.y);
                    pg.rotate(Math.toRadians(p.rotation));
                    pg.setColor(p.color);
                    pg.fillRect((int) (-p.size / 2), (int) (-p.size / 2), (int) p.size, (int) p.size);
                    pg.dispose();
                }
                g2.dispose();
            }
        };
        confettiPanel.setOpaque(false);
        confettiPanel.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
        layeredPane.add(confettiPanel, JLayeredPane.POPUP_LAYER);

        final long start = System.currentTimeMillis();
        final Timer timer = new Timer(TICK_MS, null);
        timer.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed > DURATION_MS)
                {
                    timer.stop();
                    layeredPane.remove(confettiPanel);
                    layeredPane.repaint();
                    return;
                }
                for (int i = 0; i < particles.size(); i++)
                {
                    Particle p = particles.get(i);
                    p.x += p.vx;
                    p.y += p.vy;
                    p.vy += 0.06f;
                    p.rotation += p.rotationSpeed;
                }
                confettiPanel.repaint();
            }
        });
        timer.start();
    }
}
