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
 * MazeChaseWindow
 * ---------------
 * Offline - MazeChaseGame runs entirely client-side, same pattern as
 * Snake. Arrow keys/WASD steer; a Swing Timer advances the game one
 * grid step at a time. On win or game-over (out of lives), reports
 * the final score once for a coin reward, same pattern Snake/Whack-a-
 * Mole use. Embedded in MainMenu's game-host slot (see ChessWindow's
 * javadoc for the pattern); requestLeave() stops the game timer.
 */
public class MazeChaseWindow extends JPanel implements EmbeddedGamePanel
{
    private static final int TICK_MS = 140;

    private MazeChaseGame game;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private Timer timer;
    private boolean reported;

    public MazeChaseWindow()
    {
        setLayout(new BorderLayout());

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setBorder(new EmptyBorder(0, 0, 12, 0));

        statusLabel = new JLabel("Arrows or WASD to move. Clear every dot, avoid the chasers.");
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
        game = new MazeChaseGame();
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
        statusLabel.setText((won ? "Cleared the maze! " : "Out of lives! ") + "Final score: " + finalScore);
        if (!reported)
        {
            reported = true;
            reportScore(finalScore);
        }

        SnakeGameOverDialog.show(this, finalScore,
            won ? "You cleared the whole maze!" : "The chasers got you.",
            finalScore >= 300 ? "I scored " + finalScore + " points in Maze Chase on Vertex!" : null,
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
                request.setGameId("maze-chase");
                request.setScore(finalScore);
                NetworkManager.sendAsync(request);
            }
        });
        worker.start();
    }

    private class BoardPanel extends JPanel
    {
        private static final int CELL = 28;

        BoardPanel()
        {
            setPreferredSize(new Dimension(CELL * MazeChaseGame.WIDTH, CELL * MazeChaseGame.HEIGHT));
            setFocusable(true);
            setBackground(Color.BLACK);
            bindKeys();
        }

        private void bindKeys()
        {
            bindKey("UP", MazeChaseGame.Direction.UP);
            bindKey("DOWN", MazeChaseGame.Direction.DOWN);
            bindKey("LEFT", MazeChaseGame.Direction.LEFT);
            bindKey("RIGHT", MazeChaseGame.Direction.RIGHT);
            bindKey("W", MazeChaseGame.Direction.UP);
            bindKey("S", MazeChaseGame.Direction.DOWN);
            bindKey("A", MazeChaseGame.Direction.LEFT);
            bindKey("D", MazeChaseGame.Direction.RIGHT);
        }

        private void bindKey(String keyName, final MazeChaseGame.Direction direction)
        {
            getInputMap().put(KeyStroke.getKeyStroke(keyName), keyName + "_action");
            getActionMap().put(keyName + "_action", new AbstractAction()
            {
                public void actionPerformed(ActionEvent e) { game.setPendingDirection(direction); }
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, getWidth(), getHeight());

            paintMaze(g2);
            paintChasers(g2);
            paintPlayer(g2);

            g2.dispose();
        }

        private void paintMaze(Graphics2D g2)
        {
            for (int row = 0; row < MazeChaseGame.HEIGHT; row++)
            {
                for (int col = 0; col < MazeChaseGame.WIDTH; col++)
                {
                    int x = col * CELL, y = row * CELL;
                    if (game.isWall(row, col))
                    {
                        g2.setColor(new Color(40, 60, 150));
                        g2.fillRect(x, y, CELL, CELL);
                    }
                    else if (game.hasPowerPellet(row, col))
                    {
                        g2.setColor(new Color(255, 210, 90));
                        g2.fillOval(x + CELL / 2 - 7, y + CELL / 2 - 7, 14, 14);
                    }
                    else if (game.hasPellet(row, col))
                    {
                        g2.setColor(new Color(255, 210, 90));
                        g2.fillOval(x + CELL / 2 - 3, y + CELL / 2 - 3, 6, 6);
                    }
                }
            }
        }

        private void paintPlayer(Graphics2D g2)
        {
            int x = game.getPlayerCol() * CELL, y = game.getPlayerRow() * CELL;
            g2.setColor(new Color(255, 220, 70));
            g2.fillOval(x + 3, y + 3, CELL - 6, CELL - 6);
        }

        private void paintChasers(Graphics2D g2)
        {
            boolean frightened = game.isFrightened();
            Color[] chaserColors = new Color[]
            {
                new Color(230, 90, 90),
                new Color(230, 140, 200),
                new Color(90, 210, 230)
            };
            java.util.List<MazeChaseGame.Chaser> chasers = game.getChasers();
            for (int i = 0; i < chasers.size(); i++)
            {
                MazeChaseGame.Chaser chaser = chasers.get(i);
                int x = chaser.col * CELL, y = chaser.row * CELL;
                g2.setColor(frightened ? new Color(150, 150, 220) : chaserColors[i % chaserColors.length]);
                g2.fillRoundRect(x + 3, y + 6, CELL - 6, CELL - 9, 10, 10);
            }
        }
    }
}
