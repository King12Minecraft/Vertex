/**
 * PongGame
 * --------
 * Model for Ping Pong vs AI - ball position/velocity, player paddle
 * (moved by the player), AI paddle (follows the ball with a capped
 * speed so it's beatable). First to WINNING_SCORE wins.
 */
public class PongGame
{
    public static final int WIDTH = 480;
    public static final int HEIGHT = 320;
    public static final int PADDLE_HEIGHT = 70;
    public static final int PADDLE_WIDTH = 10;
    private static final int WINNING_SCORE = 5;
    private static final double AI_SPEED = 4.0;

    private double ballX = WIDTH / 2.0;
    private double ballY = HEIGHT / 2.0;
    private double ballVelX = 4.0;
    private double ballVelY = 3.0;

    private double playerY = HEIGHT / 2.0 - PADDLE_HEIGHT / 2.0;
    private double aiY = HEIGHT / 2.0 - PADDLE_HEIGHT / 2.0;

    private int playerScore = 0;
    private int aiScore = 0;
    private boolean gameOver = false;

    public void movePlayer(int deltaY)
    {
        playerY += deltaY;
        if (playerY < 0) playerY = 0;
        if (playerY > HEIGHT - PADDLE_HEIGHT) playerY = HEIGHT - PADDLE_HEIGHT;
    }

    public void tick()
    {
        if (gameOver)
        {
            return;
        }

        ballX += ballVelX;
        ballY += ballVelY;

        if (ballY <= 0 || ballY >= HEIGHT)
        {
            ballVelY = -ballVelY;
        }

        double aiCenter = aiY + PADDLE_HEIGHT / 2.0;
        if (aiCenter < ballY - 6) aiY += AI_SPEED;
        else if (aiCenter > ballY + 6) aiY -= AI_SPEED;
        if (aiY < 0) aiY = 0;
        if (aiY > HEIGHT - PADDLE_HEIGHT) aiY = HEIGHT - PADDLE_HEIGHT;

        if (ballX <= PADDLE_WIDTH + 4 && ballY >= playerY && ballY <= playerY + PADDLE_HEIGHT && ballVelX < 0)
        {
            ballVelX = -ballVelX;
            ballVelY += (ballY - (playerY + PADDLE_HEIGHT / 2.0)) * 0.08;
        }
        if (ballX >= WIDTH - PADDLE_WIDTH - 4 && ballY >= aiY && ballY <= aiY + PADDLE_HEIGHT && ballVelX > 0)
        {
            ballVelX = -ballVelX;
        }

        if (ballX < 0)
        {
            aiScore++;
            resetBall();
        }
        else if (ballX > WIDTH)
        {
            playerScore++;
            resetBall();
        }

        if (playerScore >= WINNING_SCORE || aiScore >= WINNING_SCORE)
        {
            gameOver = true;
        }
    }

    private void resetBall()
    {
        ballX = WIDTH / 2.0;
        ballY = HEIGHT / 2.0;
        ballVelX = -ballVelX;
        ballVelY = 3.0;
    }

    public int getBallX() { return (int) ballX; }
    public int getBallY() { return (int) ballY; }
    public int getPlayerY() { return (int) playerY; }
    public int getAiY() { return (int) aiY; }
    public int getPlayerScore() { return playerScore; }
    public int getAiScore() { return aiScore; }
    public boolean isGameOver() { return gameOver; }
}
