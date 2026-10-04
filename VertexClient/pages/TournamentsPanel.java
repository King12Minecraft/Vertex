package pages;
import ui.GameHubDialog;
import net.MessageType;
import net.NetworkManager;
import ui.ThemedScrollBarUI;
import theme.UITheme;
import ui.ThemedButton;
import ui.ThemedLabel;
import ui.PageHeader;
import theme.ThemeColor;
import net.Message;
import ui.RoundedPanel;

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
public class TournamentsPanel extends PageScaffold implements NetworkManager.PushListener
{
    private final JPanel list;
    private JPanel teamList;

    public TournamentsPanel()
    {
        super("TOURNAMENTS", "Four-player knockout brackets. Create one, or join an open one.");

        // ---- start one ----
        JPanel start = new JPanel();
        start.setOpaque(false);
        start.setLayout(new BoxLayout(start, BoxLayout.Y_AXIS));

        start.add(caption("Solo brackets - 4 players, single elimination."));
        JPanel createRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        createRow.setOpaque(false);
        createRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        createRow.setBorder(new EmptyBorder(0, -10, 14, 0));

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
        start.add(createRow);

        start.add(caption("Team brackets - your whole party must be exactly the right size for the mode."));
        JPanel teamCreateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        teamCreateRow.setOpaque(false);
        teamCreateRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        teamCreateRow.setBorder(new EmptyBorder(0, -10, 0, 0));

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
        start.add(teamCreateRow);

        row(PageScaffold.fullWidth(new SectionCard("START A TOURNAMENT").withGlow().content(start)));
        gap(16);

        // ---- what's open ----
        list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        teamList = new JPanel();
        teamList.setOpaque(false);
        teamList.setLayout(new BoxLayout(teamList, BoxLayout.Y_AXIS));
        row(PageScaffold.split(new SectionCard("OPEN & IN-PROGRESS").content(list),
            new SectionCard("TEAM TOURNAMENTS").content(teamList), 1, 1));

        NetworkManager.addPushListener(this);
        refreshList();
        refreshTeamList();
    }

    private JLabel caption(String text)
    {
        JLabel label = new ThemedLabel(text, ThemeColor.TEXT_MUTED);
        label.setFont(UITheme.FONT_SMALL);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 8, 0));
        return label;
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
            JLabel empty = new ThemedLabel("No tournaments open right now - start one above.", ThemeColor.TEXT_MUTED);
            empty.setFont(UITheme.FONT_SMALL);
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

        RoundedPanel row = new RoundedPanel(ThemeColor.BG_APP, UITheme.RADIUS_BUTTON);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(9, 16, 9, 16));
        row.setMaximumSize(new Dimension(2000, 64));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));

        JLabel nameLabel = new ThemedLabel(gameName + " Tournament", ThemeColor.TEXT_PRIMARY);
        nameLabel.setFont(UITheme.FONT_NAV_BOLD);

        JLabel statusLabel = new ThemedLabel(statusText, ThemeColor.TEXT_MUTED);
        statusLabel.setFont(UITheme.FONT_SMALL);
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
            JLabel empty = new ThemedLabel("No team tournaments open right now - start one above.", ThemeColor.TEXT_MUTED);
            empty.setFont(UITheme.FONT_SMALL);
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

        RoundedPanel row = new RoundedPanel(ThemeColor.BG_APP, UITheme.RADIUS_BUTTON);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(9, 16, 9, 16));
        row.setMaximumSize(new Dimension(2000, 64));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));

        JLabel nameLabel = new ThemedLabel(mode + " Fight Arena Tournament", ThemeColor.TEXT_PRIMARY);
        nameLabel.setFont(UITheme.FONT_NAV_BOLD);

        JLabel statusLabel = new ThemedLabel(statusText, ThemeColor.TEXT_MUTED);
        statusLabel.setFont(UITheme.FONT_SMALL);
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
