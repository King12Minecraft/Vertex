package pages;
import economy.ChallengeProgressInfo;
import net.NetworkManager;
import net.MessageType;
import net.Message;
import theme.ThemeManager;
import theme.UITheme;
import games.GameLogo;
import account.PermissionManager;
import account.Session;
import account.Account;
import theme.ThemeColor;
import net.ConnectionIndicator;
import ui.SidebarButton;
import ui.ThemedScrollBarUI;
import net.NavigationListener;
import ui.RoundedPanel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.prefs.Preferences;
import java.util.List;

/**
 * Sidebar
 * -------
 * The left-hand navigation column. Collapsed by default (icon-only,
 * COLLAPSED_WIDTH) - hovering anywhere over the sidebar animates it
 * out to EXPANDED_WIDTH, revealing each nav button's text label, the
 * wordmark next to the logo, and the quest mini-list/connection text;
 * moving off collapses it back. Reports clicks to whoever is listening
 * (MainMenu) via NavigationListener.
 *
 * Admin and Moderation panels were functionally redundant, so the
 * separate Admin entry/page was removed - Moderation covers what's
 * needed, and admins can already do everything moderators can via
 * their role (see PermissionManager).
 */
public class Sidebar extends RoundedPanel
{
    private static final int COLLAPSED_WIDTH = 64;
    private static final int EXPANDED_WIDTH = 220;
    private static final int ANIMATION_MS = 160;

    private final NavigationListener listener;
    private final List<SidebarButton> buttons = new ArrayList<SidebarButton>();
    private final List<String> pageKeysInOrder = new ArrayList<String>();

    private static final Preferences PREFS = Preferences.userNodeForPackage(Sidebar.class);

    private final List<Group> groups = new ArrayList<Group>();
    /** Where addNavButton puts the next button: the current group's body, or the plain list for ungrouped entries (Home). */
    private JPanel currentBody;
    private JPanel navList;

    private JLabel wordmark;
    private JPanel questSection;
    private ConnectionIndicator connectionIndicator;

    private boolean expanded = false;
    private Timer widthTimer;

