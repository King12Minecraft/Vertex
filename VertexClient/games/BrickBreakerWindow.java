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
 * BrickBreakerWindow
 * -------------------
 * Offline - BrickBreakerGame runs entirely client-side. Arrow keys or
 * A/D hold to move the paddle continuously (press/release toggles a
 * "moving" flag rather than a one-shot direction, since a paddle
 * needs to keep sliding while the key is held, unlike Snake/Maze
 * Chase's step-once-per-key movement). A fixed-rate Swing Timer drives
 * both the physics tick and the repaint, ~60fps. Reports the final
 * score once on win or game-over, same GAME_PLAYED_REQUEST pattern as
 * the other single-player games. Embedded in MainMenu's game-host slot
 * (see ChessWindow's javadoc for the pattern); requestLeave() stops
 * the game timer.
 */
public class BrickBreakerWindow extends JPanel implements EmbeddedGamePanel
{
    private static final int TICK_MS = 16;

    private BrickBreakerGame game;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private Timer timer;
    private boolean reported;

    public BrickBreakerWindow()
    {
        setLayout(new BorderLayout());

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setBorder(new EmptyBorder(0, 0, 12, 0));

        statusLabel = new JLabel("Arrow keys or A/D to move the paddle. Don't let the ball fall.");
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
        game = new BrickBreakerGame();
        reported = false;
        updateStatus();

        timer = new Timer(PerformanceMode.getTickIntervalMs(TICK_MS), new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                game.tick();
                updateStatus();
                boardPanel.repaint();
                if (game.isOver())
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
        statusLabel.setText("Score: " + game.getScore() + "    Lives: " + game.getLives());
    }

    private void finishGame()
    {
        int finalScore = game.getScore();
        boolean won = game.isWon();
        statusLabel.setText((won ? "Wall cleared! " : "Out of lives! ") + "Final score: " + finalScore);
        if (!reported)
        {
            reported = true;
            reportScore(finalScore);
        }

        SnakeGameOverDialog.show(this, finalScore,
            won ? "You cleared the whole wall!" : "The ball got away.",
            finalScore >= 250 ? "I scored " + finalScore + " points in Brick Breaker on Vertex!" : null,
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
                request.setGameId("brick-breaker");
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
            setPreferredSize(new Dimension(BrickBreakerGame.BOARD_WIDTH, BrickBreakerGame.BOARD_HEIGHT));
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

            g2.setColor(new Color(18, 18, 26));
            g2.fillRect(0, 0, getWidth(), getHeight());

            paintBricks(g2);
            paintPaddle(g2);
            paintBall(g2);

            g2.dispose();
        }

        private void paintBricks(Graphics2D g2)
        {
            Color[] rowColors = new Color[]
            {
                new Color(230, 90, 90),
                new Color(235, 150, 80),
                new Color(235, 210, 80),
                new Color(120, 210, 110),
                new Color(90, 170, 230),
                new Color(170, 120, 230)
            };
            for (int row = 0; row < BrickBreakerGame.ROWS; row++)
            {
                for (int col = 0; col < BrickBreakerGame.COLS; col++)
                {
                    if (!game.isBrickAlive(row, col)) continue;
                    int x = BrickBreakerGame.brickLeft(col), y = BrickBreakerGame.brickTop(row);
                    g2.setColor(rowColors[row % rowColors.length]);
                    g2.fillRoundRect(x, y, BrickBreakerGame.BRICK_WIDTH, BrickBreakerGame.BRICK_HEIGHT, 4, 4);
                }
            }
        }

        private void paintPaddle(Graphics2D g2)
        {
            g2.setColor(new Color(230, 230, 240));
            g2.fillRoundRect((int) Math.round(game.getPaddleX()), BrickBreakerGame.PADDLE_Y,
                BrickBreakerGame.PADDLE_WIDTH, BrickBreakerGame.PADDLE_HEIGHT, 6, 6);
        }

        private void paintBall(Graphics2D g2)
        {
            int r = BrickBreakerGame.BALL_RADIUS;
            g2.setColor(new Color(255, 220, 90));
            g2.fillOval((int) Math.round(game.getBallX() - r), (int) Math.round(game.getBallY() - r), r * 2, r * 2);
        }
    }
}
