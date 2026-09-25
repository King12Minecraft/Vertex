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
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * KlondikeWindow
 * ---------------
 * Offline - KlondikeGame runs entirely client-side. Click the stock to
 * draw, click a card to pick it up (a tableau click picks up it and
 * everything face-up below it in that column), then click a
 * destination pile. Turn-based, so no Swing Timer - the board just
 * repaints after each click. Reports a score once all four foundations
 * are complete, same GAME_PLAYED_REQUEST pattern as the other
 * single-player games. Embedded in MainMenu's game-host slot (see
 * ChessWindow's javadoc for the pattern); requestLeave() has nothing
 * to confirm.
 */
public class KlondikeWindow extends JPanel implements EmbeddedGamePanel
{
    private static final int CARD_W = 64;
    private static final int CARD_H = 92;
    private static final int GAP = 12;
    private static final int STACK_OFFSET = 22;
    private static final int TOP_ROW_Y = 16;
    private static final int TABLEAU_Y = TOP_ROW_Y + CARD_H + 24;

    private static final Color COLOR_TABLE = new Color(18, 60, 40);
    private static final Color COLOR_SLOT = new Color(30, 90, 60);
    private static final Color COLOR_BACK = new Color(70, 90, 170);
    private static final Color COLOR_FACE = new Color(245, 245, 240);
    private static final Color COLOR_SELECTED_BORDER = new Color(255, 215, 90);

    private KlondikeGame game;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private boolean reported;

    public KlondikeWindow()
    {
        setLayout(new BorderLayout());

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setBorder(new EmptyBorder(0, 0, 12, 0));

        statusLabel = new JLabel("Click the stock to draw. Click a card, then where to move it.");
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
        game = new KlondikeGame();
        reported = false;
        updateStatus();
        boardPanel.repaint();
    }

    private void updateStatus()
    {
        if (!game.isOver())
        {
            statusLabel.setText("Foundations: " + game.getFoundationCardCount() + " / 52");
        }
    }

