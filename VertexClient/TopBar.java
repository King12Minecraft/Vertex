import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * TopBar
 * ------
 * Persistent header across every page: the current page's title on the
 * left, and account info on the right - a live online-player count
 * (reuses the existing ONLINE_USERS_REQUEST, no new protocol needed),
 * the notification bell, and the username, which now doubles as an
 * account menu (Profile/Settings) - both were moved out of the Sidebar
 * to declutter it down to game-related navigation only.
 */
public class TopBar extends RoundedPanel
{
    private final JLabel titleLabel;
    private final JLabel onlineCountLabel;
    private final JLabel usernameLabel;
    private final JLabel coinsLabel;
    private final NavigationListener navigationListener;

    public TopBar(NavigationListener navigationListener)
    {
        super(ThemeColor.BG_TOPBAR, 0);
        this.navigationListener = navigationListener;
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(0, UITheme.TOPBAR_HEIGHT));
        setBorder(new EmptyBorder(0, 28, 0, 28));

        titleLabel = new JLabel("Home");
        titleLabel.setFont(UITheme.FONT_HEADING);
        titleLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        add(titleLabel, BorderLayout.WEST);

        JPanel rightPanel = new JPanel();
        rightPanel.setOpaque(false);
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.X_AXIS));

        rightPanel.add(new QuickPlayDropdown());
        rightPanel.add(javax.swing.Box.createHorizontalStrut(12));

        ThemedButton partyButton = new ThemedButton("Party", false);
        partyButton.setPreferredSize(new Dimension(70, 30));
        partyButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent e)
            {
                new PartyDialog(TopBar.this).setVisible(true);
            }
        });
        rightPanel.add(partyButton);
        rightPanel.add(javax.swing.Box.createHorizontalStrut(12));

        onlineCountLabel = new JLabel("");
        onlineCountLabel.setFont(UITheme.FONT_SMALL);
        onlineCountLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        onlineCountLabel.setBorder(new EmptyBorder(0, 0, 0, 12));
        rightPanel.add(onlineCountLabel);

        rightPanel.add(new NotificationBell());
        rightPanel.add(javax.swing.Box.createHorizontalStrut(18));

        usernameLabel = new JLabel(currentUsername());
        usernameLabel.setFont(UITheme.FONT_BODY);
        usernameLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        usernameLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        usernameLabel.addMouseListener(new MouseAdapter()
        {
            public void mouseClicked(MouseEvent e) { showAccountMenu(); }
        });
        applyUsernameColor();

        coinsLabel = new JLabel(currentCoinsText());
        coinsLabel.setFont(UITheme.FONT_BODY);
        coinsLabel.setForeground(ThemeManager.getColor(ThemeColor.ACCENT));
        coinsLabel.setBorder(new EmptyBorder(0, 22, 0, 0));

        rightPanel.add(usernameLabel);
        rightPanel.add(coinsLabel);
        add(rightPanel, BorderLayout.EAST);

        ThemeManager.addListener(new Runnable()
        {
            public void run() { updateColors(); }
        });

        Session.addListener(new Runnable()
        {
            public void run()
            {
                usernameLabel.setText(currentUsername());
                coinsLabel.setText(currentCoinsText());
                applyUsernameColor();
            }
        });

        NetworkManager.addPushListener(new NetworkManager.PushListener()
        {
            public void onPush(final Message message)
            {
                if (message.getType() != MessageType.WALLET_UPDATE)
                {
                    return;
                }
                javax.swing.SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (Session.isLoggedIn())
                        {
                            Session.getCurrentAccount().setCoins(message.getCoins());
                            Session.notifyListeners();
                        }
                        coinsLabel.setText(currentCoinsText());
                    }
                });
            }
        });

        refreshOnlineCount();
        Timer onlineCountTimer = new Timer(20000, new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { refreshOnlineCount(); }
        });
        onlineCountTimer.start();
    }

    /** "N Online" near the notification bell - reuses ONLINE_USERS_REQUEST (already used by the group-member picker), no new protocol needed. */
    private void refreshOnlineCount()
    {
        if (!Session.isLoggedIn())
        {
            return;
        }
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.ONLINE_USERS_REQUEST);
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (response != null && response.isSuccess() && response.getOnlineUsernames() != null)
                        {
                            // The response excludes the requester, so +1 counts yourself too.
                            int count = response.getOnlineUsernames().size() + 1;
                            onlineCountLabel.setText(count + " Online");
                        }
                    }
                });
            }
        });
        worker.start();
    }

    private void showAccountMenu()
    {
        JPopupMenu menu = new JPopupMenu();
        menu.setBorder(javax.swing.BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ThemeManager.getColor(ThemeColor.BG_PANEL));
        content.setBorder(new EmptyBorder(6, 6, 6, 6));

        content.add(accountMenuRow("Profile", Pages.PROFILE, menu));
        content.add(accountMenuRow("Settings", Pages.SETTINGS, menu));

        menu.add(content);
        menu.show(usernameLabel, 0, usernameLabel.getHeight() + 6);
    }

    private JPanel accountMenuRow(String label, final String pageKey, final JPopupMenu menu)
    {
        final JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(true);
        row.setBackground(ThemeManager.getColor(ThemeColor.BG_PANEL));
        row.setBorder(new EmptyBorder(9, 14, 9, 14));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.setPreferredSize(new Dimension(150, 36));

        JLabel text = new JLabel(label);
        text.setFont(UITheme.FONT_BODY);
        text.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        row.add(text, BorderLayout.WEST);

        row.addMouseListener(new MouseAdapter()
        {
            public void mouseEntered(MouseEvent e) { row.setBackground(ThemeManager.getColor(ThemeColor.BG_PANEL_HOVER)); }
            public void mouseExited(MouseEvent e)  { row.setBackground(ThemeManager.getColor(ThemeColor.BG_PANEL)); }
            public void mouseClicked(MouseEvent e)
            {
                menu.setVisible(false);
                navigationListener.onNavigate(pageKey);
            }
        });

        return row;
    }

    /** Called by MainMenu whenever the active page changes. */
    public void setPageTitle(String title)
    {
        titleLabel.setText(title);
    }

    private String currentUsername()
    {
        return Session.isLoggedIn() ? Session.getCurrentAccount().getUsername() : "Guest";
    }

    private String currentCoinsText()
    {
        return (Session.isLoggedIn() ? Session.getCurrentAccount().getCoins() : 0) + " Coins";
    }

    private void updateColors()
    {
        titleLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        applyUsernameColor();
        coinsLabel.setForeground(ThemeManager.getColor(ThemeColor.ACCENT));
        onlineCountLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        repaint();
    }

    /** Uses the account's purchased/selected color if one is set, otherwise the theme's default text color. */
    private void applyUsernameColor()
    {
        java.awt.Color custom = Session.isLoggedIn()
            ? PlayerColorRegistry.resolve(Session.getCurrentAccount().getPlayerColorName())
            : null;
        usernameLabel.setForeground(custom != null ? custom : ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
    }

    /** Chrome detail matching the Sidebar's divider treatment - a thin accent-gradient line along the bottom edge. */
    @Override
    protected void paintComponent(java.awt.Graphics g)
    {
        super.paintComponent(g);

        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        int w = getWidth();
        int h = getHeight();

        java.awt.Color accentStart = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START);
        java.awt.Color accentEnd = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_END);
        java.awt.LinearGradientPaint divider = new java.awt.LinearGradientPaint(
            0, 0, Math.max(w, 1), 0, new float[] {0f, 1f}, new java.awt.Color[] {accentStart, accentEnd});
        g2.setPaint(divider);
        g2.fillRect(0, h - 2, w, 2);

        g2.dispose();
    }
}
