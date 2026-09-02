import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Merge2048Window
 * ---------------
 * Window + rendering combined into one file (the grid is simple enough
 * not to need a separate panel class) - arrow keys or WASD slide the
 * grid, model logic lives in Merge2048Game. Single-player, fully
 * offline.
 */
public class Merge2048Window extends JFrame
{
    private final Merge2048Game game = new Merge2048Game();
    private BoardPanel board;
    private JLabel scoreLabel;

    public Merge2048Window()
    {
        super("Vertex - 2048");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BorderLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        scoreLabel = new JLabel("Score: 0");
        scoreLabel.setFont(UITheme.FONT_NAV_BOLD);
        scoreLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        scoreLabel.setBorder(new EmptyBorder(0, 0, 14, 0));
        panel.add(scoreLabel, BorderLayout.NORTH);

        board = new BoardPanel();
        panel.add(board, BorderLayout.CENTER);

        ThemedButton close = new ThemedButton("Close", false);
        close.setPreferredSize(new Dimension(100, 36));
        close.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { dispose(); }
        });
        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottomRow.setOpaque(false);
        bottomRow.setBorder(new EmptyBorder(14, 0, 0, 0));
        bottomRow.add(close);
        panel.add(bottomRow, BorderLayout.SOUTH);

        getContentPane().add(panel, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
        SignatureOverlay.attach(this);
        GlitchEffectOverlay.attach(this);
        board.requestFocusInWindow();
    }

    private void afterMove(boolean moved)
    {
        if (!moved)
        {
            return;
        }
        scoreLabel.setText("Score: " + game.getScore());
        board.repaint();

        if (game.isGameOver())
        {
            recordPlayed(game.getScore());
            GameHubDialog.show(board, "2048", "No more moves - final score: " + game.getScore() + ".");
        }
    }

    private void recordPlayed(int score)
    {
        if (!Session.isLoggedIn())
        {
            GuestPlayTracker.recordGuestPlay("2048", score);
            return;
        }

        Message request = new Message();
        request.setType(MessageType.GAME_PLAYED_REQUEST);
        request.setGameId("2048");
        request.setScore(score);
        NetworkManager.sendAsync(request);
    }

    private class BoardPanel extends JPanel
    {
        private static final int CELL = 78;
        private static final int GAP = 8;

        BoardPanel()
        {
            int size = Merge2048Game.SIZE * CELL + (Merge2048Game.SIZE + 1) * GAP;
            setPreferredSize(new Dimension(size, size));
            setFocusable(true);
            bindKeys();

            ThemeManager.addListener(new Runnable()
            {
                public void run() { repaint(); }
            });
        }

        private void bindKeys()
        {
            bind("LEFT", "moveLeft");
            bind("A", "moveLeft");
            bind("RIGHT", "moveRight");
            bind("D", "moveRight");
            bind("UP", "moveUp");
            bind("W", "moveUp");
            bind("DOWN", "moveDown");
            bind("S", "moveDown");
        }

        private void bind(String keyName, final String direction)
        {
            getInputMap().put(KeyStroke.getKeyStroke(keyName), keyName + "_action");
            getActionMap().put(keyName + "_action", new AbstractAction()
            {
                public void actionPerformed(ActionEvent e)
                {
                    boolean moved;
                    if ("moveLeft".equals(direction)) moved = game.moveLeft();
                    else if ("moveRight".equals(direction)) moved = game.moveRight();
                    else if ("moveUp".equals(direction)) moved = game.moveUp();
                    else moved = game.moveDown();
                    afterMove(moved);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            g2.setColor(ThemeManager.getColor(ThemeColor.BG_SIDEBAR));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);

            for (int row = 0; row < Merge2048Game.SIZE; row++)
            {
                for (int col = 0; col < Merge2048Game.SIZE; col++)
                {
                    int x = GAP + col * (CELL + GAP);
                    int y = GAP + row * (CELL + GAP);
                    int value = game.getTile(row, col);

                    g2.setColor(tileColor(value));
                    g2.fillRoundRect(x, y, CELL, CELL, 8, 8);

                    if (value != 0)
                    {
                        g2.setColor(value <= 4 ? ThemeManager.getColor(ThemeColor.TEXT_PRIMARY) : Color.WHITE);
                        g2.setFont(UITheme.FONT_NAV_BOLD.deriveFont(value >= 1024 ? 20f : 24f));
                        String text = String.valueOf(value);
                        int textW = g2.getFontMetrics().stringWidth(text);
                        g2.drawString(text, x + (CELL - textW) / 2, y + CELL / 2 + 8);
                    }
                }
            }

            g2.dispose();
        }

        /** Darker for low values, brighter/more saturated toward the theme accent as tiles grow - all channels clamped to a valid 0-255 range. */
        private Color tileColor(int value)
        {
            if (value == 0)
            {
                return ThemeManager.getColor(ThemeColor.BG_PANEL);
            }
            Color accent = ThemeManager.getColor(ThemeColor.ACCENT);
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
