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
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * YahtzeeWindow
 * -------------
 * Offline - YahtzeeGame runs entirely client-side. Roll up to 3 times a
 * turn, click a die face to hold/unhold it between rolls, then click a
 * category row to score into it and end the turn. Turn-based, no Swing
 * Timer - just repaints after each action. Reports the final total via
 * the same GAME_PLAYED_REQUEST pattern as the other single-player
 * games. Embedded in MainMenu's game-host slot (see ChessWindow's
 * javadoc for the pattern); requestLeave() has nothing to confirm.
 */
public class YahtzeeWindow extends JPanel implements EmbeddedGamePanel
{
    private static final int DIE_SIZE = 64;
    private static final int DIE_GAP = 14;

    private YahtzeeGame game;
    private DicePanel dicePanel;
    private ScorecardPanel scorecardPanel;
    private JLabel statusLabel;
    private ThemedButton rollButton;
    private boolean reported;

    public YahtzeeWindow()
    {
        setLayout(new BorderLayout());

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setBorder(new EmptyBorder(0, 0, 12, 0));

        statusLabel = new JLabel("Roll the dice to begin.");
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

        JPanel centerColumn = new JPanel();
        centerColumn.setOpaque(false);
        centerColumn.setLayout(new javax.swing.BoxLayout(centerColumn, javax.swing.BoxLayout.Y_AXIS));

        dicePanel = new DicePanel();
        JPanel diceWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        diceWrap.setOpaque(false);
        diceWrap.add(dicePanel);
        centerColumn.add(diceWrap);

        rollButton = new ThemedButton("Roll", true);
        rollButton.setPreferredSize(new Dimension(160, 40));
        rollButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { handleRoll(); }
        });
        JPanel rollWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 14));
        rollWrap.setOpaque(false);
        rollWrap.add(rollButton);
        centerColumn.add(rollWrap);

        JPanel centerColumnCenterer = new JPanel(new GridBagLayout());
        centerColumnCenterer.setOpaque(false);
        centerColumnCenterer.add(centerColumn, new GridBagConstraints());
        root.add(centerColumnCenterer, BorderLayout.CENTER);

        scorecardPanel = new ScorecardPanel();
        root.add(scorecardPanel, BorderLayout.EAST);

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
        game = new YahtzeeGame();
        reported = false;
        updateStatus();
        dicePanel.repaint();
        scorecardPanel.rebuild();
    }

    private void updateStatus()
    {
        if (game.isOver())
        {
            return;
        }
        int rollsLeft = YahtzeeGame.MAX_ROLLS_PER_TURN - game.getRollsThisTurn();
        statusLabel.setText("Turn " + (game.getTurnsCompleted() + 1) + " of " + YahtzeeGame.NUM_CATEGORIES
            + " - rolls left this turn: " + rollsLeft);
        rollButton.setEnabled(game.canRoll());
    }

    private void handleRoll()
    {
        game.roll();
        updateStatus();
        dicePanel.repaint();
        scorecardPanel.rebuild();
    }

    private void handleToggleHold(int index)
    {
        game.toggleHold(index);
        dicePanel.repaint();
    }

    private void handleCommitCategory(int category)
    {
        if (game.isCategoryUsed(category) || game.getRollsThisTurn() == 0) return;
        game.commitCategory(category);
        updateStatus();
        dicePanel.repaint();
        scorecardPanel.rebuild();
        if (game.isOver())
        {
            finishGame();
        }
    }

    private void finishGame()
    {
        int finalScore = game.getTotalScore();
        statusLabel.setText("Scorecard complete! Final score: " + finalScore);
        rollButton.setEnabled(false);
        if (!reported)
        {
            reported = true;
            reportScore(finalScore);
        }

        SnakeGameOverDialog.show(this, finalScore, "Final score: " + finalScore,
            finalScore >= 200 ? "I scored " + finalScore + " points in Yahtzee on Vertex!" : null,
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
                request.setGameId("yahtzee");
                request.setScore(finalScore);
                NetworkManager.sendAsync(request);
            }
        });
        worker.start();
    }

    private class DicePanel extends JPanel
    {
        DicePanel()
        {
            int width = YahtzeeGame.NUM_DICE * (DIE_SIZE + DIE_GAP) + DIE_GAP;
            setPreferredSize(new Dimension(width, DIE_SIZE + 2 * DIE_GAP));
            setOpaque(false);
            addMouseListener(new MouseAdapter()
            {
                public void mousePressed(MouseEvent e)
                {
                    int index = (e.getX() - DIE_GAP) / (DIE_SIZE + DIE_GAP);
                    if (index >= 0 && index < YahtzeeGame.NUM_DICE && game.getRollsThisTurn() > 0)
                    {
                        handleToggleHold(index);
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

            int[] dice = game.getDice();
            for (int i = 0; i < YahtzeeGame.NUM_DICE; i++)
            {
                int x = DIE_GAP + i * (DIE_SIZE + DIE_GAP);
                int y = DIE_GAP;
                boolean held = game.isHeld(i);

                g2.setColor(held ? new Color(255, 210, 90) : Color.WHITE);
                g2.fillRoundRect(x, y, DIE_SIZE, DIE_SIZE, 12, 12);
                g2.setColor(held ? new Color(180, 130, 30) : new Color(120, 120, 120));
                g2.drawRoundRect(x, y, DIE_SIZE, DIE_SIZE, 12, 12);

                drawPips(g2, x, y, dice[i]);
            }

            g2.dispose();
        }

        private void drawPips(Graphics2D g2, int x, int y, int value)
        {
            g2.setColor(new Color(30, 30, 30));
            int r = 5;
            int cx = x + DIE_SIZE / 2, cy = y + DIE_SIZE / 2;
            int off = DIE_SIZE / 4;

            boolean[][] layout;
            switch (value)
            {
                case 1: layout = new boolean[][] { {false,false,false},{false,true,false},{false,false,false} }; break;
                case 2: layout = new boolean[][] { {true,false,false},{false,false,false},{false,false,true} }; break;
                case 3: layout = new boolean[][] { {true,false,false},{false,true,false},{false,false,true} }; break;
                case 4: layout = new boolean[][] { {true,false,true},{false,false,false},{true,false,true} }; break;
                case 5: layout = new boolean[][] { {true,false,true},{false,true,false},{true,false,true} }; break;
                default: layout = new boolean[][] { {true,false,true},{true,false,true},{true,false,true} }; break;
            }

            for (int row = 0; row < 3; row++)
            {
                for (int col = 0; col < 3; col++)
                {
                    if (!layout[row][col]) continue;
                    int px = cx + (col - 1) * off;
                    int py = cy + (row - 1) * off;
                    g2.fillOval(px - r, py - r, r * 2, r * 2);
                }
            }
        }
    }

    private class ScorecardPanel extends JPanel
    {
        private final List<JLabel> rowLabels = new ArrayList<JLabel>();

        ScorecardPanel()
        {
            setOpaque(false);
            setLayout(new GridLayout(YahtzeeGame.NUM_CATEGORIES + 1, 1, 0, 2));
            setBorder(new EmptyBorder(0, 20, 0, 0));
            setPreferredSize(new Dimension(220, (YahtzeeGame.NUM_CATEGORIES + 1) * 26));
        }

        void rebuild()
        {
            removeAll();
            rowLabels.clear();

            for (int c = 0; c < YahtzeeGame.NUM_CATEGORIES; c++)
            {
                final int category = c;
                String text = YahtzeeGame.CATEGORY_NAMES[c];
                if (game.isCategoryUsed(c))
                {
                    text += "  —  " + game.getCategoryScore(c);
                }
                else if (game.getRollsThisTurn() > 0 && !game.isOver())
                {
                    int preview = YahtzeeGame.scoreFor(c, game.getDice());
                    text += "  (" + preview + ")";
                }

                JLabel label = new JLabel(text);
                label.setFont(UITheme.FONT_BODY);
                label.setForeground(game.isCategoryUsed(c)
                    ? ThemeManager.getColor(ThemeColor.TEXT_SECONDARY)
                    : ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
                if (!game.isCategoryUsed(c))
                {
                    label.addMouseListener(new MouseAdapter()
                    {
                        public void mousePressed(MouseEvent e) { handleCommitCategory(category); }
                    });
                }
                add(label);
                rowLabels.add(label);
            }

            JLabel totalLabel = new JLabel("Total: " + game.getTotalScore()
                + " (upper bonus: " + game.getUpperBonus() + ")");
            totalLabel.setFont(UITheme.FONT_NAV_BOLD);
            totalLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
            add(totalLabel);

            revalidate();
            repaint();
        }
    }
}
