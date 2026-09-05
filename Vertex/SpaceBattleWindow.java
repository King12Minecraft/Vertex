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
 * SpaceBattleWindow
 * -----------------
 * Two ways to play, same pattern as RacingWindow:
 *
 *   - Play Online: matched with 2-5 other pilots (3-6 total), everyone
 *     gets the SAME seed and faces the identical asteroid/enemy
 *     sequence over the same time limit (see SpaceBattleGame/
 *     SpaceBattleMatch), fighting independently rather than sharing
 *     one live arena - same reasoning RacingMatch documents. Final
 *     scores are compared for 1st/2nd/3rd placement.
 *   - Practice Mode: the same dogfight, but solo and fully offline -
 *     no ranking, no reward, just "how high can you score."
 */
public class SpaceBattleWindow extends JFrame implements NetworkManager.PushListener
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String GAME = "GAME";
    private static final String WAITING = "WAITING";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private JLabel waitingLabel;
    private SpaceBattlePanel gamePanel;

    private boolean isOnlineMode = false;
    private String matchId;
    private java.util.List<String> roster;

    public SpaceBattleWindow()
    {
        super("Vertex - Space Battle");
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
                NetworkManager.removePushListener(SpaceBattleWindow.this);
            }
        });
    }

    private JPanel createModeSelectScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        panel.setPreferredSize(new Dimension(460, 320));

        JLabel title = new JLabel("Space Battle");
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

        tileRow.add(new GameModeCard("Play Online", "3-6 pilots, same fight - 1st, 2nd and 3rd place win coins.",
            ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START), new GameModeCard.ClickListener()
            {
                public void onClick() { chooseMode(true); }
            }));

        tileRow.add(new GameModeCard("Practice Mode", "Fly the same fight solo - works fully offline, no reward.",
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
            startGame(new SpaceBattleGame());
        }
    }

    private JPanel createSearchingScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(60, 60, 60, 60));
        panel.setPreferredSize(new Dimension(380, 240));

        JLabel title = new JLabel("Space Battle");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        searchingLabel = new JLabel("Waiting for more pilots... (need 3 to start)");
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

        JLabel title = new JLabel("Space Battle");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        waitingLabel = new JLabel("Waiting for the other pilots to finish...");
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
        request.setType(MessageType.SPACE_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveMatch()
    {
        Message request = new Message();
        request.setType(MessageType.SPACE_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void startGame(SpaceBattleGame game)
    {
        if (gamePanel != null)
        {
            gamePanel.stopTimer();
            cards.remove(gamePanel);
        }

        final SpaceBattleGame activeGame = game;

        Runnable onGameOver = new Runnable()
        {
            public void run() { handleGameOver(activeGame); }
        };

        gamePanel = new SpaceBattlePanel(game, onGameOver);
        cards.add(gamePanel, GAME);

        cardLayout.show(cards, GAME);
        pack();
        setLocationRelativeTo(null);
        gamePanel.requestFocusInWindow();
        gamePanel.startTimer();
    }

    private void handleGameOver(SpaceBattleGame game)
    {
        int score = game.getScore();

        if (!isOnlineMode)
        {
            recordPlayed(score);
            SnakeGameOverDialog.show(gamePanel, score, new SnakeGameOverDialog.Choice()
            {
                public void onPlayAgain() { startGame(new SpaceBattleGame()); }
                public void onClose() { SpaceBattleWindow.this.dispose(); }
            });
            return;
        }

        recordPlayed(score);
        waitingLabel.setText("Run over - final score " + score + ". Waiting for the other pilots to finish...");
        cardLayout.show(cards, WAITING);

        Message request = new Message();
        request.setType(MessageType.SPACE_FINISHED_REQUEST);
        request.setMatchId(matchId);
        request.setScore(score);
        NetworkManager.sendAsync(request);
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isSpaceType = type == MessageType.SPACE_MATCH_FOUND || type == MessageType.SPACE_RESULT
            || type == MessageType.QUEUE_UPDATE;
        if (!isSpaceType)
        {
            return;
        }
        if (type == MessageType.QUEUE_UPDATE && !"space-battle".equals(message.getQueueGameId()))
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
                searchingLabel.setText("Waiting for more pilots... " + count
                    + " in queue (need 3 to start)");
            }
        }
        else if (message.getType() == MessageType.SPACE_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            roster = message.getSpaceRosterUsernames();
            startGame(new SpaceBattleGame(message.getSpaceSeed()));
        }
        else if (message.getType() == MessageType.SPACE_RESULT)
        {
            int place = message.getSpacePlace();
            int reward = message.getSpaceReward();
            int totalPilots = roster != null ? roster.size() : 0;

            String placement = place == 1 ? "1st" : place == 2 ? "2nd" : place == 3 ? "3rd" : place + "th";
            String text = "You finished " + placement + " of " + totalPilots + " with a score of " + message.getScore() + "!";
            if (reward > 0)
            {
                text += "\n+" + reward + " coins.";
            }

            GameHubDialog.show(this, "Space Battle Result", text);
            dispose();
        }
    }

    /** Fire-and-forget - lets the server log this for "Continue Playing"/"Trending" and update the score leaderboard. Practice mode has no coin reward; Online mode's reward comes from the SPACE_RESULT above, not from this call. */
    private void recordPlayed(int score)
    {
        if (!Session.isLoggedIn())
        {
            GuestPlayTracker.recordGuestPlay("space-battle", score);
            return;
        }

        Message request = new Message();
        request.setType(MessageType.GAME_PLAYED_REQUEST);
        request.setGameId("space-battle");
        request.setScore(score);
        NetworkManager.sendAsync(request);
    }
}
