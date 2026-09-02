import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * AmongUsWindow
 * -------------
 * Handles every phase of a match: waiting in queue, the main task/kill
 * screen, meetings and voting, and the final result. Deliberately a
 * round-based social deduction game rather than a live 2D map - see
 * AmongUsMatch (server-side) for the full reasoning. No in-match chat;
 * players can use Vertex's Chat page to discuss during a meeting if
 * they want to.
 */
public class AmongUsWindow extends JFrame implements NetworkManager.PushListener
{
    private static final String SEARCHING = "SEARCHING";
    private static final String GAME = "GAME";
    private static final String MEETING = "MEETING";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;

    private JLabel roleLabel;
    private JLabel progressLabel;
    private JPanel taskList;
    private JPanel aliveList;
    private JPanel killPanel;

    private JLabel meetingReasonLabel;
    private JPanel voteList;
    private JLabel voteStatusLabel;

    private String matchId;
    private String myRole;
    private List<String> myTasks;
    private final java.util.Set<Integer> completedLocally = new java.util.HashSet<Integer>();
    private List<String> currentRoster;
    private List<String> currentAlive;
    private boolean voted = false;

    public AmongUsWindow()
    {
        super("Vertex - Among Us");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        cards.add(createSearchingScreen(), SEARCHING);
        cards.add(createGameScreen(), GAME);
        cards.add(createMeetingScreen(), MEETING);

        getContentPane().add(cards, BorderLayout.CENTER);
        cardLayout.show(cards, SEARCHING);
        pack();
        setLocationRelativeTo(null);
        SignatureOverlay.attach(this);
        GlitchEffectOverlay.attach(this);

        NetworkManager.addPushListener(this);
        findMatch();

        addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent e)
            {
                if (matchId == null)
                {
                    leaveQueue();
                }
                NetworkManager.removePushListener(AmongUsWindow.this);
            }
        });
    }

    // ==================== Searching ====================

    private JPanel createSearchingScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(60, 60, 60, 60));
        panel.setPreferredSize(new Dimension(400, 240));

        JLabel title = new JLabel("Among Us");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        searchingLabel = new JLabel("Waiting for more players... (need 4 to start)");
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
        request.setType(MessageType.AMONG_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveQueue()
    {
        Message request = new Message();
        request.setType(MessageType.AMONG_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    // ==================== Game (tasks + kill) ====================

    private JScrollPane createGameScreen()
    {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(20, 20, 20, 20));
        content.setPreferredSize(new Dimension(420, 520));

        roleLabel = new JLabel("Role");
        roleLabel.setFont(UITheme.FONT_HEADING);
        roleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(roleLabel);
        content.add(Box.createVerticalStrut(6));

        progressLabel = new JLabel("Team Tasks: 0%");
        progressLabel.setFont(UITheme.FONT_SMALL);
        progressLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        progressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressLabel.setBorder(new EmptyBorder(0, 0, 16, 0));
        content.add(progressLabel);

        content.add(sectionLabel("YOUR TASKS"));
        taskList = new JPanel();
        taskList.setOpaque(false);
        taskList.setLayout(new BoxLayout(taskList, BoxLayout.Y_AXIS));
        taskList.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(taskList);
        content.add(Box.createVerticalStrut(16));

        killPanel = new JPanel();
        killPanel.setOpaque(false);
        killPanel.setLayout(new BoxLayout(killPanel, BoxLayout.Y_AXIS));
        killPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        killPanel.setVisible(false);
        content.add(killPanel);
        content.add(Box.createVerticalStrut(16));

        content.add(sectionLabel("ALIVE"));
        aliveList = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        aliveList.setOpaque(false);
        aliveList.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(aliveList);
        content.add(Box.createVerticalStrut(20));

        ThemedButton meetingButton = new ThemedButton("Call Emergency Meeting", false);
        meetingButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        meetingButton.setMaximumSize(new Dimension(2000, 38));
        meetingButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { callMeeting(); }
        });
        content.add(meetingButton);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        return scroll;
    }

    private JLabel sectionLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_NAV_BOLD);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 8, 0));
        return label;
    }

    private void rebuildTaskList()
    {
        taskList.removeAll();
        if (myTasks != null)
        {
            for (int i = 0; i < myTasks.size(); i++)
            {
                taskList.add(buildTaskRow(i, myTasks.get(i)));
                taskList.add(Box.createVerticalStrut(6));
            }
        }
        taskList.revalidate();
        taskList.repaint();
    }

    private JPanel buildTaskRow(final int index, String name)
    {
        RoundedPanel row = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_BUTTON);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(10, 14, 10, 14));
        row.setMaximumSize(new Dimension(2000, 44));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        boolean done = completedLocally.contains(index);

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(UITheme.FONT_BODY);
        nameLabel.setForeground(ThemeManager.getColor(done ? ThemeColor.TEXT_MUTED : ThemeColor.TEXT_PRIMARY));
        row.add(nameLabel, BorderLayout.WEST);

        final ThemedButton complete = new ThemedButton(done ? "Done" : "Complete", !done);
        complete.setEnabled(!done);
        complete.setPreferredSize(new Dimension(90, 30));
        complete.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { completeTask(index); }
        });
        row.add(complete, BorderLayout.EAST);

        return row;
    }

    private void completeTask(int index)
    {
        completedLocally.add(index);
        rebuildTaskList();

        Message request = new Message();
        request.setType(MessageType.AMONG_TASK_COMPLETE_REQUEST);
        request.setMatchId(matchId);
        request.setAmongTaskIndex(index);
        NetworkManager.sendAsync(request);
    }

    private void rebuildAliveList()
    {
        aliveList.removeAll();
        if (currentAlive != null)
        {
            for (int i = 0; i < currentAlive.size(); i++)
            {
                JLabel dot = new JLabel("\u2022 " + currentAlive.get(i));
                dot.setFont(UITheme.FONT_SMALL);
                dot.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
                aliveList.add(dot);
            }
        }
        aliveList.revalidate();
        aliveList.repaint();

        rebuildKillPanel();
    }

    private void rebuildKillPanel()
    {
        killPanel.removeAll();
        boolean isImpostor = "IMPOSTOR".equals(myRole);
        killPanel.setVisible(isImpostor);

        if (isImpostor && currentAlive != null)
        {
            killPanel.add(sectionLabel("KILL A CREWMATE"));
            String myUsername = Session.isLoggedIn() ? Session.getCurrentAccount().getUsername() : null;

            for (int i = 0; i < currentAlive.size(); i++)
            {
                final String target = currentAlive.get(i);
                if (target.equalsIgnoreCase(myUsername))
                {
                    continue;
                }
                RoundedPanel row = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_BUTTON);
                row.setLayout(new BorderLayout());
                row.setBorder(new EmptyBorder(8, 12, 8, 12));
                row.setMaximumSize(new Dimension(2000, 40));
                row.setAlignmentX(Component.LEFT_ALIGNMENT);

                JLabel nameLabel = new JLabel(target);
                nameLabel.setFont(UITheme.FONT_SMALL);
                nameLabel.setForeground(new Color(240, 100, 100));
                row.add(nameLabel, BorderLayout.WEST);

                ThemedButton kill = new ThemedButton("Kill", false);
                kill.setPreferredSize(new Dimension(70, 28));
                kill.addActionListener(new ActionListener()
                {
                    public void actionPerformed(ActionEvent e) { attemptKill(target); }
                });
                row.add(kill, BorderLayout.EAST);

                killPanel.add(row);
                killPanel.add(Box.createVerticalStrut(4));
            }
        }
        killPanel.revalidate();
        killPanel.repaint();
    }

    private void attemptKill(String targetUsername)
    {
        Message request = new Message();
        request.setType(MessageType.AMONG_KILL_REQUEST);
        request.setMatchId(matchId);
        request.setToUsername(targetUsername);
        NetworkManager.sendAsync(request);
    }

    private void callMeeting()
    {
        Message request = new Message();
        request.setType(MessageType.AMONG_CALL_MEETING_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    // ==================== Meeting ====================

    private JPanel createMeetingScreen()
    {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(24, 24, 24, 24));
        panel.setPreferredSize(new Dimension(400, 460));

        JLabel title = new JLabel("Emergency Meeting");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        meetingReasonLabel = new JLabel(" ");
        meetingReasonLabel.setFont(UITheme.FONT_SUBHEAD);
        meetingReasonLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        meetingReasonLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        meetingReasonLabel.setBorder(new EmptyBorder(6, 0, 20, 0));
        panel.add(meetingReasonLabel);

        panel.add(sectionLabel("VOTE TO EJECT"));
        voteList = new JPanel();
        voteList.setOpaque(false);
        voteList.setLayout(new BoxLayout(voteList, BoxLayout.Y_AXIS));
        voteList.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(voteList);
        panel.add(Box.createVerticalStrut(10));

        voteStatusLabel = new JLabel(" ");
        voteStatusLabel.setFont(UITheme.FONT_SMALL);
        voteStatusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        voteStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(voteStatusLabel);

        return panel;
    }

    private void showMeeting(String reason, String deadUsername, List<String> alive)
    {
        voted = false;
        currentAlive = alive;

        if ("BODY_FOUND".equals(reason))
        {
            meetingReasonLabel.setText(deadUsername + "'s body was found!");
        }
        else
        {
            meetingReasonLabel.setText("An emergency meeting was called.");
        }

        voteStatusLabel.setText(" ");
        rebuildVoteList();
        cardLayout.show(cards, MEETING);
    }

    private void rebuildVoteList()
    {
        voteList.removeAll();
        if (currentAlive != null)
        {
            for (int i = 0; i < currentAlive.size(); i++)
            {
                final String target = currentAlive.get(i);
                voteList.add(buildVoteRow(target));
                voteList.add(Box.createVerticalStrut(6));
            }
        }

        RoundedPanel skipRow = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_BUTTON);
        skipRow.setLayout(new BorderLayout());
        skipRow.setBorder(new EmptyBorder(10, 14, 10, 14));
        skipRow.setMaximumSize(new Dimension(2000, 44));
        skipRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel skipLabel = new JLabel("Skip Vote");
        skipLabel.setFont(UITheme.FONT_BODY);
        skipLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        skipRow.add(skipLabel, BorderLayout.WEST);

        ThemedButton skipButton = new ThemedButton("Vote", false);
        skipButton.setPreferredSize(new Dimension(80, 30));
        skipButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { castVote(null); }
        });
        skipRow.add(skipButton, BorderLayout.EAST);
        voteList.add(skipRow);

        voteList.revalidate();
        voteList.repaint();
    }

    private JPanel buildVoteRow(final String target)
    {
        RoundedPanel row = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_BUTTON);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(10, 14, 10, 14));
        row.setMaximumSize(new Dimension(2000, 44));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nameLabel = new JLabel(target);
        nameLabel.setFont(UITheme.FONT_BODY);
        nameLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        row.add(nameLabel, BorderLayout.WEST);

        ThemedButton voteButton = new ThemedButton("Vote", true);
        voteButton.setPreferredSize(new Dimension(80, 30));
        voteButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { castVote(target); }
        });
        row.add(voteButton, BorderLayout.EAST);

        return row;
    }

    private void castVote(String targetUsername)
    {
        if (voted)
        {
            return;
        }
        voted = true;
        voteStatusLabel.setText("Vote submitted - waiting for everyone else...");

        Message request = new Message();
        request.setType(MessageType.AMONG_VOTE_REQUEST);
        request.setMatchId(matchId);
        request.setToUsername(targetUsername);
        NetworkManager.sendAsync(request);
    }

    // ==================== Push handling ====================

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isAmongType = type == MessageType.AMONG_MATCH_FOUND || type == MessageType.AMONG_STATE_UPDATE
            || type == MessageType.AMONG_MEETING_START || type == MessageType.AMONG_MEETING_RESULT
            || type == MessageType.AMONG_GAME_OVER || type == MessageType.QUEUE_UPDATE;
        if (!isAmongType)
        {
            return;
        }
        if (type == MessageType.QUEUE_UPDATE && !"among-us".equals(message.getQueueGameId()))
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
            public void run() { handleAmongMessage(message); }
        });
    }

    private void handleAmongMessage(Message message)
    {
        if (message.getType() == MessageType.QUEUE_UPDATE)
        {
            if (matchId == null)
            {
                searchingLabel.setText("Waiting for more players... " + message.getQueueCount()
                    + " in queue (need 4 to start)");
            }
        }
        else if (message.getType() == MessageType.AMONG_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            myRole = message.getAmongRole();
            myTasks = message.getAmongTasks();
            currentRoster = message.getAmongRosterUsernames();
            currentAlive = currentRoster;

            boolean isImpostor = "IMPOSTOR".equals(myRole);
            roleLabel.setText(isImpostor ? "You are the IMPOSTOR" : "You are a Crewmate");
            roleLabel.setForeground(isImpostor ? new Color(240, 100, 100) : ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));

            rebuildTaskList();
            rebuildAliveList();
            cardLayout.show(cards, GAME);
            pack();
            setLocationRelativeTo(null);
        }
        else if (message.getType() == MessageType.AMONG_STATE_UPDATE)
        {
            progressLabel.setText("Team Tasks: " + message.getAmongTeamTaskProgress() + "%");
            currentAlive = message.getAmongAliveUsernames();
            rebuildAliveList();
        }
        else if (message.getType() == MessageType.AMONG_MEETING_START)
        {
            showMeeting(message.getAmongMeetingReason(), message.getAmongDeadUsername(), message.getAmongAliveUsernames());
        }
        else if (message.getType() == MessageType.AMONG_MEETING_RESULT)
        {
            currentAlive = message.getAmongAliveUsernames();
            String ejected = message.getAmongEjectedUsername();
            String text = ejected == null
                ? "No one was ejected."
                : ejected + " was ejected. They were a " + message.getAmongEjectedRole() + ".";
            GameHubDialog.show(this, "Meeting Result", text);

            rebuildAliveList();
            cardLayout.show(cards, GAME);
        }
        else if (message.getType() == MessageType.AMONG_GAME_OVER)
        {
            String winningTeam = message.getAmongWinningTeam();
            String text = "CREWMATES".equals(winningTeam)
                ? "Crewmates win!"
                : "Impostors win!";
            recordPlayed();
            GameHubDialog.show(this, "Game Over", text);
            dispose();
        }
    }

    private void recordPlayed()
    {
        if (!Session.isLoggedIn())
        {
            GuestPlayTracker.recordGuestPlay("among-us", 0);
            return;
        }

        Message request = new Message();
        request.setType(MessageType.GAME_PLAYED_REQUEST);
        request.setGameId("among-us");
        NetworkManager.sendAsync(request);
    }
}
