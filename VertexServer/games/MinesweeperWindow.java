package games;

import net.Message;
import net.MessageType;
import net.NetworkManager;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.RoundedPanel;
import ui.ThemedButton;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * MinesweeperWindow
 * -----------------
 * Offline - MinesweeperGame runs entirely client-side, same as Snake/
 * Tetris/2048, since there's no opponent to keep in sync with. On a
 * win, reports completion to the server once (GAME_PLAYED_REQUEST,
 * gameId "minesweeper") for a flat coin reward, the same pattern
 * Puzzle Quest already uses.
 */
public class MinesweeperWindow extends JFrame
{
    private static final int[] NUMBER_COLORS_RGB = {
        0x000000, 0x1565C0, 0x2E7D32, 0xC62828, 0x6A1B9A, 0xE65100, 0x00838F, 0x424242, 0x757575
    };

    private MinesweeperGame game;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private boolean reported;

    public MinesweeperWindow()
    {
        super("Vertex - Minesweeper");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setBorder(new EmptyBorder(0, 0, 12, 0));

        statusLabel = new JLabel("Left-click to reveal, right-click to flag.");
        statusLabel.setFont(UITheme.FONT_NAV_BOLD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        topRow.add(statusLabel, BorderLayout.WEST);

        ThemedButton restart = new ThemedButton("Restart", false);
        restart.setPreferredSize(new Dimension(90, 34));
        restart.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { startNewGame(); }
        });
        JPanel restartWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        restartWrap.setOpaque(false);
        restartWrap.add(restart);
        topRow.add(restartWrap, BorderLayout.EAST);

        root.add(topRow, BorderLayout.NORTH);

        boardPanel = new BoardPanel();
        root.add(boardPanel, BorderLayout.CENTER);

        getContentPane().add(root);
        startNewGame();
        pack();
        setLocationRelativeTo(null);
        theme.SignatureOverlay.attach(this);
        theme.GlitchEffectOverlay.attach(this);
    }

    private void startNewGame()
    {
        game = new MinesweeperGame();
        reported = false;
        statusLabel.setText("Left-click to reveal, right-click to flag.");
        boardPanel.repaint();
    }

    private void handleReveal(int index)
    {
        if (game.isGameOver()) return;
        game.reveal(index);
        boardPanel.repaint();
        checkGameEnd();
    }

    private void handleFlag(int index)
    {
        if (game.isGameOver()) return;
        game.toggleFlag(index);
        boardPanel.repaint();
    }

    private void checkGameEnd()
    {
        if (!game.isGameOver()) return;

        if (game.isWon())
        {
            statusLabel.setText("Cleared it!");
            if (!reported)
            {
                reported = true;
                reportCompletion();
            }
        }
        else
        {
            statusLabel.setText("Hit a mine.");
        }

        SnakeGameOverDialog.show(this, 0, game.isWon() ? "You cleared the board!" : "You hit a mine.",
            game.isWon() ? "I cleared a Minesweeper board on Vertex!" : null,
            new SnakeGameOverDialog.Choice()
            {
                public void onPlayAgain() { startNewGame(); }
                public void onClose() { MinesweeperWindow.this.dispose(); }
            });
    }

    private void reportCompletion()
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.GAME_PLAYED_REQUEST);
                request.setGameId("minesweeper");
                NetworkManager.sendAsync(request);
            }
        });
        worker.start();
    }

    private class BoardPanel extends JPanel
    {
        private static final int CELL = 32;

        BoardPanel()
        {
            setPreferredSize(new Dimension(CELL * MinesweeperGame.COLS, CELL * MinesweeperGame.ROWS));
            addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e)
                {
                    int col = e.getX() / CELL;
                    int row = e.getY() / CELL;
                    if (col < 0 || col >= MinesweeperGame.COLS || row < 0 || row >= MinesweeperGame.ROWS) return;
                    int index = row * MinesweeperGame.COLS + col;

                    if (SwingUtilities.isRightMouseButton(e))
                    {
                        handleFlag(index);
                    }
                    else
                    {
                        handleReveal(index);
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

            for (int row = 0; row < MinesweeperGame.ROWS; row++)
            {
                for (int col = 0; col < MinesweeperGame.COLS; col++)
                {
                    int index = row * MinesweeperGame.COLS + col;
                    drawCell(g2, col, row, index);
                }
            }
            g2.dispose();
        }

        private void drawCell(Graphics2D g2, int col, int row, int index)
        {
            int x = col * CELL, y = row * CELL;
            int state = game.getState(index);
            boolean gameOver = game.isGameOver();
            boolean showMine = gameOver && game.isMine(index);

            if (state == MinesweeperGame.REVEALED || showMine)
            {
                g2.setColor(showMine && !game.isWon() ? new Color(230, 90, 90) : new Color(40, 42, 50));
                g2.fillRect(x, y, CELL - 1, CELL - 1);

                if (game.isMine(index))
                {
                    g2.setColor(Color.BLACK);
                    g2.fillOval(x + CELL / 4, y + CELL / 4, CELL / 2, CELL / 2);
                }
                else if (game.getAdjacentCount(index) > 0)
                {
                    int count = game.getAdjacentCount(index);
                    g2.setColor(new Color(NUMBER_COLORS_RGB[Math.min(count, 8)]));
                    g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
                    String text = String.valueOf(count);
                    int textWidth = g2.getFontMetrics().stringWidth(text);
                    g2.drawString(text, x + (CELL - textWidth) / 2, y + CELL - 10);
                }
            }
            else
            {
                g2.setColor(new Color(70, 74, 88));
                g2.fillRect(x, y, CELL - 1, CELL - 1);
                g2.setColor(new Color(90, 95, 112));
                g2.setStroke(new BasicStroke(1));
                g2.drawLine(x, y, x + CELL - 2, y);
                g2.drawLine(x, y, x, y + CELL - 2);

                if (state == MinesweeperGame.FLAGGED)
                {
                    g2.setColor(new Color(230, 90, 90));
                    g2.fillRect(x + CELL / 2 - 1, y + 7, 2, 14);
                    int[] xs = { x + CELL / 2 + 1, x + CELL / 2 + 1, x + CELL / 2 - 8 };
                    int[] ys = { y + 7, y + 15, y + 11 };
                    g2.fillPolygon(xs, ys, 3);
                }
            }
        }
    }
}
