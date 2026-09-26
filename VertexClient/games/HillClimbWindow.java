package games;

import account.Session;
import economy.GuestPlayTracker;
import economy.PerformanceMode;
import engine.GameLoop;
import net.Message;
import net.MessageType;
import net.NetworkManager;
import pages.MainMenu;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.ThemedButton;

import javax.swing.AbstractAction;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * HillClimbWindow
 * ---------------
 * Offline - HillClimbGame runs entirely client-side. Right arrow/D
 * (held) accelerates, Left arrow/A or Down/S (held) brakes - press/
 * release toggles a "held" flag rather than a one-shot input, same
 * continuous-hold convention BrickBreakerWindow's paddle uses. Built
 * on engine.GameLoop instead of a raw javax.swing.Timer, and
 * engine.Vector2 for the slope/gravity math (see HillClimbGame) - the
 * first game in this codebase to actually use the engine package
 * rather than just having it available. Embedded in MainMenu's
 * game-host slot; requestLeave() stops the loop.
 */
public class HillClimbWindow extends JPanel implements EmbeddedGamePanel
{
    private static final Color SKY_TOP = new Color(30, 40, 60);
    private static final Color SKY_BOTTOM = new Color(60, 90, 130);
    private static final Color GROUND = new Color(70, 130, 80);
    private static final Color GROUND_LINE = new Color(120, 200, 130);
    private static final Color CAR_BODY = new Color(220, 90, 70);
    private static final Color WHEEL = new Color(30, 30, 35);

    private static final int PANEL_WIDTH = 640;
    private static final int PANEL_HEIGHT = 420;
    private static final double CAR_SCREEN_FRACTION = 0.32;
    private static final double PIXELS_PER_UNIT = 2.4;

    private HillClimbGame game;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private GameLoop loop;
    private boolean reported;

