import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;

/**
 * MainMenu
 * --------
 * The main Vertex window. Assembles the Sidebar (west), TopBar (north),
 * and a CardLayout content area (center) holding every page panel.
 * Implements NavigationListener so Sidebar can tell it which page to
 * switch to.
 *
 * Page switches crossfade instead of snapping instantly: a screenshot
 * of the outgoing page is taken, the CardLayout swap happens
 * immediately underneath (so the new page is already live and
 * interactive), and the screenshot is laid over it in a JLayeredPane,
 * fading out over ~220ms to reveal the new page. Cheap and robust -
 * no extra libraries, just a BufferedImage snapshot and a Swing Timer.
 *
 * The window/taskbar icon is generated from GameLogo - no external image
 * file. Note: real taskbar *pinning* is an OS-level thing that needs the
 * app packaged as an actual .exe (Phase 16, via jpackage) - a bare .jar
 * can't be pinned properly. Setting the icon here now means it's ready
 * to carry straight over once that packaging step happens.
 */
public class MainMenu extends JFrame implements NavigationListener, NetworkManager.PushListener
{
    private static final int TRANSITION_MS = 220;

    private final TopBar topBar;
    private Sidebar sidebar;
    private final CardLayout cardLayout;
    private final JPanel contentPanel;
    private final JLayeredPane transitionPane;
    private final GamesPanel gamesPanel;
    private String currentPageKey = Pages.GAMES;

