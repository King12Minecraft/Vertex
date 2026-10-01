package pages;
import games.GameLauncher;
import ui.ThemedButton;
import net.NetworkManager;
import net.MessageType;
import net.Message;
import theme.ThemeManager;
import theme.UITheme;
import games.GameManager;
import ui.ThemedScrollBarUI;
import ui.PageHeader;
import theme.ThemeColor;
import games.GameInfo;
import ui.MarqueeBanner;
import ui.RoundedPanel;
import ui.ThemedLabel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * HomePanel
 * ---------
 * The app's default landing page. Games used to open straight into the
 * full catalog (GamesPanel); now that lives one click away via the
 * Sidebar's own "Games" entry (see Pages.GAMES), and this page is what
 * greets you instead - a quick, at-a-glance overview rather than the
 * whole store front:
 *
 *   - A scrolling ticker (MarqueeBanner) combining recently-played
 *     games, leaderboard leaders, and NotificationCenter messages into
 *     one continuously-moving feed.
 *   - "Top Players": a compact leaderboard snapshot for a few
 *     spotlighted rated games (LEADERBOARD_REQUEST, same protocol
 *     LeaderboardPanel already uses).
 *   - "Recently Played": your own recent games (GAME_HISTORY_REQUEST,
 *     same as GamesPanel's Home view) with a Play button right here,
 *     so re-launching something doesn't require a detour through the
 *     Games page at all.
 *
 * (2026-10-01) Above those sit the six sections of the Home redesign -
 * welcome/daily reward/quests, quick play, continue playing, friends online,
 * tournaments and what's new - each its own HomeSectionPanel subclass with real
 * data (see each class). They are laid out by fullWidth(...) / split(...) rows
 * (FitRow: height follows content) inside a width-tracking scroll view, so a
 * wide section can never push the page past the window. The older sections
 * below stay until the redesign places them.
 *
 * Refreshes on a timer (like TopBar's online-count) and immediately
 * whenever a new NotificationCenter item arrives, so the ticker stays
 * current without the user having to do anything.
 */
public class HomePanel extends RoundedPanel
{
    private static final int REFRESH_MS = 30000;

    /** A handful of the rated games worth spotlighting on the ticker/Top Players row - kept short so startup doesn't fire a burst of leaderboard requests for every game in the catalog. */
    private static final String[] SPOTLIGHT_GAME_IDS =
        { "chess", "tictactoe-online", "battleship", "rock-paper-scissors" };

    private final List<HomeSectionPanel> homeSections = new ArrayList<HomeSectionPanel>();
    private final MarqueeBanner ticker;
    private final JPanel topPlayersRow;
    private final HomeWelcomeSection welcome;
    private final HomeContinueSection continuePlaying;
    private final HomeQuickPlaySection quickPlay;
    private final HomeFriendsSection friends;
    private final HomeTournamentsSection tournaments;
    private final HomeWhatsNewSection whatsNew;
    private JPanel exploreRow;

    private List<String> lastRecentNames = new ArrayList<String>();
    private List<String> lastTopPlayerLines = new ArrayList<String>();

    public HomePanel()
    {
        super(ThemeColor.BG_APP, 0);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 32, 24, 32));

        JPanel content = new WidthTrackingPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // Left-aligned like everything else in this column - a centre-aligned header among left-aligned rows shifts them all sideways.
        PageHeader header = new PageHeader("HOME");
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(4000, header.getPreferredSize().height));
        content.add(header);

        ticker = new MarqueeBanner();
        ticker.setAlignmentX(Component.LEFT_ALIGNMENT);
        ticker.setMaximumSize(new Dimension(4000, 44));
        content.add(ticker);
        content.add(Box.createVerticalStrut(24));

        // The Home sections, each its own class (see HomeSectionPanel): welcome across the top, then Quick play beside
        // Continue playing (1:2), Friends beside Tournaments, and What's new across the bottom.
        welcome = new HomeWelcomeSection();
        continuePlaying = new HomeContinueSection();
        quickPlay = new HomeQuickPlaySection();
        friends = new HomeFriendsSection();
        tournaments = new HomeTournamentsSection();
        whatsNew = new HomeWhatsNewSection();
        homeSections.add(welcome);
        homeSections.add(continuePlaying);
        homeSections.add(quickPlay);
        homeSections.add(friends);
        homeSections.add(tournaments);
        homeSections.add(whatsNew);
        content.add(fullWidth(welcome));
        content.add(Box.createVerticalStrut(16));
        content.add(split(quickPlay, continuePlaying, 1, 2));
        content.add(Box.createVerticalStrut(16));
        content.add(split(friends, tournaments, 1, 1));
        content.add(Box.createVerticalStrut(16));
        content.add(fullWidth(whatsNew));
        content.add(Box.createVerticalStrut(32));

        content.add(sectionLabel("TOP PLAYERS"));
        topPlayersRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        topPlayersRow.setOpaque(false);
        topPlayersRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(topPlayersRow);
        content.add(Box.createVerticalStrut(24));

        content.add(sectionLabel("EXPLORE GAMES"));
        exploreRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        exploreRow.setOpaque(false);
        exploreRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(exploreRow);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        add(scroll, BorderLayout.CENTER);

        refreshAll();

        // GamesPanel triggers the actual GAME_LIST_REQUEST fetch on its own
        // construction; this just means whichever page loads first (Home
        // now always does), the other's cache eventually fills in behind
        // it. Listening here means Top Players/Recently Played don't have
        // to wait out a stale empty GameManager cache until the 30s timer
        // comes back around - they refresh the moment it's actually filled.
        GameManager.addListener(new Runnable()
        {
            public void run() { refreshAll(); }
        });

        NotificationCenter.addListener(new Runnable()
        {
            public void run() { rebuildTicker(); }
        });

        Timer refreshTimer = new Timer(REFRESH_MS, new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { refreshAll(); }
        });
        refreshTimer.start();
    }

    private JLabel sectionLabel(String text)
    {
        JLabel label = new ThemedLabel(text, ThemeColor.TEXT_SECONDARY);
        label.setFont(UITheme.FONT_NAV_BOLD);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 12, 0));
        return label;
    }

    /**
     * A row whose maximum height always equals its current preferred height. A BoxLayout column otherwise hands spare
     * height to any child that allows it, which would stretch the cards - and a fixed maximum set at construction would
     * go stale the moment a section's content changes (a list gains a row). Asking at layout time avoids both.
     */
    private static class FitRow extends JPanel
    {
        FitRow(java.awt.LayoutManager layout)
        {
            super(layout);
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        public Dimension getMaximumSize()
        {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /**
     * The scroll view: follows the viewport's width instead of its own preferred width. Without this a wide section (a
     * row of game tiles, a long changelog line) would stretch the whole page past the window and add a horizontal
     * scrollbar - sections have to fit the window, not the other way round.
     */
    private static class WidthTrackingPanel extends JPanel implements javax.swing.Scrollable
    {
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(java.awt.Rectangle r, int orientation, int direction) { return 16; }
        public int getScrollableBlockIncrement(java.awt.Rectangle r, int orientation, int direction) { return r.height - 32; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** One section spanning the full width. */
    private JPanel fullWidth(HomeSectionPanel section)
    {
        JPanel row = new FitRow(new BorderLayout());
        row.add(section, BorderLayout.CENTER);
        return row;
    }

    /** Two sections side by side; widths in the ratio leftWeight:rightWeight, equal heights. */
    private JPanel split(HomeSectionPanel left, HomeSectionPanel right, double leftWeight, double rightWeight)
    {
        JPanel row = new FitRow(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.fill = java.awt.GridBagConstraints.BOTH;
        c.weighty = 1;
        c.gridy = 0;
        c.gridx = 0;
        c.weightx = leftWeight;
        c.insets = new java.awt.Insets(0, 0, 0, 8);
        left.setMinimumSize(new Dimension(300, 0));
        right.setMinimumSize(new Dimension(300, 0));
        row.add(left, c);
        c.gridx = 1;
        c.weightx = rightWeight;
        c.insets = new java.awt.Insets(0, 8, 0, 0);
        row.add(right, c);
        return row;
    }

    private void refreshAll()
    {
        for (int i = 0; i < homeSections.size(); i++)
        {
            homeSections.get(i).refresh();
        }
        fetchHistoryInBackground();
        fetchTopPlayersInBackground();
        rebuildExplore();
    }

    /** A handful of playable games as a quick jump-in point, so Home has something to look at even for a brand new account with no recent/leaderboard activity yet - skips anything still "Coming Soon" and anything already shown in Recently Played, capped at 6 so this stays a preview, not a second copy of the full Games page. */
    private void rebuildExplore()
    {
        exploreRow.removeAll();

        List<GameInfo> all = GameManager.getCachedGames();
        int shown = 0;
        for (int i = 0; i < all.size() && shown < 6; i++)
        {
            GameInfo game = all.get(i);
            if (game.isComingSoon() || lastRecentNames.contains(game.getName()))
            {
                continue;
            }
            exploreRow.add(buildRecentCard(game));
            shown++;
        }

        exploreRow.revalidate();
        exploreRow.repaint();
    }

    // ==================== Recently played ====================

    private void fetchHistoryInBackground()
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.GAME_HISTORY_REQUEST);
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (response != null && response.isSuccess())
                        {
                            renderRecent(response.getRecentGameIds());
                        }
                        rebuildTicker();
                    }
                });
            }
        });
        worker.start();
    }

    private void renderRecent(List<String> gameIds)
    {
        // Recently played now lives in the Continue playing section (and informs Quick play): hand the ids over.
        continuePlaying.setRecentGameIds(gameIds);
        quickPlay.setRecentGameIds(gameIds);

        lastRecentNames = new ArrayList<String>();
        if (gameIds != null)
        {
            for (int i = 0; i < gameIds.size(); i++)
            {
                GameInfo info = findCachedGame(gameIds.get(i));
                if (info != null)
                {
                    lastRecentNames.add(info.getName());
                }
            }
        }
        rebuildExplore();
    }

    private JPanel buildRecentCard(final GameInfo game)
    {
        RoundedPanel card = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        card.setPreferredSize(new Dimension(220, 118));
        card.enableTopAccent();

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        JLabel name = new ThemedLabel(game.getName(), ThemeColor.TEXT_PRIMARY);
        name.setFont(UITheme.FONT_NAV_BOLD);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel type = new ThemedLabel(game.getType(), ThemeColor.TEXT_MUTED);
        type.setFont(UITheme.FONT_SMALL);
        type.setAlignmentX(Component.LEFT_ALIGNMENT);
        type.setBorder(new EmptyBorder(2, 0, 10, 0));

        info.add(name);
        info.add(type);
        info.add(Box.createVerticalGlue());

        final ThemedButton play = new ThemedButton("Play", true);
        play.setAlignmentX(Component.LEFT_ALIGNMENT);
        play.setPreferredSize(new Dimension(188, 32));
        play.setMaximumSize(new Dimension(188, 32));
        play.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { GameLauncher.launch(play, game); }
        });
        info.add(play);

        card.add(info, BorderLayout.CENTER);
        return card;
    }

    // ==================== Top players ====================

    private void fetchTopPlayersInBackground()
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                final List<String[]> results = new ArrayList<String[]>(); // {gameName, rank|username|value...}

                for (int i = 0; i < SPOTLIGHT_GAME_IDS.length; i++)
                {
                    String gameId = SPOTLIGHT_GAME_IDS[i];
                    GameInfo info = findCachedGame(gameId);
                    if (info == null || info.isComingSoon())
                    {
                        continue;
                    }

                    Message request = new Message();
                    request.setType(MessageType.LEADERBOARD_REQUEST);
                    request.setGameId(gameId);
                    Message response = NetworkManager.send(request);

                    if (response != null && response.isSuccess()
                        && response.getLeaderboardEntries() != null
                        && !response.getLeaderboardEntries().isEmpty())
                    {
                        results.add(new String[] { info.getName(), response.getLeaderboardEntries().get(0) });
                    }
                }

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        renderTopPlayers(results);
                        rebuildTicker();
                    }
                });
            }
        });
        worker.start();
    }

    private void renderTopPlayers(List<String[]> results)
    {
        topPlayersRow.removeAll();
        lastTopPlayerLines = new ArrayList<String>();

        if (results.isEmpty())
        {
            JLabel empty = new ThemedLabel("No leaderboard activity yet - be the first to play!", ThemeColor.TEXT_MUTED);
            empty.setFont(UITheme.FONT_BODY);
            topPlayersRow.add(empty);
        }
        else
        {
            for (int i = 0; i < results.size(); i++)
            {
                String gameName = results.get(i)[0];
                String entry = results.get(i)[1];
                String[] parts = entry.split("\\|", -1);
                String username = parts.length > 1 ? parts[1] : "?";
                String value = parts.length > 2 ? parts[2] : "0";

                topPlayersRow.add(buildTopPlayerCard(gameName, username, value));
                lastTopPlayerLines.add(gameName + " leader: " + username + " (" + value + ")");
            }
        }

        topPlayersRow.revalidate();
        topPlayersRow.repaint();
    }

    private JPanel buildTopPlayerCard(String gameName, String username, String value)
    {
        RoundedPanel card = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        card.setPreferredSize(new Dimension(200, 92));
        card.enableTopAccent();

        JLabel game = new ThemedLabel(gameName, ThemeColor.TEXT_MUTED);
        game.setFont(UITheme.FONT_SMALL);
        game.setAlignmentX(Component.LEFT_ALIGNMENT);
        game.setBorder(new EmptyBorder(0, 0, 8, 0));

        JLabel who = new ThemedLabel("#1  " + username, ThemeColor.TEXT_PRIMARY);
        who.setFont(UITheme.FONT_NAV_BOLD);
        who.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel val = new ThemedLabel(value, ThemeColor.ACCENT);
        val.setFont(UITheme.FONT_BODY);
        val.setAlignmentX(Component.LEFT_ALIGNMENT);
        val.setBorder(new EmptyBorder(4, 0, 0, 0));

        card.add(game);
        card.add(who);
        card.add(val);
        return card;
    }

    // ==================== Ticker ====================

    private void rebuildTicker()
    {
        List<String> items = new ArrayList<String>();

        for (int i = 0; i < lastTopPlayerLines.size(); i++)
        {
            items.add(lastTopPlayerLines.get(i));
        }
        for (int i = 0; i < lastRecentNames.size(); i++)
        {
            items.add("Recently played: " + lastRecentNames.get(i));
        }

        List<NotificationCenter.NotificationItem> notifications = NotificationCenter.getAll();
        for (int i = 0; i < notifications.size() && i < 5; i++)
        {
            NotificationCenter.NotificationItem item = notifications.get(i);
            items.add(item.title + ": " + item.body);
        }

        ticker.setItems(items);
    }

    // ==================== Shared lookup ====================

    private GameInfo findCachedGame(String gameId)
    {
        List<GameInfo> games = GameManager.getCachedGames();
        for (int i = 0; i < games.size(); i++)
        {
            if (games.get(i).getGameId().equals(gameId))
            {
                return games.get(i);
            }
        }
        return null;
    }
}