    public HillClimbWindow()
    {
        setLayout(new BorderLayout());

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setBorder(new EmptyBorder(0, 0, 12, 0));

        statusLabel = new JLabel("Hold Right/D to drive. Don't run out of fuel.");
        statusLabel.setFont(UITheme.FONT_NAV_BOLD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        topRow.add(statusLabel, BorderLayout.WEST);

        ThemedButton restart = new ThemedButton("New Game", false);
        restart.setPreferredSize(new Dimension(100, 34));
        restart.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { startNewGame(); }
        });
        ThemedButton leave = new ThemedButton("Leave", false);
        leave.setPreferredSize(new Dimension(90, 34));
        leave.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                if (requestLeave()) { MainMenu.getInstance().returnToGames(); }
            }
        });
        JPanel buttonsWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonsWrap.setOpaque(false);
        buttonsWrap.add(restart);
        buttonsWrap.add(leave);
        topRow.add(buttonsWrap, BorderLayout.EAST);

        root.add(topRow, BorderLayout.NORTH);

        boardPanel = new BoardPanel();
        JPanel boardCenterer = new JPanel(new GridBagLayout());
        boardCenterer.setOpaque(false);
        boardCenterer.add(boardPanel, new GridBagConstraints());
        root.add(boardCenterer, BorderLayout.CENTER);

        add(root, BorderLayout.CENTER);
        startNewGame();
    }

    @Override
    public boolean requestLeave()
    {
        if (loop != null) { loop.stop(); }
        return true;
    }

    private void startNewGame()
    {
        if (loop != null) { loop.stop(); }

        game = new HillClimbGame();
        reported = false;
        updateStatus();

        loop = new GameLoop(PerformanceMode.getTickIntervalMs(16), new GameLoop.Ticker()
        {
            public void tick(double dtSeconds)
            {
                game.tick(dtSeconds);
                updateStatus();
                boardPanel.repaint();
                if (game.isOver())
                {
                    loop.stop();
                    finishGame();
                }
            }
        });
        loop.start();
        boardPanel.requestFocusInWindow();
        boardPanel.repaint();
    }

    private void updateStatus()
    {
        statusLabel.setText("Distance: " + game.getScore() + "m    Fuel: " + (int) (game.getFuelFraction() * 100) + "%");
    }

    private void finishGame()
    {
        int finalScore = game.getScore();
        String reason = "Ran out of fuel.";
        statusLabel.setText(reason + " Final distance: " + finalScore + "m");
        if (!reported)
        {
            reported = true;
            reportScore(finalScore);
        }

        SnakeGameOverDialog.show(this, finalScore,
            reason + " You made it " + finalScore + " meters.",
            finalScore >= 400 ? "I drove " + finalScore + " meters in Hill Climb on Vertex!" : null,
            new SnakeGameOverDialog.Choice()
            {
                public void onPlayAgain() { startNewGame(); }
                public void onClose() { MainMenu.getInstance().returnToGames(); }
            });
    }

    private void reportScore(final int finalScore)
    {
        if (!Session.isLoggedIn())
        {
            GuestPlayTracker.recordGuestPlay("hill-climb", finalScore);
            return;
        }

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.GAME_PLAYED_REQUEST);
                request.setGameId("hill-climb");
                request.setScore(finalScore);
                NetworkManager.sendAsync(request);
            }
        });
        worker.start();
    }

    private class BoardPanel extends JPanel
    {
        BoardPanel()
        {
            setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
            setFocusable(true);
            bindKeys();
        }

        private void bindKeys()
        {
            bindHoldKey("RIGHT", true);
            bindHoldKey("D", true);
            bindHoldKey("LEFT", false);
            bindHoldKey("A", false);
            bindHoldKey("DOWN", false);
            bindHoldKey("S", false);
        }

        private void bindHoldKey(String keyName, final boolean isThrottle)
        {
            getInputMap().put(KeyStroke.getKeyStroke(keyName), keyName + "_press");
            getActionMap().put(keyName + "_press", new AbstractAction()
            {
                public void actionPerformed(ActionEvent e)
                {
                    if (isThrottle) game.setThrottling(true); else game.setBraking(true);
                }
            });
            getInputMap().put(KeyStroke.getKeyStroke("released " + keyName), keyName + "_release");
            getActionMap().put(keyName + "_release", new AbstractAction()
            {
                public void actionPerformed(ActionEvent e)
                {
                    if (isThrottle) game.setThrottling(false); else game.setBraking(false);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            int width = getWidth();
            int height = getHeight();

            java.awt.GradientPaint sky = new java.awt.GradientPaint(0, 0, SKY_TOP, 0, height, SKY_BOTTOM);
            g2.setPaint(sky);
            g2.fillRect(0, 0, width, height);

            double worldAtScreenLeft = game.getDistance() - (width * CAR_SCREEN_FRACTION) / PIXELS_PER_UNIT;
            int groundY = height - 70;

            int[] xs = new int[width + 2];
            int[] ys = new int[width + 2];
            for (int sx = 0; sx <= width; sx++)
            {
                double worldX = worldAtScreenLeft + sx / PIXELS_PER_UNIT;
                double h = HillClimbGame.terrainHeight(worldX);
                xs[sx] = sx;
                ys[sx] = (int) (groundY - h);
            }
            xs[width + 1] = width;
            ys[width + 1] = height;
            xs[0] = 0;

            int[] fillXs = new int[width + 2];
            int[] fillYs = new int[width + 2];
            System.arraycopy(xs, 0, fillXs, 0, width + 1);
            System.arraycopy(ys, 0, fillYs, 0, width + 1);
            fillXs[width + 1] = width;
            fillYs[width + 1] = height;

            g2.setColor(GROUND);
            g2.fillPolygon(fillXs, fillYs, width + 2);

            g2.setColor(GROUND_LINE);
            g2.setStroke(new BasicStroke(3));
            for (int sx = 0; sx < width; sx++)
            {
                g2.drawLine(xs[sx], ys[sx], xs[sx + 1], ys[sx + 1]);
            }

            int carScreenX = (int) (width * CAR_SCREEN_FRACTION);
            double carWorldH = HillClimbGame.terrainHeight(game.getDistance());
            int carScreenY = (int) (groundY - carWorldH);
            drawCar(g2, carScreenX, carScreenY, game.getCarAngle());

            g2.dispose();
        }

        private void drawCar(Graphics2D g2, int cx, int cy, double angle)
        {
            Graphics2D car = (Graphics2D) g2.create();
            car.translate(cx, cy);
            car.rotate(-angle);

            car.setColor(CAR_BODY);
            car.fillRoundRect(-24, -22, 48, 18, 8, 8);

            car.setColor(WHEEL);
            car.fillOval(-18, -8, 16, 16);
            car.fillOval(6, -8, 16, 16);

            car.dispose();
        }
    }
}
