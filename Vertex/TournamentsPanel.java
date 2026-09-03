import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * TournamentsPanel
 * ----------------
 * Create or join a 4-player single-elimination bracket, for
 * Battleship or Rock Paper Scissors only (both always produce a
 * decisive winner - see TournamentManager for why). Once a bracket
 * fills, matches are created directly server-side and the existing
 * BattleshipWindow/RockPaperScissorsWindow game windows receive the
 * normal MATCH_FOUND push the moment the player opens that game -
 * this page only handles registration and status, not gameplay itself.
 */
public class TournamentsPanel extends RoundedPanel implements NetworkManager.PushListener
{
    private final JPanel list;
    private JPanel teamList;

    public TournamentsPanel()
    {
        super(ThemeColor.BG_APP, 0);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 32, 24, 32));

        add(new PageHeader("TOURNAMENTS"), BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(24, 0, 24, 0));

        JPanel createRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        createRow.setOpaque(false);
        createRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        ThemedButton createBattleship = new ThemedButton("New Battleship Tournament", true);
        createBattleship.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { createTournament("battleship"); }
        });
        createRow.add(createBattleship);

        ThemedButton createRps = new ThemedButton("New RPS Tournament", true);
        createRps.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { createTournament("rock-paper-scissors"); }
        });
        createRow.add(createRps);

        content.add(createRow);
        content.add(Box.createVerticalStrut(20));

        JLabel sectionLabel = new JLabel("OPEN & IN-PROGRESS TOURNAMENTS");
        sectionLabel.setFont(UITheme.FONT_NAV_BOLD);
        sectionLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        sectionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionLabel.setBorder(new EmptyBorder(0, 0, 14, 0));
        content.add(sectionLabel);

        list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(list);

        content.add(Box.createVerticalStrut(28));

        JLabel teamCreateHint = new JLabel("Requires your whole party to be the exact right size for the mode.");
        teamCreateHint.setFont(UITheme.FONT_SMALL);
        teamCreateHint.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        teamCreateHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        teamCreateHint.setBorder(new EmptyBorder(0, 0, 8, 0));
        content.add(teamCreateHint);

        JPanel teamCreateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        teamCreateRow.setOpaque(false);
        teamCreateRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        ThemedButton create2v2 = new ThemedButton("New 2v2 Fight Arena Tournament", true);
        create2v2.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { createTeamTournament("2V2"); }
        });
        teamCreateRow.add(create2v2);

        ThemedButton create3v3 = new ThemedButton("New 3v3 Fight Arena Tournament", true);
        create3v3.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { createTeamTournament("3V3"); }
        });
        teamCreateRow.add(create3v3);

        content.add(teamCreateRow);
        content.add(Box.createVerticalStrut(20));

        JLabel teamSectionLabel = new JLabel("OPEN & IN-PROGRESS TEAM TOURNAMENTS");
        teamSectionLabel.setFont(UITheme.FONT_NAV_BOLD);
        teamSectionLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        teamSectionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        teamSectionLabel.setBorder(new EmptyBorder(0, 0, 14, 0));
        content.add(teamSectionLabel);

        teamList = new JPanel();
        teamList.setOpaque(false);
        teamList.setLayout(new BoxLayout(teamList, BoxLayout.Y_AXIS));
        teamList.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(teamList);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        add(scroll, BorderLayout.CENTER);

        NetworkManager.addPushListener(this);
        refreshList();
        refreshTeamList();
    }

    private void createTournament(String gameId)
    {
        Message request = new Message();
        request.setType(MessageType.TOURNAMENT_CREATE_REQUEST);
        request.setGameId(gameId);
        NetworkManager.sendAsync(request);
    }

    private void createTeamTournament(String mode)
    {
        Message request = new Message();
        request.setType(MessageType.TEAM_TOURNAMENT_CREATE_REQUEST);
        request.setGameId(mode);
        NetworkManager.sendAsync(request);
    }

    /**
     * TOURNAMENT_LIST_RESPONSE is deliberately routed as a push (see
     * NetworkManager.RESPONSE_TYPES's note on why), so a blocking
     * send() here would never actually get its answer that way - it
     * would just tie up NetworkManager's one shared connection lock for
     * a full 10-second timeout on every single login (TournamentsPanel
     * is built eagerly at startup like every other sidebar page), and
     * since send()/sendAsync() are both synchronized on the same lock,
     * that also stalled every OTHER panel's own data loading behind it
     * for as long as this call sat there waiting - GamesPanel's "Home"
     * view included. sendAsync() is the correct call here: it just
     * fires the request and returns immediately, and the real answer
     * still comes back through onPush() below either way.
     */
    private void refreshList()
    {
        Message request = new Message();
        request.setType(MessageType.TOURNAMENT_LIST_REQUEST);
        NetworkManager.sendAsync(request);
    }

    /** See refreshList()'s note - same reasoning, TEAM_TOURNAMENT_LIST_RESPONSE is also push-only. */
    private void refreshTeamList()
    {
        Message request = new Message();
        request.setType(MessageType.TEAM_TOURNAMENT_LIST_REQUEST);
        NetworkManager.sendAsync(request);
    }

    @Override
    public void onPush(final Message message)
    {
        if (message.getType() != MessageType.TOURNAMENT_LIST_RESPONSE
            && message.getType() != MessageType.TOURNAMENT_COMPLETE
            && message.getType() != MessageType.TEAM_TOURNAMENT_LIST_RESPONSE)
        {
            return;
        }

        if (message.getType() == MessageType.TOURNAMENT_COMPLETE)
        {
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    GameHubDialog.show(TournamentsPanel.this, "Tournament Complete",
                        message.getUsername() + " is the champion!");
                }
            });
            return;
        }

        if (message.getType() == MessageType.TEAM_TOURNAMENT_LIST_RESPONSE)
        {
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run() { renderTeamList(message.getTournamentEntries()); }
            });
            return;
        }

        SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { renderList(message.getTournamentEntries()); }
        });
    }

    private void renderList(List<String> entries)
    {
        list.removeAll();

        if (entries == null || entries.isEmpty())
        {
            JLabel empty = new JLabel("No tournaments open right now - start one above.");
            empty.setFont(UITheme.FONT_SMALL);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            list.add(empty);
        }
        else
        {
            for (int i = 0; i < entries.size(); i++)
            {
                list.add(buildRow(entries.get(i)));
                list.add(Box.createVerticalStrut(6));
            }
        }

        list.revalidate();
        list.repaint();
    }

    private JPanel buildRow(String entry)
    {
        String[] parts = entry.split("\\|", -1);
        final String id = parts.length > 0 ? parts[0] : "";
        String gameId = parts.length > 1 ? parts[1] : "";
        String status = parts.length > 2 ? parts[2] : "";
        String playerCount = parts.length > 3 ? parts[3] : "0";
        String champion = parts.length > 4 ? parts[4] : "";

        String gameName = "battleship".equals(gameId) ? "Battleship" : "Rock Paper Scissors";
        String statusText;
        if ("REGISTRATION".equals(status)) statusText = playerCount + "/4 players registered";
        else if ("ROUND_1".equals(status)) statusText = "Semifinals in progress";
        else if ("FINAL".equals(status)) statusText = "Final in progress";
        else statusText = "Complete - " + champion + " won";

        RoundedPanel row = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_BUTTON);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(12, 16, 12, 16));
        row.setMaximumSize(new Dimension(2000, 56));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel(gameName + " Tournament");
        nameLabel.setFont(UITheme.FONT_NAV_BOLD);
        nameLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));

        JLabel statusLabel = new JLabel(statusText);
        statusLabel.setFont(UITheme.FONT_SMALL);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        statusLabel.setBorder(new EmptyBorder(3, 0, 0, 0));

        textCol.add(nameLabel);
        textCol.add(statusLabel);
        row.add(textCol, BorderLayout.WEST);

        if ("REGISTRATION".equals(status))
        {
            ThemedButton join = new ThemedButton("Join", true);
            join.setPreferredSize(new Dimension(80, 32));
            join.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    Message request = new Message();
                    request.setType(MessageType.TOURNAMENT_JOIN_REQUEST);
                    request.setTournamentId(id);
                    NetworkManager.sendAsync(request);
                }
            });
            row.add(join, BorderLayout.EAST);
        }

        return row;
    }

    private void renderTeamList(List<String> entries)
    {
        teamList.removeAll();

        if (entries == null || entries.isEmpty())
        {
            JLabel empty = new JLabel("No team tournaments open right now - start one above.");
            empty.setFont(UITheme.FONT_SMALL);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            teamList.add(empty);
        }
        else
        {
            for (int i = 0; i < entries.size(); i++)
            {
                teamList.add(buildTeamRow(entries.get(i)));
                teamList.add(Box.createVerticalStrut(6));
            }
        }

        teamList.revalidate();
        teamList.repaint();
    }

    private JPanel buildTeamRow(String entry)
    {
        String[] parts = entry.split("\\|", -1);
        final String id = parts.length > 0 ? parts[0] : "";
        String mode = parts.length > 1 ? parts[1] : "";
        String status = parts.length > 2 ? parts[2] : "";
        String teamCount = parts.length > 3 ? parts[3] : "0";
        String champions = parts.length > 4 ? parts[4] : "";

        String statusText;
        if ("REGISTRATION".equals(status)) statusText = teamCount + "/2 teams registered";
        else if ("IN_PROGRESS".equals(status)) statusText = "Decider match in progress";
        else statusText = "Complete - " + champions + " won";

        RoundedPanel row = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_BUTTON);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(12, 16, 12, 16));
        row.setMaximumSize(new Dimension(2000, 56));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel(mode + " Fight Arena Tournament");
        nameLabel.setFont(UITheme.FONT_NAV_BOLD);
        nameLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));

        JLabel statusLabel = new JLabel(statusText);
        statusLabel.setFont(UITheme.FONT_SMALL);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        statusLabel.setBorder(new EmptyBorder(3, 0, 0, 0));

        textCol.add(nameLabel);
        textCol.add(statusLabel);
        row.add(textCol, BorderLayout.WEST);

        if ("REGISTRATION".equals(status))
        {
            ThemedButton join = new ThemedButton("Join", true);
            join.setPreferredSize(new Dimension(80, 32));
            join.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    Message request = new Message();
                    request.setType(MessageType.TEAM_TOURNAMENT_JOIN_REQUEST);
                    request.setTournamentId(id);
                    NetworkManager.sendAsync(request);
                }
            });
            row.add(join, BorderLayout.EAST);
        }

        return row;
    }
}
