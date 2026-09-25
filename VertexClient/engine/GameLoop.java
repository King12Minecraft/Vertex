package engine;

import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * GameLoop
 * --------
 * A named wrapper around the javax.swing.Timer-driven tick loop every
 * offline game in this codebase already hand-rolls (see
 * BrickBreakerWindow, FightArenaPanel, TetrisWindow) - same underlying
 * mechanism (a Swing Timer firing on the EDT, so game state, physics,
 * and repaint() all stay safely on the UI thread with zero extra
 * synchronization), just given one shared name and API instead of a
 * private Timer field reinvented per game.
 *
 * Deliberately NOT a separate thread or a fixed-timestep-with-
 * catch-up-steps loop - those solve problems this codebase doesn't
 * have (no game here needs sub-frame physics accuracy or to survive a
 * dropped frame by simulating multiple steps at once), and a second
 * thread touching Swing components would violate the single-threaded
 * rule Swing requires everywhere else in Vertex.
 *
 * Ticker.tick(dt) is handed the actual elapsed time in seconds since
 * the previous tick, for a game that wants frame-rate-independent
 * motion (position += velocity * dt). A game that prefers the existing
 * codebase's convention of moving a fixed amount every tick regardless
 * of real elapsed time (BrickBreakerGame's ballX += ballVx, for
 * example) can simply ignore dt - both styles work with the same loop.
 */
public class GameLoop
{
    public interface Ticker
    {
        void tick(double dtSeconds);
    }

    private final Timer timer;
    private long lastTickNanos;

    /** intervalMs is both the Timer's period and dt's fallback value (as seconds) for the very first tick, when there's no previous tick to measure elapsed time from. */
    public GameLoop(int intervalMs, final Ticker ticker)
    {
        final double fallbackDt = intervalMs / 1000.0;
        timer = new Timer(intervalMs, new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                long now = System.nanoTime();
                double dt = lastTickNanos == 0 ? fallbackDt : (now - lastTickNanos) / 1_000_000_000.0;
                lastTickNanos = now;
                ticker.tick(dt);
            }
        });
    }

    public void start()
    {
        lastTickNanos = 0;
        timer.start();
    }

    public void stop()
    {
        timer.stop();
    }

    public boolean isRunning()
    {
        return timer.isRunning();
    }
}
