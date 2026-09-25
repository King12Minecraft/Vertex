package games;

import net.Message;
import net.MessageType;
import net.NetworkManager;
import pages.MainMenu;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.ThemedButton;

import javax.swing.JLabel;
import javax.swing.JPanel;
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
 * LightsOutWindow
 * -----------------
 * Offline - LightsOutGame runs entirely client-side. Click any cell to
 * toggle it and its orthogonal neighbors. Turn-based, so unlike the
 * arcade games there's no Swing Timer - the board just repaints after
 * each click. Reports a score once solved, same GAME_PLAYED_REQUEST
 * pattern as the other single-player games. Embedded in MainMenu's
 * game-host slot (see ChessWindow's javadoc for the pattern);
 * requestLeave() has nothing to confirm.
 */
public class LightsOutWindow extends JPanel implements EmbeddedGamePanel
{
    private static final int CELL = 64;
    private static final int GAP = 6;

    private static final Color COLOR_ON = new Color(255, 210, 90);
    private static final Color COLOR_OFF = new Color(45, 45, 58);
    private static final Color COLOR_BORDER = new Color(90, 90, 105);

    private LightsOutGame game;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private boolean reported;

    public LightsOutWindow()
    {
        setLayout(new BorderLayout());

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setBorder(new EmptyBorder(0, 0, 12, 0));

        statusLabel = new JLabel("Click a light to toggle it and its neighbors. Turn them all off.");
        statusLabel.setFont(UITheme.FONT_NAV_BOLD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        topRow.add(statusLabel, BorderLayout.WEST);

        ThemedButton restart = new ThemedButton("New Puzzle", false);
        restart.setPreferredSize(new Dimension(110, 34));
        restart.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { startNewGame(); }
        });
        ThemedButton leave = new ThemedButton("Leave", false);
        leave.setPreferredSize(new Dimension(90, 34));
        leave.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { MainMenu.getInstance().returnToGames(); }
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
        return true;
    }

    private void startNewGame()
    {
        game = new LightsOutGame();
        reported = false;
        updateStatus();
        boardPanel.repaint();
    }

    private void updateStatus()
    {
        if (!game.isWon())
        {
            statusLabel.setText("Moves: " + game.getMovesUsed());
        }
    }

    private void handleClick(int row, int col)
    {
        game.click(row, col);
        updateStatus();
        boardPanel.repaint();
        if (game.isWon())
        {
            finishGame();
        }
    }

    private void finishGame()
    {
        int finalScore = game.getScore();
        statusLabel.setText("Solved in " + game.getMovesUsed() + " moves! Score: " + finalScore);
        if (!reported)
        {
            reported = true;
            reportScore(finalScore);
        }

        SnakeGameOverDialog.show(this, finalScore, "Solved it in " + game.getMovesUsed() + " moves.",
            finalScore >= 300 ? "I solved Lights Out in " + game.getMovesUsed() + " moves on Vertex!" : null,
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
                request.setGameId("lights-out");
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
            int size = LightsOutGame.SIZE * (CELL + GAP) + GAP;
            setPreferredSize(new Dimension(size, size));
            setBackground(new Color(24, 24, 32));
            addMouseListener(new MouseAdapter()
            {
                public void mousePressed(MouseEvent e)
                {
                    int col = (e.getX() - GAP) / (CELL + GAP);
                    int row = (e.getY() - GAP) / (CELL + GAP);
                    if (row >= 0 && row < LightsOutGame.SIZE && col >= 0 && col < LightsOutGame.SIZE)
                    {
                        handleClick(row, col);
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            for (int row = 0; row < LightsOutGame.SIZE; row++)
            {
                for (int col = 0; col < LightsOutGame.SIZE; col++)
                {
                    int x = GAP + col * (CELL + GAP);
                    int y = GAP + row * (CELL + GAP);
                    g2.setColor(game.isOn(row, col) ? COLOR_ON : COLOR_OFF);
                    g2.fillRoundRect(x, y, CELL, CELL, 10, 10);
                    g2.setColor(COLOR_BORDER);
                    g2.drawRoundRect(x, y, CELL, CELL, 10, 10);
                }
            }

            g2.dispose();
        }
    }
}
