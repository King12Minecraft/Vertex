package pages;
import net.NetworkManager;
import net.MessageType;
import games.GameManager;
import games.GameInfo;
import ui.ThemedScrollBarUI;
import theme.ThemeManager;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import games.ReplayBrowserDialog;
import games.SpectateDialog;
import theme.UITheme;
import theme.ThemeColor;
import net.Message;
import ui.ThemedButton;
import ui.ThemedLabel;
import ui.RoundedPanel;
import ui.PlaceholderPanel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * LeaderboardPanel
 * ----------------
 * One page, all games - a scrolling game list on the left (the selected game is highlighted)
 * and the ranked table for it on the right, top three marked in the accent colour. Rated games (Tic-Tac-Toe, Chess,
 * Battleship, Rock Paper Scissors, Fight Arena) show ELO + win/loss/
 * draw record; score-based games (Racing, Snake, Tetris, etc.) show
 * best score instead - the server decides which via
 * LEADERBOARD_RESPONSE, this panel just renders whichever it gets.
 *
 * Uses a plain blocking NetworkManager.send() per chip click rather
 * than sendAsync()+PushListener - LEADERBOARD_RESPONSE is only ever a
 * direct reply, never pushed unprompted, so there's nothing to listen
 * for. (sendAsync()+onPush here used to be able to steal a response
 * meant for someone else's concurrent blocking send() call, since
 * NetworkManager's response queue has no per-request correlation -
 * see AchievementsPanel's note for the full explanation.)
 */
public class LeaderboardPanel extends PageScaffold
{
    private static final Set<String> RATED_GAMES = new HashSet<String>(java.util.Arrays.asList(
        "tictactoe-online", "chess", "battleship", "rock-paper-scissors", "fight-arena"));

    private final JPanel gameList = new JPanel();
    private final JPanel entriesList = new JPanel();
    private final JLabel myRankLabel;
    private final SectionCard resultsCard;
    private final List<GameItem> items = new java.util.ArrayList<GameItem>();
    private ThemedButton spectateButton;
    private ThemedButton replaysButton;
    private String selectedGameId;

    public LeaderboardPanel()
    {
        super("LEADERBOARDS", "Pick a game to see its top players. Rated games show an ELO rating, the rest show best scores.");

        // ---- left: the game list ----
        gameList.setOpaque(false);
        gameList.setLayout(new BoxLayout(gameList, BoxLayout.Y_AXIS));
        JScrollPane gameScroll = new JScrollPane(gameList);
        gameScroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        gameScroll.setOpaque(false);
        gameScroll.getViewport().setOpaque(false);
        gameScroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(gameScroll);
        SectionCard gamesCard = new SectionCard("GAMES").content(gameScroll);
        gamesCard.setPreferredSize(new Dimension(240, 100));

        // ---- right: the table ----
        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        myRankLabel = new ThemedLabel("Choose a game on the left.", ThemeColor.ACCENT);
        myRankLabel.setFont(UITheme.FONT_NAV_BOLD);
        myRankLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        myRankLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
        top.add(myRankLabel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setBorder(new EmptyBorder(0, -8, 10, 0));
        spectateButton = new ThemedButton("Spectate Live Matches", false);
        spectateButton.setPreferredSize(new Dimension(210, 34));
        spectateButton.setVisible(false);
        spectateButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { SpectateDialog.show(LeaderboardPanel.this, selectedGameId); }
        });
        buttons.add(spectateButton);
        replaysButton = new ThemedButton("My Replays", false);
        replaysButton.setPreferredSize(new Dimension(140, 34));
        replaysButton.setVisible(false);
        replaysButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { ReplayBrowserDialog.show(LeaderboardPanel.this); }
        });
        buttons.add(replaysButton);
        top.add(buttons);
        right.add(top, BorderLayout.NORTH);

        entriesList.setOpaque(false);
        entriesList.setLayout(new BoxLayout(entriesList, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(entriesList);
        scroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        right.add(scroll, BorderLayout.CENTER);
        resultsCard = new SectionCard("TOP PLAYERS").content(right);

        JPanel layout = new JPanel(new BorderLayout(16, 0));
        layout.setOpaque(false);
        layout.add(gamesCard, BorderLayout.WEST);
        layout.add(resultsCard, BorderLayout.CENTER);
        setBody(layout);

        populateGames();
    }

    /** One clickable row in the game list; highlighted while it's the selected game. */
    private class GameItem extends JPanel
    {
        final String gameId;
        final String name;
        boolean selected;
        boolean hover;

        GameItem(final String gameId, final String name, final boolean rated)
        {
            this.gameId = gameId;
            this.name = name;
            setOpaque(false);
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(7, 12, 7, 10));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(4000, 34));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            JLabel label = new ThemedLabel(name, ThemeColor.TEXT_PRIMARY);
            label.setFont(UITheme.FONT_BODY);
            add(label, BorderLayout.CENTER);
            if (rated)
            {
                JLabel tag = new ThemedLabel("ELO", ThemeColor.ACCENT);
                tag.setFont(UITheme.FONT_SMALL.deriveFont(10f));
                add(tag, BorderLayout.EAST);
            }
            addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e) { selectGame(gameId, name); }
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            if (selected || hover)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.applyAntialiasing(g2);
                Color c = ThemeManager.getColor(selected ? ThemeColor.ACCENT : ThemeColor.BG_PANEL_HOVER);
                g2.setColor(selected ? new Color(c.getRed(), c.getGreen(), c.getBlue(), 40) : c);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                if (selected)
                {
                    g2.setColor(ThemeManager.getColor(ThemeColor.ACCENT));
                    g2.fillRoundRect(0, 6, 3, getHeight() - 12, 3, 3);
                }
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    private void populateGames()
    {
        List<GameInfo> games = GameManager.getCachedGames();
        String first = null;
        String firstName = null;
        // rated games first - they're the ones with a real ladder
        for (int pass = 0; pass < 2; pass++)
        {
            for (int i = 0; i < games.size(); i++)
            {
                GameInfo game = games.get(i);
                boolean rated = RATED_GAMES.contains(game.getGameId());
                if (game.isComingSoon() || rated != (pass == 0))
                {
                    continue;
                }
                GameItem item = new GameItem(game.getGameId(), game.getName(), rated);
                items.add(item);
                gameList.add(item);
                if (first == null)
                {
                    first = game.getGameId();
                    firstName = game.getName();
                }
            }
        }
        if (first != null)
        {
            selectGame(first, firstName);
        }
    }

    private void selectGame(final String gameId, String gameName)
    {
        selectedGameId = gameId;
        for (GameItem item : items)
        {
            item.selected = item.gameId.equals(gameId);
            item.repaint();
        }
        resultsCard.setTitle(gameName.toUpperCase() + " - TOP PLAYERS");
        myRankLabel.setText("Loading " + gameName + " leaderboard...");
        boolean spectatableGame = "chess".equals(gameId) || "rock-paper-scissors".equals(gameId) || "battleship".equals(gameId);
        boolean replayableGame = "chess".equals(gameId) || "rock-paper-scissors".equals(gameId) || "battleship".equals(gameId);
        spectateButton.setVisible(spectatableGame);
        replaysButton.setVisible(replayableGame);
        entriesList.removeAll();
        entriesList.revalidate();
        entriesList.repaint();

        final Message request = new Message();
        request.setType(MessageType.LEADERBOARD_REQUEST);
        request.setGameId(gameId);

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                final Message response = NetworkManager.send(request);

                javax.swing.SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (response == null)
                        {
                            if (selectedGameId != null && selectedGameId.equals(gameId))
                            {
                                String connectionIssue = NetworkManager.describeIfNotReady();
                                myRankLabel.setText(connectionIssue != null ? connectionIssue
                                    : "Couldn't load the leaderboard - try again.");
                            }
                            return;
                        }
                        if (!response.isSuccess())
                        {
                            return;
                        }
                        if (selectedGameId == null || !selectedGameId.equals(response.getGameId()))
                        {
                            return;
                        }
                        renderLeaderboard(response);
                    }
                });
            }
        });
        worker.start();
    }

    private void renderLeaderboard(Message message)
    {
        boolean rated = RATED_GAMES.contains(message.getGameId());
        List<String> entries = message.getLeaderboardEntries();

        if (message.getMyRank() > 0)
        {
            myRankLabel.setText("Your rank: #" + message.getMyRank()
                + (rated ? "  (rating " + message.getMyRating() + ")" : "  (best score " + message.getMyRating() + ")"));
        }
        else
        {
            myRankLabel.setText(rated
                ? "Your rating: " + message.getMyRating() + " - not yet ranked in the top 20"
                : "Your best score: " + message.getMyRating() + " - not yet ranked in the top 20");
        }

        entriesList.removeAll();
        if (entries == null || entries.isEmpty())
        {
            entriesList.add(PlaceholderPanel.mutedLabel("No one has played this yet - be the first!"));
        }
        else
        {
            for (int i = 0; i < entries.size(); i++)
            {
                entriesList.add(buildRow(entries.get(i), rated, i));
                entriesList.add(Box.createVerticalStrut(6));
            }
        }
        entriesList.revalidate();
        entriesList.repaint();
    }

    private JPanel buildRow(String entry, boolean rated, int index)
    {
        String[] parts = entry.split("\\|", -1);
        String rank = parts.length > 0 ? parts[0] : "?";
        String username = parts.length > 1 ? parts[1] : "?";
        String value = parts.length > 2 ? parts[2] : "0";
        boolean podium = index < 3;

        RoundedPanel row = new RoundedPanel(ThemeColor.BG_APP, UITheme.RADIUS_BUTTON);
        row.setLayout(new BorderLayout(14, 0));
        row.setBorder(new EmptyBorder(9, 14, 9, 14));
        row.setMaximumSize(new Dimension(4000, 44));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel rankLabel = new ThemedLabel("#" + rank, podium ? ThemeColor.ACCENT : ThemeColor.TEXT_MUTED);
        rankLabel.setFont(podium ? UITheme.FONT_NAV_BOLD : UITheme.FONT_BODY);
        rankLabel.setPreferredSize(new Dimension(40, 20));
        row.add(rankLabel, BorderLayout.WEST);

        JLabel name = new ThemedLabel(username, ThemeColor.TEXT_PRIMARY);
        name.setFont(podium ? UITheme.FONT_NAV_BOLD : UITheme.FONT_BODY);
        row.add(name, BorderLayout.CENTER);

        String rightText;
        if (rated && parts.length >= 6)
        {
            rightText = value + " rating   (" + parts[3] + "W " + parts[4] + "L " + parts[5] + "D)";
        }
        else
        {
            rightText = rated ? value + " rating" : value + " pts";
        }

        JLabel right = new ThemedLabel(rightText, podium ? ThemeColor.TEXT_PRIMARY : ThemeColor.TEXT_MUTED);
        right.setFont(UITheme.FONT_SMALL);
        row.add(right, BorderLayout.EAST);

        return row;
    }
}