    private void afterInput()
    {
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
        statusLabel.setText("All four foundations complete! Score: " + finalScore);
        if (!reported)
        {
            reported = true;
            reportScore(finalScore);
        }

        SnakeGameOverDialog.show(this, finalScore, "You cleared the whole deck onto the foundations.",
            "I won a game of Klondike Solitaire on Vertex!",
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
                request.setGameId("klondike");
                request.setScore(finalScore);
                NetworkManager.sendAsync(request);
            }
        });
        worker.start();
    }

    private static String suitSymbol(int suit)
    {
        switch (suit)
        {
            case KlondikeGame.CLUBS: return "♣";
            case KlondikeGame.DIAMONDS: return "♦";
            case KlondikeGame.HEARTS: return "♥";
            default: return "♠";
        }
    }

    private static String rankLabel(int rank)
    {
        if (rank == 1) return "A";
        if (rank == 11) return "J";
        if (rank == 12) return "Q";
        if (rank == 13) return "K";
        return String.valueOf(rank);
    }

    private class BoardPanel extends JPanel
    {
        BoardPanel()
        {
            int width = GAP + KlondikeGame.COLUMNS * (CARD_W + GAP);
            int height = TABLEAU_Y + CARD_H + 19 * STACK_OFFSET + 20;
            setPreferredSize(new Dimension(width, height));
            setBackground(COLOR_TABLE);
            addMouseListener(new MouseAdapter()
            {
                public void mousePressed(MouseEvent e) { handleMouseClick(e.getX(), e.getY()); }
            });
        }

        private void handleMouseClick(int mx, int my)
        {
            // Stock pile (top-left).
            if (within(mx, my, GAP, TOP_ROW_Y))
            {
                game.clickStock();
                afterInput();
                return;
            }
            // Waste pile.
            if (within(mx, my, GAP + CARD_W + GAP, TOP_ROW_Y))
            {
                game.clickWaste();
                afterInput();
                return;
            }
            // Foundations (top-right, 4 slots).
            int foundationsStartX = GAP + (KlondikeGame.COLUMNS - KlondikeGame.SUITS) * (CARD_W + GAP);
            for (int s = 0; s < KlondikeGame.SUITS; s++)
            {
                int x = foundationsStartX + s * (CARD_W + GAP);
                if (within(mx, my, x, TOP_ROW_Y))
                {
                    game.clickFoundation(s);
                    afterInput();
                    return;
                }
            }
            // Tableau columns.
            for (int col = 0; col < KlondikeGame.COLUMNS; col++)
            {
                int x = GAP + col * (CARD_W + GAP);
                if (mx < x || mx > x + CARD_W) continue;
                List<KlondikeGame.Card> column = game.getTableauColumn(col);
                if (column.isEmpty())
                {
                    if (within(mx, my, x, TABLEAU_Y))
                    {
                        game.clickTableau(col, 0);
                        afterInput();
                    }
                    return;
                }
                int relativeY = my - TABLEAU_Y;
                if (relativeY < 0) return;
                int index = Math.min(column.size() - 1, relativeY / STACK_OFFSET);
                game.clickTableau(col, index);
                afterInput();
                return;
            }
        }

        private boolean within(int mx, int my, int x, int y)
        {
            return mx >= x && mx <= x + CARD_W && my >= y && my <= y + CARD_H;
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);
            g2.setFont(UITheme.FONT_NAV_BOLD.deriveFont(Font.BOLD, 15f));

            paintStockAndWaste(g2);
            paintFoundations(g2);
            paintTableau(g2);

            g2.dispose();
        }

        private void paintSlot(Graphics2D g2, int x, int y)
        {
            g2.setColor(COLOR_SLOT);
            g2.drawRoundRect(x, y, CARD_W, CARD_H, 8, 8);
        }

        private void paintCardBack(Graphics2D g2, int x, int y)
        {
            g2.setColor(COLOR_BACK);
            g2.fillRoundRect(x, y, CARD_W, CARD_H, 8, 8);
            g2.setColor(Color.WHITE);
            g2.drawRoundRect(x, y, CARD_W, CARD_H, 8, 8);
        }

        private void paintCardFace(Graphics2D g2, int x, int y, KlondikeGame.Card card, boolean selected)
        {
            g2.setColor(COLOR_FACE);
            g2.fillRoundRect(x, y, CARD_W, CARD_H, 8, 8);
            g2.setColor(selected ? COLOR_SELECTED_BORDER : Color.DARK_GRAY);
            g2.drawRoundRect(x, y, CARD_W, CARD_H, 8, 8);
            if (selected)
            {
                g2.drawRoundRect(x + 1, y + 1, CARD_W - 2, CARD_H - 2, 7, 7);
            }

            g2.setColor(card.isRed() ? new Color(200, 40, 40) : Color.BLACK);
            String label = rankLabel(card.rank) + suitSymbol(card.suit);
            g2.drawString(label, x + 6, y + 20);
            g2.drawString(suitSymbol(card.suit), x + CARD_W / 2 - 6, y + CARD_H / 2 + 6);
        }

        private void paintStockAndWaste(Graphics2D g2)
        {
            int stockX = GAP;
            paintSlot(g2, stockX, TOP_ROW_Y);
            if (!game.getStock().isEmpty())
            {
                paintCardBack(g2, stockX, TOP_ROW_Y);
            }

            int wasteX = GAP + CARD_W + GAP;
            paintSlot(g2, wasteX, TOP_ROW_Y);
            List<KlondikeGame.Card> waste = game.getWaste();
            if (!waste.isEmpty())
            {
                boolean selected = game.getSelectedType() == KlondikeGame.SourceType.WASTE;
                paintCardFace(g2, wasteX, TOP_ROW_Y, waste.get(waste.size() - 1), selected);
            }
        }

        private void paintFoundations(Graphics2D g2)
        {
            int foundationsStartX = GAP + (KlondikeGame.COLUMNS - KlondikeGame.SUITS) * (CARD_W + GAP);
            for (int s = 0; s < KlondikeGame.SUITS; s++)
            {
                int x = foundationsStartX + s * (CARD_W + GAP);
                paintSlot(g2, x, TOP_ROW_Y);
                List<KlondikeGame.Card> f = game.getFoundation(s);
                if (!f.isEmpty())
                {
                    paintCardFace(g2, x, TOP_ROW_Y, f.get(f.size() - 1), false);
                }
                else
                {
                    g2.setColor(new Color(255, 255, 255, 90));
                    g2.drawString(suitSymbol(s), x + CARD_W / 2 - 6, TOP_ROW_Y + CARD_H / 2 + 6);
                }
            }
        }

        private void paintTableau(Graphics2D g2)
        {
            for (int col = 0; col < KlondikeGame.COLUMNS; col++)
            {
                int x = GAP + col * (CARD_W + GAP);
                List<KlondikeGame.Card> column = game.getTableauColumn(col);
                if (column.isEmpty())
                {
                    paintSlot(g2, x, TABLEAU_Y);
                    continue;
                }
                for (int i = 0; i < column.size(); i++)
                {
                    int y = TABLEAU_Y + i * STACK_OFFSET;
                    KlondikeGame.Card card = column.get(i);
                    if (!card.faceUp)
                    {
                        paintCardBack(g2, x, y);
                    }
                    else
                    {
                        boolean selected = game.getSelectedType() == KlondikeGame.SourceType.TABLEAU
                            && game.getSelectedCol() == col && i >= game.getSelectedIndex();
                        paintCardFace(g2, x, y, card, selected);
                    }
                }
            }
        }
    }
}
