import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * SpaceBattleGame
 * ---------------
 * The model for Space Battle - a fixed-length arcade dogfight against
 * drifting asteroids and enemy fighters. Same "independent local
 * simulation, shared seed" approach as Racing (see RacingGame): every
 * pilot in an online match gets the identical asteroid/enemy spawn
 * sequence over the same MATCH_FRAMES window, then final scores are
 * compared for 1st/2nd/3rd placement - no live position sync needed
 * between clients for what's otherwise fully deterministic once local
 * input (rotation, thrust, firing) is applied.
 *
 * Controls are thrust-and-rotate (classic arcade dogfight feel)
 * rather than Zombie Survival's direct WASD movement - turn with
 * A/D or Left/Right, thrust with W/Up, fire with Space or the mouse
 * button.
 */
public class SpaceBattleGame
{
    public static final int BOARD_WIDTH = 700;
    public static final int BOARD_HEIGHT = 500;
    /** ~30 seconds at the 16ms tick rate SpaceBattlePanel runs at - the shared time limit for every pilot, whether Practice or Online. */
    public static final int MATCH_FRAMES = 1800;

    private static final double SHIP_RADIUS = 14;
    private static final double TURN_RATE = 0.08;
    private static final double THRUST_POWER = 0.22;
    private static final double DRAG = 0.988;
    private static final double MAX_SPEED = 6.0;
    private static final int PLAYER_MAX_HP = 100;
    private static final int INVULN_TICKS_AFTER_HIT = 30;

    private static final double BULLET_SPEED = 10.0;
    private static final int BULLET_DAMAGE = 20;
    private static final int FIRE_COOLDOWN_TICKS = 10;

    public static final int ENTITY_ASTEROID = 0;
    public static final int ENTITY_ENEMY = 1;

    public static class Entity
    {
        public double x, y, dx, dy;
        public int hp;
        public final int maxHp;
        public final int type;
        public double angle;

        Entity(double x, double y, double dx, double dy, int hp, int type)
        {
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            this.hp = hp;
            this.maxHp = hp;
            this.type = type;
        }
    }

    private static class Bullet
    {
        double x, y, dx, dy;
        boolean fromPlayer;
        Bullet(double x, double y, double dx, double dy, boolean fromPlayer)
        {
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            this.fromPlayer = fromPlayer;
        }
    }

    private final Random random;
    private final List<Entity> entities = new ArrayList<Entity>();
    private final List<Bullet> bullets = new ArrayList<Bullet>();

    private double shipX = BOARD_WIDTH / 2.0;
    private double shipY = BOARD_HEIGHT / 2.0;
    private double shipDx = 0;
    private double shipDy = 0;
    private double shipAngle = -Math.PI / 2;
    private int playerHp = PLAYER_MAX_HP;
    private int invulnTicks = 0;

    private boolean turnLeft, turnRight, thrusting, firing;
    private int fireCooldown = 0;
    private int enemyFireTimer = 90;

    private int frameCount = 0;
    private int score = 0;
    private boolean gameOver = false;
    private boolean timeUp = false;

    /** Practice mode - unseeded, every playthrough is different. */
    public SpaceBattleGame()
    {
        this.random = new Random();
    }

    /** Online mode - every pilot gets the identical asteroid/enemy sequence from the same seed, so comparing final scores stays a fair comparison without needing to broadcast live positions between clients. */
    public SpaceBattleGame(long seed)
    {
        this.random = new Random(seed);
    }

    public void setControls(boolean turnLeft, boolean turnRight, boolean thrusting)
    {
        this.turnLeft = turnLeft;
        this.turnRight = turnRight;
        this.thrusting = thrusting;
    }

    public void setFiring(boolean firing)
    {
        this.firing = firing;
    }

    public void tick()
    {
        if (isOver())
        {
            return;
        }
        frameCount++;
        if (frameCount >= MATCH_FRAMES)
        {
            timeUp = true;
            return;
        }

        moveShip();
        if (invulnTicks > 0) invulnTicks--;

        handleFiring();
        moveBullets();
        spawnEntities();
        moveEntities();
        handleEnemyFire();
        resolveBulletHits();
        resolveShipContact();
    }

    private void moveShip()
    {
        if (turnLeft) shipAngle -= TURN_RATE;
        if (turnRight) shipAngle += TURN_RATE;

        if (thrusting)
        {
            shipDx += Math.cos(shipAngle) * THRUST_POWER;
            shipDy += Math.sin(shipAngle) * THRUST_POWER;
        }

        shipDx *= DRAG;
        shipDy *= DRAG;

        double speed = Math.sqrt(shipDx * shipDx + shipDy * shipDy);
        if (speed > MAX_SPEED)
        {
            shipDx = shipDx / speed * MAX_SPEED;
            shipDy = shipDy / speed * MAX_SPEED;
        }

        shipX += shipDx;
        shipY += shipDy;

        // Wrap around the edges, classic arcade dogfight style.
        if (shipX < 0) shipX += BOARD_WIDTH;
        if (shipX > BOARD_WIDTH) shipX -= BOARD_WIDTH;
        if (shipY < 0) shipY += BOARD_HEIGHT;
        if (shipY > BOARD_HEIGHT) shipY -= BOARD_HEIGHT;
    }

    private void handleFiring()
    {
        if (fireCooldown > 0) fireCooldown--;
        if (firing && fireCooldown == 0)
        {
            double dx = Math.cos(shipAngle) * BULLET_SPEED;
            double dy = Math.sin(shipAngle) * BULLET_SPEED;
            bullets.add(new Bullet(shipX, shipY, dx, dy, true));
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
            if (b.x < -20 || b.x > BOARD_WIDTH + 20 || b.y < -20 || b.y > BOARD_HEIGHT + 20)
            {
                it.remove();
            }
        }
    }

