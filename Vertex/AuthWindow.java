import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * AuthWindow
 * ----------
 * The window shown before MainMenu: a branded header (logo + pulsing
 * glow + wordmark) above whichever of LoginPanel/CreateAccountPanel is
 * currently showing via CardLayout. On success, disposes itself and
 * opens MainMenu with the logged-in account attached to Session.
 *
 * Attempts to connect to the server in the background as soon as this
 * window opens, and shows a live ConnectionIndicator so it's obvious
 * before you even try to log in whether the server is reachable.
 */
public class AuthWindow extends JFrame
{
    private static final String LOGIN = "LOGIN";
    private static final String CREATE = "CREATE";

    private final AuthHeader authHeader = new AuthHeader();

    public AuthWindow()
    {
        super("Vertex - Log In");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(520, 760);
        setMinimumSize(new Dimension(480, 700));
        setLocationRelativeTo(null);
        SignatureOverlay.attach(this);
        GlitchEffectOverlay.attach(this);
        setIconImage(GameLogo.renderIcon(64));

        final CardLayout cardLayout = new CardLayout();
        final JPanel cards = new JPanel(cardLayout);

        LoginPanel.LoginSuccessListener onSuccess = new LoginPanel.LoginSuccessListener()
        {
            public void onLoginSuccess(Account account, String password)
            {
                Session.login(account, password);
                GuestPlayTracker.flushToServer();
                authHeader.stopAnimation();
                AuthWindow.this.dispose();
                MainMenu window = new MainMenu();
                window.setVisible(true);
            }
        };

        Runnable toCreate = new Runnable()
        {
            public void run() { cardLayout.show(cards, CREATE); }
        };
        Runnable toLogin = new Runnable()
        {
            public void run() { cardLayout.show(cards, LOGIN); }
        };

        cards.add(new LoginPanel(onSuccess, toCreate), LOGIN);
        cards.add(new CreateAccountPanel(onSuccess, toLogin), CREATE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        root.add(authHeader, BorderLayout.NORTH);
        root.add(cards, BorderLayout.CENTER);
        root.add(createConnectionRow(), BorderLayout.SOUTH);

        getContentPane().setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        getContentPane().add(root, BorderLayout.CENTER);
        cardLayout.show(cards, LOGIN);

        ThemeManager.addListener(new Runnable()
        {
            public void run()
            {
                root.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
                getContentPane().setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
                repaint();
            }
        });

        addWindowListener(new WindowAdapter()
        {
            public void windowClosed(WindowEvent e) { authHeader.stopAnimation(); }
        });

        Thread connectThread = new Thread(new Runnable()
        {
            public void run() { NetworkManager.connect(); }
        });
        connectThread.start();
    }

    private JPanel createConnectionRow()
    {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new javax.swing.BoxLayout(col, javax.swing.BoxLayout.Y_AXIS));
        col.setBorder(new EmptyBorder(0, 0, 20, 0));

        JPanel indicatorRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        indicatorRow.setOpaque(false);
        indicatorRow.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);

        final ConnectionIndicator indicator = new ConnectionIndicator(NetworkManager.getState());
        indicatorRow.add(indicator);

        NetworkManager.addListener(new Runnable()
        {
            public void run() { indicator.setState(NetworkManager.getState()); }
        });

        col.add(indicatorRow);
        col.add(javax.swing.Box.createVerticalStrut(6));
        col.add(createChangeServerRow());
        col.add(javax.swing.Box.createVerticalStrut(10));
        col.add(createPlayOfflineRow());

        return col;
    }

    /**
     * A small, easy-to-ignore link rather than a dialog that blocks the
     * whole app on every launch (see Vertex.java's own comment on why
     * HostOrConnectDialog got removed) - only shown to someone who
     * actually wants to point this client at a different server than
     * the default. Reuses ConnectDialog's address field, but since a
     * connection attempt (successful or not) may already be under way by
     * the time this is clicked, the callback goes through
     * NetworkManager.switchServer(...) rather than just connect() - that
     * closes whatever socket is already open/half-open first, so it
     * always targets the address just entered instead of possibly being
     * a no-op against an existing connection to the old one.
     */
    private JPanel createChangeServerRow()
    {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);

        javax.swing.JButton changeServer = new javax.swing.JButton("Change Server");
        changeServer.setFont(UITheme.FONT_SMALL);
        changeServer.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        changeServer.setFocusPainted(false);
        changeServer.setBorderPainted(false);
        changeServer.setContentAreaFilled(false);
        changeServer.setOpaque(false);
        changeServer.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        changeServer.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent e)
            {
                ConnectDialog.show(new Runnable()
                {
                    public void run()
                    {
                        final String host = NetworkConfig.getServerHost();
                        final int port = NetworkConfig.getServerPort();
                        Thread worker = new Thread(new Runnable()
                        {
                            public void run() { NetworkManager.switchServer(host, port); }
                        });
                        worker.start();
                    }
                });
            }
        });

        row.add(changeServer);
        return row;
    }

    /**
     * Opens the Offline Hub - a proper landing screen (OfflineHubWindow)
     * rather than jumping straight into a specific game. Snake is the
     * only entry today since it's the only game that needs no account
     * or server connection at all, but the hub is what's real here, not
     * a hardcoded shortcut - more offline-capable games slot into it
     * later without touching this button. Any plays made offline are
     * queued locally (GuestPlayTracker) and attributed to whichever
     * account logs in next, matching the offline-play promise shown on
     * the Create Account screen when it's unreachable.
     */
    private JPanel createPlayOfflineRow()
    {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);

        ThemedButton playOffline = new ThemedButton("Play Offline", false);
        playOffline.setPreferredSize(new Dimension(180, 34));
        playOffline.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent e)
            {
                OfflineHubWindow window = new OfflineHubWindow();
                window.setVisible(true);
            }
        });

        row.add(playOffline);
        return row;
    }
}
