import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * ZombieSurvivalGame
 * ------------------
 * The model for Zombie Survival - a top-down wave shooter. Same
 * "independent local simulation, shared seed" approach as Racing
 * (see RacingGame/RacingMatch): every player in an online match gets
 * the identical zombie spawn sequence and fights it out in their own
 * window, then the final result (survived all waves, or died on
 * wave N with however many zombies killed) is what gets compared and
 * rewarded - no need to broadcast live positions between clients
 * every frame for what is otherwise a fully deterministic simulation
 * once movement/aim input is applied locally.
 *
 * Movement is WASD (continuous, held-key), aiming/firing follows the
 * mouse - see ZombieSurvivalPanel for input wiring.
 */
public class ZombieSurvivalGame
{
    public static final int BOARD_WIDTH = 700;
    public static final int BOARD_HEIGHT = 500;
    public static final int WAVE_COUNT = 8;

    private static final double PLAYER_RADIUS = 14;
    private static final double PLAYER_SPEED = 3.6;
    private static final int PLAYER_MAX_HP = 100;
    private static final int INVULN_TICKS_AFTER_HIT = 25;

    private static final double BULLET_RADIUS = 4;
    private static final double BULLET_SPEED = 9.5;
    private static final int BULLET_DAMAGE = 20;
    private static final int FIRE_COOLDOWN_TICKS = 11;

    public static final int TYPE_NORMAL = 0;
    public static final int TYPE_FAST = 1;
    public static final int TYPE_TANK = 2;

    public static class Zombie
    {
        public double x, y;
        public int hp;
        public final int maxHp;
        public final double speed;
        public final int contactDamage;
        public final int type;

        Zombie(double x, double y, int hp, double speed, int contactDamage, int type)
        {
            this.x = x;
            this.y = y;
            this.hp = hp;
            this.maxHp = hp;
            this.speed = speed;
            this.contactDamage = contactDamage;
            this.type = type;
        }
    }

    private static class Bullet
    {
        double x, y, dx, dy;
        Bullet(double x, double y, double dx, double dy) { this.x = x; this.y = y; this.dx = dx; this.dy = dy; }
    }

    private final Random random;
    private final List<Zombie> zombies = new ArrayList<Zombie>();
    private final List<Bullet> bullets = new ArrayList<Bullet>();

    private double playerX = BOARD_WIDTH / 2.0;
    private double playerY = BOARD_HEIGHT / 2.0;
    private int playerHp = PLAYER_MAX_HP;
    private int invulnTicks = 0;

    private boolean up, down, left, right;
    private double aimX = BOARD_WIDTH / 2.0;
    private double aimY = 0;
    private boolean firing;
    private int fireCooldown = 0;

    private int wave = 0;
    private int zombiesToSpawnThisWave = 0;
    private int spawnTimer = 0;
    private int score = 0;
    private int zombiesKilled = 0;

    private boolean gameOver = false;
    private boolean won = false;

    /** Practice mode - unseeded, every playthrough is different. */
    public ZombieSurvivalGame()
    {
        this.random = new Random();
        startNextWave();
    }

    /** Online mode - every player gets the identical spawn sequence from the same seed, so comparing final results (waves cleared, zombies killed) stays a fair comparison without live position sync. */
    public ZombieSurvivalGame(long seed)
    {
        this.random = new Random(seed);
        startNextWave();
    }

    public void setInput(boolean up, boolean down, boolean left, boolean right)
    {
        this.up = up;
        this.down = down;
        this.left = left;
        this.right = right;
    }

    public void setAim(double targetX, double targetY)
    {
        this.aimX = targetX;
        this.aimY = targetY;
    }

    public void setFiring(boolean firing)
    {
        this.firing = firing;
    }

    private void startNextWave()
    {
        wave++;
        zombiesToSpawnThisWave = 3 + wave * 2;
        spawnTimer = 0;
    }

    public void tick()
    {
        if (gameOver || won)
        {
            return;
        }

        moveplayer();
        if (invulnTicks > 0)
        {
            invulnTicks--;
        }

        handleFiring();
        moveBullets();
        spawnZombies();
        moveZombiesAndResolveContact();
        resolveBulletHits();

        if (zombiesToSpawnThisWave == 0 && zombies.isEmpty())
        {
            if (wave >= WAVE_COUNT)
            {
                won = true;
            }
            else
            {
                startNextWave();
            }
        }
    }

    private void moveplayer()
    {
        double dx = 0, dy = 0;
        if (up) dy -= 1;
        if (down) dy += 1;
        if (left) dx -= 1;
        if (right) dx += 1;

        if (dx != 0 || dy != 0)
        {
            double len = Math.sqrt(dx * dx + dy * dy);
            playerX += (dx / len) * PLAYER_SPEED;
            playerY += (dy / len) * PLAYER_SPEED;
        }

        playerX = clamp(playerX, PLAYER_RADIUS, BOARD_WIDTH - PLAYER_RADIUS);
        playerY = clamp(playerY, PLAYER_RADIUS, BOARD_HEIGHT - PLAYER_RADIUS);
    }

