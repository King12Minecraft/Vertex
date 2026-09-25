package games;
import net.NetworkManager;
import net.MessageType;
import net.Message;
import economy.GuestPlayTracker;
import account.Session;
import pages.MainMenu;
import ui.GameHubDialog;
import theme.GameColors;
import theme.UITheme;
import theme.ThemeColor;
import ui.RoundedPanel;
import ui.ThemedButton;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
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
import java.util.Random;

/**
 * AimTrainerWindow
 * ----------------
 * Click the target before it times out - 20 rounds, score = hits.
 * Single file, no separate Game/Panel classes needed (same reasoning
 * as RockPaperScissorsWindow): the round timer and click handling are
 * simple enough not to need a model/view split. Embedded in MainMenu's
 * game-host slot (see ChessWindow's javadoc for the pattern);
 * requestLeave() just stops the round timer.
 */
public class AimTrainerWindow extends JPanel implements EmbeddedGamePanel
{
    private static final int WIDTH = 520;
    private static final int HEIGHT = 400;
    private static final int TARGET_RADIUS = 22;
    private static final int ROUND_MS = 1100;
    private static final int TOTAL_TARGETS = 20;

    private final Random random = new Random();
    private JPanel playArea;
    private JLabel scoreLabel;
    private Timer roundTimer;

    private int targetX;
    private int targetY;
    private boolean targetVisible = false;
    private int hits = 0;
    private int roundsPlayed = 0;
    private boolean finished = false;

    public AimTrainerWindow()
    {
        setLayout(new BorderLayout());

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_APP, 0);
        root.setLayout(new BorderLayout());
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        headerRow.setBorder(new EmptyBorder(0, 4, 10, 4));

        JLabel title = new JLabel("Aim Trainer");
        title.setFont(UITheme.FONT_NAV_BOLD);
        title.setForeground(GameColors.TEXT_PRIMARY);
        headerRow.add(title, BorderLayout.WEST);

        scoreLabel = new JLabel("Hits: 0 / 0");
        scoreLabel.setFont(UITheme.FONT_SMALL);
        scoreLabel.setForeground(GameColors.TEXT_MUTED);
        headerRow.add(scoreLabel, BorderLayout.EAST);
        root.add(headerRow, BorderLayout.NORTH);

        playArea = new JPanel()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.applyAntialiasing(g2);
                g2.setColor(GameColors.BG_BOARD);
                g2.fillRect(0, 0, getWidth(), getHeight());

                if (targetVisible)
                {
                    g2.setColor(GameColors.ACCENT);
                    g2.fillOval(targetX - TARGET_RADIUS, targetY - TARGET_RADIUS, TARGET_RADIUS * 2, TARGET_RADIUS * 2);
                }
                g2.dispose();
            }
        };
        playArea.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        playArea.addMouseListener(new MouseAdapter()
        {
            public void mouseClicked(MouseEvent e) { handleClick(e.getX(), e.getY()); }
        });
        JPanel playAreaCenterer = new JPanel(new GridBagLayout());
        playAreaCenterer.setOpaque(false);
        playAreaCenterer.add(playArea, new GridBagConstraints());
        root.add(playAreaCenterer, BorderLayout.CENTER);

        ThemedButton leave = new ThemedButton("Leave", false);
        leave.setPreferredSize(new Dimension(90, 34));
        leave.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                if (requestLeave()) { MainMenu.getInstance().returnToGames(); }
            }
        });
        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottomRow.setOpaque(false);
        bottomRow.setBorder(new EmptyBorder(10, 0, 0, 0));
        bottomRow.add(leave);
        root.add(bottomRow, BorderLayout.SOUTH);

        add(root, BorderLayout.CENTER);

        spawnTarget();
    }

    @Override
    public boolean requestLeave()
    {
        if (roundTimer != null) { roundTimer.stop(); }
        return true;
    }

    private void spawnTarget()
    {
        if (finished)
        {
            return;
        }

        targetX = TARGET_RADIUS + random.nextInt(WIDTH - TARGET_RADIUS * 2);
        targetY = TARGET_RADIUS + random.nextInt(HEIGHT - TARGET_RADIUS * 2);
        targetVisible = true;
        playArea.repaint();

        if (roundTimer != null)
        {
            roundTimer.stop();
        }
        roundTimer = new Timer(ROUND_MS, new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { handleMiss(); }
        });
        roundTimer.setRepeats(false);
        roundTimer.start();
    }

    private void handleClick(int x, int y)
    {
        if (finished || !targetVisible)
        {
            return;
        }

        double distance = Math.hypot(x - targetX, y - targetY);
        if (distance <= TARGET_RADIUS)
        {
            hits++;
        }
        advanceRound();
    }

    private void handleMiss()
    {
        if (finished)
        {
            return;
        }
        advanceRound();
    }

    private void advanceRound()
    {
        targetVisible = false;
        roundsPlayed++;
        scoreLabel.setText("Hits: " + hits + " / " + roundsPlayed);

        if (roundsPlayed >= TOTAL_TARGETS)
        {
            finished = true;
            playArea.repaint();
            recordPlayed(hits);
            GameHubDialog.show(playArea, "Aim Trainer", "Finished! You hit " + hits + " of " + TOTAL_TARGETS + " targets.");
            return;
        }

        spawnTarget();
    }

    private void recordPlayed(int score)
    {
        if (!Session.isLoggedIn())
        {
            GuestPlayTracker.recordGuestPlay("aim-trainer", score);
            return;
        }

        Message request = new Message();
        request.setType(MessageType.GAME_PLAYED_REQUEST);
        request.setGameId("aim-trainer");
        request.setScore(score);
        NetworkManager.sendAsync(request);
    }
}
