package games;

import net.Message;
import net.MessageType;
import net.NetworkManager;
import pages.MainMenu;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.RoundedPanel;
import ui.ThemedButton;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * SimonWindow
 * -----------
 * Offline - SimonGame runs entirely client-side, same as Minesweeper/
 * Sudoku, since there's no opponent to keep in sync with. Watches the
 * growing color sequence flash, then click the same colors back in
 * order. Reports the final score (sequence length reached) to the
 * server once the game ends, for a score-scaled coin reward, same
 * "practice score" pattern Dino Dash/Aim Trainer already use. Embedded
 * in MainMenu's game-host slot (see ChessWindow's javadoc for the
 * pattern); requestLeave() has nothing to confirm.
 */
public class SimonWindow extends JPanel implements EmbeddedGamePanel
{
    private static final Color[] BASE_COLORS = {
        new Color(200, 60, 60), new Color(60, 120, 200), new Color(60, 170, 90), new Color(220, 190, 50)
    };
    private static final Color[] LIT_COLORS = {
        new Color(255, 100, 100), new Color(100, 170, 255), new Color(100, 220, 130), new Color(255, 235, 90)
    };
    private static final long FLASH_ON_MS = 450;
    private static final long FLASH_GAP_MS = 200;

    private SimonGame game;
    private JLabel statusLabel;
    private ColorButton[] buttons = new ColorButton[SimonGame.COLOR_COUNT];
    private boolean acceptingInput = false;
    private boolean reported;

    public SimonWindow()
    {
        setLayout(new BorderLayout());

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        statusLabel = new JLabel("Watch the sequence...");
        statusLabel.setFont(UITheme.FONT_NAV_BOLD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        statusLabel.setBorder(new EmptyBorder(0, 0, 12, 0));
        root.add(statusLabel, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(2, 2, 8, 8));
        grid.setOpaque(false);
        grid.setPreferredSize(new Dimension(280, 280));
        for (int i = 0; i < SimonGame.COLOR_COUNT; i++)
        {
            final int colorIndex = i;
            ColorButton button = new ColorButton(BASE_COLORS[i]);
            button.addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e) { handlePick(colorIndex); }
            });
            buttons[i] = button;
            grid.add(button);
        }
        JPanel gridCenterer = new JPanel(new GridBagLayout());
        gridCenterer.setOpaque(false);
        gridCenterer.add(grid, new GridBagConstraints());
        root.add(gridCenterer, BorderLayout.CENTER);

        ThemedButton leave = new ThemedButton("Leave", false);
        leave.setPreferredSize(new Dimension(90, 34));
        leave.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { MainMenu.getInstance().returnToGames(); }
        });
        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottomRow.setOpaque(false);
        bottomRow.setBorder(new EmptyBorder(12, 0, 0, 0));
        bottomRow.add(leave);
        root.add(bottomRow, BorderLayout.SOUTH);

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
        game = new SimonGame();
        reported = false;
        playSequence();
    }

    private void playSequence()
    {
        acceptingInput = false;
        statusLabel.setText("Watch the sequence... (round " + game.getSequence().size() + ")");

        final java.util.List<Integer> sequence = game.getSequence();
        final Timer[] timerHolder = new Timer[1];
        final int[] step = { 0 };

        timerHolder[0] = new Timer((int) (FLASH_ON_MS + FLASH_GAP_MS), new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                if (step[0] >= sequence.size())
                {
                    timerHolder[0].stop();
                    acceptingInput = true;
                    statusLabel.setText("Your turn - repeat the sequence");
                    return;
                }
                flashButton(sequence.get(step[0]));
                step[0]++;
            }
        });
        timerHolder[0].setInitialDelay(500);
        timerHolder[0].start();
    }

    private void flashButton(final int colorIndex)
    {
        buttons[colorIndex].setLit(true);
        Timer offTimer = new Timer((int) FLASH_ON_MS, new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { buttons[colorIndex].setLit(false); }
        });
        offTimer.setRepeats(false);
        offTimer.start();
    }

    private void handlePick(int colorIndex)
    {
        if (!acceptingInput || game.isGameOver()) return;
        flashButton(colorIndex);

        int sequenceSizeBefore = game.getSequence().size();
        boolean stillGoing = game.submitPick(colorIndex);
        if (!stillGoing)
        {
            acceptingInput = false;
            int finalScore = game.getScore();
            statusLabel.setText("Wrong! You reached round " + (finalScore + 1) + ".");
            if (!reported)
            {
                reported = true;
                reportScore(finalScore);
            }
            SnakeGameOverDialog.show(this, finalScore, "You reached round " + (finalScore + 1) + "!",
                finalScore >= 5 ? "I reached round " + (finalScore + 1) + " in Simon Says on Vertex!" : null,
                new SnakeGameOverDialog.Choice()
                {
                    public void onPlayAgain() { startNewGame(); }
                    public void onClose() { MainMenu.getInstance().returnToGames(); }
                });
            return;
        }

        // The sequence only grows once the player has correctly repeated the entire
        // current one (see SimonGame.submitPick) - that's the signal a new round just
        // started and it's time to play the (now one-longer) sequence back out.
        if (game.getSequence().size() > sequenceSizeBefore)
        {
            acceptingInput = false;
            Timer delay = new Timer(700, new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { playSequence(); }
            });
            delay.setRepeats(false);
            delay.start();
        }
    }

    private void reportScore(int score)
    {
        final int finalScore = score;
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.GAME_PLAYED_REQUEST);
                request.setGameId("simon-says");
                request.setScore(finalScore);
                NetworkManager.sendAsync(request);
            }
        });
        worker.start();
    }

    private static class ColorButton extends RoundedPanel
    {
        private final Color base;
        private boolean lit = false;
        private int litIndex = -1;

        ColorButton(Color base)
        {
            super(ThemeColor.BG_APP, 12);
            this.base = base;
            setPreferredSize(new Dimension(130, 130));
        }

        void setLit(boolean lit)
        {
            this.lit = lit;
            repaint();
        }

        @Override
        protected void paintComponent(java.awt.Graphics g)
        {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);
            g2.setColor(lit ? base.brighter() : base.darker());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g2.dispose();
        }
    }
}
