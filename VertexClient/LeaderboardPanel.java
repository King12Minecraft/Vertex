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
 * One page, all games - a row of game-picker chips across the top,
 * and a ranked table below. Rated games (Tic-Tac-Toe, Chess,
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
public class LeaderboardPanel extends RoundedPanel
{
    private static final Set<String> RATED_GAMES = new HashSet<String>(java.util.Arrays.asList(
        "tictactoe-online", "chess", "battleship", "rock-paper-scissors", "fight-arena"));

    private final JPanel chipRow;
    private final JPanel entriesList;
    private final JLabel myRankLabel;
    private ThemedButton spectateButton;
    private ThemedButton replaysButton;
    private String selectedGameId;

    public LeaderboardPanel()
    {
        super(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(24, 24, 24, 24));

        JLabel title = new JLabel("Leaderboards");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setBorder(new EmptyBorder(0, 0, 16, 0));
        add(title, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        chipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        chipRow.setOpaque(false);
        chipRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(chipRow);
        body.add(Box.createVerticalStrut(16));

        myRankLabel = new JLabel(" ");
        myRankLabel.setFont(UITheme.FONT_NAV_BOLD);
        myRankLabel.setForeground(ThemeManager.getColor(ThemeColor.ACCENT));
        myRankLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        myRankLabel.setBorder(new EmptyBorder(0, 0, 12, 0));
        body.add(myRankLabel);

        spectateButton = new ThemedButton("Spectate Live Matches", false);
        spectateButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        spectateButton.setMaximumSize(new Dimension(220, 34));
        spectateButton.setVisible(false);
        spectateButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { SpectateDialog.show(LeaderboardPanel.this, selectedGameId); }
        });
        body.add(spectateButton);
        body.add(Box.createVerticalStrut(8));

        replaysButton = new ThemedButton("My Replays", false);
        replaysButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        replaysButton.setMaximumSize(new Dimension(220, 34));
        replaysButton.setVisible(false);
        replaysButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { ReplayBrowserDialog.show(LeaderboardPanel.this); }
        });
        body.add(replaysButton);
        body.add(Box.createVerticalStrut(12));

        entriesList = new JPanel();
        entriesList.setOpaque(false);
        entriesList.setLayout(new BoxLayout(entriesList, BoxLayout.Y_AXIS));
        entriesList.setAlignmentX(Component.LEFT_ALIGNMENT);

        JScrollPane scroll = new JScrollPane(entriesList);
        scroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        body.add(scroll);

        add(body, BorderLayout.CENTER);

        populateChips();
    }

    private void populateChips()
    {
        List<GameInfo> games = GameManager.getCachedGames();
        for (int i = 0; i < games.size(); i++)
        {
            final GameInfo game = games.get(i);
            if (game.isComingSoon())
            {
                continue;
            }
            ThemedButton chip = new ThemedButton(game.getName(), false);
            chip.setPreferredSize(new Dimension(chip.getPreferredSize().width + 20, 32));
            chip.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { selectGame(game.getGameId(), game.getName()); }
            });
            chipRow.add(chip);
        }
    }

    private void selectGame(String gameId, String gameName)
    {
        selectedGameId = gameId;
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
                        if (response == null || !response.isSuccess())
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
            JLabel empty = new JLabel("No one has played this yet - be the first!");
            empty.setFont(UITheme.FONT_SMALL);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            entriesList.add(empty);
        }
        else
        {
            for (int i = 0; i < entries.size(); i++)
            {
                entriesList.add(buildRow(entries.get(i), rated));
                entriesList.add(Box.createVerticalStrut(4));
            }
        }
        entriesList.revalidate();
        entriesList.repaint();
    }

    private JPanel buildRow(String entry, boolean rated)
    {
        String[] parts = entry.split("\\|", -1);
        String rank = parts.length > 0 ? parts[0] : "?";
        String username = parts.length > 1 ? parts[1] : "?";
        String value = parts.length > 2 ? parts[2] : "0";

        RoundedPanel row = new RoundedPanel(ThemeColor.BG_APP, UITheme.RADIUS_BUTTON);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(10, 14, 10, 14));
        row.setMaximumSize(new Dimension(2000, 44));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel left = new JLabel("#" + rank + "   " + username);
        left.setFont(UITheme.FONT_BODY);
        left.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        row.add(left, BorderLayout.WEST);

        String rightText;
        if (rated && parts.length >= 6)
        {
            rightText = value + " rating   (" + parts[3] + "W " + parts[4] + "L " + parts[5] + "D)";
        }
        else
        {
            rightText = rated ? value + " rating" : value + " pts";
        }

        JLabel right = new JLabel(rightText);
        right.setFont(UITheme.FONT_SMALL);
        right.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        row.add(right, BorderLayout.EAST);

        return row;
    }
}
