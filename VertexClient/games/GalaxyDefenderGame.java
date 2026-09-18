package games;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * GalaxyDefenderGame
 * -------------------
 * An original implementation of the well-known, uncopyrightable "fixed
 * shooter" mechanic - a ship at the bottom of the screen shoots upward
 * at a grid of enemies that marches side to side and steps down each
 * time it hits an edge, picking up speed as the grid thins out. No
 * relation to any specific existing game's code, art, or enemy design.
 * Continuous pixel-space movement/collision, same overall shape as
 * BrickBreakerGame; GalaxyDefenderWindow drives tick() on a fixed-rate
 * timer and handles rendering/input. Endless (wave after wave) rather
 * than a fixed win condition, matching the arcade-score genre of the
 * other single-player games - you play until you're out of lives.
 */
public class GalaxyDefenderGame
{
    public static final int BOARD_WIDTH = 480;
    public static final int BOARD_HEIGHT = 640;

    public static final int PLAYER_WIDTH = 44;
    public static final int PLAYER_HEIGHT = 16;
    public static final int PLAYER_Y = BOARD_HEIGHT - 40;
    private static final double PLAYER_SPEED = 6.0;

    public static final int BULLET_WIDTH = 4;
    public static final int BULLET_HEIGHT = 12;
    private static final double BULLET_SPEED = 9.0;
    private static final double ENEMY_BULLET_SPEED = 5.0;
    private static final int SHOOT_COOLDOWN_TICKS = 15;
    private static final double ENEMY_FIRE_CHANCE = 0.012;

    public static final int ENEMY_ROWS = 4;
    public static final int ENEMY_COLS = 8;
    public static final int ENEMY_WIDTH = 32;
    public static final int ENEMY_HEIGHT = 22;
    private static final int ENEMY_GAP_X = 14;
    private static final int ENEMY_GAP_Y = 16;
    private static final int ENEMY_TOP_MARGIN = 60;
    private static final int GRID_WIDTH = ENEMY_COLS * ENEMY_WIDTH + (ENEMY_COLS - 1) * ENEMY_GAP_X;
    private static final int DROP_STEP = 18;

    public static final int STARTING_LIVES = 3;

    public static class Bullet
    {
        public double x, y;
        Bullet(double x, double y) { this.x = x; this.y = y; }
    }

    private final Random random = new Random();
    private final boolean[][] enemyAlive = new boolean[ENEMY_ROWS][ENEMY_COLS];
    private final List<Bullet> playerBullets = new ArrayList<Bullet>();
    private final List<Bullet> enemyBullets = new ArrayList<Bullet>();

    private double playerX;
    private boolean movingLeft, movingRight;
    private int shootCooldown;

    private double groupX, groupY;
    private double groupDirection = 1;
    private double groupSpeed;
    private int enemiesRemaining;

    private int wave = 1;
    private int score;
    private int lives = STARTING_LIVES;
    private boolean lost;

    public GalaxyDefenderGame()
    {
        playerX = (BOARD_WIDTH - PLAYER_WIDTH) / 2.0;
        spawnWave();
    }

    private void spawnWave()
    {
        for (int row = 0; row < ENEMY_ROWS; row++)
        {
            for (int col = 0; col < ENEMY_COLS; col++)
            {
                enemyAlive[row][col] = true;
            }
        }
        enemiesRemaining = ENEMY_ROWS * ENEMY_COLS;
        groupX = (BOARD_WIDTH - GRID_WIDTH) / 2.0;
        groupY = ENEMY_TOP_MARGIN;
        groupDirection = 1;
        groupSpeed = 0.8 + (wave - 1) * 0.25;
        enemyBullets.clear();
    }

    public void setMovingLeft(boolean value) { movingLeft = value; }
    public void setMovingRight(boolean value) { movingRight = value; }

    public void shoot()
    {
        if (lost || shootCooldown > 0) return;
        playerBullets.add(new Bullet(playerX + PLAYER_WIDTH / 2.0 - BULLET_WIDTH / 2.0, PLAYER_Y - BULLET_HEIGHT));
        shootCooldown = SHOOT_COOLDOWN_TICKS;
    }

    public boolean isEnemyAlive(int row, int col) { return enemyAlive[row][col]; }
    public double enemyLeft(int col) { return groupX + col * (ENEMY_WIDTH + ENEMY_GAP_X); }
    public double enemyTop(int row) { return groupY + row * (ENEMY_HEIGHT + ENEMY_GAP_Y); }
    public double getPlayerX() { return playerX; }
    public List<Bullet> getPlayerBullets() { return new ArrayList<Bullet>(playerBullets); }
    public List<Bullet> getEnemyBullets() { return new ArrayList<Bullet>(enemyBullets); }
    public int getScore() { return score; }
    public int getLives() { return lives; }
    public int getWave() { return wave; }
    public boolean isLost() { return lost; }

    private int enemyValue(int row)
    {
        return (ENEMY_ROWS - row) * 10;
    }

