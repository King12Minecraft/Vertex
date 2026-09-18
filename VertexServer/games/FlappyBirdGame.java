package games;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * FlappyBirdGame
 * --------------
 * An original implementation of the well-known, uncopyrightable
 * "tap to flap upward, gravity pulls you back down, thread the gaps
 * between scrolling obstacles" mechanic - no relation to any specific
 * existing game's code, art, or level design. Continuous pixel-space
 * physics, same overall shape as BrickBreakerGame; FlappyBirdWindow
 * drives tick() on a fixed-rate timer, handles the single "flap"
 * input, and renders.
 */
public class FlappyBirdGame
{
    public static final int BOARD_WIDTH = 480;
    public static final int BOARD_HEIGHT = 640;

    public static final int BIRD_X = 110;
    public static final int BIRD_RADIUS = 14;
    private static final double GRAVITY = 0.35;
    private static final double FLAP_VELOCITY = -7.2;
    private static final double MAX_FALL_SPEED = 10.0;

    public static final int PIPE_WIDTH = 60;
    public static final int PIPE_GAP = 155;
    private static final int PIPE_SPACING = 230;
    private static final double PIPE_SPEED = 3.0;

    public static class Pipe
    {
        public double x;
        public final int gapTop;
        public boolean scored;

        Pipe(double x, int gapTop)
        {
            this.x = x;
            this.gapTop = gapTop;
        }
    }

    /** Cap on how far a new gap's vertical position may drift from the previous pipe's, so two gaps in a row can't land at opposite extremes with no fair way to fly from one to the other. */
    private static final int MAX_GAP_DRIFT = 110;

    private final Random random = new Random();
    private final LinkedList<Pipe> pipes = new LinkedList<Pipe>();
    private int lastGapTop = -1;

    private double birdY;
    private double birdVy;
    private boolean started;
    private boolean gameOver;
    private int score;

    public FlappyBirdGame()
    {
        birdY = BOARD_HEIGHT / 2.0;
        spawnPipe(BOARD_WIDTH + 120);
        spawnPipe(BOARD_WIDTH + 120 + PIPE_SPACING);
    }

    private void spawnPipe(double x)
    {
        int minTop = 60;
        int maxTop = BOARD_HEIGHT - 60 - PIPE_GAP;

        int lo = minTop;
        int hi = maxTop;
        if (lastGapTop >= 0)
        {
            lo = Math.max(minTop, lastGapTop - MAX_GAP_DRIFT);
            hi = Math.min(maxTop, lastGapTop + MAX_GAP_DRIFT);
        }

        int gapTop = lo + random.nextInt(Math.max(1, hi - lo + 1));
        lastGapTop = gapTop;
        pipes.add(new Pipe(x, gapTop));
    }

    public double getBirdY() { return birdY; }
    public List<Pipe> getPipes() { return new ArrayList<Pipe>(pipes); }
    public int getScore() { return score; }
    public boolean isGameOver() { return gameOver; }
    public boolean isStarted() { return started; }

    /** The first flap also starts the game - the bird just hangs in place until then, same "waiting to begin" feel the real thing has. */
    public void flap()
    {
        if (gameOver) return;
        started = true;
        birdVy = FLAP_VELOCITY;
    }

    public void tick()
    {
        if (gameOver || !started) return;

        birdVy = Math.min(birdVy + GRAVITY, MAX_FALL_SPEED);
        birdY += birdVy;

        for (Pipe pipe : pipes)
        {
            pipe.x -= PIPE_SPEED;
        }
        while (!pipes.isEmpty() && pipes.getFirst().x + PIPE_WIDTH < 0)
        {
            pipes.removeFirst();
        }
        if (pipes.isEmpty() || pipes.getLast().x < BOARD_WIDTH + 120 - PIPE_SPACING)
        {
            double nextX = pipes.isEmpty() ? BOARD_WIDTH + 120 : pipes.getLast().x + PIPE_SPACING;
            spawnPipe(nextX);
        }

        checkScoring();
        checkCollisions();
    }

    private void checkScoring()
    {
        for (Pipe pipe : pipes)
        {
            if (!pipe.scored && pipe.x + PIPE_WIDTH < BIRD_X)
            {
                pipe.scored = true;
                score++;
            }
        }
    }

    private void checkCollisions()
    {
        if (birdY - BIRD_RADIUS < 0 || birdY + BIRD_RADIUS > BOARD_HEIGHT)
        {
            gameOver = true;
            return;
        }

        for (Pipe pipe : pipes)
        {
            boolean overlapsX = BIRD_X + BIRD_RADIUS >= pipe.x && BIRD_X - BIRD_RADIUS <= pipe.x + PIPE_WIDTH;
            if (!overlapsX) continue;

            boolean hitsTopPipe = birdY - BIRD_RADIUS < pipe.gapTop;
            boolean hitsBottomPipe = birdY + BIRD_RADIUS > pipe.gapTop + PIPE_GAP;
            if (hitsTopPipe || hitsBottomPipe)
            {
                gameOver = true;
                return;
            }
        }
    }
}
