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
 * PegSolitaireWindow
 * --------------------
 * Offline - PegSolitaireGame runs entirely client-side. Click a peg to
 * select it, then click a hole two cells away in the same row/column to
 * jump it there. Turn-based, so no Swing Timer - the board just
 * repaints after each click. Reports a score once no more jumps are
 * possible, same GAME_PLAYED_REQUEST pattern as the other single-player
 * games. Embedded in MainMenu's game-host slot (see ChessWindow's
 * javadoc for the pattern); requestLeave() has nothing to confirm.
 */
public class PegSolitaireWindow extends JPanel implements EmbeddedGamePanel
{
    private static final int CELL = 56;
    private static final int GAP = 4;

    private static final Color COLOR_PEG = new Color(210, 170, 90);
    private static final Color COLOR_PEG_SELECTED = new Color(255, 210, 90);
    private static final Color COLOR_HOLE = new Color(45, 45, 58);
    private static final Color COLOR_INVALID = new Color(24, 24, 32);

    private PegSolitaireGame game;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private boolean reported;

    public PegSolitaireWindow()
    {
        setLayout(new BorderLayout());

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setBorder(new EmptyBorder(0, 0, 12, 0));

        statusLabel = new JLabel("Click a peg, then a hole two spaces away to jump it there.");
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
        game = new PegSolitaireGame();
        reported = false;
        updateStatus();
        boardPanel.repaint();
    }

    private void updateStatus()
    {
        if (!game.isOver())
        {
            statusLabel.setText("Pegs remaining: " + game.getPegsRemaining());
        }
    }

    private void handleClick(int row, int col)
    {
        game.click(row, col);
        updateStatus();
        boardPanel.repaint();
        if (game.isOver())
        {
            finishGame();
        }
    }

    private void finishGame()
    {
        int finalScore = game.getScore();
        String outcome = game.isPerfectFinish() ? "Perfect finish - down to one, right in the center! "
            : game.isWon() ? "Solved - just one peg left! "
            : "No more jumps - " + game.getPegsRemaining() + " pegs left. ";
        statusLabel.setText(outcome + "Score: " + finalScore);
        if (!reported)
        {
            reported = true;
            reportScore(finalScore);
        }

        SnakeGameOverDialog.show(this, finalScore, outcome.trim(),
            game.isWon() && finalScore >= 300 ? "I finished Peg Solitaire with " + game.getPegsRemaining()
                + " peg(s) left on Vertex!" : null,
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
                request.setGameId("peg-solitaire");
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
            int size = PegSolitaireGame.SIZE * (CELL + GAP) + GAP;
            setPreferredSize(new Dimension(size, size));
            setBackground(new Color(20, 20, 28));
            addMouseListener(new MouseAdapter()
            {
                public void mousePressed(MouseEvent e)
                {
                    int col = (e.getX() - GAP) / (CELL + GAP);
                    int row = (e.getY() - GAP) / (CELL + GAP);
                    if (row >= 0 && row < PegSolitaireGame.SIZE && col >= 0 && col < PegSolitaireGame.SIZE)
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

            for (int row = 0; row < PegSolitaireGame.SIZE; row++)
            {
                for (int col = 0; col < PegSolitaireGame.SIZE; col++)
                {
                    int x = GAP + col * (CELL + GAP);
                    int y = GAP + row * (CELL + GAP);

                    if (!game.isValidCell(row, col))
                    {
                        g2.setColor(COLOR_INVALID);
                        g2.fillRect(x, y, CELL, CELL);
                        continue;
                    }

                    g2.setColor(COLOR_HOLE);
                    g2.fillOval(x + 6, y + 6, CELL - 12, CELL - 12);

                    if (game.hasPeg(row, col))
                    {
                        boolean selected = game.hasSelection()
                            && game.getSelectedRow() == row && game.getSelectedCol() == col;
                        g2.setColor(selected ? COLOR_PEG_SELECTED : COLOR_PEG);
                        g2.fillOval(x + 12, y + 12, CELL - 24, CELL - 24);
                        if (selected)
                        {
                            g2.setColor(Color.WHITE);
                            g2.drawOval(x + 12, y + 12, CELL - 24, CELL - 24);
                        }
                    }
                }
            }

            g2.dispose();
        }
    }
}
