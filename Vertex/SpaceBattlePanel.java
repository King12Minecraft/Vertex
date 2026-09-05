import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/**
 * SpaceBattlePanel
 * ----------------
 * Renders SpaceBattleGame's state and drives its own tick timer, same
 * principle as RacingPanel/ZombieSurvivalPanel. Left/right (or A/D)
 * rotate the ship, up (or W) thrusts forward, Space fires - classic
 * arcade dogfight controls, tracked as held-key state same as
 * FightArenaPanel/ZombieSurvivalPanel.
 */
public class SpaceBattlePanel extends JPanel
{
    private final SpaceBattleGame game;
    private final Runnable onGameOver;
    private Timer timer;

    private boolean turnLeft, turnRight, thrusting;

    public SpaceBattlePanel(SpaceBattleGame game, Runnable onGameOver)
    {
        this.game = game;
        this.onGameOver = onGameOver;
        setPreferredSize(new Dimension(SpaceBattleGame.BOARD_WIDTH, SpaceBattleGame.BOARD_HEIGHT));
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
    }

    private void handleKey(int keyCode, boolean pressed)
    {
        if (keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_A) turnLeft = pressed;
        else if (keyCode == KeyEvent.VK_RIGHT || keyCode == KeyEvent.VK_D) turnRight = pressed;
        else if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_W) thrusting = pressed;
        else if (keyCode == KeyEvent.VK_SPACE) game.setFiring(pressed);
        game.setControls(turnLeft, turnRight, thrusting);
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

    private Color shipColor()
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

        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, w, h);

        for (double[] bullet : game.getBulletPositions())
        {
            g2.setColor(bullet[2] == 1 ? new Color(255, 230, 120) : new Color(255, 100, 100));
            g2.fill(new Ellipse2D.Double(bullet[0] - 3, bullet[1] - 3, 6, 6));
        }

        for (SpaceBattleGame.Entity e : game.getEntities())
        {
            drawEntity(g2, e);
        }

        drawShip(g2);

        g2.setColor(Color.WHITE);
        g2.setFont(UITheme.FONT_NAV_BOLD);
        String progress = Math.min(100, game.getFrameCount() * 100 / SpaceBattleGame.MATCH_FRAMES) + "% through the match";
        g2.drawString("Score: " + game.getScore() + "   \u2022   " + progress, 12, 22);

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
        g2.setColor(Color.WHITE);
        g2.setFont(UITheme.FONT_SMALL);
        g2.drawString("Hull " + game.getPlayerHp() + "/" + game.getPlayerMaxHp(), x + 4, y + 13);
    }

    private void drawShip(Graphics2D g2)
    {
        double x = game.getShipX();
        double y = game.getShipY();
        double angle = game.getShipAngle();
        int size = 14;

        Path2D.Double path = new Path2D.Double();
        path.moveTo(x + Math.cos(angle) * size, y + Math.sin(angle) * size);
        path.lineTo(x + Math.cos(angle + 2.5) * size, y + Math.sin(angle + 2.5) * size);
        path.lineTo(x + Math.cos(angle - 2.5) * size, y + Math.sin(angle - 2.5) * size);
        path.closePath();

        Color color = shipColor();
        if (game.isInvulnerable())
        {
            color = new Color(color.getRed(), color.getGreen(), color.getBlue(), 130);
        }
        g2.setColor(color);
        g2.fill(path);
    }

    private void drawEntity(Graphics2D g2, SpaceBattleGame.Entity e)
    {
        int r = e.type == SpaceBattleGame.ENTITY_ENEMY ? 13 : 16;
        Color color = e.type == SpaceBattleGame.ENTITY_ENEMY ? new Color(230, 90, 90) : new Color(150, 150, 160);

        g2.setColor(color);
        g2.fillOval((int) (e.x - r), (int) (e.y - r), r * 2, r * 2);

        double hpPct = e.hp / (double) e.maxHp;
        g2.setColor(new Color(40, 40, 40));
        g2.fillRect((int) (e.x - r), (int) (e.y - r - 8), r * 2, 4);
        g2.setColor(new Color(230, 90, 90));
        g2.fillRect((int) (e.x - r), (int) (e.y - r - 8), (int) (r * 2 * hpPct), 4);
    }
}
