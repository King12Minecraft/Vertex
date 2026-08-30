import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * SnakePanel
 * ----------
 * Renders SnakeGame and handles keyboard input (arrows or WASD).
 * Fully theme-aware. Movement is smoothly interpolated between grid
 * steps rather than snapping instantly - a fast render timer (~60fps)
 * runs independently of the slower game-logic timer, and paintComponent
 * blends each snake segment's position between where it was and where
 * it's going based on elapsed time since the last logic tick. A subtle
 * pulsing glow sits behind the food for extra polish.
 */
public class SnakePanel extends JPanel
{
    private static final int CELL_SIZE = 22;
    private static final int SCORE_BAR_HEIGHT = 34;
    private static final int RENDER_TICK_MS = 16;

    private final SnakeGame game;
    private final Timer logicTimer;
    private final Timer renderTimer;
    private final Runnable onGameOver;
    private final long startTime = System.currentTimeMillis();

    private List<Point> previousBody;
    private List<Point> currentBody;
    private long lastTickTime;
    private int lastTickInterval;
    private int lastScore = 0;
    private long lastEatTime = 0;
    private static final int EAT_FLASH_MS = 300;

    public SnakePanel(SnakeGame game, Runnable onGameOver)
    {
        this.game = game;
        this.onGameOver = onGameOver;

        int width = SnakeGame.GRID_WIDTH * CELL_SIZE;
        int height = SnakeGame.GRID_HEIGHT * CELL_SIZE + SCORE_BAR_HEIGHT;
        setPreferredSize(new Dimension(width, height));
        setFocusable(true);

        currentBody = game.getSnakeBody();
        previousBody = currentBody;
        lastTickTime = System.currentTimeMillis();
        lastTickInterval = game.getTickIntervalMillis();

        bindKeys();

        logicTimer = new Timer(game.getTickIntervalMillis(), new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                previousBody = currentBody;
                game.tick();
                currentBody = game.getSnakeBody();
                lastTickTime = System.currentTimeMillis();
                lastTickInterval = game.getTickIntervalMillis();
                logicTimer.setDelay(lastTickInterval);

                if (game.getScore() > lastScore)
                {
                    lastScore = game.getScore();
                    lastEatTime = System.currentTimeMillis();
                }

                if (game.isGameOver())
                {
                    logicTimer.stop();
                    renderTimer.stop();
                    onGameOver.run();
                }
            }
        });

        renderTimer = new Timer(RENDER_TICK_MS, new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { repaint(); }
        });

        ThemeManager.addListener(new Runnable()
        {
            public void run() { repaint(); }
        });
    }

    public void startTimer()
    {
        logicTimer.start();
        renderTimer.start();
    }

    public void stopTimer()
    {
        logicTimer.stop();
        renderTimer.stop();
    }

    private void bindKeys()
    {
        bindKey("UP", SnakeGame.Direction.UP);
        bindKey("DOWN", SnakeGame.Direction.DOWN);
        bindKey("LEFT", SnakeGame.Direction.LEFT);
        bindKey("RIGHT", SnakeGame.Direction.RIGHT);
        bindKey("W", SnakeGame.Direction.UP);
        bindKey("S", SnakeGame.Direction.DOWN);
        bindKey("A", SnakeGame.Direction.LEFT);
        bindKey("D", SnakeGame.Direction.RIGHT);
    }

    private void bindKey(String keyName, final SnakeGame.Direction direction)
    {
        getInputMap().put(KeyStroke.getKeyStroke(keyName), keyName + "_action");
        getActionMap().put(keyName + "_action", new AbstractAction()
        {
            public void actionPerformed(ActionEvent e)
            {
                game.setPendingDirection(direction);
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        g2.setColor(ThemeManager.getColor(ThemeColor.BG_SIDEBAR));
        g2.fillRect(0, 0, getWidth(), SCORE_BAR_HEIGHT);

        g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        g2.setFont(UITheme.FONT_NAV_BOLD);
        g2.drawString("Score: " + game.getScore(), 10, SCORE_BAR_HEIGHT - 11);

        g2.setColor(ThemeManager.getColor(ThemeColor.BG_PANEL));
        g2.fillRect(0, SCORE_BAR_HEIGHT, getWidth(), getHeight() - SCORE_BAR_HEIGHT);

        paintFood(g2);
        paintSnake(g2);
        paintEatFlash(g2);

        g2.dispose();
    }

    /** Brief fading accent-colored overlay across the board when food is eaten - purely cosmetic feedback. */
    private void paintEatFlash(Graphics2D g2)
    {
        long age = System.currentTimeMillis() - lastEatTime;
        if (age >= EAT_FLASH_MS)
        {
            return;
        }
        float fade = 1f - (age / (float) EAT_FLASH_MS);
        Color accent = ThemeManager.getColor(ThemeColor.SUCCESS);
        int alpha = (int) (55 * fade);
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), alpha));
        g2.fillRect(0, SCORE_BAR_HEIGHT, getWidth(), getHeight() - SCORE_BAR_HEIGHT);
    }

    private void paintFood(Graphics2D g2)
    {
        Point food = game.getFood();
        Color foodColor = ThemeManager.getColor(ThemeColor.SUCCESS);

        double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
        float pulse = (float) (0.5 + 0.5 * Math.sin(elapsed * 4));
        int glowPad = (int) (3 + 3 * pulse);

        int fx = food.x * CELL_SIZE;
        int fy = SCORE_BAR_HEIGHT + food.y * CELL_SIZE;

        g2.setColor(new Color(foodColor.getRed(), foodColor.getGreen(), foodColor.getBlue(), 70));
        g2.fillRoundRect(fx - glowPad, fy - glowPad,
            CELL_SIZE - 2 + glowPad * 2, CELL_SIZE - 2 + glowPad * 2, 10, 10);

        g2.setColor(foodColor);
        g2.fillRoundRect(fx, fy, CELL_SIZE - 2, CELL_SIZE - 2, 6, 6);
    }

    private void paintSnake(Graphics2D g2)
    {
        float progress = lastTickInterval <= 0 ? 1f
            : Math.min(1f, (System.currentTimeMillis() - lastTickTime) / (float) lastTickInterval);

        boolean grew = currentBody.size() > previousBody.size();

        for (int i = 0; i < currentBody.size(); i++)
        {
            Point to = currentBody.get(i);
            Point from;

            if (grew)
            {
                from = (i == 0) ? previousBody.get(0) : previousBody.get(i - 1);
            }
            else
            {
                from = (i < previousBody.size()) ? previousBody.get(i) : to;
            }

            float drawX = to.x;
            float drawY = to.y;

            int dx = Math.abs(to.x - from.x);
            int dy = Math.abs(to.y - from.y);
            boolean teleport = dx > 1 || dy > 1; // wrap-around edge jump - snap instead of sliding across the board

            if (!teleport)
            {
                drawX = from.x + (to.x - from.x) * progress;
                drawY = from.y + (to.y - from.y) * progress;
            }

            Color playerColor = resolvePlayerColor();
            Color color;
            if (playerColor != null)
            {
                color = (i == 0) ? playerColor.brighter() : playerColor;
            }
            else
            {
                color = (i == 0)
                    ? ThemeManager.getColor(ThemeColor.ACCENT_HOVER)
                    : ThemeManager.getColor(ThemeColor.ACCENT);
            }
            g2.setColor(color);
            g2.fillRoundRect(Math.round(drawX * CELL_SIZE), SCORE_BAR_HEIGHT + Math.round(drawY * CELL_SIZE),
                CELL_SIZE - 2, CELL_SIZE - 2, 6, 6);
        }
    }

    /** The snake renders in the player's purchased username color (Shop → Profile) if they own/selected one, falling back to the theme accent otherwise - same reasoning as Racing's car color. */
    private Color resolvePlayerColor()
    {
        if (Session.isLoggedIn())
        {
            return PlayerColorRegistry.resolve(Session.getCurrentAccount().getPlayerColorName());
        }
        return null;
    }
}