    /** Spawns an asteroid or enemy fighter every ~55 ticks (faster as the match goes on) from a random edge point, drifting inward. */
    private int spawnTimer = 0;

    private void spawnEntities()
    {
        spawnTimer--;
        if (spawnTimer > 0) return;
        spawnTimer = Math.max(20, 55 - frameCount / 60);

        double[] pos = randomEdgePosition();
        boolean isEnemy = random.nextInt(100) < Math.min(45, 15 + frameCount / 100);
        double angleToward = Math.atan2(BOARD_HEIGHT / 2.0 - pos[1], BOARD_WIDTH / 2.0 - pos[0])
            + (random.nextDouble() - 0.5);
        double speed = isEnemy ? 1.4 : 1.0 + random.nextDouble() * 1.2;

        Entity e = new Entity(pos[0], pos[1], Math.cos(angleToward) * speed, Math.sin(angleToward) * speed,
            isEnemy ? 30 : 40, isEnemy ? ENTITY_ENEMY : ENTITY_ASTEROID);
        entities.add(e);
    }

    private double[] randomEdgePosition()
    {
        int edge = random.nextInt(4);
        if (edge == 0) return new double[] { random.nextDouble() * BOARD_WIDTH, -20 };
        if (edge == 1) return new double[] { random.nextDouble() * BOARD_WIDTH, BOARD_HEIGHT + 20 };
        if (edge == 2) return new double[] { -20, random.nextDouble() * BOARD_HEIGHT };
        return new double[] { BOARD_WIDTH + 20, random.nextDouble() * BOARD_HEIGHT };
    }

    private void moveEntities()
    {
        Iterator<Entity> it = entities.iterator();
        while (it.hasNext())
        {
            Entity e = it.next();
            e.x += e.dx;
            e.y += e.dy;
            if (e.x < -60 || e.x > BOARD_WIDTH + 60 || e.y < -60 || e.y > BOARD_HEIGHT + 60)
            {
                it.remove();
            }
        }
    }

    private void handleEnemyFire()
    {
        enemyFireTimer--;
        if (enemyFireTimer > 0) return;
        enemyFireTimer = 90;

        for (int i = 0; i < entities.size(); i++)
        {
            Entity e = entities.get(i);
            if (e.type != ENTITY_ENEMY) continue;
            double dx = shipX - e.x;
            double dy = shipY - e.y;
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 0.001) len = 1;
            bullets.add(new Bullet(e.x, e.y, (dx / len) * (BULLET_SPEED * 0.6), (dy / len) * (BULLET_SPEED * 0.6), false));
        }
    }

    private void resolveBulletHits()
    {
        Iterator<Bullet> bulletIt = bullets.iterator();
        while (bulletIt.hasNext())
        {
            Bullet b = bulletIt.next();

            if (b.fromPlayer)
            {
                boolean consumed = false;
                for (int i = 0; i < entities.size() && !consumed; i++)
                {
                    Entity e = entities.get(i);
                    if (distance(b.x, b.y, e.x, e.y) < 16)
                    {
                        e.hp -= BULLET_DAMAGE;
                        consumed = true;
                        if (e.hp <= 0)
                        {
                            entities.remove(i);
                            score += e.type == ENTITY_ENEMY ? 25 : 10;
                        }
                    }
                }
                if (consumed) bulletIt.remove();
            }
            else if (invulnTicks == 0 && distance(b.x, b.y, shipX, shipY) < SHIP_RADIUS + 3)
            {
                applyDamage(12);
                bulletIt.remove();
            }
        }
    }

    private void resolveShipContact()
    {
        if (invulnTicks > 0) return;
        for (int i = 0; i < entities.size(); i++)
        {
            Entity e = entities.get(i);
            if (distance(shipX, shipY, e.x, e.y) < SHIP_RADIUS + 14)
            {
                applyDamage(e.type == ENTITY_ENEMY ? 18 : 14);
                return;
            }
        }
    }

    private void applyDamage(int amount)
    {
        playerHp -= amount;
        invulnTicks = INVULN_TICKS_AFTER_HIT;
        if (playerHp <= 0)
        {
            playerHp = 0;
            gameOver = true;
        }
    }

    private double distance(double x1, double y1, double x2, double y2)
    {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public double getShipX() { return shipX; }
    public double getShipY() { return shipY; }
    public double getShipAngle() { return shipAngle; }
    public int getPlayerHp() { return playerHp; }
    public int getPlayerMaxHp() { return PLAYER_MAX_HP; }
    public boolean isInvulnerable() { return invulnTicks > 0; }
    public List<Entity> getEntities() { return entities; }
    public int getScore() { return score; }
    public int getFrameCount() { return frameCount; }
    public boolean isGameOver() { return gameOver; }
    public boolean isTimeUp() { return timeUp; }
    public boolean isOver() { return gameOver || timeUp; }

    /** For rendering only - a snapshot list of {x, y, fromPlayer(1/0)} bullet positions. */
    public List<double[]> getBulletPositions()
    {
        List<double[]> positions = new ArrayList<double[]>();
        for (int i = 0; i < bullets.size(); i++)
        {
            Bullet b = bullets.get(i);
            positions.add(new double[] { b.x, b.y, b.fromPlayer ? 1 : 0 });
        }
        return positions;
    }
}
