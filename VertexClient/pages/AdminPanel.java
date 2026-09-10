package pages;
import ui.GameHubDialog;
import ui.ThemedButton;
import ui.StatusPill;
import net.NetworkManager;
import net.MessageType;
import theme.ThemeManager;
import theme.UITheme;
import ui.ThemedScrollBarUI;
import ui.PageHeader;
import theme.ThemeColor;
import net.Message;
import ui.RoundedPanel;

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
    private static final String BANS = "BANS";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private JPanel playersGrid;
    private JPanel logList;
    private JPanel bansGrid;

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
        cards.add(createBansView(), BANS);
        add(cards, BorderLayout.CENTER);
        cardLayout.show(cards, OVERVIEW);
    }

    private JScrollPane createOverview()
    {
        JPanel grid = new JPanel(new GridLayout(0, 3, 18, 18));
        grid.setOpaque(false);

        grid.add(sectionCard("Players", "Manage account roles - promote to Moderator or revert to Player.",
            new Runnable() { public void run() { openPlayers(); } }));
        grid.add(sectionCard("Bans", "Ban or unban players, with a reason kept on record.",
            new Runnable() { public void run() { openBans(); } }));
        grid.add(sectionCard("Admin Log", "See every approval, removal, and role change made on this server.",
            new Runnable() { public void run() { openLog(); } }));
        grid.add(placeholderCard("Games", "Publish, update, and manage games.", "a future phase"));
        grid.add(placeholderCard("Economy", "Coin balances and shop items.", "a future phase"));
        grid.add(placeholderCard("Announcements", "Post platform-wide notices.", "a future phase"));

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

    private void openBans()
    {
        cardLayout.show(cards, BANS);
        refreshBans();
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
        else
        {
            if ("MODERATOR".equals(role))
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

            ThemedButton ban = new ThemedButton("Ban", false);
            ban.setPreferredSize(new Dimension(70, 32));
            ban.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    BanReasonDialog.show(AdminPanel.this, username, new Runnable()
                    {
                        public void run() { refreshPlayers(); }
                    });
                }
            });
            right.add(ban);

            final ThemedButton mute = new ThemedButton("Mute", false);
            mute.setPreferredSize(new Dimension(76, 32));
            mute.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { showMuteDurationPicker(mute, username); }
            });
            right.add(mute);

            ThemedButton kick = new ThemedButton("Kick", false);
            kick.setPreferredSize(new Dimension(76, 32));
            kick.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { sendModAction(MessageType.MOD_KICK_REQUEST, username, 0); }
            });
            right.add(kick);
        }

        row.add(right, BorderLayout.EAST);
        return row;
    }

    /** A tiny popup with a few preset durations - a full dialog felt like overkill for "how long," and 5/15/60 covers the realistic range of a mute (long enough to cool someone down, short enough that a mistaken mute isn't a big deal). */
    private void showMuteDurationPicker(Component anchor, final String username)
    {
        final javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
        JPanel picker = new JPanel();
        picker.setLayout(new BoxLayout(picker, BoxLayout.Y_AXIS));
        picker.setBackground(ThemeManager.getColor(ThemeColor.BG_PANEL));
        picker.setBorder(new EmptyBorder(6, 6, 6, 6));

        int[] durations = { 5, 15, 60 };
        for (final int minutes : durations)
        {
            ThemedButton option = new ThemedButton(minutes + " minutes", false);
            option.setAlignmentX(Component.LEFT_ALIGNMENT);
            option.setMaximumSize(new Dimension(160, 32));
            option.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    popup.setVisible(false);
                    sendModAction(MessageType.MOD_MUTE_REQUEST, username, minutes);
                }
            });
            picker.add(option);
            picker.add(javax.swing.Box.createVerticalStrut(4));
        }

        popup.add(picker);
        popup.show(anchor, 0, anchor.getHeight());
    }

    private void sendModAction(final MessageType type, final String username, final int minutes)
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(type);
                request.setUsername(username);
                if (minutes > 0) request.setMuteDurationMinutes(minutes);
                NetworkManager.send(request);
            }
        });
        worker.start();
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

    // ==================== Bans ====================

    private JScrollPane createBansView()
    {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(backRow());
        content.add(sectionTitle("BANS"));

        bansGrid = new JPanel();
        bansGrid.setOpaque(false);
        bansGrid.setLayout(new BoxLayout(bansGrid, BoxLayout.Y_AXIS));
        bansGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(bansGrid);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        return scroll;
    }

    private void refreshBans()
    {
        bansGrid.removeAll();
        JLabel loading = new JLabel("Loading bans...");
        loading.setFont(UITheme.FONT_BODY);
        loading.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        loading.setAlignmentX(Component.LEFT_ALIGNMENT);
        bansGrid.add(loading);
        bansGrid.revalidate();
        bansGrid.repaint();

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.ADMIN_BAN_LIST_REQUEST);
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run() { renderBans(response); }
                });
            }
        });
        worker.start();
    }

    private void renderBans(Message response)
    {
        bansGrid.removeAll();

        if (response == null || !response.isSuccess() || response.getBanRecords() == null
            || response.getBanRecords().isEmpty())
        {
            JLabel empty = new JLabel(response != null && !response.isSuccess() && response.getErrorText() != null
                ? response.getErrorText() : "No one is banned right now.");
            empty.setFont(UITheme.FONT_BODY);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            bansGrid.add(empty);
        }
        else
        {
            List<String> records = response.getBanRecords();
            for (int i = 0; i < records.size(); i++)
            {
                String[] parts = records.get(i).split("\\|", -1);
                if (parts.length < 4) continue;
                bansGrid.add(buildBanRow(parts[0], parts[1], parts[2], parts[3]));
                bansGrid.add(javax.swing.Box.createVerticalStrut(10));
            }
        }

        bansGrid.revalidate();
        bansGrid.repaint();
    }

    private JPanel buildBanRow(final String username, String reason, String bannedBy, String timestampMillis)
    {
        RoundedPanel row = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(12, 16, 12, 16));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(900, 64));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JLabel name = new JLabel(username);
        name.setFont(UITheme.FONT_NAV_BOLD);
        name.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(name);

        String when = "";
        try
        {
            when = new java.text.SimpleDateFormat("MMM d, yyyy").format(new java.util.Date(Long.parseLong(timestampMillis)));
        }
        catch (NumberFormatException ignored) { }

        String detail = (reason == null || reason.trim().isEmpty() ? "No reason given" : reason)
            + " - banned by " + (bannedBy == null || bannedBy.isEmpty() ? "unknown" : bannedBy)
            + (when.isEmpty() ? "" : " on " + when);
        JLabel detailLabel = new JLabel(detail);
        detailLabel.setFont(UITheme.FONT_SMALL);
        detailLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        detailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(detailLabel);

        row.add(left, BorderLayout.WEST);

        ThemedButton unban = new ThemedButton("Unban", false);
        unban.setPreferredSize(new Dimension(90, 32));
        unban.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                Thread worker = new Thread(new Runnable()
                {
                    public void run()
                    {
                        Message request = new Message();
                        request.setType(MessageType.ADMIN_UNBAN_REQUEST);
                        request.setTargetUsername(username);
                        NetworkManager.send(request);
                        SwingUtilities.invokeLater(new Runnable()
                        {
                            public void run() { refreshBans(); }
                        });
                    }
                });
                worker.start();
            }
        });
        row.add(unban, BorderLayout.EAST);

        return row;
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
