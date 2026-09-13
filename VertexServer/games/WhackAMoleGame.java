package games;

import java.util.Random;

/**
 * WhackAMoleGame
 * --------------
 * Standard whack-a-mole rules, an original implementation. A 3x3 grid
 * of holes; moles pop up one at a time (occasionally two at once as
 * the game speeds up) for a shrinking window before ducking back
 * down. Click a mole before it disappears to score - clicking an
 * empty hole scores nothing. Runs for a fixed duration; score is
 * total mole hits.
 */
public class WhackAMoleGame
{
    public static final int GRID_SIZE = 9;
    public static final long GAME_DURATION_MS = 30_000;

    private final boolean[] moleUp = new boolean[GRID_SIZE];
    private final long[] moleUpSince = new long[GRID_SIZE];
    private final Random random = new Random();
    private int score = 0;
    private long startedAt;
    private boolean over = false;

    public WhackAMoleGame()
    {
        startedAt = System.currentTimeMillis();
    }

    public boolean isMoleUp(int index) { return moleUp[index]; }
    public int getScore() { return score; }
    public boolean isOver() { return over; }

    public long getElapsedMs()
    {
        return System.currentTimeMillis() - startedAt;
    }

    public void checkTimeUp()
    {
        if (getElapsedMs() >= GAME_DURATION_MS)
        {
            over = true;
        }
    }

    /** Called on a fixed client-side tick - occasionally pops a new mole up (getting slightly faster/more frequent as the game goes on, since a flat difficulty the whole 30 seconds would be a lot less interesting), and ducks any mole that's been up too long without being hit. Mole "up" duration also shrinks over time, matching the same difficulty ramp as spawn frequency. */
    public void tick()
    {
        if (over) return;
        double progress = getElapsedMs() / (double) GAME_DURATION_MS;

        long upDurationMs = (long) (1100 - progress * 500);
        long now = System.currentTimeMillis();
        for (int i = 0; i < GRID_SIZE; i++)
        {
            if (moleUp[i] && now - moleUpSince[i] > upDurationMs)
            {
                moleUp[i] = false;
            }
        }

        double spawnChance = 0.06 + progress * 0.08;
        if (random.nextDouble() < spawnChance)
        {
            int index = random.nextInt(GRID_SIZE);
            if (!moleUp[index])
            {
                moleUp[index] = true;
                moleUpSince[index] = now;
            }
        }
    }

    /** Returns true if this click actually hit a mole (and scores it) - false for an empty hole. */
    public boolean whack(int index)
    {
        if (over || !moleUp[index]) return false;
        moleUp[index] = false;
        score++;
        return true;
    }
}
