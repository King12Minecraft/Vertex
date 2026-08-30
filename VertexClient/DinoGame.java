import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * DinoGame
 * --------
 * Model for the Chrome Dino runner - a single jump mechanic, scrolling
 * ground-level obstacles, speed ramps up over time. Same overall shape
 * as RacingGame (obstacles as a list of positions, ticked each frame).
 */
public class DinoGame
{
    public static final int WIDTH = 500;
    public static final int HEIGHT = 220;
    public static final int GROUND_Y = 170;
    public static final int DINO_X = 60;
    public static final int DINO_SIZE = 34;

    private static final double GRAVITY = 1.4;
    private static final double JUMP_VELOCITY = -17;

    /** Each obstacle: its x position - just a scrolling marker at ground level. */
    private final List<Integer> obstacles = new ArrayList<Integer>();

    private double dinoY = GROUND_Y - DINO_SIZE;
    private double velocityY = 0;
    private boolean jumping = false;

    private int score = 0;
    private int frameCount = 0;
    private double speed = 6.0;
    private boolean gameOver = false;

    public void jump()
    {
        if (!jumping)
        {
            jumping = true;
            velocityY = JUMP_VELOCITY;
        }
    }

    public void tick()
    {
        if (gameOver)
        {
            return;
        }
        frameCount++;
        score++;

        if (jumping)
        {
            dinoY += velocityY;
            velocityY += GRAVITY;
            if (dinoY >= GROUND_Y - DINO_SIZE)
            {
                dinoY = GROUND_Y - DINO_SIZE;
                jumping = false;
                velocityY = 0;
            }
        }

        for (int i = 0; i < obstacles.size(); i++)
        {
            obstacles.set(i, obstacles.get(i) - (int) speed);
        }
        Iterator<Integer> it = obstacles.iterator();
        while (it.hasNext())
        {
            if (it.next() < -30)
            {
                it.remove();
            }
        }

        int spawnInterval = Math.max(45, 90 - frameCount / 30);
        if (frameCount % spawnInterval == 0)
        {
            obstacles.add(WIDTH);
        }

        if (frameCount % 300 == 0)
        {
            speed += 0.6;
        }

        int dinoBottom = (int) dinoY + DINO_SIZE;
        for (int i = 0; i < obstacles.size(); i++)
        {
            int obstacleX = obstacles.get(i);
            boolean overlapsX = obstacleX < DINO_X + DINO_SIZE - 8 && obstacleX + 20 > DINO_X + 8;
            boolean overlapsY = dinoBottom > GROUND_Y - 26;
            if (overlapsX && overlapsY)
            {
                gameOver = true;
                return;
            }
        }
    }

    public int getDinoY() { return (int) dinoY; }
    public List<Integer> getObstacleXs() { return obstacles; }
    public int getScore() { return score / 6; }
    public boolean isGameOver() { return gameOver; }
}
