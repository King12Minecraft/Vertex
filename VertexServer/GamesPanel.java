import javax.swing.BorderFactory;
import javax.swing.Box;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GamesPanel
 * ----------
 * Two views behind an internal tab switcher, since the catalog grew
 * too large for one long scrolling page:
 *
 *   - HOME: the hero banner, "Continue Playing" (real recent-play
 *     history), and "Quick Play" (a personal pinned shortlist, see
 *     PinnedGamesStore - local, no server round-trip).
 *   - ALL GAMES: the full catalog with filter chips - All / Trending /
 *     Offline / Multiplayer. Trending and Offline/Multiplayer are both
 *     derived from data already fetched (GAME_HISTORY_RESPONSE's
 *     trending list, and GameInfo.isOnline()) - no new server protocol
 *     needed for filtering.
 *
 * Every card (on either view) has a small Pin/Unpin toggle, so a user
 * can build their Quick Play list from wherever they find a game.
 */
public class GamesPanel extends RoundedPanel implements NetworkManager.PushListener
{
    private static final String HOME = "HOME";
    private static final String ALL_GAMES = "ALL_GAMES";

    private static final String FILTER_ALL = "ALL";
    private static final String FILTER_TRENDING = "TRENDING";
    private static final String FILTER_OFFLINE = "OFFLINE";
    private static final String FILTER_MULTIPLAYER = "MULTIPLAYER";

    private final Map<String, JLabel> queueLabelsByGameId = new HashMap<String, JLabel>();
    private final Map<String, ThemedButton> filterButtons = new HashMap<String, ThemedButton>();

    private final CardLayout viewCardLayout = new CardLayout();
    private final JPanel viewCards = new JPanel(viewCardLayout);
    private ThemedButton homeTabButton;
    private ThemedButton allGamesTabButton;
    private String currentFilter = FILTER_ALL;

    private JPanel heroContainer;
    private JPanel recentRow;
    private JPanel recentSection;
    private JPanel pinnedRow;
    private JPanel pinnedSection;
    private JPanel allGamesGrid;

    private ThemedButton refreshButton;
    private List<String> lastTrendingIds = new ArrayList<String>();

