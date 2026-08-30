import java.awt.Point;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * SnakeGame
 * ---------
 * The first real game in Vertex (Phase 7) - built fresh, not
 * converted from an old project. Implements the Game interface
 * scaffolded in Phase 6. Pure game logic/state - SnakePanel handles
 * rendering and input, SnakeWindow hosts the whole thing.
 */
public class SnakeGame implements Game
{
    public enum Mode { CLASSIC, WRAP_AROUND }
    public enum Direction { UP, DOWN, LEFT, RIGHT }

    public static final int GRID_WIDTH = 20;
    public static final int GRID_HEIGHT = 20;

    private final LinkedList<Point> snake = new LinkedList<Point>();
    private Direction direction = Direction.RIGHT;
    private Direction pendingDirection = Direction.RIGHT;
    private Point food;
    private int score = 0;
    private boolean gameOver = false;
    private boolean paused = false;
    private final Mode mode;
    private final Random random = new Random();

    public SnakeGame(Mode mode)
    {
        this.mode = mode;
        reset();
    }

    private void reset()
    {
        snake.clear();
        int startX = GRID_WIDTH / 2;
        int startY = GRID_HEIGHT / 2;
        snake.add(new Point(startX, startY));
        snake.add(new Point(startX - 1, startY));
        snake.add(new Point(startX - 2, startY));
        direction = Direction.RIGHT;
        pendingDirection = Direction.RIGHT;
        score = 0;
        gameOver = false;
        paused = false;
        spawnFood();
    }

    private void spawnFood()
    {
        Point candidate;
        do
        {
            candidate = new Point(random.nextInt(GRID_WIDTH), random.nextInt(GRID_HEIGHT));
        }
        while (snake.contains(candidate));
        food = candidate;
    }

    public void setPendingDirection(Direction newDirection)
    {
        // Prevent reversing directly into the snake's own body.
        if (isOpposite(newDirection, direction))
        {
            return;
        }
        pendingDirection = newDirection;
    }

    private boolean isOpposite(Direction a, Direction b)
    {
        return (a == Direction.UP && b == Direction.DOWN)
            || (a == Direction.DOWN && b == Direction.UP)
            || (a == Direction.LEFT && b == Direction.RIGHT)
            || (a == Direction.RIGHT && b == Direction.LEFT);
    }

    /** Advances the game by one step. Does nothing if paused or already over. */
    public void tick()
    {
        if (gameOver || paused)
        {
            return;
        }

        direction = pendingDirection;
        Point head = snake.getFirst();
        Point next = new Point(head);

        if (direction == Direction.UP)    next.y -= 1;
        if (direction == Direction.DOWN)  next.y += 1;
        if (direction == Direction.LEFT)  next.x -= 1;
        if (direction == Direction.RIGHT) next.x += 1;

        if (mode == Mode.WRAP_AROUND)
        {
            next.x = ((next.x % GRID_WIDTH) + GRID_WIDTH) % GRID_WIDTH;
            next.y = ((next.y % GRID_HEIGHT) + GRID_HEIGHT) % GRID_HEIGHT;
        }
        else if (next.x < 0 || next.x >= GRID_WIDTH || next.y < 0 || next.y >= GRID_HEIGHT)
        {
            gameOver = true;
            return;
        }

        if (snake.contains(next))
        {
            gameOver = true;
            return;
        }

        snake.addFirst(next);

        if (next.equals(food))
        {
            score += 10;
            spawnFood();
        }
        else
        {
            snake.removeLast();
        }
    }

    public List<Point> getSnakeBody() { return new ArrayList<Point>(snake); }
    public Point getFood() { return food; }
    public int getScore() { return score; }
    public boolean isGameOver() { return gameOver; }
    public boolean isPaused() { return paused; }
    public Mode getMode() { return mode; }

    /** Current speed in milliseconds per tick - speeds up gradually as the score grows. */
    public int getTickIntervalMillis()
    {
        int interval = 160 - (score / 10) * 4;
        return Math.max(interval, 60);
    }

    // ---- Game interface ----

    public GameInfo getInfo()
    {
        return new GameInfo("snake", "Snake", "Single Player", "Practice Mode", false, false, "1.0");
    }

    public void start()
    {
        paused = false;
    }

    public void pause()
    {
        paused = true;
    }

    public String saveState()
    {
        // Simple placeholder format for now - real cross-device sync
        // (Phase 12) will need this to round-trip through the server
        // via a future SaveManager. Present now so the Game contract
        // is genuinely implemented, not stubbed.
        return mode.name() + "|" + score;
    }

    public void loadState(String savedState)
    {
        // Not wired to anything yet - see saveState() note above.
    }
}
