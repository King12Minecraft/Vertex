package games;

/**
 * BrickBreakerGame
 * ----------------
 * An original implementation of the well-known, uncopyrightable
 * "bounce a ball off a paddle to smash a wall of bricks" mechanic -
 * no relation to any specific existing game's code, art, or brick
 * layout. Continuous pixel-space physics (not grid-based like Snake/
 * Maze Chase) since a bouncing ball needs real sub-cell movement to
 * feel right; BrickBreakerWindow drives tick() on a fixed-rate timer
 * and handles rendering/input.
 */
public class BrickBreakerGame
{
    public static final int BOARD_WIDTH = 560;
    public static final int BOARD_HEIGHT = 640;

    public static final int PADDLE_WIDTH = 80;
    public static final int PADDLE_HEIGHT = 12;
    public static final int PADDLE_Y = BOARD_HEIGHT - 40;
    private static final double PADDLE_SPEED = 7.0;

    public static final int BALL_RADIUS = 7;
    private static final double BASE_BALL_SPEED = 4.6;

    public static final int ROWS = 6;
    public static final int COLS = 10;
    public static final int BRICK_WIDTH = 52;
    public static final int BRICK_HEIGHT = 18;
    private static final int BRICK_GAP = 3;
    private static final int BRICK_TOP_MARGIN = 50;
    private static final int BRICK_SIDE_MARGIN =
        (BOARD_WIDTH - (COLS * BRICK_WIDTH + (COLS - 1) * BRICK_GAP)) / 2;

    public static final int STARTING_LIVES = 3;

    private final boolean[][] brickAlive = new boolean[ROWS][COLS];
    private int bricksRemaining;

    private double paddleX;
    private boolean movingLeft, movingRight;

    private double ballX, ballY, ballVx, ballVy;
    private boolean ballInPlay;

    private int score;
    private int lives = STARTING_LIVES;
    private boolean won;
    private boolean lost;

    public BrickBreakerGame()
    {
        for (int row = 0; row < ROWS; row++)
        {
            for (int col = 0; col < COLS; col++)
            {
                brickAlive[row][col] = true;
            }
        }
        bricksRemaining = ROWS * COLS;

        paddleX = (BOARD_WIDTH - PADDLE_WIDTH) / 2.0;
        launchBall();
    }

    private void launchBall()
    {
        ballX = BOARD_WIDTH / 2.0;
        ballY = PADDLE_Y - BALL_RADIUS - 1;
        double angle = Math.toRadians(60 + Math.random() * 60); // somewhere between 60 and 120 degrees off the horizontal
        ballVx = BASE_BALL_SPEED * Math.cos(angle) * (Math.random() < 0.5 ? -1 : 1);
        ballVy = -BASE_BALL_SPEED * Math.sin(angle);
        ballInPlay = true;
    }

    public void setMovingLeft(boolean value) { movingLeft = value; }
    public void setMovingRight(boolean value) { movingRight = value; }

    public boolean isBrickAlive(int row, int col) { return brickAlive[row][col]; }
    public double getPaddleX() { return paddleX; }
    public double getBallX() { return ballX; }
    public double getBallY() { return ballY; }
    public int getScore() { return score; }
    public int getLives() { return lives; }
    public boolean isWon() { return won; }
    public boolean isLost() { return lost; }
    public boolean isOver() { return won || lost; }

    public static int brickLeft(int col) { return BRICK_SIDE_MARGIN + col * (BRICK_WIDTH + BRICK_GAP); }
    public static int brickTop(int row) { return BRICK_TOP_MARGIN + row * (BRICK_HEIGHT + BRICK_GAP); }

    /** A brick's point value increases toward the top row - the classic "further/harder to reach rows are worth more" convention. */
    private int brickValue(int row)
    {
        return (ROWS - row) * 5;
    }

    public void tick()
    {
        if (isOver()) return;

        movePaddle();
        moveBall();
    }

    private void movePaddle()
    {
        if (movingLeft) paddleX -= PADDLE_SPEED;
        if (movingRight) paddleX += PADDLE_SPEED;
        if (paddleX < 0) paddleX = 0;
        if (paddleX > BOARD_WIDTH - PADDLE_WIDTH) paddleX = BOARD_WIDTH - PADDLE_WIDTH;
    }

