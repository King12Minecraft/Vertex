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
import java.util.List;

/**
 * FightArenaWindow
 * ----------------
 * Mode-select (1v1/2v2/3v3/Chaos), a per-mode queue screen, the live
 * match (FightArenaPanel handles rendering/input), and the result.
 * Same overall CardLayout pattern as RacingWindow/AmongUsWindow, but
 * this is the one game whose "board" screen is continuously fed by a
 * server tick loop rather than event-driven updates.
 */
public class FightArenaWindow extends JFrame implements NetworkManager.PushListener
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String GAME = "GAME";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private JLabel scoreLabel;
    private FightArenaPanel fightPanel;

    private String matchId;
    private String chosenMode;

    public FightArenaWindow()
    {
        super("Vertex - Fight Arena");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        cards.add(createModeSelectScreen(), MODE_SELECT);
        cards.add(createSearchingScreen(), SEARCHING);
        cards.add(createGameScreen(), GAME);

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
                if (matchId == null && chosenMode != null)
                {
                    leaveQueue();
                }
                NetworkManager.removePushListener(FightArenaWindow.this);
            }
        });
    }

    // ==================== Mode select ====================

    private JPanel createModeSelectScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(40, 50, 40, 50));
        panel.setPreferredSize(new Dimension(440, 420));

        JLabel title = new JLabel("Fight Arena");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("Real-time melee combat - pick a mode.");
        subtitle.setFont(UITheme.FONT_SUBHEAD);
        subtitle.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(6, 0, 24, 0));
        panel.add(subtitle);

        JPanel grid = new JPanel(new java.awt.GridLayout(2, 2, 14, 14));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);

        grid.add(new GameModeCard("1v1", "Duel - first to 10 KOs wins.",
            new java.awt.Color(90, 160, 240), new GameModeCard.ClickListener()
            {
                public void onClick() { chooseMode("1V1"); }
            }));
        grid.add(new GameModeCard("2v2", "Team battle, two on each side.",
            new java.awt.Color(240, 100, 100), new GameModeCard.ClickListener()
            {
                public void onClick() { chooseMode("2V2"); }
            }));
        grid.add(new GameModeCard("3v3", "Bigger team battle, three each side.",
            new java.awt.Color(120, 210, 120), new GameModeCard.ClickListener()
            {
                public void onClick() { chooseMode("3V3"); }
            }));
        grid.add(new GameModeCard("Chaos Mode", "Free-for-all, 3-8 players.",
            new java.awt.Color(230, 190, 70), new GameModeCard.ClickListener()
            {
                public void onClick() { chooseMode("FFA"); }
            }));

        panel.add(grid);

        return panel;
    }

    private void chooseMode(String mode)
    {
        chosenMode = mode;
        cardLayout.show(cards, SEARCHING);
        pack();
        setLocationRelativeTo(null);
        findMatch();
    }

    // ==================== Searching ====================

    private JPanel createSearchingScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(60, 60, 60, 60));
        panel.setPreferredSize(new Dimension(380, 240));

        JLabel title = new JLabel("Fight Arena");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        searchingLabel = new JLabel("Waiting for more players...");
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
                leaveQueue();
                dispose();
            }
        });
        panel.add(cancel);

        return panel;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.FIGHT_FIND_MATCH_REQUEST);
        request.setFightMode(chosenMode);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveQueue()
    {
        Message request = new Message();
        request.setType(MessageType.FIGHT_LEAVE_QUEUE_REQUEST);
        NetworkManager.sendAsync(request);
    }

    // ==================== Game ====================

    private JPanel createGameScreen()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        scoreLabel = new JLabel("Move: Left/Right or A/D   -   Attack: Space");
        scoreLabel.setFont(UITheme.FONT_SMALL);
        scoreLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        scoreLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
        panel.add(scoreLabel, BorderLayout.NORTH);

        fightPanel = new FightArenaPanel(new FightArenaPanel.InputListener()
        {
            public void onInputChanged(boolean left, boolean right, boolean attack)
            {
                sendInput(left, right, attack);
            }
        });
        panel.add(fightPanel, BorderLayout.CENTER);

        return panel;
    }

    private void sendInput(boolean left, boolean right, boolean attack)
    {
        Message request = new Message();
        request.setType(MessageType.FIGHT_INPUT_UPDATE);
        request.setMatchId(matchId);
        request.setFightMovingLeft(left);
        request.setFightMovingRight(right);
        request.setFightAttacking(attack);
        NetworkManager.sendAsync(request);
    }

    // ==================== Push handling ====================

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isFightType = type == MessageType.FIGHT_MATCH_FOUND || type == MessageType.FIGHT_TICK_UPDATE
            || type == MessageType.FIGHT_MATCH_OVER || type == MessageType.QUEUE_UPDATE;
        if (!isFightType)
        {
            return;
        }
        if (type == MessageType.QUEUE_UPDATE)
        {
            String expected = "fight-arena:" + chosenMode;
            if (chosenMode == null || !expected.equals(message.getQueueGameId()))
            {
                return;
            }
        }
        if (matchId != null && message.getMatchId() != null && !message.getMatchId().equals(matchId)
            && type != MessageType.QUEUE_UPDATE)
        {
            return;
        }

        SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { handleFightMessage(message); }
        });
    }

    private void handleFightMessage(Message message)
    {
        if (message.getType() == MessageType.QUEUE_UPDATE)
        {
            if (matchId == null)
            {
                searchingLabel.setText("Waiting for more players... " + message.getQueueCount() + " in queue");
            }
        }
        else if (message.getType() == MessageType.FIGHT_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            String myUsername = Session.isLoggedIn() ? Session.getCurrentAccount().getUsername() : null;
            fightPanel.setMyUsername(myUsername);
            fightPanel.setTeamAssignments(message.getFightTeamAssignments());

            cardLayout.show(cards, GAME);
            pack();
            setLocationRelativeTo(null);
            fightPanel.requestFocusInWindow();
        }
        else if (message.getType() == MessageType.FIGHT_TICK_UPDATE)
        {
            fightPanel.applyTick(message.getFightTickData());
            updateScoreLabel(message.getFightScores());
        }
        else if (message.getType() == MessageType.FIGHT_MATCH_OVER)
        {
            recordPlayed();
            GameHubDialog.show(this, "Match Over", message.getFightResultText());
            dispose();
        }
    }

    private void updateScoreLabel(List<String> scores)
    {
        if (scores == null || scores.isEmpty())
        {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < scores.size(); i++)
        {
            String[] parts = scores.get(i).split("\\|", 2);
            if (parts.length == 2)
            {
                if (sb.length() > 0) sb.append("   ");
                sb.append(parts[0]).append(": ").append(parts[1]);
            }
        }
        scoreLabel.setText(sb.toString());
    }

    private void recordPlayed()
    {
        if (!Session.isLoggedIn())
        {
            GuestPlayTracker.recordGuestPlay("fight-arena", 0);
            return;
        }

        Message request = new Message();
        request.setType(MessageType.GAME_PLAYED_REQUEST);
        request.setGameId("fight-arena");
        NetworkManager.sendAsync(request);
    }
}
