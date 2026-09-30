package ui;

import mechanics.ReconnectPolicy;

import javax.swing.JLabel;
import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * ReconnectCountdown
 * ------------------
 * Turns the static "waiting to reconnect (up to 30s)..." notice into a ticking one
 * ("...(27s left)...") on whichever label a game window uses for it. Starts a 1-second Swing
 * timer that rewrites the label each tick and stops by itself when the 30 seconds are up or as
 * soon as anything else writes to that label (the resume update, a result, another status) - so
 * the 18 game windows need only swap one setText call for show(), with no cleanup hooks. Must
 * be called on the Swing thread, like the setText it replaces. Client-only, purely cosmetic:
 * the real window is enforced by the server.
 */
public final class ReconnectCountdown
{
    private ReconnectCountdown() { }

    public static void show(final JLabel label, final String notice)
    {
        final long start = System.currentTimeMillis();
        final String[] lastWritten = { render(notice, ReconnectPolicy.GRACE_SECONDS) };
        label.setText(lastWritten[0]);

        final Timer timer = new Timer(1000, null);
        timer.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                long elapsed = (System.currentTimeMillis() - start) / 1000;
                int left = (int) (ReconnectPolicy.GRACE_SECONDS - elapsed);
                if (left <= 0 || !lastWritten[0].equals(label.getText()))
                {
                    timer.stop();
                    return;
                }
                lastWritten[0] = render(notice, left);
                label.setText(lastWritten[0]);
            }
        });
        timer.start();
    }

    /** "up to 30s" -> "27s left"; a notice without that phrase is shown as-is (nothing to count). */
    static String render(String notice, int secondsLeft)
    {
        if (notice == null)
        {
            return "";
        }
        return notice.replaceFirst("up to \\d+s", secondsLeft + "s left");
    }
}