    private void moveBall()
    {
        if (!ballInPlay) return;

        ballX += ballVx;
        ballY += ballVy;

        if (ballX - BALL_RADIUS < 0)
        {
            ballX = BALL_RADIUS;
            ballVx = -ballVx;
        }
        else if (ballX + BALL_RADIUS > BOARD_WIDTH)
        {
            ballX = BOARD_WIDTH - BALL_RADIUS;
            ballVx = -ballVx;
        }
        if (ballY - BALL_RADIUS < 0)
        {
            ballY = BALL_RADIUS;
            ballVy = -ballVy;
        }

        checkPaddleCollision();
        checkBrickCollision();

        if (ballY - BALL_RADIUS > BOARD_HEIGHT)
        {
            loseLife();
        }
    }

    private void checkPaddleCollision()
    {
        if (ballVy <= 0) return; // only care while heading downward, toward the paddle
        boolean overlapsY = ballY + BALL_RADIUS >= PADDLE_Y && ballY - BALL_RADIUS <= PADDLE_Y + PADDLE_HEIGHT;
        boolean overlapsX = ballX + BALL_RADIUS >= paddleX && ballX - BALL_RADIUS <= paddleX + PADDLE_WIDTH;
        if (!overlapsY || !overlapsX) return;

        ballY = PADDLE_Y - BALL_RADIUS;
        ballVy = -Math.abs(ballVy);

        // Hitting off-center adds some horizontal steer, same "aim the bounce" feel a real paddle game has.
        double paddleCenter = paddleX + PADDLE_WIDTH / 2.0;
        double offset = (ballX - paddleCenter) / (PADDLE_WIDTH / 2.0);
        ballVx += offset * 1.5;
        double speed = Math.sqrt(ballVx * ballVx + ballVy * ballVy);
        double maxSpeed = BASE_BALL_SPEED * 1.6;
        if (speed > maxSpeed)
        {
            ballVx = ballVx / speed * maxSpeed;
            ballVy = ballVy / speed * maxSpeed;
        }
    }

    /** Only one brick is ever broken per tick - the ball moves a few pixels at a time, so hitting two bricks in the same tick essentially never happens, and this keeps the collision response simple (one bounce, not an averaged/ambiguous one). */
    private void checkBrickCollision()
    {
        for (int row = 0; row < ROWS; row++)
        {
            for (int col = 0; col < COLS; col++)
            {
                if (!brickAlive[row][col]) continue;

                int left = brickLeft(col), top = brickTop(row);
                int right = left + BRICK_WIDTH, bottom = top + BRICK_HEIGHT;

                boolean overlapsX = ballX + BALL_RADIUS >= left && ballX - BALL_RADIUS <= right;
                boolean overlapsY = ballY + BALL_RADIUS >= top && ballY - BALL_RADIUS <= bottom;
                if (!overlapsX || !overlapsY) continue;

                brickAlive[row][col] = false;
                bricksRemaining--;
                score += brickValue(row);

                // Bounce vertically if the ball is clearly above/below the brick, horizontally if clearly beside it -
                // otherwise (a corner hit) default to a vertical bounce, the common case by far.
                double overlapLeft = (ballX + BALL_RADIUS) - left;
                double overlapRight = right - (ballX - BALL_RADIUS);
                double overlapTop = (ballY + BALL_RADIUS) - top;
                double overlapBottom = bottom - (ballY - BALL_RADIUS);
                double minX = Math.min(overlapLeft, overlapRight);
                double minY = Math.min(overlapTop, overlapBottom);

                if (minY <= minX)
                {
                    ballVy = -ballVy;
                }
                else
                {
                    ballVx = -ballVx;
                }

                if (bricksRemaining <= 0)
                {
                    won = true;
                }
                return;
            }
        }
    }

    private void loseLife()
    {
        lives--;
        if (lives <= 0)
        {
            lost = true;
            ballInPlay = false;
            return;
        }
        paddleX = (BOARD_WIDTH - PADDLE_WIDTH) / 2.0;
        launchBall();
    }
}
