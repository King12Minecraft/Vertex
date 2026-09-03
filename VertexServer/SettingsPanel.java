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
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * SettingsPanel
 * -------------
 * Real Phase 2 content, scoped to what doesn't need a server yet: a
 * local-only startup toggle, a read-out of the active theme (the picker
 * UI itself is Phase 17), the connection status, and Account buttons
 * that explain they're coming in Phase 3.
 */
public class SettingsPanel extends RoundedPanel
{
    public SettingsPanel()
    {
        super(ThemeColor.BG_APP, 0);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 32, 24, 32));

        add(new PageHeader("SETTINGS"), BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(24, 0, 24, 0));

        content.add(section("GENERAL", createGeneralSection()));
        content.add(Box.createVerticalStrut(20));
        content.add(section("APPEARANCE", createAppearanceSection()));
        content.add(Box.createVerticalStrut(20));
        content.add(section("CONNECTION", createConnectionSection()));
        content.add(Box.createVerticalStrut(20));
        content.add(section("HOSTING SERVER", createHostingSection()));
        content.add(Box.createVerticalStrut(20));
        content.add(section("FEEDBACK", createFeedbackSection()));
        content.add(Box.createVerticalStrut(20));
        content.add(section("ACCOUNT", createAccountSection()));

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);

        add(scroll, BorderLayout.CENTER);
    }

    private JPanel section(String title, JPanel body)
    {
        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.setMaximumSize(new Dimension(2000, 600));

        JLabel label = new JLabel(title);
        label.setFont(UITheme.FONT_NAV_BOLD);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 12, 0));
        wrap.add(label);

        RoundedPanel card = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(18, 20, 18, 20));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(body);
        wrap.add(card);

        return wrap;
    }

    private JPanel createGeneralSection()
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);

        JLabel label = new JLabel("Launch Vertex on startup");
        label.setFont(UITheme.FONT_BODY);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        row.add(label, BorderLayout.WEST);

        // Local device preference only - not persisted anywhere yet.
        ToggleSwitch toggle = new ToggleSwitch(false);
        JPanel toggleWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        toggleWrap.setOpaque(false);
        toggleWrap.add(toggle);
        row.add(toggleWrap, BorderLayout.EAST);

        return row;
    }

    private JPanel createAppearanceSection()
    {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        JLabel label = new JLabel("Theme");
        label.setFont(UITheme.FONT_BODY);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 10, 0));
        col.add(label);

        ThemeDropdown dropdown = new ThemeDropdown();
        dropdown.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(dropdown);

        return col;
    }

    private JPanel createConnectionSection()
    {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setOpaque(false);
        statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel("Server status");
        label.setFont(UITheme.FONT_BODY);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        statusRow.add(label, BorderLayout.WEST);

        ConnectionIndicator indicator = new ConnectionIndicator(NetworkManager.getState());
        JPanel indicatorWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        indicatorWrap.setOpaque(false);
        indicatorWrap.add(indicator);
        statusRow.add(indicatorWrap, BorderLayout.EAST);

        NetworkManager.addListener(new Runnable()
        {
            public void run() { indicator.setState(NetworkManager.getState()); }
        });

        col.add(statusRow);
        col.add(Box.createVerticalStrut(12));

        final ThemedButton switchServer = new ThemedButton("Switch Server", false);
        switchServer.setAlignmentX(Component.LEFT_ALIGNMENT);
        switchServer.setMaximumSize(new Dimension(220, 38));
        switchServer.setPreferredSize(new Dimension(220, 38));
        switchServer.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { ServerBrowserDialog.show(switchServer); }
        });
        col.add(switchServer);

        return col;
    }

    /**
     * Hosting used to be the very first thing the app asked - before you
     * could even log in, via a plain JOptionPane prompt (HostOrConnectDialog)
     * that didn't match the rest of the UI at all. It now lives here instead:
     * something you turn on once you're already in, from a themed dialog
     * that fits the rest of the app (see HostServerDialog).
     */
    private JPanel createHostingSection()
    {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel note = new JLabel("<html><body style='width:420px'>Run a Vertex server on this computer so others can join - either as the main server, or as a satellite that stays synced with an existing one.</body></html>");
        note.setFont(UITheme.FONT_BODY);
        note.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        note.setBorder(new EmptyBorder(0, 0, 14, 0));
        col.add(note);

        final ThemedButton hostButton = new ThemedButton("Start Hosting", false);
        hostButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        hostButton.setMaximumSize(new Dimension(220, 38));
        hostButton.setPreferredSize(new Dimension(220, 38));
        hostButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { HostServerDialog.show(hostButton); }
        });
        col.add(hostButton);

        return col;
    }

    /** Bug reports and suggestions about Vertex itself - see FeedbackDialog/FeedbackListDialog/FeedbackManager. Separate from reporting another player (Friends/Chat -> Report). */
    private JPanel createFeedbackSection()
    {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel note = new JLabel("Found a bug, or have an idea? It goes straight to the admins.");
        note.setFont(UITheme.FONT_BODY);
        note.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        note.setBorder(new EmptyBorder(0, 0, 14, 0));
        col.add(note);

        final ThemedButton reportButton = new ThemedButton("Report a Bug / Suggest Something", true);
        reportButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        reportButton.setMaximumSize(new Dimension(320, 38));
        reportButton.setPreferredSize(new Dimension(320, 38));
        reportButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { FeedbackDialog.show(reportButton); }
        });
        col.add(reportButton);
        col.add(Box.createVerticalStrut(10));

        final ThemedButton viewButton = new ThemedButton(
            PermissionManager.isAdmin(Session.getCurrentAccount()) ? "View All Feedback" : "View My Feedback", false);
        viewButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        viewButton.setMaximumSize(new Dimension(220, 38));
        viewButton.setPreferredSize(new Dimension(220, 38));
        viewButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { FeedbackListDialog.show(viewButton); }
        });
        col.add(viewButton);

        return col;
    }

    private JPanel createAccountSection()
    {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        JLabel note = new JLabel("Signed in as " + currentUsername() + " (Account ID " + currentAccountId() + ").");
        note.setFont(UITheme.FONT_BODY);
        note.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        note.setBorder(new EmptyBorder(0, 0, 14, 0));
        col.add(note);

        final ThemedButton changeUsername = new ThemedButton("Change Username", false);
        changeUsername.setAlignmentX(Component.LEFT_ALIGNMENT);
        changeUsername.setMaximumSize(new Dimension(220, 38));
        changeUsername.setPreferredSize(new Dimension(220, 38));
        changeUsername.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { ChangeUsernameDialog.show(changeUsername); }
        });
        col.add(changeUsername);
        col.add(Box.createVerticalStrut(10));

        final ThemedButton changePassword = new ThemedButton("Change Password", false);
        changePassword.setAlignmentX(Component.LEFT_ALIGNMENT);
        changePassword.setMaximumSize(new Dimension(220, 38));
        changePassword.setPreferredSize(new Dimension(220, 38));
        changePassword.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { ChangePasswordDialog.show(changePassword); }
        });
        col.add(changePassword);
        col.add(Box.createVerticalStrut(18));

        final ThemedButton logOut = new ThemedButton("Log Out", false);
        logOut.setAlignmentX(Component.LEFT_ALIGNMENT);
        logOut.setMaximumSize(new Dimension(220, 38));
        logOut.setPreferredSize(new Dimension(220, 38));
        logOut.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                Session.logout();
                Frame owner = (Frame) SwingUtilities.getWindowAncestor(logOut);
                owner.dispose();
                AuthWindow window = new AuthWindow();
                window.setVisible(true);
            }
        });
        col.add(logOut);

        return col;
    }

    private String currentUsername()
    {
        return Session.isLoggedIn() ? Session.getCurrentAccount().getUsername() : "Guest";
    }

    private String currentAccountId()
    {
        return Session.isLoggedIn() ? String.format("%06d", Session.getCurrentAccount().getAccountId()) : "n/a";
    }
}
