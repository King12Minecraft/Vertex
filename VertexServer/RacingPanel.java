import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * RacingPanel
 * -----------
 * Renders RacingGame's state - three lanes, the player's car, and
 * falling obstacle cars - entirely with Graphics2D shapes, same
 * principle as every other game in Vertex. Runs its own tick timer
 * (~60fps) and calls back to onGameOver once the model reports a
 * collision. Keyboard input uses Swing key bindings (InputMap/
 * ActionMap), same pattern as SnakePanel.
 */
public class RacingPanel extends JPanel
{
    private static final int LANE_WIDTH = 90;
    private static final int BOARD_WIDTH = LANE_WIDTH * RacingGame.LANES;

    private final RacingGame game;
    private final Runnable onGameOver;
    private Timer timer;

    public RacingPanel(RacingGame game, Runnable onGameOver)
    {
        this.game = game;
        this.onGameOver = onGameOver;
        setPreferredSize(new Dimension(BOARD_WIDTH, RacingGame.BOARD_HEIGHT));
        setFocusable(true);
        bindKeys();

        ThemeManager.addListener(new Runnable()
        {
            public void run() { repaint(); }
        });
    }

    private void bindKeys()
    {
        bindKey("LEFT", true);
        bindKey("A", true);
        bindKey("RIGHT", false);
        bindKey("D", false);
    }

    private void bindKey(String keyName, final boolean moveLeft)
    {
        getInputMap().put(KeyStroke.getKeyStroke(keyName), keyName + "_action");
        getActionMap().put(keyName + "_action", new AbstractAction()
        {
            public void actionPerformed(ActionEvent e)
            {
                if (moveLeft) game.moveLeft(); else game.moveRight();
            }
        });
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

    /** The player's car renders in their equipped username color (Shop → Profile) if they own/selected one, falling back to the theme accent otherwise - a car color reward doesn't need its own separate cosmetic system when this one already exists. */
    private Color playerCarColor()
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
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] {10f, 10f}, 0f));
        for (int lane = 1; lane < RacingGame.LANES; lane++)
        {
            int x = lane * LANE_WIDTH;
            g2.drawLine(x, 0, x, h);
        }

        List<int[]> entities = game.getEntities();
        for (int i = 0; i < entities.size(); i++)
        {
            int[] entity = entities.get(i);
            if (entity[2] == RacingGame.TYPE_OBSTACLE)
            {
                drawCar(g2, entity[0], entity[1], new Color(240, 100, 100));
            }
            else
            {
                drawPowerUp(g2, entity[0], entity[1], entity[2]);
            }
        }

        drawCar(g2, game.getPlayerLane(), RacingGame.PLAYER_Y, playerCarColor());
        if (game.hasShield())
        {
            drawShieldRing(g2, game.getPlayerLane(), RacingGame.PLAYER_Y);
        }

        g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        g2.setFont(UITheme.FONT_NAV_BOLD);
        String progress = Math.min(100, game.getFrameCount() * 100 / RacingGame.FINISH_FRAMES) + "% to finish";
        String boostTag = game.isBoosting() ? "   \u2022   BOOST!" : "";
        g2.drawString("Score: " + game.getScore() + "   \u2022   " + progress + boostTag, 12, 24);

        g2.dispose();
    }

    private void drawPowerUp(Graphics2D g2, int lane, int y, int type)
    {
        int size = 34;
        int x = lane * LANE_WIDTH + (LANE_WIDTH - size) / 2;

        Color color = type == RacingGame.TYPE_SHIELD ? new Color(90, 170, 240)
            : type == RacingGame.TYPE_BOOST ? new Color(250, 190, 60)
            : new Color(255, 215, 90);

        g2.setColor(color);
        g2.fillOval(x, y - size / 2, size, size);
        g2.setColor(ThemeManager.getColor(ThemeColor.BG_SIDEBAR));
        g2.setFont(UITheme.FONT_SMALL);
        String glyph = type == RacingGame.TYPE_SHIELD ? "S" : type == RacingGame.TYPE_BOOST ? "B" : "$";
        g2.drawString(glyph, x + size / 2 - 4, y + 4);
    }

    private void drawShieldRing(Graphics2D g2, int lane, int y)
    {
        int carW = 46;
        int carH = 64;
        int x = lane * LANE_WIDTH + (LANE_WIDTH - carW) / 2;

        g2.setColor(new Color(90, 170, 240, 150));
        g2.setStroke(new BasicStroke(3f));
        g2.drawRoundRect(x - 6, y - carH / 2 - 6, carW + 12, carH + 12, 16, 16);
    }

    private void drawCar(Graphics2D g2, int lane, int y, Color color)
    {
        int carW = 46;
        int carH = 64;
        int x = lane * LANE_WIDTH + (LANE_WIDTH - carW) / 2;

        g2.setColor(color);
        g2.fillRoundRect(x, y - carH / 2, carW, carH, 12, 12);

        g2.setColor(ThemeManager.getColor(ThemeColor.BG_SIDEBAR));
        g2.fillRoundRect(x + 6, y - carH / 2 + 10, carW - 12, 16, 6, 6);
        g2.fillRoundRect(x + 6, y - carH / 2 + carH - 26, carW - 12, 16, 6, 6);
    }
}
