import javax.swing.BorderFactory;
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
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * ModeratorPanel
 * --------------
 * Reachable by MODERATOR and ADMIN accounts (see Sidebar). Real,
 * functional tools throughout - not mock cards:
 *
 *   - PLAYERS: who's online right now (with Mute/Kick/Ban per row) and
 *     every account that has ever registered (with Ban/Unban per row) -
 *     fetched via ADMIN_PLAYER_LIST_REQUEST.
 *   - REPORTS: the unresolved player-report queue, each with a Resolve
 *     button - fetched via REPORT_LIST_REQUEST.
 *
 * All moderation actions are server-verified role checks
 * (ClientHandler.isModeratorOrAdmin) - never trusts the client's own
 * role claim, matching the pattern already used for the player lists.
 */
public class ModeratorPanel extends RoundedPanel
{
    private JLabel onlineCountLabel;
    private JLabel allCountLabel;
    private JPanel onlineList;
    private JPanel allList;

    private JLabel reportsCountLabel;
    private JPanel reportsList;

    public ModeratorPanel()
    {
        super(ThemeColor.BG_APP, 0);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 32, 24, 32));

        PageHeader header = new PageHeader("MODERATION");
        add(header, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(createPlayersSection());
        content.add(Box.createVerticalStrut(20));
        content.add(createReportsSection());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        add(scroll, BorderLayout.CENTER);

        loadPlayerLists();
        loadReports();
    }

    private JLabel sectionLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_NAV_BOLD);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        label.setBorder(new EmptyBorder(0, 0, 14, 0));
        return label;
    }

    // ==================== Players ====================

    private JPanel createPlayersSection()
    {
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        headerRow.setBorder(new EmptyBorder(0, 0, 14, 0));

        JLabel title = sectionLabel("PLAYERS");
        title.setBorder(new EmptyBorder(0, 0, 0, 0));
        headerRow.add(title, BorderLayout.WEST);

        ThemedButton refresh = new ThemedButton("Refresh", false);
        refresh.setPreferredSize(new Dimension(100, 32));
        refresh.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { loadPlayerLists(); }
        });
        headerRow.add(refresh, BorderLayout.EAST);

        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.add(headerRow);

        JPanel columns = new JPanel(new GridLayout(1, 2, 18, 0));
        columns.setOpaque(false);
        columns.setAlignmentX(Component.LEFT_ALIGNMENT);
        columns.setMaximumSize(new Dimension(2000, 320));

        onlineCountLabel = new JLabel("ONLINE NOW");
        onlineList = new JPanel();
        columns.add(playerListCard(onlineCountLabel, onlineList));

        allCountLabel = new JLabel("ALL PLAYERS");
        allList = new JPanel();
        columns.add(playerListCard(allCountLabel, allList));

        wrap.add(columns);
        return wrap;
    }

    private RoundedPanel playerListCard(JLabel countLabel, JPanel listPanel)
    {
        RoundedPanel card = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.setPreferredSize(new Dimension(0, 300));
        card.enableTopAccent();

        countLabel.setFont(UITheme.FONT_NAV_BOLD);
        countLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        countLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
        card.add(countLabel, BorderLayout.NORTH);

        listPanel.setOpaque(false);
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        card.add(scroll, BorderLayout.CENTER);

        return card;
    }

    private void loadPlayerLists()
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.ADMIN_PLAYER_LIST_REQUEST);
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (response != null && response.isSuccess())
                        {
                            renderOnlineList(response.getOnlineUsernames());
                            renderAllList(response.getAllRegisteredUsernames());
                        }
                        else if (response != null)
                        {
                            onlineCountLabel.setText("ONLINE NOW");
                            onlineList.removeAll();
                            onlineList.add(mutedLabel(response.getErrorText()));
                            onlineList.revalidate();
                            onlineList.repaint();
                        }
                    }
                });
            }
        });
        worker.start();
    }

    private void renderOnlineList(List<String> usernames)
    {
        int count = usernames == null ? 0 : usernames.size();
        onlineCountLabel.setText("ONLINE NOW (" + count + ")");

        onlineList.removeAll();
        if (usernames == null || usernames.isEmpty())
        {
            onlineList.add(mutedLabel("Nobody online."));
        }
        else
        {
            for (int i = 0; i < usernames.size(); i++)
            {
                onlineList.add(buildOnlinePlayerRow(usernames.get(i)));
                onlineList.add(Box.createVerticalStrut(6));
            }
        }
        onlineList.revalidate();
        onlineList.repaint();
    }

    private JPanel buildOnlinePlayerRow(final String username)
    {
        RoundedPanel row = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_BUTTON);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(8, 10, 8, 10));
        row.setMaximumSize(new Dimension(2000, 44));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nameLabel = new JLabel(username);
        nameLabel.setFont(UITheme.FONT_SMALL);
        nameLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        row.add(nameLabel, BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.setOpaque(false);
        buttons.add(smallActionButton("Mute", new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { mutePlayer(username); }
        }));
        buttons.add(smallActionButton("Kick", new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { sendModAction(MessageType.MOD_KICK_REQUEST, username); }
        }));
        buttons.add(smallActionButton("Ban", new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { sendModAction(MessageType.MOD_BAN_REQUEST, username); }
        }));
        row.add(buttons, BorderLayout.EAST);

        return row;
    }

    private void renderAllList(List<String> usernames)
    {
        int count = usernames == null ? 0 : usernames.size();
        allCountLabel.setText("ALL PLAYERS (" + count + ")");

        allList.removeAll();
        if (usernames == null || usernames.isEmpty())
        {
            allList.add(mutedLabel("Nobody yet."));
        }
        else
        {
            for (int i = 0; i < usernames.size(); i++)
            {
                allList.add(buildAllPlayerRow(usernames.get(i)));
                allList.add(Box.createVerticalStrut(6));
            }
        }
        allList.revalidate();
        allList.repaint();
    }

    private JPanel buildAllPlayerRow(final String username)
    {
        RoundedPanel row = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_BUTTON);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(8, 10, 8, 10));
        row.setMaximumSize(new Dimension(2000, 44));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nameLabel = new JLabel(username);
        nameLabel.setFont(UITheme.FONT_SMALL);
        nameLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        row.add(nameLabel, BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.setOpaque(false);
        buttons.add(smallActionButton("Ban", new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { sendModAction(MessageType.MOD_BAN_REQUEST, username); }
        }));
        buttons.add(smallActionButton("Unban", new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { sendModAction(MessageType.MOD_UNBAN_REQUEST, username); }
        }));
        row.add(buttons, BorderLayout.EAST);

        return row;
    }

    private ThemedButton smallActionButton(String label, ActionListener listener)
    {
        ThemedButton button = new ThemedButton(label, false);
        button.setPreferredSize(new Dimension(64, 28));
        button.addActionListener(listener);
        return button;
    }

    private void mutePlayer(final String username)
    {
        final Message request = new Message();
        request.setType(MessageType.MOD_MUTE_REQUEST);
        request.setUsername(username);
        request.setMuteDurationMinutes(10);

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                final Message response = NetworkManager.send(request);
                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run() { handleModActionResult(response, username + " muted for 10 minutes."); }
                });
            }
        });
        worker.start();
    }

    private void sendModAction(final MessageType type, final String username)
    {
        final Message request = new Message();
        request.setType(type);
        request.setUsername(username);

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                final Message response = NetworkManager.send(request);
                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run() { handleModActionResult(response, "Done."); }
                });
            }
        });
        worker.start();
    }

    private void handleModActionResult(Message response, String successMessage)
    {
        if (response != null && response.isSuccess())
        {
            loadPlayerLists();
        }
        else if (response != null)
        {
            GameHubDialog.show(this, "Moderation", response.getErrorText());
        }
    }

    // ==================== Reports ====================

    private JPanel createReportsSection()
    {
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        headerRow.setBorder(new EmptyBorder(0, 0, 14, 0));

        reportsCountLabel = sectionLabel("REPORTS");
        reportsCountLabel.setBorder(new EmptyBorder(0, 0, 0, 0));
        headerRow.add(reportsCountLabel, BorderLayout.WEST);

        ThemedButton refresh = new ThemedButton("Refresh", false);
        refresh.setPreferredSize(new Dimension(100, 32));
        refresh.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { loadReports(); }
        });
        headerRow.add(refresh, BorderLayout.EAST);

        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.add(headerRow);

        reportsList = new JPanel();
        reportsList.setOpaque(false);
        reportsList.setLayout(new BoxLayout(reportsList, BoxLayout.Y_AXIS));
        reportsList.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.add(reportsList);

        return wrap;
    }

    private void loadReports()
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.REPORT_LIST_REQUEST);
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (response != null && response.isSuccess())
                        {
                            renderReports(response.getReportDescriptions());
                        }
                    }
                });
            }
        });
        worker.start();
    }

    private void renderReports(List<String> entries)
    {
        int count = entries == null ? 0 : entries.size();
        reportsCountLabel.setText("REPORTS" + (count > 0 ? " (" + count + ")" : ""));

        reportsList.removeAll();
        if (entries == null || entries.isEmpty())
        {
            reportsList.add(mutedLabel("No open reports."));
        }
        else
        {
            for (int i = 0; i < entries.size(); i++)
            {
                String[] parts = entries.get(i).split("::", 2);
                if (parts.length == 2)
                {
                    reportsList.add(buildReportRow(parts[0], parts[1]));
                    reportsList.add(Box.createVerticalStrut(8));
                }
            }
        }
        reportsList.revalidate();
        reportsList.repaint();
    }

    private JPanel buildReportRow(final String reportId, String displayText)
    {
        RoundedPanel row = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(12, 16, 12, 16));
        row.setMaximumSize(new Dimension(2000, 60));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel textLabel = new JLabel("<html><body style='width:500px'>" + displayText + "</body></html>");
        textLabel.setFont(UITheme.FONT_SMALL);
        textLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        row.add(textLabel, BorderLayout.WEST);

        ThemedButton resolve = new ThemedButton("Resolve", false);
        resolve.setPreferredSize(new Dimension(90, 32));
        resolve.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { resolveReport(reportId); }
        });
        row.add(resolve, BorderLayout.EAST);

        return row;
    }

    private void resolveReport(final String reportId)
    {
        final Message request = new Message();
        request.setType(MessageType.REPORT_RESOLVE_REQUEST);
        request.setUsername(reportId);

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                final Message response = NetworkManager.send(request);
                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (response != null && response.isSuccess())
                        {
                            loadReports();
                        }
                    }
                });
            }
        });
        worker.start();
    }

    private JLabel mutedLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_SMALL);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        return label;
    }
}
