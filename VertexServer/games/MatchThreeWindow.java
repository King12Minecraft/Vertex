package games;

import net.Message;
import net.MessageType;
import net.NetworkManager;
import theme.GlitchEffectOverlay;
import theme.SignatureOverlay;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.ThemedButton;

import javax.swing.JFrame;
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * MatchThreeWindow
 * ----------------
 * Offline - MatchThreeGame runs entirely client-side, same pattern as
 * Sudoku/Minesweeper/Snake. Click a gem, then click an adjacent gem to
 * swap them; a swap that forms a match resolves (and cascades) with a
 * score bump, an invalid swap just bounces back - either way it costs
 * one of the limited moves. On running out of moves, reports the final
 * score once for a coin reward, same pattern Whack-a-Mole/Aim Trainer use.
 */
public class MatchThreeWindow extends JFrame
{
    private MatchThreeGame game;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private int selectedRow = -1, selectedCol = -1;
    private boolean reported;

    private static final Color[] GEM_COLORS = new Color[]
    {
        new Color(230, 90, 90),
        new Color(90, 170, 230),
        new Color(90, 210, 130),
        new Color(235, 200, 80),
        new Color(190, 110, 230),
        new Color(240, 150, 70)
    };

    public MatchThreeWindow()
    {
        super("Vertex - Gem Match");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setBorder(new EmptyBorder(0, 0, 12, 0));

        statusLabel = new JLabel("Click a gem, then an adjacent gem to swap.");
        statusLabel.setFont(UITheme.FONT_NAV_BOLD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        topRow.add(statusLabel, BorderLayout.WEST);

        ThemedButton restart = new ThemedButton("New Game", false);
        restart.setPreferredSize(new Dimension(100, 34));
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
        SignatureOverlay.attach(this);
        GlitchEffectOverlay.attach(this);
    }

    private void startNewGame()
    {
        game = new MatchThreeGame();
        reported = false;
        selectedRow = -1;
        selectedCol = -1;
        updateStatus();
        boardPanel.repaint();
    }

    private void updateStatus()
    {
        statusLabel.setText("Score: " + game.getScore() + "    Moves left: " + game.getMovesLeft());
    }

    private void handleClick(int row, int col)
    {
        if (game.isOver()) return;

        if (selectedRow < 0)
        {
            selectedRow = row;
            selectedCol = col;
            boardPanel.repaint();
            return;
        }

        if (selectedRow == row && selectedCol == col)
        {
            selectedRow = -1;
            selectedCol = -1;
            boardPanel.repaint();
            return;
        }

        game.trySwap(selectedRow, selectedCol, row, col);
        selectedRow = -1;
        selectedCol = -1;
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
        statusLabel.setText("Out of moves! Final score: " + finalScore);
        if (!reported)
        {
            reported = true;
            reportScore(finalScore);
        }

        SnakeGameOverDialog.show(this, finalScore, "You scored " + finalScore + " points!",
            finalScore >= 200 ? "I scored " + finalScore + " points in Gem Match on Vertex!" : null,
            new SnakeGameOverDialog.Choice()
            {
                public void onPlayAgain() { startNewGame(); }
                public void onClose() { MatchThreeWindow.this.dispose(); }
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
                request.setGameId("match-three");
                request.setScore(finalScore);
                NetworkManager.sendAsync(request);
            }
        });
        worker.start();
    }

    private class BoardPanel extends JPanel
    {
        private static final int CELL = 48;
        private static final int GEM_MARGIN = 6;

        BoardPanel()
        {
            setPreferredSize(new Dimension(CELL * MatchThreeGame.SIZE, CELL * MatchThreeGame.SIZE));
            setOpaque(false);
            addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e)
                {
                    int col = e.getX() / CELL, row = e.getY() / CELL;
                    if (col >= 0 && col < MatchThreeGame.SIZE && row >= 0 && row < MatchThreeGame.SIZE)
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

            g2.setColor(new Color(30, 32, 40));
            g2.fillRect(0, 0, getWidth(), getHeight());

            for (int row = 0; row < MatchThreeGame.SIZE; row++)
            {
                for (int col = 0; col < MatchThreeGame.SIZE; col++)
                {
                    drawGem(g2, row, col);
                }
            }

            g2.setColor(new Color(55, 58, 70));
            g2.setStroke(new BasicStroke(1));
            for (int i = 0; i <= MatchThreeGame.SIZE; i++)
            {
                g2.drawLine(i * CELL, 0, i * CELL, CELL * MatchThreeGame.SIZE);
                g2.drawLine(0, i * CELL, CELL * MatchThreeGame.SIZE, i * CELL);
            }
            g2.dispose();
        }

        private void drawGem(Graphics2D g2, int row, int col)
        {
            int x = col * CELL, y = row * CELL;
            int color = game.getCell(row, col);
            if (color < 0 || color >= GEM_COLORS.length) return;

            boolean isSelected = row == selectedRow && col == selectedCol;
            if (isSelected)
            {
                g2.setColor(new Color(255, 255, 255, 60));
                g2.fillRoundRect(x + 1, y + 1, CELL - 2, CELL - 2, 10, 10);
            }

            g2.setColor(GEM_COLORS[color]);
            g2.fillRoundRect(x + GEM_MARGIN, y + GEM_MARGIN, CELL - GEM_MARGIN * 2, CELL - GEM_MARGIN * 2, 12, 12);
            g2.setColor(GEM_COLORS[color].darker());
            g2.setStroke(new BasicStroke(2));
            g2.drawRoundRect(x + GEM_MARGIN, y + GEM_MARGIN, CELL - GEM_MARGIN * 2, CELL - GEM_MARGIN * 2, 12, 12);
        }
    }
}
