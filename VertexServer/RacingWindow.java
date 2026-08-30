import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * RacingWindow
 * ------------
 * Two ways to play, same pattern as TicTacToeWindow:
 *
 *   - Play Online: matched with 2-5 other racers (3-6 total), everyone
 *     gets the SAME seed and SAME finish line (RacingGame.FINISH_FRAMES
 *     - see RacingGame / RacingMatch server-side) and races
 *     independently. This is NOT live position sync between clients
 *     (that would mean broadcasting positions many times a second, a
 *     much bigger real-time protocol than anything else in Vertex).
 *     Comparing finish times (or, for anyone who crashes, how far they
 *     got) on an identical track is an honest, much smaller way to
 *     make this a real ranked competition. 1st/2nd/3rd place earn
 *     coins - see EconomyConfig.getRacingPlacementReward.
 *   - Practice Mode: the same finish-line race, but solo and fully
 *     offline - no ranking, no reward, just "can you make it to the
 *     end."
 */
public class RacingWindow extends JFrame implements NetworkManager.PushListener
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String RACE = "RACE";
    private static final String WAITING = "WAITING";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private JLabel waitingLabel;
    private RacingPanel racingPanel;

    private boolean isOnlineMode = false;
    private String matchId;
    private java.util.List<String> raceRoster;
    private boolean myFinished;
    private int myFrameCount;

    public RacingWindow()
    {
        super("Vertex - Racing");
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
                if (isOnlineMode) leaveRace();
                NetworkManager.removePushListener(RacingWindow.this);
            }
        });
    }

    private JPanel createModeSelectScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        panel.setPreferredSize(new Dimension(460, 320));

        JLabel title = new JLabel("Racing");
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

        tileRow.add(new GameModeCard("Play Online", "3-6 racers, same track - 1st, 2nd and 3rd place win coins.",
            ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START), new GameModeCard.ClickListener()
            {
                public void onClick() { chooseMode(true); }
            }));

        tileRow.add(new GameModeCard("Practice Mode", "Race the same course solo - works fully offline, no reward.",
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
            findRaceMatch();
        }
        else
        {
            startRace(new RacingGame());
        }
    }

    private JPanel createSearchingScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(60, 60, 60, 60));
        panel.setPreferredSize(new Dimension(380, 240));

        JLabel title = new JLabel("Racing");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        searchingLabel = new JLabel("Waiting for more racers... (need 3 to start)");
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
                leaveRace();
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

        JLabel title = new JLabel("Racing");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        waitingLabel = new JLabel("Waiting for the other racers to finish...");
        waitingLabel.setFont(UITheme.FONT_SUBHEAD);
        waitingLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        waitingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        waitingLabel.setBorder(new EmptyBorder(10, 0, 0, 0));
        panel.add(waitingLabel);

        return panel;
    }

    private void findRaceMatch()
    {
        Message request = new Message();
        request.setType(MessageType.RACE_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveRace()
    {
        Message request = new Message();
        request.setType(MessageType.RACE_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void startRace(RacingGame game)
    {
        if (racingPanel != null)
        {
            racingPanel.stopTimer();
            cards.remove(racingPanel);
        }

        final RacingGame activeGame = game;

        Runnable onGameOver = new Runnable()
        {
            public void run() { handleRaceOver(activeGame); }
        };

        racingPanel = new RacingPanel(game, onGameOver);
        cards.add(racingPanel, RACE);

        cardLayout.show(cards, RACE);
        pack();
        setLocationRelativeTo(null);
        racingPanel.requestFocusInWindow();
        racingPanel.startTimer();
    }

    private void handleRaceOver(RacingGame game)
    {
        boolean finished = game.isFinished();
        int frameCount = game.getFrameCount();
        int score = game.getScore();

        if (!isOnlineMode)
        {
            recordPlayed(score);
            if (finished)
            {
                GameHubDialog.show(racingPanel, "Racing",
                    "You made it to the finish line! Score: " + score);
                dispose();
            }
            else
            {
                SnakeGameOverDialog.show(racingPanel, score, new SnakeGameOverDialog.Choice()
                {
                    public void onPlayAgain() { startRace(new RacingGame()); }
                    public void onClose() { RacingWindow.this.dispose(); }
                });
            }
            return;
        }

        myFinished = finished;
        myFrameCount = frameCount;
        recordPlayed(score);
        waitingLabel.setText((finished ? "You finished! " : "You crashed. ")
            + "Waiting for the other racers to finish...");
        cardLayout.show(cards, WAITING);

        Message request = new Message();
        request.setType(MessageType.RACE_FINISHED_REQUEST);
        request.setMatchId(matchId);
        request.setRaceFinished(finished);
        request.setScore(frameCount);
        NetworkManager.sendAsync(request);
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isRaceType = type == MessageType.RACE_MATCH_FOUND || type == MessageType.RACE_RESULT
            || type == MessageType.QUEUE_UPDATE;
        if (!isRaceType)
        {
            return;
        }
        if (type == MessageType.QUEUE_UPDATE && !"racing".equals(message.getQueueGameId()))
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
            public void run() { handleRaceMessage(message); }
        });
    }

    private void handleRaceMessage(Message message)
    {
        if (message.getType() == MessageType.QUEUE_UPDATE)
        {
            if (searchingLabel != null && matchId == null)
            {
                int count = message.getQueueCount();
                searchingLabel.setText("Waiting for more racers... " + count
                    + " in queue (need 3 to start)");
            }
        }
        else if (message.getType() == MessageType.RACE_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            raceRoster = message.getRaceRosterUsernames();
            startRace(new RacingGame(message.getRaceSeed()));
        }
        else if (message.getType() == MessageType.RACE_RESULT)
        {
            int place = message.getRacePlace();
            int reward = message.getRaceReward();
            int totalRacers = raceRoster != null ? raceRoster.size() : 0;

            String placement = place == 1 ? "1st" : place == 2 ? "2nd" : place == 3 ? "3rd" : place + "th";
            String text = "You finished " + placement + " of " + totalRacers + "!";
            if (reward > 0)
            {
                text += "\n+" + reward + " coins.";
            }

            GameHubDialog.show(this, "Race Result", text);
            dispose();
        }
    }

    /** Fire-and-forget - lets the server log this for "Continue Playing"/"Trending". Practice mode has no coin reward; Online mode's reward comes from the placement result above, not from this call. */
    private void recordPlayed(int score)
    {
        if (!Session.isLoggedIn())
        {
            GuestPlayTracker.recordGuestPlay("racing", score);
            return;
        }

        Message request = new Message();
        request.setType(MessageType.GAME_PLAYED_REQUEST);
        request.setGameId("racing");
        request.setScore(score);
        NetworkManager.sendAsync(request);
    }
}
