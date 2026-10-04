package pages;
import account.AuthWindow;
import account.ChangePasswordDialog;
import account.ChangeUsernameDialog;
import economy.AvatarFrameRegistry;
import economy.AvatarEditorDialog;
import economy.AvatarCache;
import admin.FeedbackListDialog;
import account.Session;
import account.PermissionManager;
import admin.FeedbackDialog;
import net.NetworkConfig;
import games.ServerBrowserDialog;
import ui.CursorManager;
import ui.ThemedButton;
import ui.ThemedLabel;
import net.NetworkManager;
import net.ConnectionIndicator;
import theme.ThemeDropdown;
import economy.FpsCounterSetting;
import economy.PerformanceMode;
import economy.PartyMode;
import economy.NotificationSoundSetting;
import ui.ToggleSwitch;
import theme.ThemeManager;
import theme.UITheme;
import ui.ThemedScrollBarUI;
import ui.PageHeader;
import theme.ThemeColor;
import ui.RoundedPanel;

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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
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
public class SettingsPanel extends PageScaffold
{
    public SettingsPanel()
    {
        super("SETTINGS", "How Vertex looks, runs and connects on this computer, plus your account.");

        row(PageScaffold.split(section("GENERAL", createGeneralSection()), section("PERFORMANCE", createPerformanceSection()), 1, 1));
        gap(16);
        row(PageScaffold.split(section("APPEARANCE", createAppearanceSection()), section("FUN", createPartyModeSection()), 1, 1));
        gap(16);
        row(PageScaffold.split(section("CONNECTION", createConnectionSection()), section("FEEDBACK", createFeedbackSection()), 1, 1));
        gap(16);
        row(PageScaffold.fullWidth(section("ACCOUNT", createAccountSection())));
    }

    private SectionCard section(String title, JPanel body)
    {
        // NORTH keeps the body at its own height when a neighbouring card makes this one taller (no stretched rows)
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(body, BorderLayout.NORTH);
        return new SectionCard(title).content(holder);
    }

