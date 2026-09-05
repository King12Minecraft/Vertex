import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Ellipse2D;
import java.util.List;

/**
 * ZombieSurvivalPanel
 * -------------------
 * Renders ZombieSurvivalGame's state and drives its own tick timer,
 * same principle as RacingPanel. WASD (held) moves the player, the
 * mouse aims, and holding the left mouse button fires continuously
 * (subject to the model's own fire cooldown) - matching the "track
 * held-key/held-button state, don't re-read every frame" approach
 * FightArenaPanel uses for its own continuous input.
 */
public class ZombieSurvivalPanel extends JPanel
{
    private final ZombieSurvivalGame game;
    private final Runnable onGameOver;
    private Timer timer;

    private boolean up, down, left, right;

    public ZombieSurvivalPanel(ZombieSurvivalGame game, Runnable onGameOver)
    {
        this.game = game;
        this.onGameOver = onGameOver;
        setPreferredSize(new Dimension(ZombieSurvivalGame.BOARD_WIDTH, ZombieSurvivalGame.BOARD_HEIGHT));
        setFocusable(true);
        bindInput();

        ThemeManager.addListener(new Runnable()
        {
            public void run() { repaint(); }
        });
    }

    private void bindInput()
    {
        addKeyListener(new KeyAdapter()
        {
            public void keyPressed(KeyEvent e) { handleKey(e.getKeyCode(), true); }
            public void keyReleased(KeyEvent e) { handleKey(e.getKeyCode(), false); }
        });

        addMouseMotionListener(new MouseMotionAdapter()
        {
            public void mouseMoved(MouseEvent e) { game.setAim(e.getX(), e.getY()); }
            public void mouseDragged(MouseEvent e) { game.setAim(e.getX(), e.getY()); }
        });

        addMouseListener(new MouseAdapter()
        {
            public void mousePressed(MouseEvent e) { game.setFiring(true); }
            public void mouseReleased(MouseEvent e) { game.setFiring(false); }
        });
    }

    private void handleKey(int keyCode, boolean pressed)
    {
        if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_W) up = pressed;
        else if (keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_S) down = pressed;
        else if (keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_A) left = pressed;
        else if (keyCode == KeyEvent.VK_RIGHT || keyCode == KeyEvent.VK_D) right = pressed;
        else if (keyCode == KeyEvent.VK_SPACE) game.setFiring(pressed);
        game.setInput(up, down, left, right);
    }

    public void startTimer()
    {
        timer = new Timer(16, new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                game.tick();
                repaint();
                if (game.isOver())
                {
                    stopTimer();
                    onGameOver.run();
                }
            }
        });
        timer.start();
    }

    public void stopTimer()
    {
        if (timer != null) timer.stop();
    }

    private Color playerColor()
    {
        if (Session.isLoggedIn())
        {
            Color owned = PlayerColorRegistry.resolve(Session.getCurrentAccount().getPlayerColorName());
            if (owned != null)
            {
                return owned;
            }
        }
        return ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        int w = getWidth();
        int h = getHeight();

        g2.setColor(ThemeManager.getColor(ThemeColor.BG_SIDEBAR));
        g2.fillRect(0, 0, w, h);

        g2.setColor(ThemeManager.getColor(ThemeColor.BORDER));
        g2.drawRect(1, 1, w - 3, h - 3);

        for (double[] bullet : game.getBulletPositions())
        {
            g2.setColor(new Color(255, 230, 120));
            g2.fill(new Ellipse2D.Double(bullet[0] - 4, bullet[1] - 4, 8, 8));
        }

        for (ZombieSurvivalGame.Zombie z : game.getZombies())
        {
            drawZombie(g2, z);
        }

        drawPlayer(g2);

        g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        g2.setFont(UITheme.FONT_NAV_BOLD);
        g2.drawString("Wave " + game.getWave() + "/" + ZombieSurvivalGame.WAVE_COUNT
            + "   \u2022   Score: " + game.getScore()
            + "   \u2022   Killed: " + game.getZombiesKilled(), 12, 22);

        drawHealthBar(g2, w);

        g2.dispose();
    }

    private void drawHealthBar(Graphics2D g2, int w)
    {
        int barW = 220;
        int barH = 16;
        int x = w - barW - 14;
        int y = 12;
        double pct = Math.max(0, game.getPlayerHp()) / (double) game.getPlayerMaxHp();

        g2.setColor(new Color(60, 60, 60));
        g2.fillRoundRect(x, y, barW, barH, 8, 8);
        g2.setColor(pct > 0.3 ? new Color(90, 210, 120) : new Color(230, 90, 90));
        g2.fillRoundRect(x, y, (int) (barW * pct), barH, 8, 8);
        g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        g2.setFont(UITheme.FONT_SMALL);
        g2.drawString("HP " + game.getPlayerHp() + "/" + game.getPlayerMaxHp(), x + 4, y + 13);
    }

    private void drawPlayer(Graphics2D g2)
    {
        int r = 14;
        int x = (int) (game.getPlayerX() - r);
        int y = (int) (game.getPlayerY() - r);

        Color color = playerColor();
        if (game.isInvulnerable())
        {
            color = new Color(color.getRed(), color.getGreen(), color.getBlue(), 130);
        }
        g2.setColor(color);
        g2.fillOval(x, y, r * 2, r * 2);
        g2.setColor(ThemeManager.getColor(ThemeColor.BG_SIDEBAR));
        g2.fillOval(x + r - 4, y + r - 4, 8, 8);
    }

    private void drawZombie(Graphics2D g2, ZombieSurvivalGame.Zombie z)
    {
        int r = z.type == ZombieSurvivalGame.TYPE_TANK ? 18 : z.type == ZombieSurvivalGame.TYPE_FAST ? 10 : 13;
        Color color = z.type == ZombieSurvivalGame.TYPE_TANK ? new Color(120, 90, 60)
            : z.type == ZombieSurvivalGame.TYPE_FAST ? new Color(200, 90, 200)
            : new Color(110, 170, 90);

        g2.setColor(color);
        g2.fillOval((int) (z.x - r), (int) (z.y - r), r * 2, r * 2);

        double hpPct = z.hp / (double) z.maxHp;
        g2.setColor(new Color(40, 40, 40));
        g2.fillRect((int) (z.x - r), (int) (z.y - r - 8), r * 2, 4);
        g2.setColor(new Color(230, 90, 90));
        g2.fillRect((int) (z.x - r), (int) (z.y - r - 8), (int) (r * 2 * hpPct), 4);
    }
}
