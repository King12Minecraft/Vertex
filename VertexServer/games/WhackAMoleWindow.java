package games;

import net.Message;
import net.MessageType;
import net.NetworkManager;
import theme.GlitchEffectOverlay;
import theme.SignatureOverlay;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.RoundedPanel;
import ui.ThemedButton;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * WhackAMoleWindow
 * ----------------
 * Offline - WhackAMoleGame runs entirely client-side, same as
 * Minesweeper/Sudoku, since there's no opponent to keep in sync with.
 * A single Swing Timer drives both the game's tick() (mole spawning/
 * ducking) and the repaint, at a steady ~60ms interval. Reports the
 * final score to the server once the 30-second round ends, for a
 * score-scaled coin reward, same "practice score" pattern Dino Dash/
 * Aim Trainer already use.
 */
public class WhackAMoleWindow extends JFrame
{
    private WhackAMoleGame game;
    private HoleButton[] holes = new HoleButton[WhackAMoleGame.GRID_SIZE];
    private JLabel statusLabel;
    private Timer gameTimer;
    private boolean reported;

    public WhackAMoleWindow()
    {
        super("Vertex - Whack-a-Mole");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        statusLabel = new JLabel("Score: 0    Time: 30s");
        statusLabel.setFont(UITheme.FONT_NAV_BOLD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        statusLabel.setBorder(new EmptyBorder(0, 0, 12, 0));
        root.add(statusLabel, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(3, 3, 10, 10));
        grid.setOpaque(false);
        grid.setPreferredSize(new Dimension(330, 330));
        for (int i = 0; i < WhackAMoleGame.GRID_SIZE; i++)
        {
            final int index = i;
            HoleButton hole = new HoleButton();
            hole.addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e) { handleWhack(index); }
            });
            holes[i] = hole;
            grid.add(hole);
        }
        root.add(grid, BorderLayout.CENTER);

        getContentPane().add(root);
        startNewGame();
        pack();
        setLocationRelativeTo(null);
        SignatureOverlay.attach(this);
        GlitchEffectOverlay.attach(this);
    }

    private void startNewGame()
    {
        game = new WhackAMoleGame();
        reported = false;
        statusLabel.setText("Score: 0    Time: 30s");

        if (gameTimer != null) gameTimer.stop();
        gameTimer = new Timer(60, new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { onTick(); }
        });
        gameTimer.start();
    }

    private void onTick()
    {
        game.tick();
        game.checkTimeUp();

        for (int i = 0; i < holes.length; i++)
        {
            holes[i].setMoleUp(game.isMoleUp(i));
        }

        long remainingSec = Math.max(0, (WhackAMoleGame.GAME_DURATION_MS - game.getElapsedMs()) / 1000);
        statusLabel.setText("Score: " + game.getScore() + "    Time: " + remainingSec + "s");

        if (game.isOver())
        {
            gameTimer.stop();
            finishGame();
        }
    }

    private void handleWhack(int index)
    {
        if (game.isOver()) return;
        if (game.whack(index))
        {
            holes[index].setMoleUp(false);
        }
    }

    private void finishGame()
    {
        int finalScore = game.getScore();
        statusLabel.setText("Time's up! Score: " + finalScore);
        if (!reported)
        {
            reported = true;
            reportScore(finalScore);
        }

        SnakeGameOverDialog.show(this, finalScore, "You whacked " + finalScore + " moles!",
            finalScore >= 10 ? "I whacked " + finalScore + " moles in Whack-a-Mole on Vertex!" : null,
            new SnakeGameOverDialog.Choice()
            {
                public void onPlayAgain() { startNewGame(); }
                public void onClose() { WhackAMoleWindow.this.dispose(); }
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
                request.setGameId("whack-a-mole");
                request.setScore(finalScore);
                NetworkManager.sendAsync(request);
            }
        });
        worker.start();
    }

    private static class HoleButton extends RoundedPanel
    {
        private boolean moleUp = false;

        HoleButton()
        {
            super(ThemeColor.BG_APP, 60);
        }

        void setMoleUp(boolean up)
        {
            if (this.moleUp != up)
            {
                this.moleUp = up;
                repaint();
            }
        }

        @Override
        protected void paintComponent(java.awt.Graphics g)
        {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            g2.setColor(new Color(50, 35, 25));
            g2.fillOval(4, getHeight() / 2, getWidth() - 8, getHeight() / 2 - 4);

            if (moleUp)
            {
                g2.setColor(new Color(150, 100, 70));
                g2.fillOval(getWidth() / 4, 4, getWidth() / 2, getHeight() * 2 / 3);
                g2.setColor(Color.BLACK);
                int eyeSize = 6;
                g2.fillOval(getWidth() / 2 - 14, getHeight() / 3, eyeSize, eyeSize);
                g2.fillOval(getWidth() / 2 + 8, getHeight() / 3, eyeSize, eyeSize);
            }

            g2.setColor(new Color(30, 20, 15));
            g2.fillOval(8, getHeight() / 2 + 6, getWidth() - 16, getHeight() / 3);
            g2.dispose();
        }
    }
}