    public MainMenu()
    {
        super("Vertex");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 650));
        setSize(1280, 800);
        setLocationRelativeTo(null);
        SignatureOverlay.attach(this);
        GlitchEffectOverlay.attach(this);

        BufferedImage icon = GameLogo.renderIcon(64);
        setIconImage(icon);

        PlayerColorRegistry.fetchInBackground();

        Sidebar sidebar = new Sidebar(this);
        this.sidebar = sidebar;
        topBar = new TopBar(this);

        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        gamesPanel = new GamesPanel();
        contentPanel.add(gamesPanel, Pages.GAMES);
        contentPanel.add(new QuestsPanel(), Pages.QUESTS);
        contentPanel.add(new LeaderboardPanel(), Pages.LEADERBOARDS);
        contentPanel.add(new AchievementsPanel(), Pages.ACHIEVEMENTS);
        contentPanel.add(new TournamentsPanel(), Pages.TOURNAMENTS);
        contentPanel.add(new FriendsPanel(), Pages.FRIENDS);
        contentPanel.add(new ChatPanel(), Pages.CHAT);
        contentPanel.add(new ShopPanel(), Pages.SHOP);
        contentPanel.add(new ProfilePanel(), Pages.PROFILE);
        contentPanel.add(new SettingsPanel(), Pages.SETTINGS);

        Account current = Session.getCurrentAccount();
        if (PermissionManager.isAtLeastModerator(current))
        {
            contentPanel.add(new ModeratorPanel(), Pages.MODERATION);
        }
        if (PermissionManager.isAdmin(current))
        {
            contentPanel.add(new SatelliteServersPanel(), Pages.SATELLITE_SERVERS);
        }

        transitionPane = new JLayeredPane();
        transitionPane.setLayout(null);
        transitionPane.add(contentPanel, JLayeredPane.DEFAULT_LAYER);
        transitionPane.addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent e)
            {
                contentPanel.setBounds(0, 0, transitionPane.getWidth(), transitionPane.getHeight());
            }
        });

        JPanel centerColumn = new JPanel(new BorderLayout());
        centerColumn.add(topBar, BorderLayout.NORTH);
        centerColumn.add(transitionPane, BorderLayout.CENTER);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(sidebar, BorderLayout.WEST);
        getContentPane().add(centerColumn, BorderLayout.CENTER);

        cardLayout.show(contentPanel, Pages.GAMES);

        NetworkManager.addPushListener(this);
    }

    /**
     * Global moderation notices - shown regardless of which page is
     * currently visible, since a mute/kick/ban can land at any time.
     * ERROR_NOTICE covers mute notices (see ClientHandler.sendMuteNotice
     * and handleModMute); FORCE_DISCONNECT_NOTICE means a kick or ban
     * just closed the connection - the normal reconnect/offline flow
     * takes over right after this.
     */
    @Override
    public void onPush(final Message message)
    {
        if (message.getType() == MessageType.ERROR_NOTICE)
        {
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    NotificationCenter.add("Moderation", message.getErrorText());
                }
            });
        }
        else if (message.getType() == MessageType.FORCE_DISCONNECT_NOTICE)
        {
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    GameHubDialog.show(MainMenu.this, "Disconnected", message.getErrorText());
                }
            });
        }
        else if (message.getType() == MessageType.GAME_INVITE)
        {
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    GameInviteDialog.show(MainMenu.this, message.getUsername(), message.getGameId());
                }
            });
        }
        else if (message.getType() == MessageType.REMATCH_OFFERED)
        {
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    RematchOfferDialog.show(MainMenu.this, message.getUsername(), message.getGameId());
                }
            });
        }
        else if (message.getType() == MessageType.ACHIEVEMENT_UNLOCKED)
        {
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    AchievementToast.show(message.getUsername(), message.getErrorText());
                }
            });
        }
        else if (message.getType() == MessageType.FRIEND_STATUS_UPDATE)
        {
            if (message.isOnline())
            {
                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run() { sidebar.setFriendsBadge(true); }
                });
            }
        }
    }

    @Override
    public void onNavigate(String pageKey)
    {
        if (pageKey.equals(currentPageKey))
        {
            return;
        }

        BufferedImage snapshot = captureSnapshot();

        // ALL_GAMES is a virtual entry - it shows the same GamesPanel instance
        // registered under Pages.GAMES, just switched to its All Games view.
        if (pageKey.equals(Pages.ALL_GAMES))
        {
            gamesPanel.showAllGamesView();
            cardLayout.show(contentPanel, Pages.GAMES);
        }
        else if (pageKey.equals(Pages.GAMES))
        {
            gamesPanel.showHomeView();
            cardLayout.show(contentPanel, Pages.GAMES);
        }
        else
        {
            cardLayout.show(contentPanel, pageKey);
        }

        currentPageKey = pageKey;
        topBar.setPageTitle(titleFor(pageKey));

        if (snapshot != null)
        {
            runFadeTransition(snapshot);
        }
    }

    /** Screenshots the currently-visible page content before swapping - null if not laid out yet (e.g. very first frame). */
    private BufferedImage captureSnapshot()
    {
        int w = contentPanel.getWidth();
        int h = contentPanel.getHeight();
        if (w <= 0 || h <= 0)
        {
            return null;
        }

        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        contentPanel.paint(g2);
        g2.dispose();
        return image;
    }

    /** Lays the outgoing page's screenshot over the (already-swapped) new page, fading it out to reveal what's underneath. */
    private void runFadeTransition(final BufferedImage snapshot)
    {
        final FadeOverlay overlay = new FadeOverlay(snapshot);
        overlay.setBounds(0, 0, transitionPane.getWidth(), transitionPane.getHeight());
        transitionPane.add(overlay, JLayeredPane.DRAG_LAYER);
        transitionPane.moveToFront(overlay);

        final long start = System.currentTimeMillis();
        final Timer timer = new Timer(15, null);
        timer.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                float t = Math.min(1f, (System.currentTimeMillis() - start) / (float) TRANSITION_MS);
                overlay.setAlpha(1f - t);

                if (t >= 1f)
                {
                    timer.stop();
                    transitionPane.remove(overlay);
                    transitionPane.repaint();
                }
            }
        });
        timer.start();
    }

    private String titleFor(String pageKey)
    {
        if (pageKey.equals(Pages.ALL_GAMES))    return "All Games";
        if (pageKey.equals(Pages.LEADERBOARDS)) return "Leaderboards";
        if (pageKey.equals(Pages.ACHIEVEMENTS)) return "Achievements";
        if (pageKey.equals(Pages.TOURNAMENTS)) return "Tournaments";
        if (pageKey.equals(Pages.QUESTS))      return "Quests";
        if (pageKey.equals(Pages.FRIENDS))    return "Friends";
        if (pageKey.equals(Pages.CHAT))       return "Chat";
        if (pageKey.equals(Pages.SHOP))       return "Shop";
        if (pageKey.equals(Pages.PROFILE))    return "Profile";
        if (pageKey.equals(Pages.SETTINGS))   return "Settings";
        if (pageKey.equals(Pages.MODERATION)) return "Moderation";
        if (pageKey.equals(Pages.SATELLITE_SERVERS)) return "Servers";
        return "Games";
    }

    /** A static image drawn at a settable alpha - the fading "ghost" of the page being left. */
    private static class FadeOverlay extends JPanel
    {
        private final BufferedImage image;
        private float alpha = 1f;

        FadeOverlay(BufferedImage image)
        {
            this.image = image;
            setOpaque(false);
        }

        void setAlpha(float alpha)
        {
            this.alpha = Math.max(0f, Math.min(1f, alpha));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.drawImage(image, 0, 0, getWidth(), getHeight(), null);
            g2.dispose();
        }
    }
}
