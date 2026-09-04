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

    private final MarqueeBanner ticker;
    private final JPanel topPlayersRow;
    private final JPanel recentRow;
    private final JPanel recentSection;

    private List<String> lastRecentNames = new ArrayList<String>();
    private List<String> lastTopPlayerLines = new ArrayList<String>();

    public HomePanel()
    {
        super(ThemeColor.BG_APP, 0);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 32, 24, 32));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(new PageHeader("HOME"));

        ticker = new MarqueeBanner();
        ticker.setAlignmentX(Component.LEFT_ALIGNMENT);
        ticker.setMaximumSize(new Dimension(4000, 44));
        content.add(ticker);
        content.add(Box.createVerticalStrut(24));

        content.add(sectionLabel("TOP PLAYERS"));
        topPlayersRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        topPlayersRow.setOpaque(false);
        topPlayersRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(topPlayersRow);
        content.add(Box.createVerticalStrut(24));

        recentSection = new JPanel();
        recentSection.setOpaque(false);
        recentSection.setLayout(new BoxLayout(recentSection, BoxLayout.Y_AXIS));
        recentSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        recentSection.setVisible(false);
        recentSection.add(sectionLabel("RECENTLY PLAYED"));
        recentRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        recentRow.setOpaque(false);
        recentRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        recentSection.add(recentRow);
        content.add(recentSection);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        add(scroll, BorderLayout.CENTER);

        refreshAll();

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
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_NAV_BOLD);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 12, 0));
        return label;
    }

    private void refreshAll()
    {
        fetchHistoryInBackground();
        fetchTopPlayersInBackground();
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
        recentRow.removeAll();
        lastRecentNames = new ArrayList<String>();

        List<GameInfo> matched = new ArrayList<GameInfo>();
        if (gameIds != null)
        {
            for (int i = 0; i < gameIds.size(); i++)
            {
                GameInfo info = findCachedGame(gameIds.get(i));
                if (info != null)
                {
                    matched.add(info);
                    lastRecentNames.add(info.getName());
                }
            }
        }

        recentSection.setVisible(!matched.isEmpty());
        for (int i = 0; i < matched.size(); i++)
        {
            recentRow.add(buildRecentCard(matched.get(i)));
        }
        recentRow.revalidate();
        recentRow.repaint();
    }

    private JPanel buildRecentCard(final GameInfo game)
    {
        RoundedPanel card = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        card.setPreferredSize(new Dimension(220, 92));
        card.enableTopAccent();

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        JLabel name = new JLabel(game.getName());
        name.setFont(UITheme.FONT_NAV_BOLD);
        name.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel type = new JLabel(game.getType());
        type.setFont(UITheme.FONT_SMALL);
        type.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
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
            JLabel empty = new JLabel("No leaderboard activity yet - be the first to play!");
            empty.setFont(UITheme.FONT_BODY);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
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

        JLabel game = new JLabel(gameName);
        game.setFont(UITheme.FONT_SMALL);
        game.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        game.setAlignmentX(Component.LEFT_ALIGNMENT);
        game.setBorder(new EmptyBorder(0, 0, 8, 0));

        JLabel who = new JLabel("#1  " + username);
        who.setFont(UITheme.FONT_NAV_BOLD);
        who.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        who.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel val = new JLabel(value);
        val.setFont(UITheme.FONT_BODY);
        val.setForeground(ThemeManager.getColor(ThemeColor.ACCENT));
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