    private JPanel createGeneralSection()
    {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new ThemedLabel("Launch Vertex on startup", ThemeColor.TEXT_PRIMARY);
        label.setFont(UITheme.FONT_BODY);
        row.add(label, BorderLayout.WEST);

        // Local device preference only - not persisted anywhere yet.
        ToggleSwitch toggle = new ToggleSwitch(false);
        JPanel toggleWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        toggleWrap.setOpaque(false);
        toggleWrap.add(toggle);
        row.add(toggleWrap, BorderLayout.EAST);
        col.add(row);

        col.add(Box.createVerticalStrut(14));

        JPanel soundRow = new JPanel(new BorderLayout());
        soundRow.setOpaque(false);
        soundRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel soundLabel = new ThemedLabel("Notification Sounds", ThemeColor.TEXT_PRIMARY);
        soundLabel.setFont(UITheme.FONT_BODY);
        soundRow.add(soundLabel, BorderLayout.WEST);

        final ToggleSwitch soundToggle = new ToggleSwitch(NotificationSoundSetting.isEnabled());
        soundToggle.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { NotificationSoundSetting.setEnabled(soundToggle.isOn()); }
        });
        JPanel soundToggleWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        soundToggleWrap.setOpaque(false);
        soundToggleWrap.add(soundToggle);
        soundRow.add(soundToggleWrap, BorderLayout.EAST);
        col.add(soundRow);

        JLabel soundDescription = new ThemedLabel("<html><table width='420' cellpadding='0' cellspacing='0'><tr><td>Plays a short system beep when a "
            + "new chat message or group invite comes in.</td></tr></table></html>", ThemeColor.TEXT_MUTED);
        soundDescription.setFont(UITheme.FONT_SMALL);
        soundDescription.setAlignmentX(Component.LEFT_ALIGNMENT);
        soundDescription.setBorder(new EmptyBorder(6, 0, 0, 0));
        col.add(soundDescription);

        return col;
    }

    private JPanel createPerformanceSection()
    {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new ThemedLabel("Performance Mode", ThemeColor.TEXT_PRIMARY);
        label.setFont(UITheme.FONT_BODY);
        row.add(label, BorderLayout.WEST);

        final ToggleSwitch toggle = new ToggleSwitch(PerformanceMode.isEnabled());
        toggle.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { PerformanceMode.setEnabled(toggle.isOn()); }
        });
        JPanel toggleWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        toggleWrap.setOpaque(false);
        toggleWrap.add(toggle);
        row.add(toggleWrap, BorderLayout.EAST);
        col.add(row);

        JLabel description = new ThemedLabel("<html><table width='420' cellpadding='0' cellspacing='0'><tr><td>Turns off antialiased/high-quality "
            + "rendering app-wide, drops in-game frame rate from 60 to 30fps, and skips a couple of decorative "
            + "background effects. Takes effect the next time you open a game or restart Vertex.</td></tr></table></html>",
            ThemeColor.TEXT_MUTED);
        description.setFont(UITheme.FONT_SMALL);
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        description.setBorder(new EmptyBorder(6, 0, 0, 0));
        col.add(description);

        col.add(Box.createVerticalStrut(18));

        JPanel fpsRow = new JPanel(new BorderLayout());
        fpsRow.setOpaque(false);
        fpsRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel fpsLabel = new ThemedLabel("Show FPS Counter", ThemeColor.TEXT_PRIMARY);
        fpsLabel.setFont(UITheme.FONT_BODY);
        fpsRow.add(fpsLabel, BorderLayout.WEST);

        final ToggleSwitch fpsToggle = new ToggleSwitch(FpsCounterSetting.isEnabled());
        fpsToggle.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { FpsCounterSetting.setEnabled(fpsToggle.isOn()); }
        });
        JPanel fpsToggleWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        fpsToggleWrap.setOpaque(false);
        fpsToggleWrap.add(fpsToggle);
        fpsRow.add(fpsToggleWrap, BorderLayout.EAST);
        col.add(fpsRow);

        JLabel fpsDescription = new ThemedLabel("<html><table width='420' cellpadding='0' cellspacing='0'><tr><td>Shows a live frame-rate readout in "
            + "the corner of every game - useful for checking whether Performance Mode (or your hardware) is "
            + "actually giving you a smooth 60/30fps.</td></tr></table></html>", ThemeColor.TEXT_MUTED);
        fpsDescription.setFont(UITheme.FONT_SMALL);
        fpsDescription.setAlignmentX(Component.LEFT_ALIGNMENT);
        fpsDescription.setBorder(new EmptyBorder(6, 0, 0, 0));
        col.add(fpsDescription);

        return col;
    }

    private JPanel createPartyModeSection()
    {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new ThemedLabel("Party Mode", ThemeColor.TEXT_PRIMARY);
        label.setFont(UITheme.FONT_BODY);
        row.add(label, BorderLayout.WEST);

        final ToggleSwitch toggle = new ToggleSwitch(PartyMode.isEnabled());
        toggle.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { PartyMode.setEnabled(toggle.isOn()); }
        });
        JPanel toggleWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        toggleWrap.setOpaque(false);
        toggleWrap.add(toggle);
        row.add(toggleWrap, BorderLayout.EAST);
        col.add(row);

        JLabel description = new ThemedLabel("<html><table width='420' cellpadding='0' cellspacing='0'><tr><td>A silly rainbow cursor trail "
            + "everywhere in the app, plus confetti when you win a game. Purely cosmetic - doesn't touch scores, "
            + "matchmaking, or anything else. Takes effect immediately, no restart needed.</td></tr></table></html>",
            ThemeColor.TEXT_MUTED);
        description.setFont(UITheme.FONT_SMALL);
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        description.setBorder(new EmptyBorder(6, 0, 0, 0));
        col.add(description);

        return col;
    }

    private JPanel createAppearanceSection()
    {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        JLabel modeLabel = new ThemedLabel("Mode", ThemeColor.TEXT_SECONDARY);
        modeLabel.setFont(UITheme.FONT_BODY);
        modeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        modeLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
        col.add(modeLabel);
        col.add(createModeSwitch());

        JLabel label = new ThemedLabel("Colour theme", ThemeColor.TEXT_SECONDARY);
        label.setFont(UITheme.FONT_BODY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(18, 0, 10, 0));
        col.add(label);

        ThemeDropdown dropdown = new ThemeDropdown();
        dropdown.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(dropdown);

        JLabel cursorLabel = new ThemedLabel("Mouse cursor", ThemeColor.TEXT_SECONDARY);
        cursorLabel.setFont(UITheme.FONT_BODY);
        cursorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        cursorLabel.setBorder(new EmptyBorder(18, 0, 4, 0));
        col.add(cursorLabel);

        final ui.CursorPicker cursorPicker = new ui.CursorPicker();
        cursorPicker.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(cursorPicker);

        JPanel glowRow = new JPanel(new BorderLayout());
        glowRow.setOpaque(false);
        glowRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        glowRow.setBorder(new EmptyBorder(14, 0, 0, 0));
        JLabel glowLabel = new ThemedLabel("Cursor glow", ThemeColor.TEXT_PRIMARY);
        glowLabel.setFont(UITheme.FONT_BODY);
        glowRow.add(glowLabel, BorderLayout.WEST);
        final ToggleSwitch glowToggle = new ToggleSwitch(CursorManager.isGlow());
        glowToggle.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { CursorManager.setGlow(glowToggle.isOn()); cursorPicker.repaint(); }
        });
        JPanel glowWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        glowWrap.setOpaque(false);
        glowWrap.add(glowToggle);
        glowRow.add(glowWrap, BorderLayout.EAST);
        col.add(glowRow);

        return col;
    }

    /** System / Light / Dark: the calm Claude-style looks. "System" follows the operating system and is the default; the other colour themes below remain available. */
    private JPanel createModeSwitch()
    {
        final ThemedButton system = new ThemedButton("System", false);
        final ThemedButton light = new ThemedButton("Light", false);
        final ThemedButton dark = new ThemedButton("Dark", false);
        final Runnable refresh = new Runnable()
        {
            public void run()
            {
                String name = theme.ThemeManager.getCurrentTheme().getName();
                boolean following = theme.ThemeManager.isFollowingSystem();
                system.setPrimary(following);
                light.setPrimary(!following && "Claude Light".equals(name));
                dark.setPrimary(!following && "Claude Dark".equals(name));
            }
        };
        system.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { theme.ThemeManager.useSystem(); }
        });
        light.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { theme.ThemeManager.setTheme(new theme.ClaudeLightTheme()); }
        });
        dark.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { theme.ThemeManager.setTheme(new theme.ClaudeDarkTheme()); }
        });
        theme.ThemeManager.addListener(refresh);
        refresh.run();

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(0, -8, 0, 0));
        for (ThemedButton b : new ThemedButton[] { system, light, dark })
        {
            b.setPreferredSize(new Dimension(96, 36));
            row.add(b);
        }
        // keep a strong reference: ThemeManager only holds its listeners weakly
        row.putClientProperty("modeRefresh", refresh);
        return row;
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

        JLabel label = new ThemedLabel("Server status", ThemeColor.TEXT_SECONDARY);
        label.setFont(UITheme.FONT_BODY);
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
        col.add(Box.createVerticalStrut(10));

        final ThemedButton copyDiagnostics = new ThemedButton("Copy Diagnostic Info", false);
        copyDiagnostics.setAlignmentX(Component.LEFT_ALIGNMENT);
        copyDiagnostics.setMaximumSize(new Dimension(220, 38));
        copyDiagnostics.setPreferredSize(new Dimension(220, 38));
        copyDiagnostics.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(buildDiagnosticInfo()), null);
                copyDiagnostics.setText("Copied!");
            }
        });
        col.add(copyDiagnostics);

        JLabel diagNote = new ThemedLabel("<html><table width='420' cellpadding='0' cellspacing='0'><tr><td>Useful to paste into a bug report - "
            + "includes your Java version, OS, and which server you're connected to. No account info or "
            + "message content.</td></tr></table></html>", ThemeColor.TEXT_MUTED);
        diagNote.setFont(UITheme.FONT_SMALL);
        diagNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        diagNote.setBorder(new EmptyBorder(6, 0, 0, 0));
        col.add(diagNote);

        return col;
    }

    /** Deliberately excludes anything account-specific (username, coins, etc.) - this is meant to be safely pasteable into a public bug report without exposing personal info, just the environment details that actually help diagnose a problem. */
    private String buildDiagnosticInfo()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Vertex Diagnostic Info\n");
        sb.append("-----------------------\n");
        sb.append("Java: ").append(System.getProperty("java.version"))
            .append(" (").append(System.getProperty("java.vendor")).append(")\n");
        sb.append("OS: ").append(System.getProperty("os.name")).append(" ")
            .append(System.getProperty("os.version")).append(" (").append(System.getProperty("os.arch")).append(")\n");
        sb.append("Max heap: ").append(Runtime.getRuntime().maxMemory() / (1024 * 1024)).append(" MB\n");
        sb.append("Server: ").append(NetworkConfig.getServerHost()).append(":").append(NetworkConfig.getServerPort()).append("\n");
        sb.append("Connection state: ").append(NetworkManager.getState()).append("\n");
        sb.append("Performance Mode: ").append(PerformanceMode.isEnabled() ? "On" : "Off").append("\n");
        return sb.toString();
    }

    /** Bug reports and suggestions about Vertex itself - see FeedbackDialog/FeedbackListDialog/FeedbackManager. Separate from reporting another player (Friends/Chat -> Report). */
    private JPanel createFeedbackSection()
    {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel note = new ThemedLabel("Found a bug, or have an idea? It goes straight to the admins.", ThemeColor.TEXT_SECONDARY);
        note.setFont(UITheme.FONT_BODY);
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

    private JPanel createAvatarRow()
    {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        final AvatarBubble bubble = new AvatarBubble();
        row.add(bubble);

        if (Session.isLoggedIn())
        {
            AvatarCache.get(Session.getCurrentAccount().getUsername(), new AvatarCache.Listener()
            {
                public void onLoaded(Image image) { bubble.setImage(image); }
            });
        }

        final ThemedButton edit = new ThemedButton("Edit Avatar", false);
        edit.setPreferredSize(new Dimension(150, 38));
        edit.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                AvatarEditorDialog.show(edit, new AvatarEditorDialog.SaveListener()
                {
                    public void onSaved()
                    {
                        if (Session.isLoggedIn())
                        {
                            AvatarCache.get(Session.getCurrentAccount().getUsername(), new AvatarCache.Listener()
                            {
                                public void onLoaded(Image image) { bubble.setImage(image); }
                            });
                        }
                    }
                });
            }
        });
        row.add(edit);

        return row;
    }

    /** A small circular avatar preview - null image just renders as a plain filled circle, so there's no broken-image state to handle. */
    private static class AvatarBubble extends JPanel
    {
        private Image image;
        private final Runnable animationTick = new Runnable()
        {
            public void run() { repaint(); }
        };

        AvatarBubble()
        {
            setPreferredSize(new Dimension(56, 56));
            setOpaque(false);
            AvatarFrameRegistry.addAnimationListener(animationTick);
        }

        void setImage(Image image)
        {
            this.image = image;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);
            g2.setColor(ThemeManager.getColor(ThemeColor.BG_APP));
            g2.fillOval(0, 0, getWidth(), getHeight());
            if (image != null)
            {
                g2.setClip(new java.awt.geom.Ellipse2D.Float(0, 0, getWidth(), getHeight()));
                g2.drawImage(image, 0, 0, getWidth(), getHeight(), null);
                g2.setClip(null);
            }
            if (Session.isLoggedIn())
            {
                AvatarFrameRegistry.paintFrame(g2, 0, 0, getWidth(), Session.getCurrentAccount().getEquippedFrameId());
            }
            g2.dispose();
        }
    }

    private JPanel createAccountSection()
    {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        JLabel note = new ThemedLabel("Signed in as " + currentUsername() + " (Account ID " + currentAccountId() + ").",
            ThemeColor.TEXT_SECONDARY);
        note.setFont(UITheme.FONT_BODY);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        note.setBorder(new EmptyBorder(0, 0, 14, 0));
        col.add(note);

        col.add(createAvatarRow());
        col.add(Box.createVerticalStrut(18));

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
