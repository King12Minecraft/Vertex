import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * ZombieSurvivalWindow
 * --------------------
 * Two ways to play, same pattern as RacingWindow:
 *
 *   - Play Online: matched with 1-3 others (2-4 total), everyone gets
 *     the SAME seed and faces the identical zombie spawn sequence
 *     (see ZombieSurvivalGame/ZombieSurvivalMatch), fighting it out
 *     independently rather than sharing one live arena - the same
 *     reasoning RacingMatch documents for why this isn't live
 *     position sync. Survive all 8 waves and everyone who does earns
 *     the full coin reward, not just whoever was fastest.
 *   - Practice Mode: the same wave sequence, but solo and fully
 *     offline - no reward, just "how far can you get."
 */
public class ZombieSurvivalWindow extends JFrame implements NetworkManager.PushListener
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String GAME = "GAME";
    private static final String WAITING = "WAITING";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private JLabel waitingLabel;
    private ZombieSurvivalPanel gamePanel;

    private boolean isOnlineMode = false;
    private String matchId;
    private java.util.List<String> roster;

    public ZombieSurvivalWindow()
    {
        super("Vertex - Zombie Survival");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        cards.add(createModeSelectScreen(), MODE_SELECT);
        cards.add(createSearchingScreen(), SEARCHING);
        cards.add(createWaitingScreen(), WAITING);

        getContentPane().add(cards, BorderLayout.CENTER);
        cardLayout.show(cards, MODE_SELECT);
        pack();
        setLocationRelativeTo(null);
        SignatureOverlay.attach(this);
        GlitchEffectOverlay.attach(this);

        NetworkManager.addPushListener(this);

        addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent e)
            {
                if (isOnlineMode) leaveMatch();
                NetworkManager.removePushListener(ZombieSurvivalWindow.this);
            }
        });
    }

    private JPanel createModeSelectScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        panel.setPreferredSize(new Dimension(460, 320));

        JLabel title = new JLabel("Zombie Survival");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("Choose how you want to play.");
        subtitle.setFont(UITheme.FONT_SUBHEAD);
        subtitle.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(6, 0, 24, 0));
        panel.add(subtitle);

        JPanel tileRow = new JPanel(new java.awt.GridLayout(1, 2, 16, 0));
        tileRow.setOpaque(false);
        tileRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        tileRow.setMaximumSize(new Dimension(2000, 150));

        tileRow.add(new GameModeCard("Play Online", "2-4 survivors, same waves - clear all 8 for coins.",
            ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START), new GameModeCard.ClickListener()
            {
                public void onClick() { chooseMode(true); }
            }));

        tileRow.add(new GameModeCard("Practice Mode", "Fight the same waves solo - works fully offline, no reward.",
            ThemeManager.getColor(ThemeColor.TEXT_MUTED), new GameModeCard.ClickListener()
            {
                public void onClick() { chooseMode(false); }
            }));

        panel.add(tileRow);

        return panel;
    }

    private void chooseMode(boolean online)
    {
        isOnlineMode = online;
        if (online)
        {
            cardLayout.show(cards, SEARCHING);
            pack();
            setLocationRelativeTo(null);
            findMatch();
        }
        else
        {
            startGame(new ZombieSurvivalGame());
        }
    }

    private JPanel createSearchingScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(60, 60, 60, 60));
        panel.setPreferredSize(new Dimension(380, 240));

        JLabel title = new JLabel("Zombie Survival");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        searchingLabel = new JLabel("Waiting for more survivors... (need 2 to start)");
        searchingLabel.setFont(UITheme.FONT_SUBHEAD);
        searchingLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        searchingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchingLabel.setBorder(new EmptyBorder(10, 0, 30, 0));
        panel.add(searchingLabel);

        ThemedButton cancel = new ThemedButton("Cancel", false);
        cancel.setAlignmentX(Component.LEFT_ALIGNMENT);
        cancel.setPreferredSize(new Dimension(120, 38));
        cancel.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                leaveMatch();
                dispose();
            }
        });
        panel.add(cancel);

        return panel;
    }

    private JPanel createWaitingScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(60, 60, 60, 60));
        panel.setPreferredSize(new Dimension(380, 240));

        JLabel title = new JLabel("Zombie Survival");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        waitingLabel = new JLabel("Waiting for the other survivors to finish...");
        waitingLabel.setFont(UITheme.FONT_SUBHEAD);
        waitingLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        waitingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        waitingLabel.setBorder(new EmptyBorder(10, 0, 0, 0));
        panel.add(waitingLabel);

        return panel;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.ZOMBIE_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveMatch()
    {
        Message request = new Message();
        request.setType(MessageType.ZOMBIE_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void startGame(ZombieSurvivalGame game)
    {
        if (gamePanel != null)
        {
            gamePanel.stopTimer();
            cards.remove(gamePanel);
        }

        final ZombieSurvivalGame activeGame = game;

        Runnable onGameOver = new Runnable()
        {
            public void run() { handleGameOver(activeGame); }
        };

        gamePanel = new ZombieSurvivalPanel(game, onGameOver);
        cards.add(gamePanel, GAME);

        cardLayout.show(cards, GAME);
        pack();
        setLocationRelativeTo(null);
        gamePanel.requestFocusInWindow();
        gamePanel.startTimer();
    }

    private void handleGameOver(ZombieSurvivalGame game)
    {
        boolean won = game.isWon();
        int waveReached = game.getWave();
        int kills = game.getZombiesKilled();

        if (!isOnlineMode)
        {
            recordPlayed(kills);
            if (won)
            {
                GameHubDialog.show(gamePanel, "Zombie Survival",
                    "You survived all " + ZombieSurvivalGame.WAVE_COUNT + " waves! Zombies killed: " + kills);
                dispose();
            }
            else
            {
                SnakeGameOverDialog.show(gamePanel, kills, new SnakeGameOverDialog.Choice()
                {
                    public void onPlayAgain() { startGame(new ZombieSurvivalGame()); }
                    public void onClose() { ZombieSurvivalWindow.this.dispose(); }
                });
            }
            return;
        }

        recordPlayed(kills);
        waitingLabel.setText((won ? "You survived! " : "You went down on wave " + waveReached + ". ")
            + "Waiting for the other survivors to finish...");
        cardLayout.show(cards, WAITING);

        Message request = new Message();
        request.setType(MessageType.ZOMBIE_FINISHED_REQUEST);
        request.setMatchId(matchId);
        request.setZombieWon(won);
        request.setZombieWaveReached(waveReached);
        request.setScore(kills);
        NetworkManager.sendAsync(request);
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isZombieType = type == MessageType.ZOMBIE_MATCH_FOUND || type == MessageType.ZOMBIE_RESULT
            || type == MessageType.QUEUE_UPDATE;
        if (!isZombieType)
        {
            return;
        }
        if (type == MessageType.QUEUE_UPDATE && !"zombie-survival".equals(message.getQueueGameId()))
        {
            return;
        }
        if (matchId != null && message.getMatchId() != null && !message.getMatchId().equals(matchId)
            && type != MessageType.QUEUE_UPDATE)
        {
            return;
        }

        SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { handleServerMessage(message); }
        });
    }

    private void handleServerMessage(Message message)
    {
        if (message.getType() == MessageType.QUEUE_UPDATE)
        {
            if (searchingLabel != null && matchId == null)
            {
                int count = message.getQueueCount();
                searchingLabel.setText("Waiting for more survivors... " + count
                    + " in queue (need 2 to start)");
            }
        }
        else if (message.getType() == MessageType.ZOMBIE_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            roster = message.getZombieRosterUsernames();
            startGame(new ZombieSurvivalGame(message.getZombieSeed()));
        }
        else if (message.getType() == MessageType.ZOMBIE_RESULT)
        {
            boolean won = message.isZombieWon();
            int reward = message.getZombieReward();
            int waveReached = message.getZombieWaveReached();

            String text = won
                ? "You survived all " + ZombieSurvivalGame.WAVE_COUNT + " waves!"
                : "You went down on wave " + waveReached + ".";
            if (reward > 0)
            {
                text += "\n+" + reward + " coins.";
            }

            GameHubDialog.show(this, "Zombie Survival Result", text);
            dispose();
        }
    }

    /** Fire-and-forget - lets the server log this for "Continue Playing"/"Trending" and update the score leaderboard. Practice mode has no coin reward; Online mode's reward comes from the ZOMBIE_RESULT above, not from this call. */
    private void recordPlayed(int kills)
    {
        if (!Session.isLoggedIn())
        {
            GuestPlayTracker.recordGuestPlay("zombie-survival", kills);
            return;
        }

        Message request = new Message();
        request.setType(MessageType.GAME_PLAYED_REQUEST);
        request.setGameId("zombie-survival");
        request.setScore(kills);
        NetworkManager.sendAsync(request);
    }
}
