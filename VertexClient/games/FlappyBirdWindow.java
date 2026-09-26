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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * FlappyBirdWindow
 * ----------------
 * Offline - FlappyBirdGame runs entirely client-side. Space, Up, or a
 * click flaps; the bird just hangs in place until the first flap
 * starts the game. A fixed-rate Swing Timer drives both physics and
 * repaint, same shape as BrickBreakerWindow. Reports the final score
 * once on game-over, same GAME_PLAYED_REQUEST pattern as the other
 * single-player games. Embedded in MainMenu's game-host slot (see
 * ChessWindow's javadoc for the pattern); requestLeave() stops the
 * game timer.
 */
public class FlappyBirdWindow extends JPanel implements EmbeddedGamePanel
{
    private static final int TICK_MS = 16;

    private FlappyBirdGame game;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private Timer timer;
    private boolean reported;

    public FlappyBirdWindow()
    {
        setLayout(new BorderLayout());

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setBorder(new EmptyBorder(0, 0, 12, 0));

        statusLabel = new JLabel("Space, Up, or click to flap. Thread the gaps.");
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
        game = new FlappyBirdGame();
        reported = false;
        updateStatus();

        timer = new Timer(PerformanceMode.getTickIntervalMs(TICK_MS), new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                game.tick();
                updateStatus();
                boardPanel.repaint();
                if (game.isGameOver())
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
        statusLabel.setText(game.isStarted() ? "Score: " + game.getScore()
            : "Space, Up, or click to flap. Thread the gaps.");
    }

    private void finishGame()
    {
        int finalScore = game.getScore();
        statusLabel.setText("You crashed! Final score: " + finalScore);
        if (!reported)
        {
            reported = true;
            reportScore(finalScore);
        }

        SnakeGameOverDialog.show(this, finalScore, "You made it through " + finalScore + " gaps.",
            finalScore >= 10 ? "I scored " + finalScore + " in Flappy Bird on Vertex!" : null,
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
                request.setGameId("flappy-bird");
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
            setPreferredSize(new Dimension(FlappyBirdGame.BOARD_WIDTH, FlappyBirdGame.BOARD_HEIGHT));
            setFocusable(true);
            setBackground(new Color(120, 190, 230));
            bindKeys();
            addMouseListener(new MouseAdapter()
            {
                public void mousePressed(MouseEvent e) { game.flap(); }
            });
        }

        private void bindKeys()
        {
            bindFlapKey("SPACE");
            bindFlapKey("UP");
        }

        private void bindFlapKey(String keyName)
        {
            getInputMap().put(KeyStroke.getKeyStroke(keyName), keyName + "_flap");
            getActionMap().put(keyName + "_flap", new AbstractAction()
            {
                public void actionPerformed(ActionEvent e) { game.flap(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            g2.setColor(new Color(120, 190, 230));
            g2.fillRect(0, 0, getWidth(), getHeight());

            paintPipes(g2);
            paintBird(g2);

            g2.dispose();
        }

        private void paintPipes(Graphics2D g2)
        {
            g2.setColor(new Color(90, 190, 90));
            for (FlappyBirdGame.Pipe pipe : game.getPipes())
            {
                int x = (int) Math.round(pipe.x);
                g2.fillRect(x, 0, FlappyBirdGame.PIPE_WIDTH, pipe.gapTop);
                int bottomY = pipe.gapTop + FlappyBirdGame.PIPE_GAP;
                g2.fillRect(x, bottomY, FlappyBirdGame.PIPE_WIDTH, getHeight() - bottomY);
            }
        }

        private void paintBird(Graphics2D g2)
        {
            int r = FlappyBirdGame.BIRD_RADIUS;
            int x = FlappyBirdGame.BIRD_X, y = (int) Math.round(game.getBirdY());
            g2.setColor(new Color(255, 210, 60));
            g2.fillOval(x - r, y - r, r * 2, r * 2);
            g2.setColor(new Color(60, 40, 10));
            g2.fillOval(x + 3, y - 6, 5, 5);
        }
    }
}
