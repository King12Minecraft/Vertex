import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Timer;

/**
 * HoverGlowAnimator
 * -----------------
 * Smoothly animates a 0-1 "glow intensity" value on hover enter/exit,
 * repainting the target component each tick. Used by ThemedButton and
 * other accent elements for the animated glow effect - not a full
 * animation framework, just enough for a clean fade in/out.
 */
public class HoverGlowAnimator
{
    private static final float STEP = 0.15f;
    private static final int TICK_MS = 15;

    private final Component target;
    private float intensity = 0f;
    private boolean increasing = false;
    private Timer timer;

    public HoverGlowAnimator(Component target)
    {
        this.target = target;
    }

    public float getIntensity()
    {
        return intensity;
    }

    public void animateIn()
    {
        start(true);
    }

    public void animateOut()
    {
        start(false);
    }

    private void start(boolean toIncrease)
    {
        increasing = toIncrease;
        if (timer != null && timer.isRunning())
        {
            return; // already ticking - it picks up the new direction on its next tick
        }
        timer = new Timer(TICK_MS, null);
        timer.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                intensity = increasing
                    ? Math.min(1f, intensity + STEP)
                    : Math.max(0f, intensity - STEP);
                target.repaint();

                boolean shouldContinue = increasing ? intensity < 1f : intensity > 0f;
                if (!shouldContinue)
                {
                    timer.stop();
                }
            }
        });
        timer.start();
    }
}
