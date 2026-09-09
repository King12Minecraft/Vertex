package economy;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

/**
 * FpsTracker
 * ----------
 * One instance per game panel - call tick() once per frame (from the
 * same Timer callback that drives the game itself), then render() at
 * the end of paintComponent to draw a small "FPS: NN" readout if
 * FpsCounterSetting is on. Averages over the last second's worth of
 * frames rather than showing instantaneous per-frame timing, which
 * jitters too much to read at a glance.
 */
public class FpsTracker
{
    private int frameCount = 0;
    private long windowStart = System.currentTimeMillis();
    private int displayedFps = 60;

    public void tick()
    {
        frameCount++;
        long now = System.currentTimeMillis();
        long elapsed = now - windowStart;
        if (elapsed >= 1000)
        {
            displayedFps = (int) Math.round(frameCount * 1000.0 / elapsed);
            frameCount = 0;
            windowStart = now;
        }
    }

    /** No-op if FpsCounterSetting is off - callers can call this unconditionally at the end of paintComponent without checking the setting themselves. */
    public void render(Graphics2D g2, int panelWidth)
    {
        if (!FpsCounterSetting.isEnabled())
        {
            return;
        }

        String text = "FPS: " + displayedFps;
        Graphics2D g = (Graphics2D) g2.create();
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        int textWidth = g.getFontMetrics().stringWidth(text);
        int x = panelWidth - textWidth - 10;
        int y = 18;

        g.setColor(new Color(0, 0, 0, 140));
        g.fillRoundRect(x - 6, y - 14, textWidth + 12, 20, 6, 6);
        g.setColor(displayedFps >= 50 ? new Color(90, 220, 120) : displayedFps >= 24 ? new Color(230, 200, 60) : new Color(230, 90, 90));
        g.drawString(text, x, y);
        g.dispose();
    }
}
