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
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * SudokuWindow
 * ------------
 * Offline - SudokuGame runs entirely client-side, same as Minesweeper/
 * Snake/Tetris, since there's no opponent to keep in sync with. Click
 * a cell to select it, type 1-9 to fill it in (or 0/Backspace/Delete
 * to clear it) - fixed "given" cells can't be edited. On a win,
 * reports completion to the server once for a flat coin reward, same
 * pattern Minesweeper/Puzzle Quest already use. Embedded in MainMenu's
 * game-host slot (see ChessWindow's javadoc for the pattern);
 * requestLeave() has nothing to confirm.
 */
public class SudokuWindow extends JPanel implements EmbeddedGamePanel
{
    private SudokuGame game;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private int selectedIndex = -1;
    private boolean reported;

    public SudokuWindow()
    {
        setLayout(new BorderLayout());

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setBorder(new EmptyBorder(0, 0, 12, 0));

        statusLabel = new JLabel("Click a cell, then type 1-9. Backspace clears it.");
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
        game = new SudokuGame();
        reported = false;
        selectedIndex = -1;
        statusLabel.setText("Click a cell, then type 1-9. Backspace clears it.");
        boardPanel.requestFocusInWindow();
        boardPanel.repaint();
    }

    private void selectCell(int index)
    {
        selectedIndex = index;
        boardPanel.repaint();
    }

    private void enterValue(int value)
    {
        if (selectedIndex < 0 || game.isWon()) return;
        game.setValue(selectedIndex, value);
        boardPanel.repaint();

        if (game.isWon())
        {
            statusLabel.setText("Solved it!");
            if (!reported)
            {
                reported = true;
                reportCompletion();
            }
            SnakeGameOverDialog.show(this, 0, "You solved the puzzle!", "I solved a Sudoku puzzle on Vertex!",
                new SnakeGameOverDialog.Choice()
                {
                    public void onPlayAgain() { startNewGame(); }
                    public void onClose() { MainMenu.getInstance().returnToGames(); }
                });
        }
    }

    private void reportCompletion()
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.GAME_PLAYED_REQUEST);
                request.setGameId("sudoku");
                NetworkManager.sendAsync(request);
            }
        });
        worker.start();
    }

    private class BoardPanel extends JPanel
    {
        private static final int CELL = 48;

        BoardPanel()
        {
            setPreferredSize(new Dimension(CELL * SudokuGame.SIZE, CELL * SudokuGame.SIZE));
            setFocusable(true);
            addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e)
                {
                    int col = e.getX() / CELL, row = e.getY() / CELL;
                    if (col >= 0 && col < SudokuGame.SIZE && row >= 0 && row < SudokuGame.SIZE)
                    {
                        selectCell(row * SudokuGame.SIZE + col);
                        requestFocusInWindow();
                    }
                }
            });
            addKeyListener(new KeyAdapter()
            {
                public void keyTyped(KeyEvent e)
                {
                    char c = e.getKeyChar();
                    if (c >= '1' && c <= '9')
                    {
                        enterValue(c - '0');
                    }
                }
                public void keyPressed(KeyEvent e)
                {
                    if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE || e.getKeyCode() == KeyEvent.VK_DELETE
                        || e.getKeyCode() == KeyEvent.VK_0)
                    {
                        enterValue(0);
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

            for (int row = 0; row < SudokuGame.SIZE; row++)
            {
                for (int col = 0; col < SudokuGame.SIZE; col++)
                {
                    drawCell(g2, row, col);
                }
            }

            g2.setColor(new Color(150, 155, 175));
            g2.setStroke(new BasicStroke(3));
            for (int i = 0; i <= SudokuGame.SIZE; i += SudokuGame.BOX_SIZE)
            {
                g2.drawLine(i * CELL, 0, i * CELL, CELL * SudokuGame.SIZE);
                g2.drawLine(0, i * CELL, CELL * SudokuGame.SIZE, i * CELL);
            }
            g2.dispose();
        }

        private void drawCell(Graphics2D g2, int row, int col)
        {
            int index = row * SudokuGame.SIZE + col;
            int x = col * CELL, y = row * CELL;

            boolean isSelected = index == selectedIndex;
            g2.setColor(isSelected ? new Color(60, 70, 100) : new Color(35, 37, 46));
            g2.fillRect(x, y, CELL, CELL);
            g2.setColor(new Color(55, 58, 70));
            g2.drawRect(x, y, CELL, CELL);

            int value = game.getValue(index);
            if (value != 0)
            {
                boolean fixed = game.isFixed(index);
                g2.setColor(fixed ? ThemeManager.getColor(ThemeColor.TEXT_PRIMARY) : ThemeManager.getColor(ThemeColor.ACCENT));
                g2.setFont(new Font(Font.SANS_SERIF, fixed ? Font.BOLD : Font.PLAIN, 22));
                String text = String.valueOf(value);
                int textWidth = g2.getFontMetrics().stringWidth(text);
                g2.drawString(text, x + (CELL - textWidth) / 2, y + CELL - 15);
            }
        }
    }
}
