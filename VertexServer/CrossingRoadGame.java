import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * CrossingRoadGame
 * ----------------
 * Frogger-style: move up through lanes of horizontal traffic to reach
 * the top. Reaching the top counts a crossing and resets you to the
 * bottom with slightly faster traffic (same continuous-difficulty-ramp
 * shape as Racing/Dino/Snake) - the only way the run ends is a crash.
 * Purely single-player, no server involvement in gameplay, matching
 * every other offline game.
 */
public class CrossingRoadGame
{
    public static final int COLS = 7;
    public static final int ROWS = 9;
    public static final int CELL = 56;
    public static final int BOARD_WIDTH = COLS * CELL;
    public static final int BOARD_HEIGHT = ROWS * CELL;

    private final Random random = new Random();
    private final List<List<double[]>> laneCars = new ArrayList<List<double[]>>();
    private final double[] laneSpeed = new double[ROWS];
    private final boolean[] laneDirRight = new boolean[ROWS];

    private int playerCol;
    private int playerRow;
    private int score = 0;
    private boolean gameOver = false;

    public CrossingRoadGame()
    {
        playerCol = COLS / 2;
        playerRow = ROWS - 1;

        for (int r = 0; r < ROWS; r++)
        {
            List<double[]> cars = new ArrayList<double[]>();
            if (r > 0 && r < ROWS - 1)
            {
                laneDirRight[r] = r % 2 == 0;
                laneSpeed[r] = 1.4 + random.nextDouble() * 1.6;
                int carCount = 2 + random.nextInt(2);
                for (int i = 0; i < carCount; i++)
                {
                    cars.add(new double[] { random.nextInt(BOARD_WIDTH), 50 });
                }
            }
            laneCars.add(cars);
        }
    }

    public void moveUp()
    {
        if (gameOver || playerRow <= 0)
        {
            return;
        }
        playerRow--;
        if (playerRow == 0)
        {
            completeCrossing();
        }
    }

    public void moveDown()
    {
        if (!gameOver && playerRow < ROWS - 1) playerRow++;
    }

    public void moveLeft()
    {
        if (!gameOver && playerCol > 0) playerCol--;
    }

    public void moveRight()
    {
        if (!gameOver && playerCol < COLS - 1) playerCol++;
    }

    private void completeCrossing()
    {
        score++;
        playerRow = ROWS - 1;
        playerCol = COLS / 2;
        for (int r = 1; r < ROWS - 1; r++)
        {
            laneSpeed[r] += 0.15;
        }
    }

    public void tick()
    {
        if (gameOver)
        {
            return;
        }

        for (int r = 1; r < ROWS - 1; r++)
        {
            for (double[] car : laneCars.get(r))
            {
                car[0] += laneDirRight[r] ? laneSpeed[r] : -laneSpeed[r];
                if (laneDirRight[r] && car[0] > BOARD_WIDTH) car[0] = -car[1];
                if (!laneDirRight[r] && car[0] < -car[1]) car[0] = BOARD_WIDTH;
            }
        }

        if (playerRow > 0 && playerRow < ROWS - 1)
        {
            double playerX = playerCol * CELL + CELL / 2.0;
            List<double[]> cars = laneCars.get(playerRow);
            for (int i = 0; i < cars.size(); i++)
            {
                double[] car = cars.get(i);
                double carCenter = car[0] + car[1] / 2.0;
                if (Math.abs(carCenter - playerX) < (car[1] / 2.0 + CELL / 2.0 - 8))
                {
                    gameOver = true;
                    return;
                }
            }
        }
    }

    public int getPlayerCol() { return playerCol; }
    public int getPlayerRow() { return playerRow; }
    public List<List<double[]>> getLaneCars() { return laneCars; }
    public int getScore() { return score; }
    public boolean isGameOver() { return gameOver; }
}