    public void tick()
    {
        if (lost) return;

        movePlayer();
        if (shootCooldown > 0) shootCooldown--;
        moveGroup();
        moveBullets();
        enemyFire();
        checkBulletHits();
        checkPlayerHit();
        checkGroundedInvasion();

        if (enemiesRemaining <= 0 && !lost)
        {
            wave++;
            spawnWave();
        }
    }

    private void movePlayer()
    {
        if (movingLeft) playerX -= PLAYER_SPEED;
        if (movingRight) playerX += PLAYER_SPEED;
        if (playerX < 0) playerX = 0;
        if (playerX > BOARD_WIDTH - PLAYER_WIDTH) playerX = BOARD_WIDTH - PLAYER_WIDTH;
    }

    private void moveGroup()
    {
        groupX += groupSpeed * groupDirection;
        if (groupX < 0 || groupX + GRID_WIDTH > BOARD_WIDTH)
        {
            groupX = Math.max(0, Math.min(groupX, BOARD_WIDTH - GRID_WIDTH));
            groupDirection = -groupDirection;
            groupY += DROP_STEP;
        }
    }

    private void moveBullets()
    {
        for (int i = playerBullets.size() - 1; i >= 0; i--)
        {
            Bullet b = playerBullets.get(i);
            b.y -= BULLET_SPEED;
            if (b.y + BULLET_HEIGHT < 0) playerBullets.remove(i);
        }
        for (int i = enemyBullets.size() - 1; i >= 0; i--)
        {
            Bullet b = enemyBullets.get(i);
            b.y += ENEMY_BULLET_SPEED;
            if (b.y > BOARD_HEIGHT) enemyBullets.remove(i);
        }
    }

    /** Only the front-most (lowest) alive enemy in a column may fire - matches the classic "front row shoots" feel and keeps the bullet volume sane. */
    private void enemyFire()
    {
        for (int col = 0; col < ENEMY_COLS; col++)
        {
            int frontRow = -1;
            for (int row = ENEMY_ROWS - 1; row >= 0; row--)
            {
                if (enemyAlive[row][col]) { frontRow = row; break; }
            }
            if (frontRow < 0) continue;
            if (random.nextDouble() < ENEMY_FIRE_CHANCE)
            {
                double x = enemyLeft(col) + ENEMY_WIDTH / 2.0 - BULLET_WIDTH / 2.0;
                double y = enemyTop(frontRow) + ENEMY_HEIGHT;
                enemyBullets.add(new Bullet(x, y));
            }
        }
    }

    private void checkBulletHits()
    {
        for (int i = playerBullets.size() - 1; i >= 0; i--)
        {
            Bullet b = playerBullets.get(i);
            boolean hit = false;
            for (int row = 0; row < ENEMY_ROWS && !hit; row++)
            {
                for (int col = 0; col < ENEMY_COLS; col++)
                {
                    if (!enemyAlive[row][col]) continue;
                    double left = enemyLeft(col), top = enemyTop(row);
                    boolean overlapsX = b.x + BULLET_WIDTH >= left && b.x <= left + ENEMY_WIDTH;
                    boolean overlapsY = b.y <= top + ENEMY_HEIGHT && b.y + BULLET_HEIGHT >= top;
                    if (!overlapsX || !overlapsY) continue;

                    enemyAlive[row][col] = false;
                    enemiesRemaining--;
                    score += enemyValue(row);
                    hit = true;
                    break;
                }
            }
            if (hit) playerBullets.remove(i);
        }
    }

    private void checkPlayerHit()
    {
        for (int i = enemyBullets.size() - 1; i >= 0; i--)
        {
            Bullet b = enemyBullets.get(i);
            boolean overlapsX = b.x + BULLET_WIDTH >= playerX && b.x <= playerX + PLAYER_WIDTH;
            boolean overlapsY = b.y + BULLET_HEIGHT >= PLAYER_Y && b.y <= PLAYER_Y + PLAYER_HEIGHT;
            if (overlapsX && overlapsY)
            {
                enemyBullets.remove(i);
                loseLife();
                if (lost) return;
            }
        }
    }

    /** If any surviving enemy's row reaches the player's line, that's an instant loss - same "they got through" fail condition the genre always uses. */
    private void checkGroundedInvasion()
    {
        for (int row = 0; row < ENEMY_ROWS; row++)
        {
            for (int col = 0; col < ENEMY_COLS; col++)
            {
                if (enemyAlive[row][col] && enemyTop(row) + ENEMY_HEIGHT >= PLAYER_Y)
                {
                    lives = 0;
                    lost = true;
                    return;
                }
            }
        }
    }

    private void loseLife()
    {
        lives--;
        if (lives <= 0)
        {
            lost = true;
        }
        else
        {
            playerX = (BOARD_WIDTH - PLAYER_WIDTH) / 2.0;
            playerBullets.clear();
        }
    }
}
