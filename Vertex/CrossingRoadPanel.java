import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * CrossingRoadPanel
 * -----------------
 * Renders CrossingRoadGame's state - lanes, traffic, and the player -
 * with Graphics2D shapes, same principle as every other game in
 * GameHub. Runs its own tick timer (~60fps). Keyboard input uses Swing
 * key bindings (InputMap/ActionMap), same pattern as SnakePanel.
 */
public class CrossingRoadPanel extends JPanel
{
    private final CrossingRoadGame game;
    private final Runnable onGameOver;
    private Timer timer;

    public CrossingRoadPanel(CrossingRoadGame game, Runnable onGameOver)
    {
        this.game = game;
        this.onGameOver = onGameOver;
        setPreferredSize(new Dimension(CrossingRoadGame.BOARD_WIDTH, CrossingRoadGame.BOARD_HEIGHT));
        setFocusable(true);
        bindKeys();

        ThemeManager.addListener(new Runnable()
        {
            public void run() { repaint(); }
        });
    }

    private void bindKeys()
    {
        bindKey("UP", "up");
        bindKey("W", "up");
        bindKey("DOWN", "down");
        bindKey("S", "down");
        bindKey("LEFT", "left");
        bindKey("A", "left");
        bindKey("RIGHT", "right");
        bindKey("D", "right");
    }

    private void bindKey(String keyName, final String action)
    {
        getInputMap().put(KeyStroke.getKeyStroke(keyName), action);
        getActionMap().put(action, new AbstractAction()
        {
            public void actionPerformed(ActionEvent e)
            {
                if ("up".equals(action)) game.moveUp();
                else if ("down".equals(action)) game.moveDown();
                else if ("left".equals(action)) game.moveLeft();
                else if ("right".equals(action)) game.moveRight();
                repaint();
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
                if (game.isGameOver())
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

    /** The player renders in their purchased username color if set, matching Racing/Snake/Pong/Dino's precedent. */
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

        int cell = CrossingRoadGame.CELL;
        int w = getWidth();

        for (int r = 0; r < CrossingRoadGame.ROWS; r++)
        {
            Color rowColor = (r == 0 || r == CrossingRoadGame.ROWS - 1)
                ? ThemeManager.getColor(ThemeColor.BG_PANEL)
                : ThemeManager.getColor(ThemeColor.BG_SIDEBAR);
            g2.setColor(rowColor);
            g2.fillRect(0, r * cell, w, cell);
        }

        g2.setColor(new Color(240, 100, 100));
        List<List<double[]>> laneCars = game.getLaneCars();
        for (int r = 0; r < laneCars.size(); r++)
        {
            List<double[]> cars = laneCars.get(r);
            for (int i = 0; i < cars.size(); i++)
            {
                double[] car = cars.get(i);
                g2.fillRoundRect((int) car[0], r * cell + 10, (int) car[1], cell - 20, 8, 8);
            }
        }

        int px = game.getPlayerCol() * cell + cell / 2;
        int py = game.getPlayerRow() * cell + cell / 2;
        g2.setColor(playerColor());
        g2.fillOval(px - cell / 3, py - cell / 3, cell * 2 / 3, cell * 2 / 3);

        g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        g2.setFont(UITheme.FONT_NAV_BOLD);
        g2.drawString("Crossings: " + game.getScore(), 12, 22);

        g2.dispose();
    }
}