    private void handleFiring()
    {
        if (fireCooldown > 0)
        {
            fireCooldown--;
        }
        if (firing && fireCooldown == 0)
        {
            double dx = aimX - playerX;
            double dy = aimY - playerY;
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 0.001) len = 1;
            bullets.add(new Bullet(playerX, playerY, (dx / len) * BULLET_SPEED, (dy / len) * BULLET_SPEED));
            fireCooldown = FIRE_COOLDOWN_TICKS;
        }
    }

    private void moveBullets()
    {
        Iterator<Bullet> it = bullets.iterator();
        while (it.hasNext())
        {
            Bullet b = it.next();
            b.x += b.dx;
            b.y += b.dy;
            if (b.x < -10 || b.x > BOARD_WIDTH + 10 || b.y < -10 || b.y > BOARD_HEIGHT + 10)
            {
                it.remove();
            }
        }
    }

    /** Spawns one zombie every ~35 ticks (faster on later waves) from a random point along the arena edge, until this wave's quota is used up. */
    private void spawnZombies()
    {
        if (zombiesToSpawnThisWave <= 0)
        {
            return;
        }
        spawnTimer--;
        if (spawnTimer > 0)
        {
            return;
        }
        spawnTimer = Math.max(14, 35 - wave);

        double[] pos = randomEdgePosition();
        int type = randomZombieType();
        zombies.add(makeZombie(pos[0], pos[1], type));
        zombiesToSpawnThisWave--;
    }

    private double[] randomEdgePosition()
    {
        int edge = random.nextInt(4);
        if (edge == 0) return new double[] { random.nextDouble() * BOARD_WIDTH, -20 };
        if (edge == 1) return new double[] { random.nextDouble() * BOARD_WIDTH, BOARD_HEIGHT + 20 };
        if (edge == 2) return new double[] { -20, random.nextDouble() * BOARD_HEIGHT };
        return new double[] { BOARD_WIDTH + 20, random.nextDouble() * BOARD_HEIGHT };
    }

    /** Tougher zombie mix as waves progress - purely a spawn-weight change, same three types throughout. */
    private int randomZombieType()
    {
        int roll = random.nextInt(100);
        int fastChance = Math.min(40, 10 + wave * 3);
        int tankChance = Math.min(30, wave * 3);
        if (roll < tankChance) return TYPE_TANK;
        if (roll < tankChance + fastChance) return TYPE_FAST;
        return TYPE_NORMAL;
    }

    private Zombie makeZombie(double x, double y, int type)
    {
        if (type == TYPE_FAST) return new Zombie(x, y, 30, 2.4, 6, TYPE_FAST);
        if (type == TYPE_TANK) return new Zombie(x, y, 90, 1.2, 16, TYPE_TANK);
        return new Zombie(x, y, 50, 1.6, 10, TYPE_NORMAL);
    }

    private void moveZombiesAndResolveContact()
    {
        for (int i = 0; i < zombies.size(); i++)
        {
            Zombie z = zombies.get(i);
            double dx = playerX - z.x;
            double dy = playerY - z.y;
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len > 0.001)
            {
                z.x += (dx / len) * z.speed;
                z.y += (dy / len) * z.speed;
            }

            double contactDistance = PLAYER_RADIUS + 12;
            if (len < contactDistance && invulnTicks == 0)
            {
                playerHp -= z.contactDamage;
                invulnTicks = INVULN_TICKS_AFTER_HIT;
                if (playerHp <= 0)
                {
                    playerHp = 0;
                    gameOver = true;
                }
            }
        }
    }

    private void resolveBulletHits()
    {
        Iterator<Bullet> bulletIt = bullets.iterator();
        while (bulletIt.hasNext())
        {
            Bullet b = bulletIt.next();
            boolean consumed = false;

            for (int i = 0; i < zombies.size() && !consumed; i++)
            {
                Zombie z = zombies.get(i);
                double dx = b.x - z.x;
                double dy = b.y - z.y;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < BULLET_RADIUS + 13)
                {
                    z.hp -= BULLET_DAMAGE;
                    consumed = true;
                    if (z.hp <= 0)
                    {
                        zombies.remove(i);
                        zombiesKilled++;
                        score += scoreValueFor(z.type);
                    }
                }
            }

            if (consumed)
            {
                bulletIt.remove();
            }
        }
    }

    private int scoreValueFor(int type)
    {
        if (type == TYPE_FAST) return 15;
        if (type == TYPE_TANK) return 25;
        return 10;
    }

    private double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }

    public double getPlayerX() { return playerX; }
    public double getPlayerY() { return playerY; }
    public int getPlayerHp() { return playerHp; }
    public int getPlayerMaxHp() { return PLAYER_MAX_HP; }
    public boolean isInvulnerable() { return invulnTicks > 0; }
    public List<Zombie> getZombies() { return zombies; }
    public int getWave() { return Math.min(wave, WAVE_COUNT); }
    public int getScore() { return score; }
    public int getZombiesKilled() { return zombiesKilled; }
    public boolean isGameOver() { return gameOver; }
    public boolean isWon() { return won; }
    public boolean isOver() { return gameOver || won; }

    /** For rendering only - a snapshot list of {x, y} bullet positions. */
    public List<double[]> getBulletPositions()
    {
        List<double[]> positions = new ArrayList<double[]>();
        for (int i = 0; i < bullets.size(); i++)
        {
            Bullet b = bullets.get(i);
            positions.add(new double[] { b.x, b.y });
        }
        return positions;
    }
}
