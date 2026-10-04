package ui;

import pages.MainMenu;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseMotionAdapter;

/**
 * ScreenBreakOverlay
 * ------------------
 * A mandatory eye-rest break: every ACTIVE_SECONDS_BEFORE_BREAK (20 real
 * minutes) the window has actually been focused and not minimized, a
 * full-screen, input-blocking overlay covers the app for BREAK_SECONDS
 * (30 seconds). No exceptions: there is deliberately no close button, no
 * Escape binding, and no Settings toggle to turn this off. If a game is
 * currently embedded in the GAME_HOST slot (MainMenu.isGameInProgress())
 * when the threshold is reached, the break is deferred - rechecked once a
 * second - until that game ends, rather than interrupting an active match.
 *
 * "Active" screen time, not wall-clock time: the count only advances
 * while the frame is focused and not minimized (frame.isActive() and not
 * ICONIFIED), so the 20-minute clock doesn't run down while nobody is
 * actually looking at the app. Attached once, whole-app, from MainMenu -
 * the same "one static attach() call from the shell" shape
 * SignatureOverlay/CursorTrailOverlay already use, via the frame's glass
 * pane rather than a layered-pane child, since this one needs to actually
 * block input rather than being click-through.
 */
public class ScreenBreakOverlay
{
    private static final int ACTIVE_SECONDS_BEFORE_BREAK = 20 * 60;
    private static final int BREAK_SECONDS = 30;

    private ScreenBreakOverlay()
    {
        // Static utility class - never instantiated.
    }

    public static void attach(final MainMenu menu)
    {
        final JFrame frame = menu;
        final BreakPanel breakPanel = new BreakPanel();
        frame.setGlassPane(breakPanel);

        final int[] activeSeconds = { 0 };
        final int[] remainingBreakSeconds = { 0 };
        final boolean[] onBreak = { false };

        Timer clock = new Timer(1000, new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                if (onBreak[0])
                {
                    remainingBreakSeconds[0]--;
                    breakPanel.setSecondsLeft(remainingBreakSeconds[0]);
                    if (remainingBreakSeconds[0] <= 0)
                    {
                        onBreak[0] = false;
                        activeSeconds[0] = 0;
                        breakPanel.setVisible(false);
                        frame.getContentPane().requestFocusInWindow();
                    }
                    return;
                }

                boolean windowActive = frame.isActive()
                    && (frame.getExtendedState() & JFrame.ICONIFIED) == 0;
                if (!windowActive)
                {
                    return;
                }
                activeSeconds[0]++;

                if (activeSeconds[0] >= ACTIVE_SECONDS_BEFORE_BREAK && !menu.isGameInProgress())
                {
                    onBreak[0] = true;
                    remainingBreakSeconds[0] = BREAK_SECONDS;
                    breakPanel.setSecondsLeft(remainingBreakSeconds[0]);
                    breakPanel.setVisible(true);
                    breakPanel.requestFocusInWindow();
                }
            }
        });
        clock.start();
    }

    /**
     * The full-screen scrim itself - opaque and input-blocking. A glass
     * pane only actually stops clicks/keys from reaching what's underneath
     * once something is listening on it, so real (if empty) mouse/key
     * listeners are attached specifically to swallow every event rather
     * than letting it fall through to the app while a break is showing.
     */
    private static class BreakPanel extends JComponent
    {
        private int secondsLeft = BREAK_SECONDS;

        BreakPanel()
        {
            setOpaque(true);
            setFocusable(true);
            setVisible(false);
            addMouseListener(new MouseAdapter() {});
            addMouseMotionListener(new MouseMotionAdapter() {});
            addKeyListener(new KeyAdapter()
            {
                public void keyPressed(KeyEvent e) { e.consume(); }
            });
        }

        void setSecondsLeft(int seconds)
        {
            this.secondsLeft = Math.max(0, seconds);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            int w = getWidth();
            int h = getHeight();

            g2.setColor(new Color(6, 6, 10, 235));
            g2.fillRect(0, 0, w, h);

            Color accent = ThemeManager.getColor(ThemeColor.ACCENT);

            g2.setFont(UITheme.FONT_HEADING.deriveFont(Font.BOLD, 34f));
            g2.setColor(Color.WHITE);
            drawCentered(g2, "Time to look away", w / 2, h / 2 - 110);

            g2.setFont(UITheme.FONT_SUBHEAD.deriveFont(16f));
            g2.setColor(new Color(220, 220, 230));
            drawCentered(g2, "A real break, every 20 minutes - rest your eyes.", w / 2, h / 2 - 68);

            int ringDiameter = 140;
            int ringX = w / 2 - ringDiameter / 2;
            int ringY = h / 2 - ringDiameter / 2 + 10;
            g2.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(255, 255, 255, 40));
            g2.drawOval(ringX, ringY, ringDiameter, ringDiameter);
            double fraction = secondsLeft / (double) BREAK_SECONDS;
            g2.setColor(accent);
            g2.drawArc(ringX, ringY, ringDiameter, ringDiameter, 90, (int) Math.round(-360 * fraction));

            g2.setFont(UITheme.FONT_HEADING.deriveFont(Font.BOLD, 44f));
            g2.setColor(Color.WHITE);
            drawCentered(g2, String.valueOf(secondsLeft), w / 2, ringY + ringDiameter / 2 + 15);

            g2.setFont(UITheme.FONT_SMALL);
            g2.setColor(new Color(170, 170, 180));
            drawCentered(g2, "This break can't be skipped - it won't interrupt a game already in progress.",
                w / 2, h / 2 + 130);

            g2.dispose();
        }

        private void drawCentered(Graphics2D g2, String text, int cx, int cy)
        {
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(text);
            g2.drawString(text, cx - tw / 2, cy);
        }
    }
}
