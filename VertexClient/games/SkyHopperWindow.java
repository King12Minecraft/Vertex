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
import java.util.List;

/**
 * SkyHopperWindow
 * ---------------
 * Offline - SkyHopperGame runs entirely client-side. Left/A and Right/D
 * (held) move horizontally; bouncing off a platform is automatic, the
 * same "no jump button, gravity and a platform do the work" control
 * scheme the whole genre uses. Built on engine.GameLoop, same shape as
 * HillClimbWindow. Embedded in MainMenu's game-host slot;
 * requestLeave() stops the loop.
 */
public class SkyHopperWindow extends JPanel implements EmbeddedGamePanel
{
    private static final Color SKY_TOP = new Color(120, 170, 230);
    private static final Color SKY_BOTTOM = new Color(210, 230, 250);
    private static final Color PLAYER_COLOR = new Color(235, 90, 90);
    private static final Color NORMAL_PLATFORM = new Color(90, 170, 100);
    private static final Color MOVING_PLATFORM = new Color(80, 130, 220);
    private static final Color BREAKABLE_PLATFORM = new Color(200, 130, 70);
    private static final Color SPRING_PLATFORM = new Color(230, 200, 60);

    private SkyHopperGame game;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private GameLoop loop;
    private boolean reported;

    public SkyHopperWindow()
    {
        setLayout(new BorderLayout());

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setBorder(new EmptyBorder(0, 0, 12, 0));

        statusLabel = new JLabel("Hold Left/Right (or A/D) to steer. Bouncing is automatic.");
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

        game = new SkyHopperGame();
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
        statusLabel.setText("Height: " + game.getScore() + "m");
    }

    private void finishGame()
    {
        int finalScore = game.getScore();
        String reason = "You fell.";
        statusLabel.setText(reason + " Final height: " + finalScore + "m");
        if (!reported)
        {
            reported = true;
            reportScore(finalScore);
        }

        SnakeGameOverDialog.show(this, finalScore,
            reason + " You climbed " + finalScore + " meters.",
            finalScore >= 150 ? "I climbed " + finalScore + " meters in Sky Hopper on Vertex!" : null,
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
            GuestPlayTracker.recordGuestPlay("sky-hopper", finalScore);
            return;
        }

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.GAME_PLAYED_REQUEST);
                request.setGameId("sky-hopper");
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
            setPreferredSize(new Dimension(SkyHopperGame.WORLD_WIDTH, SkyHopperGame.VIEW_HEIGHT));
            setFocusable(true);
            bindKeys();
        }

        private void bindKeys()
        {
            bindHoldKey("LEFT", false);
            bindHoldKey("A", false);
            bindHoldKey("RIGHT", true);
            bindHoldKey("D", true);
        }

        private void bindHoldKey(String keyName, final boolean isRight)
        {
            getInputMap().put(KeyStroke.getKeyStroke(keyName), keyName + "_press");
            getActionMap().put(keyName + "_press", new AbstractAction()
            {
                public void actionPerformed(ActionEvent e)
                {
                    if (isRight) game.setMovingRight(true); else game.setMovingLeft(true);
                }
            });
            getInputMap().put(KeyStroke.getKeyStroke("released " + keyName), keyName + "_release");
            getActionMap().put(keyName + "_release", new AbstractAction()
            {
                public void actionPerformed(ActionEvent e)
                {
                    if (isRight) game.setMovingRight(false); else game.setMovingLeft(false);
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

            double cameraTop = game.getCameraTop();

            List<SkyHopperGame.Platform> platforms = game.getPlatforms();
            for (int i = 0; i < platforms.size(); i++)
            {
                SkyHopperGame.Platform p = platforms.get(i);
                if (p.broken) continue;
                int screenY = (int) (p.y - cameraTop);
                if (screenY < -SkyHopperGame.PLATFORM_HEIGHT || screenY > height) continue;
                drawPlatform(g2, (int) p.x, screenY, p.type);
            }

            int playerScreenX = (int) game.getPlayerX();
            int playerScreenY = (int) (game.getPlayerY() - cameraTop);
            g2.setColor(PLAYER_COLOR);
            g2.fillRoundRect(playerScreenX, playerScreenY, SkyHopperGame.PLAYER_SIZE, SkyHopperGame.PLAYER_SIZE, 10, 10);

            g2.dispose();
        }

        private void drawPlatform(Graphics2D g2, int x, int y, SkyHopperGame.PlatformType type)
        {
            Color color;
            if (type == SkyHopperGame.PlatformType.MOVING) color = MOVING_PLATFORM;
            else if (type == SkyHopperGame.PlatformType.BREAKABLE) color = BREAKABLE_PLATFORM;
            else if (type == SkyHopperGame.PlatformType.SPRING) color = SPRING_PLATFORM;
            else color = NORMAL_PLATFORM;

            g2.setColor(color);
            g2.fillRoundRect(x, y, SkyHopperGame.PLATFORM_WIDTH, SkyHopperGame.PLATFORM_HEIGHT, 6, 6);

            if (type == SkyHopperGame.PlatformType.BREAKABLE)
            {
                // A crack line down the middle - visually distinct from a plain
                // platform at a glance, hinting it won't survive a second landing.
                g2.setColor(new Color(120, 70, 30));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawLine(x + SkyHopperGame.PLATFORM_WIDTH / 2, y + 2,
                    x + SkyHopperGame.PLATFORM_WIDTH / 2 - 4, y + SkyHopperGame.PLATFORM_HEIGHT - 2);
            }
            else if (type == SkyHopperGame.PlatformType.SPRING)
            {
                g2.setColor(new Color(120, 100, 20));
                g2.fillRect(x + SkyHopperGame.PLATFORM_WIDTH / 2 - 4, y - 6, 8, 6);
            }
        }
    }
}
