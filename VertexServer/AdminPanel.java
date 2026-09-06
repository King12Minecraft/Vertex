import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
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
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * AdminPanel
 * ----------
 * Only reachable by ADMIN accounts (see Sidebar). Two real sections now:
 *
 *   - Players: every account on this server, with Promote/Demote
 *     buttons for MODERATOR (never ADMIN - see Message.getNewRole()'s
 *     javadoc for why that stays bootstrap-only, and
 *     handleAdminSetRole's own refusal to touch another admin).
 *   - Admin Log: the audit trail (AdminLog) of every approval,
 *     removal, and role change an admin/moderator has made.
 *
 * Same reminder as always: this page being hidden from non-admins is a
 * UI convenience, not security - the server independently verifies
 * ADMIN role for every one of these requests, never trusting the client.
 */
public class AdminPanel extends RoundedPanel
{
    private static final String OVERVIEW = "OVERVIEW";
    private static final String PLAYERS = "PLAYERS";
    private static final String LOG = "LOG";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private JPanel playersGrid;
    private JPanel logList;

    public AdminPanel()
    {
        super(ThemeColor.BG_APP, 0);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 32, 24, 32));

        PageHeader header = new PageHeader("ADMIN PANEL");
        add(header, BorderLayout.NORTH);

        cards.add(createOverview(), OVERVIEW);
        cards.add(createPlayersView(), PLAYERS);
        cards.add(createLogView(), LOG);
        add(cards, BorderLayout.CENTER);
        cardLayout.show(cards, OVERVIEW);
    }

    private JScrollPane createOverview()
    {
        JPanel grid = new JPanel(new GridLayout(0, 3, 18, 18));
        grid.setOpaque(false);

        grid.add(sectionCard("Players", "Manage account roles - promote to Moderator or revert to Player.",
            new Runnable() { public void run() { openPlayers(); } }));
        grid.add(sectionCard("Admin Log", "See every approval, removal, and role change made on this server.",
            new Runnable() { public void run() { openLog(); } }));
        grid.add(placeholderCard("Games", "Publish, update, and manage games.", "a future phase"));
        grid.add(placeholderCard("Economy", "Coin balances and shop items.", "a future phase"));
        grid.add(placeholderCard("Announcements", "Post platform-wide notices.", "a future phase"));
        grid.add(placeholderCard("Server Status", "Live server health and stats.", "a future phase"));

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        return scroll;
    }

    private void openPlayers()
    {
        cardLayout.show(cards, PLAYERS);
        refreshPlayers();
    }

    private void openLog()
    {
        cardLayout.show(cards, LOG);
        refreshLog();
    }

    // ==================== Players ====================

    private JScrollPane createPlayersView()
    {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(backRow());

        JLabel title = sectionTitle("PLAYERS");
        content.add(title);

        playersGrid = new JPanel();
        playersGrid.setOpaque(false);
        playersGrid.setLayout(new BoxLayout(playersGrid, BoxLayout.Y_AXIS));
        playersGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(playersGrid);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        return scroll;
    }

    private void refreshPlayers()
    {
        playersGrid.removeAll();
        JLabel loading = new JLabel("Loading accounts...");
        loading.setFont(UITheme.FONT_BODY);
        loading.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        loading.setAlignmentX(Component.LEFT_ALIGNMENT);
        playersGrid.add(loading);
        playersGrid.revalidate();
        playersGrid.repaint();

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.ADMIN_ACCOUNT_LIST_REQUEST);
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run() { renderPlayers(response); }
                });
            }
        });
        worker.start();
    }

    private void renderPlayers(Message response)
    {
        playersGrid.removeAll();

        if (response == null || !response.isSuccess() || response.getAccountSummaries() == null)
        {
            JLabel error = new JLabel(response != null && response.getErrorText() != null
                ? response.getErrorText() : "Could not reach the server.");
            error.setFont(UITheme.FONT_BODY);
            error.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            error.setAlignmentX(Component.LEFT_ALIGNMENT);
            playersGrid.add(error);
        }
        else
        {
            List<String> summaries = response.getAccountSummaries();
            for (int i = 0; i < summaries.size(); i++)
            {
                String[] parts = summaries.get(i).split("\\|", -1);
                if (parts.length < 3)
                {
                    continue;
                }
                playersGrid.add(buildPlayerRow(parts[0], parts[1], parts[2]));
                playersGrid.add(javax.swing.Box.createVerticalStrut(10));
            }
        }

        playersGrid.revalidate();
        playersGrid.repaint();
    }

    private JPanel buildPlayerRow(final String username, String role, String coins)
    {
        RoundedPanel row = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(12, 16, 12, 16));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(900, 56));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);

        JLabel name = new JLabel(username);
        name.setFont(UITheme.FONT_NAV_BOLD);
        name.setForeground(roleColor(role));
        left.add(name);

        StatusPill pill = new StatusPill(role, roleColor(role));
        left.add(pill);

        JLabel coinsLabel = new JLabel(coins + " coins");
        coinsLabel.setFont(UITheme.FONT_SMALL);
        coinsLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        left.add(coinsLabel);

        row.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        if ("ADMIN".equals(role))
        {
            JLabel note = new JLabel("Admin role can't be changed here.");
            note.setFont(UITheme.FONT_SMALL);
            note.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            right.add(note);
        }
        else if ("MODERATOR".equals(role))
        {
            ThemedButton demote = new ThemedButton("Revert to Player", false);
            demote.setPreferredSize(new Dimension(160, 32));
            demote.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { setRole(username, "PLAYER"); }
            });
            right.add(demote);
        }
        else
        {
            ThemedButton promote = new ThemedButton("Promote to Moderator", true);
            promote.setPreferredSize(new Dimension(180, 32));
            promote.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { setRole(username, "MODERATOR"); }
            });
            right.add(promote);
        }

        row.add(right, BorderLayout.EAST);
        return row;
    }

    private Color roleColor(String role)
    {
        if ("ADMIN".equals(role)) return new Color(230, 90, 90);
        if ("MODERATOR".equals(role)) return new Color(90, 170, 230);
        return ThemeManager.getColor(ThemeColor.TEXT_MUTED);
    }

    private void setRole(final String username, final String newRole)
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.ADMIN_SET_ROLE_REQUEST);
                request.setTargetUsername(username);
                request.setNewRole(newRole);
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (response == null || !response.isSuccess())
                        {
                            String reason = response != null && response.getErrorText() != null
                                ? response.getErrorText() : "Could not reach the server.";
                            GameHubDialog.show(playersGrid, "Role Change Failed", reason);
                        }
                        refreshPlayers();
                    }
                });
            }
        });
        worker.start();
    }

    // ==================== Admin Log ====================

    private JScrollPane createLogView()
    {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(backRow());
        content.add(sectionTitle("ADMIN LOG"));

        logList = new JPanel();
        logList.setOpaque(false);
        logList.setLayout(new BoxLayout(logList, BoxLayout.Y_AXIS));
        logList.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(logList);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        return scroll;
    }

    private void refreshLog()
    {
        logList.removeAll();
        JLabel loading = new JLabel("Loading log...");
        loading.setFont(UITheme.FONT_BODY);
        loading.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        loading.setAlignmentX(Component.LEFT_ALIGNMENT);
        logList.add(loading);
        logList.revalidate();
        logList.repaint();

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.ADMIN_LOG_REQUEST);
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run() { renderLog(response); }
                });
            }
        });
        worker.start();
    }

    private void renderLog(Message response)
    {
        logList.removeAll();

        if (response == null || !response.isSuccess() || response.getAdminLogEntries() == null
            || response.getAdminLogEntries().isEmpty())
        {
            JLabel empty = new JLabel(response != null && !response.isSuccess() && response.getErrorText() != null
                ? response.getErrorText() : "No admin actions logged yet.");
            empty.setFont(UITheme.FONT_BODY);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            logList.add(empty);
        }
        else
        {
            List<String> entries = response.getAdminLogEntries();
            for (int i = 0; i < entries.size(); i++)
            {
                JLabel line = new JLabel(entries.get(i));
                line.setFont(UITheme.FONT_SMALL);
                line.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
                line.setAlignmentX(Component.LEFT_ALIGNMENT);
                line.setBorder(new EmptyBorder(0, 0, 8, 0));
                logList.add(line);
            }
        }

        logList.revalidate();
        logList.repaint();
    }

    // ==================== Shared bits ====================

    private JPanel backRow()
    {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(0, 0, 10, 0));

        ThemedButton back = new ThemedButton("< Back", false);
        back.setPreferredSize(new Dimension(100, 32));
        back.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { cardLayout.show(cards, OVERVIEW); }
        });
        row.add(back);
        return row;
    }

    private JLabel sectionTitle(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_NAV_BOLD);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 16, 0));
        return label;
    }

    private JPanel sectionCard(final String title, String description, final Runnable onOpen)
    {
        final RoundedPanel card = buildBaseCard(title, description);
        final ThemedButton open = new ThemedButton("Open", true);
        open.setAlignmentX(Component.LEFT_ALIGNMENT);
        open.setMaximumSize(new Dimension(500, 34));
        open.setPreferredSize(new Dimension(120, 34));
        open.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { onOpen.run(); }
        });
        card.add(open);
        return card;
    }

    private JPanel placeholderCard(final String title, String description, final String arrivesIn)
    {
        final RoundedPanel card = buildBaseCard(title, description);
        final ThemedButton open = new ThemedButton("Open", false);
        open.setAlignmentX(Component.LEFT_ALIGNMENT);
        open.setMaximumSize(new Dimension(500, 34));
        open.setPreferredSize(new Dimension(120, 34));
        open.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                GameHubDialog.show(open, title, "This section arrives in " + arrivesIn + ".");
            }
        });
        card.add(open);
        return card;
    }

    private RoundedPanel buildBaseCard(String title, String description)
    {
        final RoundedPanel card = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(18, 18, 18, 18));
        card.setPreferredSize(new Dimension(220, 175));
        card.enableTopAccent();
        card.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseEntered(java.awt.event.MouseEvent e) { card.glow().animateIn(); }
            public void mouseExited(java.awt.event.MouseEvent e)  { card.glow().animateOut(); }
        });

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(UITheme.FONT_NAV_BOLD);
        titleLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel descLabel = new JLabel("<html><body style='width:170px'>" + description + "</body></html>");
        descLabel.setFont(UITheme.FONT_SMALL);
        descLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        descLabel.setBorder(new EmptyBorder(6, 0, 14, 0));

        card.add(titleLabel);
        card.add(descLabel);
        return card;
    }
}
