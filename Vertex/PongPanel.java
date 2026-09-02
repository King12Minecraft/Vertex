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

/**
 * PongPanel
 * ---------
 * Renders PongGame - two paddles and a ball, all Graphics2D shapes.
 * Same key-binding pattern as SnakePanel/RacingPanel.
 */
public class PongPanel extends JPanel
{
    private static final int PADDLE_STEP = 14;

    private final PongGame game;
    private final Runnable onGameOver;
    private Timer timer;

    public PongPanel(PongGame game, Runnable onGameOver)
    {
        this.game = game;
        this.onGameOver = onGameOver;
        setPreferredSize(new Dimension(PongGame.WIDTH, PongGame.HEIGHT));
        setFocusable(true);
        bindKeys();

        ThemeManager.addListener(new Runnable()
        {
            public void run() { repaint(); }
        });
    }

    private void bindKeys()
    {
        bindKey("UP", -PADDLE_STEP);
        bindKey("W", -PADDLE_STEP);
        bindKey("DOWN", PADDLE_STEP);
        bindKey("S", PADDLE_STEP);
    }

    private void bindKey(String keyName, final int deltaY)
    {
        getInputMap().put(KeyStroke.getKeyStroke(keyName), keyName + "_action");
        getActionMap().put(keyName + "_action", new AbstractAction()
        {
            public void actionPerformed(ActionEvent e) { game.movePlayer(deltaY); }
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

    /** The player's paddle renders in their purchased username color (Shop → Profile) if set, matching Racing's car-color/Snake's body-color precedent - falls back to the theme accent otherwise. AI's paddle stays a fixed red, same as Racing keeps opponents in a fixed contrasting color. */
    private Color playerPaddleColor()
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
        for (int y = 0; y < h; y += 16)
        {
            g2.fillRect(w / 2 - 1, y, 2, 8);
        }

        Color accent = playerPaddleColor();
        g2.setColor(accent);
        g2.fillRoundRect(6, game.getPlayerY(), PongGame.PADDLE_WIDTH, PongGame.PADDLE_HEIGHT, 4, 4);
        g2.setColor(new Color(240, 100, 100));
        g2.fillRoundRect(w - 6 - PongGame.PADDLE_WIDTH, game.getAiY(), PongGame.PADDLE_WIDTH, PongGame.PADDLE_HEIGHT, 4, 4);

        g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        g2.fillOval(game.getBallX() - 6, game.getBallY() - 6, 12, 12);

        g2.setFont(UITheme.FONT_HEADING);
        String score = game.getPlayerScore() + "   " + game.getAiScore();
        int textW = g2.getFontMetrics().stringWidth(score);
        g2.drawString(score, w / 2 - textW / 2, 34);

        g2.dispose();
    }
}
