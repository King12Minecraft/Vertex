package engine;

/**
 * Animation
 * ---------
 * Advances through a sequence of Sprite frames over time - a walk
 * cycle, an explosion, a coin spinning. Driven by the same dt a
 * GameLoop hands its Ticker each tick; call update(dt) once per tick
 * and read currentFrame() when painting.
 *
 * Looping by default (frame timing wraps around forever); a one-shot
 * animation (an explosion that plays once and disappears) sets
 * loop=false and checks isFinished() to know when to remove the
 * GameObject it belongs to.
 */
public class Animation
{
    private final Sprite[] frames;
    private final double frameDurationSeconds;
    private final boolean loop;

    private double elapsed;
    private boolean finished;

    public Animation(Sprite[] frames, double frameDurationSeconds, boolean loop)
    {
        if (frames.length == 0)
        {
            throw new IllegalArgumentException("Animation needs at least one frame");
        }
        this.frames = frames;
        this.frameDurationSeconds = frameDurationSeconds;
        this.loop = loop;
    }

    public void update(double dt)
    {
        if (finished)
        {
            return;
        }
        elapsed += dt;
        double totalDuration = frames.length * frameDurationSeconds;
        if (elapsed >= totalDuration)
        {
            if (loop)
            {
                elapsed %= totalDuration;
            }
            else
            {
                elapsed = totalDuration - frameDurationSeconds;
                finished = true;
            }
        }
    }

    public Sprite currentFrame()
    {
        int index = (int) (elapsed / frameDurationSeconds);
        index = Math.max(0, Math.min(frames.length - 1, index));
        return frames[index];
    }

    /** Always false for a looping animation - it never "ends," it just keeps cycling. */
    public boolean isFinished()
    {
        return finished;
    }

    public void reset()
    {
        elapsed = 0;
        finished = false;
    }
}
