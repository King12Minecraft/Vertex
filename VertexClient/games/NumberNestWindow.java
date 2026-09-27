package games;
import theme.ThemeManager;
import net.NetworkManager;
import net.MessageType;
import net.Message;
import economy.GuestPlayTracker;
import account.Session;
import pages.MainMenu;
import ui.GameHubDialog;
import ui.ThemedButton;
import theme.GameColors;
import theme.UITheme;
import theme.ThemeColor;
import ui.RoundedPanel;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
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
 * NumberNestWindow
 * ----------------
 * Window + rendering combined into one file, same shape as
 * Merge2048Window - the grid is simple enough not to need a separate
 * panel class. Click any empty cell to place the current piece (shown
 * above the board, alongside a preview of the next one); model logic
 * lives in NumberNestGame. Single-player, fully offline - embedded in
 * MainMenu's game-host slot (see ChessWindow's javadoc for the pattern);
 * requestLeave() has nothing to confirm.
 */
public class NumberNestWindow extends JPanel implements EmbeddedGamePanel
{
    private final NumberNestGame game = new NumberNestGame();
    private BoardPanel board;
    private JLabel scoreLabel;
    private JLabel pieceLabel;

    public NumberNestWindow()
    {
        setLayout(new BorderLayout());

        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BorderLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new javax.swing.BoxLayout(header, javax.swing.BoxLayout.Y_AXIS));

        scoreLabel = new JLabel("Score: 0");
        scoreLabel.setFont(UITheme.FONT_NAV_BOLD);
        scoreLabel.setForeground(GameColors.TEXT_PRIMARY);
        header.add(scoreLabel);

        pieceLabel = new JLabel(pieceStatusText());
        pieceLabel.setFont(UITheme.FONT_SUBHEAD);
        pieceLabel.setForeground(GameColors.TEXT_SECONDARY);
        pieceLabel.setBorder(new EmptyBorder(4, 0, 14, 0));
        header.add(pieceLabel);

        panel.add(header, BorderLayout.NORTH);

        board = new BoardPanel();
        JPanel boardCenterer = new JPanel(new GridBagLayout());
        boardCenterer.setOpaque(false);
        boardCenterer.add(board, new GridBagConstraints());
        panel.add(boardCenterer, BorderLayout.CENTER);

        ThemedButton close = new ThemedButton("Leave", false);
        close.setPreferredSize(new Dimension(100, 36));
        close.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { MainMenu.getInstance().returnToGames(); }
        });
        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottomRow.setOpaque(false);
        bottomRow.setBorder(new EmptyBorder(14, 0, 0, 0));
        bottomRow.add(close);
        panel.add(bottomRow, BorderLayout.SOUTH);

        add(panel, BorderLayout.CENTER);
    }

    @Override
    public boolean requestLeave()
    {
        return true;
    }

    private String pieceStatusText()
    {
        return "Place: " + game.getCurrentPiece() + "   (next: " + game.getNextPiece() + ")";
    }

    private void afterPlace(boolean placed)
    {
        if (!placed)
        {
            return;
        }
        scoreLabel.setText("Score: " + game.getScore());
        pieceLabel.setText(pieceStatusText());
        board.repaint();

        if (game.isGameOver())
        {
            recordPlayed(game.getScore());
            GameHubDialog.show(board, "Number Nest", "Grid's full - final score: " + game.getScore() + ".");
        }
    }

    private void recordPlayed(int score)
    {
        if (!Session.isLoggedIn())
        {
            GuestPlayTracker.recordGuestPlay("number-nest", score);
            return;
        }

        Message request = new Message();
        request.setType(MessageType.GAME_PLAYED_REQUEST);
        request.setGameId("number-nest");
        request.setScore(score);
        NetworkManager.sendAsync(request);
    }

    private class BoardPanel extends JPanel
    {
        private static final int CELL = 64;
        private static final int GAP = 8;

        BoardPanel()
        {
            int size = NumberNestGame.SIZE * CELL + (NumberNestGame.SIZE + 1) * GAP;
            setPreferredSize(new Dimension(size, size));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e)
                {
                    int col = (e.getX() - GAP) / (CELL + GAP);
                    int row = (e.getY() - GAP) / (CELL + GAP);
                    afterPlace(game.place(row, col));
                }
            });

            ThemeManager.addListener(new Runnable()
            {
                public void run() { repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            g2.setColor(GameColors.BG_BOARD);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);

            for (int row = 0; row < NumberNestGame.SIZE; row++)
            {
                for (int col = 0; col < NumberNestGame.SIZE; col++)
                {
                    int x = GAP + col * (CELL + GAP);
                    int y = GAP + row * (CELL + GAP);
                    int value = game.getTile(row, col);

                    g2.setColor(tileColor(value));
                    g2.fillRoundRect(x, y, CELL, CELL, 8, 8);

                    if (value != 0)
                    {
                        g2.setColor(value <= 4 ? GameColors.TEXT_PRIMARY : Color.WHITE);
                        g2.setFont(UITheme.FONT_NAV_BOLD.deriveFont(value >= 1024 ? 16f : 20f));
                        String text = String.valueOf(value);
                        int textW = g2.getFontMetrics().stringWidth(text);
                        g2.drawString(text, x + (CELL - textW) / 2, y + CELL / 2 + 7);
                    }
                }
            }

            g2.dispose();
        }

        /** Darker for low values, brighter/more saturated toward the theme accent as tiles grow - same formula Merge2048Window's board uses, for a consistent "bigger number = bolder tile" language across both number-merge games. */
        private Color tileColor(int value)
        {
            if (value == 0)
            {
                return GameColors.BG_BOARD;
            }
            Color accent = GameColors.ACCENT;
            int step = (int) (Math.log(value) / Math.log(2));
            float brightness = Math.min(1f, 0.35f + step * 0.06f);

            int r = clamp((int) (accent.getRed() * brightness));
            int g = clamp((int) (accent.getGreen() * brightness));
            int b = clamp((int) (accent.getBlue() * brightness));
            return new Color(r, g, b);
        }

        private int clamp(int value)
        {
            return Math.max(0, Math.min(255, value));
        }
    }
}