    public Sidebar(NavigationListener listener)
    {
        super(ThemeColor.BG_SIDEBAR, 0);
        this.listener = listener;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setPreferredSize(new Dimension(COLLAPSED_WIDTH, 0));

        add(createLogoRow());

        // Only the navigation scrolls; the logo row above and the quests/status rows below stay put.
        navList = new JPanel();
        navList.setOpaque(false);
        navList.setLayout(new BoxLayout(navList, BoxLayout.Y_AXIS));
        currentBody = navList;

        addNavButton("Home", Pages.HOME);

        beginGroup("play", "PLAY");
        addNavButton("Games", Pages.GAMES);
        addNavButton("All Games", Pages.ALL_GAMES);
        addNavButton("Tournaments", Pages.TOURNAMENTS);
        addNavButton("Dominion", Pages.DOMINION);

        beginGroup("progress", "PROGRESS");
        addNavButton("Quests", Pages.QUESTS);
        addNavButton("Achievements", Pages.ACHIEVEMENTS);
        addNavButton("Leaderboards", Pages.LEADERBOARDS);

        beginGroup("social", "SOCIAL");
        addNavButton("Friends", Pages.FRIENDS);
        addNavButton("Chat", Pages.CHAT);
        addNavButton("Forums", Pages.FORUMS);

        beginGroup("community", "SHOP & COMMUNITY");
        addNavButton("Shop", Pages.SHOP);
        addNavButton("Suggest a Game", Pages.GAME_SUGGESTIONS);
        addNavButton("Changelog", Pages.CHANGELOG);

        beginGroup("account", "ACCOUNT");
        addNavButton("Profile", Pages.PROFILE);
        addNavButton("Settings", Pages.SETTINGS);
        Account current = Session.getCurrentAccount();
        if (PermissionManager.isAtLeastModerator(current))
        {
            addNavButton("Moderation", Pages.MODERATION);
        }

        JScrollPane navScroll = new JScrollPane(navList,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        navScroll.setBorder(null);
        navScroll.setOpaque(false);
        navScroll.getViewport().setOpaque(false);
        navScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        navScroll.getVerticalScrollBar().setUnitIncrement(24);
        ThemedScrollBarUI.apply(navScroll);
        navScroll.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        add(navScroll);

        add(createQuestMiniList());
        add(createStatusRow());

        selectPage(Pages.HOME);

        addMouseListener(new MouseAdapter()
        {
            public void mouseEntered(MouseEvent e) { setExpanded(true); }
            public void mouseExited(MouseEvent e)
            {
                if (!contains(e.getPoint()))
                {
                    setExpanded(false);
                }
            }
        });
    }

    private void setExpanded(boolean newExpanded)
    {
        if (newExpanded == expanded)
        {
            return;
        }
        expanded = newExpanded;

        wordmark.setVisible(expanded);
        questSection.setVisible(expanded && questSection.getComponentCount() > 1);
        for (int i = 0; i < buttons.size(); i++)
        {
            buttons.get(i).setExpanded(expanded);
        }

        for (Group group : groups)
        {
            group.header.repaint();
        }

        animateWidthTo(expanded ? EXPANDED_WIDTH : COLLAPSED_WIDTH);
    }

    private void animateWidthTo(final int targetWidth)
    {
        if (widthTimer != null && widthTimer.isRunning())
        {
            widthTimer.stop();
        }

        final int startWidth = getPreferredSize().width;
        final long start = System.currentTimeMillis();

        widthTimer = new Timer(12, null);
        widthTimer.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                float t = Math.min(1f, (System.currentTimeMillis() - start) / (float) ANIMATION_MS);
                int width = (int) (startWidth + (targetWidth - startWidth) * t);
                setPreferredSize(new Dimension(width, 0));
                revalidate();

                if (t >= 1f)
                {
                    widthTimer.stop();
                }
            }
        });
        widthTimer.start();
    }

    private JPanel createLogoRow()
    {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(22, 20, 26, 12));

        GameLogo mark = new GameLogo(28);

        wordmark = new JLabel("VERTEX");
        wordmark.setFont(UITheme.FONT_LOGO);
        wordmark.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        wordmark.setBorder(new EmptyBorder(0, 10, 0, 0));
        wordmark.setVisible(false);

        ThemeManager.addListener(new Runnable()
        {
            public void run()
            {
                wordmark.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
            }
        });

        row.add(mark);
        row.add(wordmark);
        return row;
    }

    private void addNavButton(String label, final String pageKey)
    {
        final SidebarButton button = new SidebarButton(label, pageKey);
        button.setPreferredSize(new Dimension(EXPANDED_WIDTH - 24, 44));
        button.setMaximumSize(new Dimension(2000, 44));

        button.addMouseListener(new MouseAdapter()
        {
            public void mouseClicked(MouseEvent e)
            {
                selectPage(pageKey);
                listener.onNavigate(pageKey);
            }
        });

        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.X_AXIS));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setMaximumSize(new Dimension(2000, 44));
        wrapper.setBorder(new EmptyBorder(0, 12, 4, 12));
        wrapper.add(button);

        buttons.add(button);
        pageKeysInOrder.add(pageKey);
        currentBody.add(wrapper);
        if (!groups.isEmpty() && currentBody != navList)
        {
            groups.get(groups.size() - 1).pageKeys.add(pageKey);
            groups.get(groups.size() - 1).buttons.add(button);
        }
    }

    /** Starts a collapsible group: a header row, then a body that the following addNavButton calls fill. */
    private void beginGroup(String key, String title)
    {
        Group group = new Group(key, title);
        groups.add(group);
        navList.add(group.header);
        navList.add(group.body);
        currentBody = group.body;
    }

    /** One section of the navigation: a clickable header (collapsed state remembered per computer) over its buttons. */
    private final class Group
    {
        final String key;
        final String title;
        final List<String> pageKeys = new ArrayList<String>();
        final List<SidebarButton> buttons = new ArrayList<SidebarButton>();
        final JPanel body = new JPanel();
        final GroupHeader header;
        boolean collapsed;

        Group(String key, String title)
        {
            this.key = key;
            this.title = title;
            this.collapsed = PREFS.getBoolean("collapsed." + key, false);
            body.setOpaque(false);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.setVisible(!collapsed);
            header = new GroupHeader(this);
        }

        void setCollapsed(boolean value)
        {
            collapsed = value;
            PREFS.putBoolean("collapsed." + key, value);
            body.setVisible(!value);
            header.repaint();
            navList.revalidate();
            navList.repaint();
        }

        boolean anyBadge()
        {
            for (SidebarButton b : buttons) if (b.isShowingBadge()) return true;
            return false;
        }

        void refreshBadge() { header.repaint(); }
    }

    /**
     * The clickable title above a group. While the sidebar is the narrow icon rail it is just a thin
     * divider line (there is no room for text); expanded, it shows the group name and a chevron, and a
     * dot when the group is collapsed but something inside it wants attention (a friend came online).
     */
    private final class GroupHeader extends JPanel
    {
        private final Group group;
        private boolean hover = false;

        GroupHeader(final Group group)
        {
            this.group = group;
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setPreferredSize(new Dimension(EXPANDED_WIDTH, 30));
            setMaximumSize(new Dimension(2000, 30));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e)
                {
                    if (expanded) group.setCollapsed(!group.collapsed);
                }
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(java.awt.Graphics g)
        {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);
            int w = getWidth();
            int h = getHeight();
            java.awt.Color muted = ThemeManager.getColor(hover && expanded ? ThemeColor.TEXT_PRIMARY : ThemeColor.TEXT_MUTED);

            if (!expanded)
            {
                g2.setColor(ThemeManager.getColor(ThemeColor.BORDER));
                g2.fillRect(18, h / 2, Math.max(0, COLLAPSED_WIDTH - 36), 1);
                if (group.collapsed && group.anyBadge())
                {
                    g2.setColor(ThemeManager.getColor(ThemeColor.ACCENT));
                    g2.fillOval(COLLAPSED_WIDTH / 2 - 3, h / 2 - 3, 7, 7);
                }
            }
            else
            {
                g2.setFont(UITheme.FONT_SMALL);
                g2.setColor(muted);
                g2.drawString(group.title, 20, h / 2 + 5);
                int chevronX = EXPANDED_WIDTH - 34;
                java.awt.geom.Path2D chevron = new java.awt.geom.Path2D.Double();
                if (group.collapsed)
                {
                    chevron.moveTo(chevronX, h / 2 - 4);
                    chevron.lineTo(chevronX + 5, h / 2);
                    chevron.lineTo(chevronX, h / 2 + 4);
                }
                else
                {
                    chevron.moveTo(chevronX - 2, h / 2 - 2);
                    chevron.lineTo(chevronX + 3, h / 2 + 3);
                    chevron.lineTo(chevronX + 8, h / 2 - 2);
                }
                g2.setStroke(new java.awt.BasicStroke(1.6f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                g2.draw(chevron);
                if (group.collapsed && group.anyBadge())
                {
                    g2.setColor(ThemeManager.getColor(ThemeColor.ACCENT));
                    g2.fillOval(20 + g2.getFontMetrics().stringWidth(group.title) + 8, h / 2 - 3, 7, 7);
                }
            }
            g2.dispose();
        }
    }

    private void selectPage(String pageKey)
    {
        for (int i = 0; i < buttons.size(); i++)
        {
            boolean isMatch = pageKeysInOrder.get(i).equals(pageKey);
            buttons.get(i).setSelected(isMatch);
        }
        if (Pages.FRIENDS.equals(pageKey))
        {
            setFriendsBadge(false);
        }
        // Never leave the page you're on hidden inside a collapsed group.
        for (Group group : groups)
        {
            if (group.pageKeys.contains(pageKey) && group.collapsed)
            {
                group.setCollapsed(false);
            }
        }
    }

    /** Shown when a friend comes online while the person isn't already looking at the Friends page - cleared automatically the moment they do. */
    public void setFriendsBadge(boolean show)
    {
        for (int i = 0; i < pageKeysInOrder.size(); i++)
        {
            if (Pages.FRIENDS.equals(pageKeysInOrder.get(i)))
            {
                buttons.get(i).setShowBadge(show);
                for (Group group : groups) group.refreshBadge();
                return;
            }
        }
    }

    private JPanel createQuestMiniList()
    {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.setBorder(new EmptyBorder(0, 12, 10, 12));

        final JLabel label = new JLabel("IN PROGRESS");
        label.setFont(UITheme.FONT_SMALL);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 4, 6, 0));
        col.add(label);

        final JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(list);

        col.setVisible(false);
        questSection = col;

        Runnable refresh = new Runnable()
        {
            public void run() { loadQuestMiniList(col, list); }
        };
        refresh.run();

        NetworkManager.addPushListener(new NetworkManager.PushListener()
        {
            public void onPush(Message message)
            {
                if (message.getType() == MessageType.CHALLENGE_UPDATE)
                {
                    loadQuestMiniList(col, list);
                }
            }
        });

        return col;
    }

    private void loadQuestMiniList(final JPanel section, final JPanel list)
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.CHALLENGES_REQUEST);
                final Message response = NetworkManager.send(request);

                javax.swing.SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        list.removeAll();
                        int shown = 0;
                        if (response != null && response.isSuccess() && response.getChallenges() != null)
                        {
                            for (int i = 0; i < response.getChallenges().size() && shown < 3; i++)
                            {
                                ChallengeProgressInfo q = response.getChallenges().get(i);
                                if (!q.isCompleted())
                                {
                                    list.add(new QuestRow(q, true));
                                    list.add(Box.createVerticalStrut(6));
                                    shown++;
                                }
                            }
                        }
                        section.setVisible(expanded && shown > 0);
                        list.revalidate();
                        list.repaint();
                    }
                });
            }
        });
        worker.start();
    }

    private JPanel createStatusRow()
    {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(16, 20, 20, 12));
        row.setMaximumSize(new Dimension(2000, 40));

        connectionIndicator = new ConnectionIndicator(NetworkManager.getState());
        row.add(connectionIndicator);

        NetworkManager.addListener(new Runnable()
        {
            public void run() { connectionIndicator.setState(NetworkManager.getState()); }
        });

        return row;
    }

    /**
     * Launcher-style chrome: a subtle top-to-bottom depth gradient plus
     * a thin accent-gradient divider down the right edge, separating
     * the nav rail from page content.
     */
    @Override
    protected void paintComponent(java.awt.Graphics g)
    {
        super.paintComponent(g);

        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        int w = getWidth();
        int h = getHeight();

        java.awt.Color top = ThemeManager.getColor(ThemeColor.BG_SIDEBAR);
        java.awt.Color bottom = ThemeManager.getColor(ThemeColor.BG_APP);
        java.awt.LinearGradientPaint depth = new java.awt.LinearGradientPaint(
            0, 0, 0, Math.max(h, 1), new float[] {0f, 1f}, new java.awt.Color[] {top, bottom});
        g2.setPaint(depth);
        g2.fillRect(0, 0, w, h);

        java.awt.Color accentStart = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START);
        java.awt.Color accentEnd = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_END);
        java.awt.LinearGradientPaint divider = new java.awt.LinearGradientPaint(
            0, 0, 0, Math.max(h, 1), new float[] {0f, 1f}, new java.awt.Color[] {accentStart, accentEnd});
        g2.setPaint(divider);
        g2.fillRect(w - 2, 0, 2, h);

        g2.dispose();
    }
}