    public GamesPanel()
    {
        super(ThemeColor.BG_APP, 0);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 32, 24, 32));

        add(createHeader(), BorderLayout.NORTH);

        // Without this, viewCards paints its own default (opaque, light
        // gray/white) Swing background wherever its shown card doesn't
        // fully cover it - e.g. the Home view, whose JScrollPane content
        // (hero banner + a strut) is much shorter than the page once
        // there's nothing pinned/recently played, leaving the rest of the
        // page as a big pale box instead of showing this panel's own
        // themed background underneath.
        viewCards.setOpaque(false);

        viewCards.add(createHomeView(), HOME);
        viewCards.add(createAllGamesView(), ALL_GAMES);
        add(viewCards, BorderLayout.CENTER);

        GameManager.addListener(new Runnable()
        {
            public void run() { rebuildAll(); }
        });

        rebuildAll();
        fetchGamesInBackground(false);
        fetchHistoryInBackground();

        NetworkManager.addPushListener(this);
    }

    @Override
    public void onPush(final Message message)
    {
        if (message.getType() != MessageType.QUEUE_UPDATE)
        {
            return;
        }
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                JLabel label = queueLabelsByGameId.get(message.getQueueGameId());
                if (label != null)
                {
                    label.setText(queueText(message.getQueueCount()));
                }
            }
        });
    }

    private String queueText(int count)
    {
        return count == 1 ? "1 in queue" : count + " in queue";
    }

    // ==================== Header + tabs ====================

    private JPanel createHeader()
    {
        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));

        wrap.add(new PageHeader("GAMES"));

        JPanel tabRow = new JPanel(new BorderLayout());
        tabRow.setOpaque(false);
        tabRow.setBorder(new EmptyBorder(4, 0, 16, 0));

        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tabs.setOpaque(false);

        homeTabButton = new ThemedButton("Home", true);
        homeTabButton.setPreferredSize(new Dimension(90, 36));
        homeTabButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { switchView(HOME); }
        });

        allGamesTabButton = new ThemedButton("All Games", false);
        allGamesTabButton.setPreferredSize(new Dimension(110, 36));
        allGamesTabButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { switchView(ALL_GAMES); }
        });

        tabs.add(homeTabButton);
        tabs.add(allGamesTabButton);
        tabRow.add(tabs, BorderLayout.WEST);

        refreshButton = new ThemedButton("Refresh", false);
        refreshButton.setPreferredSize(new Dimension(110, 36));
        refreshButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                fetchGamesInBackground(true);
                fetchHistoryInBackground();
            }
        });
        JPanel refreshWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        refreshWrap.setOpaque(false);
        refreshWrap.add(refreshButton);
        tabRow.add(refreshWrap, BorderLayout.EAST);

        wrap.add(tabRow);
        return wrap;
    }

    private void switchView(String view)
    {
        homeTabButton.setPrimary(HOME.equals(view));
        allGamesTabButton.setPrimary(ALL_GAMES.equals(view));
        viewCardLayout.show(viewCards, view);
    }

    /** Called by MainMenu when the Sidebar's separate "All Games" entry is clicked - this panel is the same instance registered under Pages.GAMES, just told to show its All Games view instead of Home. */
    public void showAllGamesView()
    {
        switchView(ALL_GAMES);
    }

    /** Called by MainMenu when the Sidebar's "Games" entry is clicked, to make sure returning to it always lands on Home. */
    public void showHomeView()
    {
        switchView(HOME);
    }

    // ==================== Home view ====================

    private JScrollPane createHomeView()
    {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        heroContainer = new JPanel(new BorderLayout());
        heroContainer.setOpaque(false);
        heroContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        heroContainer.setMaximumSize(new Dimension(4000, 260));
        content.add(heroContainer);
        content.add(Box.createVerticalStrut(24));

        pinnedSection = new JPanel();
        pinnedSection.setOpaque(false);
        pinnedSection.setLayout(new BoxLayout(pinnedSection, BoxLayout.Y_AXIS));
        pinnedSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        pinnedSection.setVisible(false);
        pinnedSection.add(sectionLabel("QUICK PLAY"));
        pinnedRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        pinnedRow.setOpaque(false);
        pinnedRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        pinnedSection.add(pinnedRow);
        pinnedSection.add(Box.createVerticalStrut(24));
        content.add(pinnedSection);

        recentSection = new JPanel();
        recentSection.setOpaque(false);
        recentSection.setLayout(new BoxLayout(recentSection, BoxLayout.Y_AXIS));
        recentSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        recentSection.setVisible(false);
        recentSection.add(sectionLabel("CONTINUE PLAYING"));
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
        return scroll;
    }

    // ==================== All Games view ====================

    private JScrollPane createAllGamesView()
    {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterRow.setOpaque(false);
        filterRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterRow.setBorder(new EmptyBorder(0, 0, 20, 0));

        addFilterChip(filterRow, "All", FILTER_ALL);
        addFilterChip(filterRow, "Trending", FILTER_TRENDING);
        addFilterChip(filterRow, "Offline", FILTER_OFFLINE);
        addFilterChip(filterRow, "Multiplayer", FILTER_MULTIPLAYER);
        content.add(filterRow);

        allGamesGrid = new JPanel(new GridLayout(0, 3, 20, 20));
        allGamesGrid.setOpaque(false);
        allGamesGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(allGamesGrid);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        return scroll;
    }

    private void addFilterChip(JPanel row, String label, final String filterKey)
    {
        ThemedButton chip = new ThemedButton(label, filterKey.equals(currentFilter));
        chip.setPreferredSize(new Dimension(label.length() * 9 + 40, 34));
        chip.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { applyFilter(filterKey); }
        });
        filterButtons.put(filterKey, chip);
        row.add(chip);
    }

    private void applyFilter(String filterKey)
    {
        currentFilter = filterKey;
        for (Map.Entry<String, ThemedButton> entry : filterButtons.entrySet())
        {
            entry.getValue().setPrimary(entry.getKey().equals(filterKey));
        }
        rebuildAllGamesGrid();
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

    // ==================== Data fetching ====================

    private void fetchGamesInBackground(final boolean showFeedback)
    {
        refreshButton.setEnabled(false);
        refreshButton.setText(showFeedback ? "Refreshing..." : "Refresh");

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                final boolean ok = GameManager.refresh();

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        refreshButton.setEnabled(true);
                        refreshButton.setText("Refresh");
                        if (!ok && showFeedback)
                        {
                            GameHubDialog.show(refreshButton, "Refresh",
                                "Can't reach the server - is it running?");
                        }
                    }
                });
            }
        });
        worker.start();
    }

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
                            renderCardRow(recentSection, recentRow, response.getRecentGameIds());
                            lastTrendingIds = response.getTrendingGameIds() != null
                                ? response.getTrendingGameIds() : new ArrayList<String>();
                            refreshHero();
                            if (FILTER_TRENDING.equals(currentFilter))
                            {
                                rebuildAllGamesGrid();
                            }
                        }
                    }
                });
            }
        });
        worker.start();
    }

    private void renderCardRow(JPanel section, JPanel row, List<String> gameIds)
    {
        row.removeAll();

        List<GameInfo> matched = new ArrayList<GameInfo>();
        if (gameIds != null)
        {
            for (int i = 0; i < gameIds.size(); i++)
            {
                GameInfo info = findCachedGame(gameIds.get(i));
                if (info != null)
                {
                    matched.add(info);
                }
            }
        }

        section.setVisible(!matched.isEmpty());
        for (int i = 0; i < matched.size(); i++)
        {
            row.add(buildCard(matched.get(i)));
        }

        row.revalidate();
        row.repaint();
    }

    private void refreshPinnedRow()
    {
        pinnedRow.removeAll();

        List<String> pinnedIds = PinnedGamesStore.getPinned();
        List<GameInfo> matched = new ArrayList<GameInfo>();
        for (int i = 0; i < pinnedIds.size(); i++)
        {
            GameInfo info = findCachedGame(pinnedIds.get(i));
            if (info != null)
            {
                matched.add(info);
            }
        }

        pinnedSection.setVisible(!matched.isEmpty());
        for (int i = 0; i < matched.size(); i++)
        {
            pinnedRow.add(buildCard(matched.get(i)));
        }

        pinnedRow.revalidate();
        pinnedRow.repaint();
    }

    private void refreshHero()
    {
        GameInfo featured = null;

        if (!lastTrendingIds.isEmpty())
        {
            featured = findCachedGame(lastTrendingIds.get(0));
        }

        if (featured == null)
        {
            List<GameInfo> games = GameManager.getCachedGames();
            for (int i = 0; i < games.size(); i++)
            {
                if (!games.get(i).isComingSoon())
                {
                    featured = games.get(i);
                    break;
                }
            }
            if (featured == null && !games.isEmpty())
            {
                featured = games.get(0);
            }
        }

        heroContainer.removeAll();
        if (featured != null)
        {
            heroContainer.add(new HeroBanner(featured), BorderLayout.CENTER);
        }
        heroContainer.revalidate();
        heroContainer.repaint();
    }

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

    private void rebuildAll()
    {
        refreshPinnedRow();
        rebuildAllGamesGrid();
        refreshHero();
    }

    private void rebuildAllGamesGrid()
    {
        allGamesGrid.removeAll();
        List<GameInfo> games = filteredGames();

        if (games.isEmpty())
        {
            JLabel empty = new JLabel(GameManager.getCachedGames().isEmpty()
                ? "No games loaded yet - checking the server..."
                : "No games match this filter.");
            empty.setFont(UITheme.FONT_BODY);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            allGamesGrid.add(empty);
        }
        else
        {
            for (int i = 0; i < games.size(); i++)
            {
                allGamesGrid.add(buildCard(games.get(i)));
            }
        }

        allGamesGrid.revalidate();
        allGamesGrid.repaint();
    }

    private List<GameInfo> filteredGames()
    {
        List<GameInfo> all = GameManager.getCachedGames();
        List<GameInfo> result = new ArrayList<GameInfo>();

        for (int i = 0; i < all.size(); i++)
        {
            GameInfo game = all.get(i);
            boolean include;
            if (FILTER_TRENDING.equals(currentFilter))
            {
                include = lastTrendingIds.contains(game.getGameId());
            }
            else if (FILTER_OFFLINE.equals(currentFilter))
            {
                include = !game.isOnline();
            }
            else if (FILTER_MULTIPLAYER.equals(currentFilter))
            {
                include = game.isOnline();
            }
            else
            {
                include = true;
            }
            if (include)
            {
                result.add(game);
            }
        }
        return result;
    }

    // ==================== Card building ====================

    private JPanel buildCard(final GameInfo game)
    {
        final RoundedPanel card = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.setPreferredSize(new Dimension(260, 340));
        card.enableTopAccent();
        card.addMouseListener(new MouseAdapter()
        {
            public void mouseEntered(MouseEvent e) { card.glow().animateIn(); }
            public void mouseExited(MouseEvent e)  { card.glow().animateOut(); }
        });

        JPanel artWrap = new JPanel(new BorderLayout());
        artWrap.setOpaque(false);

        JPanel art = new GameCardArt(game.getGameId());
        art.setPreferredSize(new Dimension(228, 100));
        artWrap.add(art, BorderLayout.CENTER);

        final ThemedButton pin = new ThemedButton(PinnedGamesStore.isPinned(game.getGameId()) ? "Pinned" : "Pin", false);
        pin.setPreferredSize(new Dimension(70, 26));
        pin.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                PinnedGamesStore.toggle(game.getGameId());
                pin.setText(PinnedGamesStore.isPinned(game.getGameId()) ? "Pinned" : "Pin");
                refreshPinnedRow();
            }
        });
        JPanel pinWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        pinWrap.setOpaque(false);
        pinWrap.add(pin);
        artWrap.add(pinWrap, BorderLayout.SOUTH);

        card.add(artWrap, BorderLayout.NORTH);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBorder(new EmptyBorder(14, 0, 0, 0));

        JLabel name = new JLabel(game.getName());
        name.setFont(UITheme.FONT_NAV_BOLD);
        name.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel type = new JLabel(game.getType());
        type.setFont(UITheme.FONT_SMALL);
        type.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        type.setAlignmentX(Component.LEFT_ALIGNMENT);
        type.setBorder(new EmptyBorder(3, 0, 8, 0));

        Color statusColor = game.isComingSoon()
            ? ThemeManager.getColor(ThemeColor.ACCENT)
            : (game.isOnline() ? ThemeManager.getColor(ThemeColor.SUCCESS) : ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        StatusPill pill = new StatusPill(game.getStatusText(), statusColor);
        pill.setAlignmentX(Component.LEFT_ALIGNMENT);

        info.add(name);
        info.add(type);
        info.add(pill);

        if ("tictactoe-online".equals(game.getGameId()) || "racing".equals(game.getGameId())
            || "among-us".equals(game.getGameId()))
        {
            // Fixed-width HTML wrap - guarantees this can never render
            // wider than the card's content area, regardless of count
            // length. The earlier plain-JLabel version had no width
            // constraint of its own and could spill past the card.
            JLabel queueLabel = new JLabel(
                "<html><body style='width:180px'>" + queueText(game.getQueueCount()) + "</body></html>");
            queueLabel.setFont(UITheme.FONT_SMALL.deriveFont(java.awt.Font.BOLD));
            queueLabel.setForeground(ThemeManager.getColor(ThemeColor.ACCENT));
            queueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            queueLabel.setMaximumSize(new Dimension(200, 20));
            queueLabel.setBorder(new EmptyBorder(6, 0, 0, 0));
            info.add(queueLabel);
            queueLabelsByGameId.put(game.getGameId(), queueLabel);
        }

        info.add(Box.createVerticalGlue());

        final ThemedButton play = new ThemedButton(game.isComingSoon() ? "Coming Soon" : "Play", !game.isComingSoon());
        play.setAlignmentX(Component.LEFT_ALIGNMENT);
        play.setMaximumSize(new Dimension(500, 38));
        play.setPreferredSize(new Dimension(228, 38));
        play.setEnabled(!game.isComingSoon());
        play.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { GameLauncher.launch(play, game); }
        });
        info.add(Box.createVerticalStrut(10));
        info.add(play);

        card.add(info, BorderLayout.CENTER);
        return card;
    }
}
