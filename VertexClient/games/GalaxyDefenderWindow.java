package games;

import economy.PerformanceMode;
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
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
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
 * GalaxyDefenderWindow
 * ---------------------
 * Offline - GalaxyDefenderGame runs entirely client-side. Arrow keys or
 * A/D hold to move the ship, Space fires (rate-limited by the game's
 * own cooldown). A fixed-rate Swing Timer drives both the tick and the
 * repaint, ~60fps. Reports the final score once on game-over, same
 * GAME_PLAYED_REQUEST pattern as the other single-player games.
 * Embedded in MainMenu's game-host slot (see ChessWindow's javadoc for
 * the pattern); requestLeave() stops the game timer.
 */
public class GalaxyDefenderWindow extends JPanel implements EmbeddedGamePanel
{
    private static final int TICK_MS = 16;

    private GalaxyDefenderGame game;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private Timer timer;
    private boolean reported;

    public GalaxyDefenderWindow()
    {
        setLayout(new BorderLayout());

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setBorder(new EmptyBorder(0, 0, 12, 0));

        statusLabel = new JLabel("Arrow keys or A/D to move, Space to fire. Don't let them land.");
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
        JPanel restartWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        restartWrap.setOpaque(false);
        restartWrap.add(restart);
        restartWrap.add(leave);
        topRow.add(restartWrap, BorderLayout.EAST);

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
        if (timer != null) { timer.stop(); }
        return true;
    }

    private void startNewGame()
    {
        if (timer != null)
        {
            timer.stop();
        }
        game = new GalaxyDefenderGame();
        reported = false;
        updateStatus();

        timer = new Timer(PerformanceMode.getTickIntervalMs(TICK_MS), new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                game.tick();
                updateStatus();
                boardPanel.repaint();
                if (game.isLost())
                {
                    timer.stop();
                    finishGame();
                }
            }
        });
        timer.start();
        boardPanel.requestFocusInWindow();
        boardPanel.repaint();
    }

    private void updateStatus()
    {
        statusLabel.setText("Score: " + game.getScore() + "    Lives: " + game.getLives()
            + "    Wave: " + game.getWave());
    }

    private void finishGame()
    {
        int finalScore = game.getScore();
        statusLabel.setText("Overrun! Final score: " + finalScore + " (reached wave " + game.getWave() + ")");
        if (!reported)
        {
            reported = true;
            reportScore(finalScore);
        }

        SnakeGameOverDialog.show(this, finalScore, "You held them off through wave " + game.getWave() + ".",
            finalScore >= 200 ? "I scored " + finalScore + " points in Galaxy Defender on Vertex!" : null,
            new SnakeGameOverDialog.Choice()
            {
                public void onPlayAgain() { startNewGame(); }
                public void onClose() { MainMenu.getInstance().returnToGames(); }
            });
    }

    private void reportScore(final int finalScore)
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.GAME_PLAYED_REQUEST);
                request.setGameId("galaxy-defender");
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
            setPreferredSize(new Dimension(GalaxyDefenderGame.BOARD_WIDTH, GalaxyDefenderGame.BOARD_HEIGHT));
            setFocusable(true);
            setBackground(Color.BLACK);
            bindKeys();
        }

        private void bindKeys()
        {
            bindHoldKey("LEFT", true);
            bindHoldKey("A", true);
            bindHoldKey("RIGHT", false);
            bindHoldKey("D", false);

            getInputMap().put(KeyStroke.getKeyStroke("SPACE"), "shoot");
            getActionMap().put("shoot", new AbstractAction()
            {
                public void actionPerformed(ActionEvent e) { game.shoot(); }
            });
        }

        private void bindHoldKey(String keyName, final boolean isLeft)
        {
            getInputMap().put(KeyStroke.getKeyStroke(keyName), keyName + "_press");
            getActionMap().put(keyName + "_press", new AbstractAction()
            {
                public void actionPerformed(ActionEvent e)
                {
                    if (isLeft) game.setMovingLeft(true); else game.setMovingRight(true);
                }
            });
            getInputMap().put(KeyStroke.getKeyStroke("released " + keyName), keyName + "_release");
            getActionMap().put(keyName + "_release", new AbstractAction()
            {
                public void actionPerformed(ActionEvent e)
                {
                    if (isLeft) game.setMovingLeft(false); else game.setMovingRight(false);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            g2.setColor(new Color(10, 10, 20));
            g2.fillRect(0, 0, getWidth(), getHeight());

            paintEnemies(g2);
            paintBullets(g2);
            paintPlayer(g2);

            g2.dispose();
        }

        private void paintEnemies(Graphics2D g2)
        {
            Color[] rowColors = new Color[]
            {
                new Color(230, 90, 110),
                new Color(235, 150, 80),
                new Color(120, 210, 110),
                new Color(90, 170, 230)
            };
            for (int row = 0; row < GalaxyDefenderGame.ENEMY_ROWS; row++)
            {
                for (int col = 0; col < GalaxyDefenderGame.ENEMY_COLS; col++)
                {
                    if (!game.isEnemyAlive(row, col)) continue;
                    int x = (int) Math.round(game.enemyLeft(col));
                    int y = (int) Math.round(game.enemyTop(row));
                    g2.setColor(rowColors[row % rowColors.length]);
                    g2.fillRoundRect(x, y, GalaxyDefenderGame.ENEMY_WIDTH, GalaxyDefenderGame.ENEMY_HEIGHT, 6, 6);
                }
            }
        }

        private void paintBullets(Graphics2D g2)
        {
            g2.setColor(new Color(255, 230, 120));
            for (GalaxyDefenderGame.Bullet b : game.getPlayerBullets())
            {
                g2.fillRect((int) Math.round(b.x), (int) Math.round(b.y),
                    GalaxyDefenderGame.BULLET_WIDTH, GalaxyDefenderGame.BULLET_HEIGHT);
            }
            g2.setColor(new Color(255, 90, 90));
            for (GalaxyDefenderGame.Bullet b : game.getEnemyBullets())
            {
                g2.fillRect((int) Math.round(b.x), (int) Math.round(b.y),
                    GalaxyDefenderGame.BULLET_WIDTH, GalaxyDefenderGame.BULLET_HEIGHT);
            }
        }

        private void paintPlayer(Graphics2D g2)
        {
            int x = (int) Math.round(game.getPlayerX());
            g2.setColor(new Color(120, 220, 255));
            g2.fillRoundRect(x, GalaxyDefenderGame.PLAYER_Y,
                GalaxyDefenderGame.PLAYER_WIDTH, GalaxyDefenderGame.PLAYER_HEIGHT, 6, 6);
        }
    }
}
