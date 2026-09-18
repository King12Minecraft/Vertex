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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * MancalaWindow
 * -------------
 * Offline - MancalaGame runs entirely client-side, including its
 * built-in AI opponent. Click one of your 6 pits (bottom row) to sow
 * its seeds; the AI replies automatically (possibly taking several
 * bonus turns in a row, same rule as the player). Turn-based, no
 * Swing Timer. Reports a final score via the usual GAME_PLAYED_REQUEST
 * pattern once the board empties out on one side.
 */
public class MancalaWindow extends JFrame
{
    private static final int PIT_SIZE = 70;
    private static final int PIT_GAP = 10;
    private static final int STORE_WIDTH = 60;

    private static final Color COLOR_PIT = new Color(120, 80, 40);
    private static final Color COLOR_PIT_HOVER = new Color(150, 105, 55);
    private static final Color COLOR_STORE = new Color(90, 60, 30);
    private static final Color COLOR_SEED = new Color(230, 200, 80);

    private MancalaGame game;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private boolean reported;
    private int hoveredPit = -1;

    public MancalaWindow()
    {
        super("Vertex - Mancala");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setBorder(new EmptyBorder(0, 0, 12, 0));

        statusLabel = new JLabel("Your turn - click a pit on your side (bottom row).");
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
        game = new MancalaGame();
        reported = false;
        updateStatus();
        boardPanel.repaint();
    }

    private void updateStatus()
    {
        if (game.isOver())
        {
            return;
        }
        String turnText = game.isPlayerTurn() ? "Your turn" : "AI's turn";
        String extra = !game.getLastMessage().isEmpty() ? " - " + game.getLastMessage() : "";
        statusLabel.setText(turnText + extra + "  (You: " + game.getPlayerScore()
            + "  AI: " + game.getAiScore() + ")");
    }

    private void handlePitClick(int pit)
    {
        if (!game.isValidPlayerMove(pit)) return;
        game.playerMove(pit);
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
        String outcome = game.didPlayerWin() ? "You win! " + game.getPlayerScore() + " to " + game.getAiScore()
            : game.isTie() ? "It's a tie, " + game.getPlayerScore() + " to " + game.getAiScore()
            : "AI wins, " + game.getAiScore() + " to " + game.getPlayerScore();
        statusLabel.setText(outcome + " - Score: " + finalScore);
        if (!reported)
        {
            reported = true;
            reportScore(finalScore);
        }

        SnakeGameOverDialog.show(this, finalScore, outcome,
            game.didPlayerWin() ? "I beat the AI at Mancala " + game.getPlayerScore()
                + "-" + game.getAiScore() + " on Vertex!" : null,
            new SnakeGameOverDialog.Choice()
            {
                public void onPlayAgain() { startNewGame(); }
                public void onClose() { MancalaWindow.this.dispose(); }
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
                request.setGameId("mancala");
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
            int width = 2 * STORE_WIDTH + MancalaGame.PITS_PER_SIDE * (PIT_SIZE + PIT_GAP) + PIT_GAP;
            int height = 2 * (PIT_SIZE + PIT_GAP) + PIT_GAP;
            setPreferredSize(new Dimension(width, height));
            setBackground(new Color(60, 40, 20));

            addMouseListener(new MouseAdapter()
            {
                public void mousePressed(MouseEvent e)
                {
                    int pit = pitAt(e.getX(), e.getY());
                    if (pit >= 0 && pit < MancalaGame.PITS_PER_SIDE)
                    {
                        handlePitClick(pit);
                    }
                }
            });
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter()
            {
                public void mouseMoved(MouseEvent e)
                {
                    int newHover = pitAt(e.getX(), e.getY());
                    if (newHover != hoveredPit)
                    {
                        hoveredPit = newHover;
                        repaint();
                    }
                }
            });
        }

        /** Returns the player-pit index (0-5, bottom row only) at the given point, or -1. */
        private int pitAt(int x, int y)
        {
            int rowTop = STORE_WIDTH == 0 ? 0 : PIT_GAP + (PIT_SIZE + PIT_GAP);
            if (y < rowTop || y > rowTop + PIT_SIZE) return -1;
            for (int i = 0; i < MancalaGame.PITS_PER_SIDE; i++)
            {
                // Bottom row runs left-to-right as pit 0..5, mirroring the top (AI) row visually reversed.
                int px = STORE_WIDTH + PIT_GAP + i * (PIT_SIZE + PIT_GAP);
                if (x >= px && x <= px + PIT_SIZE) return i;
            }
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            int topRowY = PIT_GAP;
            int bottomRowY = PIT_GAP + (PIT_SIZE + PIT_GAP);

            // Two stores, spanning the full height on each end.
            g2.setColor(COLOR_STORE);
            g2.fillRoundRect(0, PIT_GAP, STORE_WIDTH, 2 * PIT_SIZE + PIT_GAP, 14, 14);
            g2.fillRoundRect(getWidth() - STORE_WIDTH, PIT_GAP, STORE_WIDTH, 2 * PIT_SIZE + PIT_GAP, 14, 14);

            drawSeedCount(g2, STORE_WIDTH / 2, PIT_GAP + PIT_SIZE, game.getPit(MancalaGame.AI_STORE));
            drawSeedCount(g2, getWidth() - STORE_WIDTH / 2, PIT_GAP + PIT_SIZE, game.getPit(MancalaGame.PLAYER_STORE));

            // AI row (top, pits 12 down to 7, displayed right-to-left so it visually faces the player's row).
            for (int i = 0; i < MancalaGame.PITS_PER_SIDE; i++)
            {
                int pitIndex = MancalaGame.AI_STORE - 1 - i; // 12, 11, ..., 7
                int px = STORE_WIDTH + PIT_GAP + i * (PIT_SIZE + PIT_GAP);
                g2.setColor(COLOR_PIT);
                g2.fillOval(px, topRowY, PIT_SIZE, PIT_SIZE);
                drawSeedCount(g2, px + PIT_SIZE / 2, topRowY + PIT_SIZE / 2, game.getPit(pitIndex));
            }

            // Player row (bottom, pits 0-5 left-to-right).
            for (int i = 0; i < MancalaGame.PITS_PER_SIDE; i++)
            {
                int px = STORE_WIDTH + PIT_GAP + i * (PIT_SIZE + PIT_GAP);
                boolean valid = game.isValidPlayerMove(i);
                boolean hover = hoveredPit == i && valid;
                g2.setColor(hover ? COLOR_PIT_HOVER : COLOR_PIT);
                g2.fillOval(px, bottomRowY, PIT_SIZE, PIT_SIZE);
                drawSeedCount(g2, px + PIT_SIZE / 2, bottomRowY + PIT_SIZE / 2, game.getPit(i));
            }

            g2.dispose();
        }

        private void drawSeedCount(Graphics2D g2, int centerX, int centerY, int count)
        {
            g2.setColor(COLOR_SEED);
            g2.setFont(UITheme.FONT_NAV_BOLD);
            String text = String.valueOf(count);
            int textWidth = g2.getFontMetrics().stringWidth(text);
            g2.drawString(text, centerX - textWidth / 2, centerY + 5);
        }
    }
}
