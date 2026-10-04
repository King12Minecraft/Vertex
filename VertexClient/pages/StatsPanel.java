package pages;

import games.GameInfo;
import games.GameManager;
import net.Message;
import net.MessageType;
import net.NetworkManager;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.PageHeader;
import ui.PlaceholderPanel;
import ui.RoundedPanel;
import ui.ThemedButton;
import ui.ThemedLabel;
import ui.ThemedScrollBarUI;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * StatsPanel
 * ----------
 * A player's stats as a full page (Pages.STATS) with a Back button, the same pattern as the game
 * detail page: reached from the Profile page, or from another player's profile window. Everything
 * shown is recorded by the server (STATS_REQUEST) - plays per game, rating and win/loss/draw for
 * the ranked games, best score for the score games, achievements. What is NOT recorded, and so is
 * not shown: time played, and losses in games that have no rating. The page says so rather than
 * implying a zero.
 */
public class StatsPanel extends PageScaffold
{
    private final String username;   // null = the logged-in player
    private final JPanel content = new JPanel();

    public StatsPanel(String username, final Runnable onBack)
    {
        super(username == null ? "MY STATS" : username.toUpperCase() + " - STATS", "Plays, ratings and records, as recorded by the server.");
        this.username = username;

        ThemedButton back = new ThemedButton("< Back", false);
        back.setPreferredSize(new Dimension(110, 34));
        back.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { onBack.run(); }
        });
        JPanel backWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        backWrap.setOpaque(false);
        backWrap.add(back);
        header().setRightComponent(backWrap);

        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        row(content);

        PlaceholderPanel.show(content, "Loading stats...");
        load();
    }

    private void load()
    {
        final String who = username;
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.STATS_REQUEST);
                request.setUsername(who);
                final Message response = NetworkManager.send(request);
                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run() { render(response); }
                });
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void render(Message response)
    {
        content.removeAll();
        if (response == null || !response.isSuccess())
        {
            String why = response != null && response.getErrorText() != null ? response.getErrorText() : "The server didn't answer.";
            PlaceholderPanel.show(content, "Couldn't load stats - " + why);
            return;
        }

        content.add(summaryRow(response));
        content.add(Box.createVerticalStrut(16));

        List<String> playCounts = response.getStatsPlayCounts() == null ? new ArrayList<String>() : response.getStatsPlayCounts();
        Map<String, String[]> rowsByGame = new HashMap<String, String[]>();
        if (response.getStatsGameRows() != null)
        {
            for (String row : response.getStatsGameRows())
            {
                String[] parts = row.split("\\|", -1);
                if (parts.length >= 6) rowsByGame.put(parts[0], parts);
            }
        }

        // One line per game: everything played, plus any game with a rating/score but no counted plays.
        List<String> gameIds = new ArrayList<String>();
        int maxPlays = 1;
        Map<String, Integer> plays = new HashMap<String, Integer>();
        for (String entry : playCounts)
        {
            int colon = entry.lastIndexOf(':');
            String id = entry.substring(0, colon);
            int n = Integer.parseInt(entry.substring(colon + 1));
            plays.put(id, n);
            gameIds.add(id);
            maxPlays = Math.max(maxPlays, n);
        }
        for (String id : rowsByGame.keySet())
        {
            if (!plays.containsKey(id)) { plays.put(id, 0); gameIds.add(id); }
        }

        if (gameIds.isEmpty())
        {
            PlaceholderPanel.show(content, "No games played yet.");
            return;
        }

        JPanel table = new JPanel();
        table.setLayout(new BoxLayout(table, BoxLayout.Y_AXIS));
        table.setOpaque(false);
        table.add(headerRow());
        for (String id : gameIds)
        {
            table.add(gameRow(id, plays.get(id), maxPlays, rowsByGame.get(id)));
        }
        content.add(PageScaffold.fullWidth(new SectionCard("BY GAME").content(table)));

        JLabel note = new ThemedLabel("<html><table width='640'><tr><td>Rating and win/loss/draw are recorded for ranked games and best score for score games. "
            + "Time played, and losses in games without a rating, aren't tracked.</td></tr></table></html>", ThemeColor.TEXT_MUTED);
        note.setFont(UITheme.FONT_SMALL);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        note.setBorder(new EmptyBorder(12, 0, 0, 0));
        content.add(note);
        content.revalidate();
        content.repaint();
    }

    private JPanel summaryRow(Message r)
    {
        JPanel grid = new JPanel(new GridLayout(1, 0, 14, 0));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension(4000, 84));
        grid.add(bigStat("Games played", String.valueOf(r.getStatsTotalPlays())));
        int games = r.getStatsPlayCounts() == null ? 0 : r.getStatsPlayCounts().size();
        grid.add(bigStat("Different games", String.valueOf(games)));
        grid.add(bigStat("Achievements", String.valueOf(r.getStatsAchievementCount())));
        return grid;
    }

    private RoundedPanel bigStat(String label, String value)
    {
        RoundedPanel stat = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        stat.setLayout(new BoxLayout(stat, BoxLayout.Y_AXIS));
        stat.setBorder(new EmptyBorder(14, 18, 14, 18));
        JLabel v = new ThemedLabel(value, ThemeColor.ACCENT);
        v.setFont(UITheme.FONT_HEADING);
        v.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel l = new ThemedLabel(label, ThemeColor.TEXT_MUTED);
        l.setFont(UITheme.FONT_SMALL);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        stat.add(v);
        stat.add(l);
        return stat;
    }

    private static final int[] COL_WIDTHS = { 190, 170, 70, 90, 90 };

    private JPanel headerRow()
    {
        return row(new String[] { "GAME", "PLAYS", "RATING", "W - L - D", "BEST" }, ThemeColor.TEXT_MUTED, UITheme.FONT_SMALL, null, 0);
    }

    private JPanel gameRow(String gameId, int plays, int maxPlays, String[] stat)
    {
        String rating = stat == null || stat[1].isEmpty() ? "-" : stat[1];
        String record = stat == null || stat[1].isEmpty() ? "-" : stat[2] + " - " + stat[3] + " - " + stat[4];
        String best = stat == null || "0".equals(stat[5]) ? "-" : stat[5];
        return row(new String[] { nameOf(gameId), String.valueOf(plays), rating, record, best },
            ThemeColor.TEXT_PRIMARY, UITheme.FONT_BODY, Integer.valueOf(plays), maxPlays);
    }

    /** One table row. When barValue is given, the PLAYS cell also shows a bar scaled to the most-played game. */
    private JPanel row(String[] cells, final ThemeColor color, java.awt.Font font, final Integer barValue, final int barMax)
    {
        JPanel row = new JPanel(null)
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);
                if (barValue != null && barValue > 0)
                {
                    Graphics2D g2 = (Graphics2D) g.create();
                    UITheme.applyAntialiasing(g2);
                    int x = COL_WIDTHS[0];
                    int w = (int) Math.round((COL_WIDTHS[1] - 44) * (barValue / (double) barMax));
                    g2.setColor(ThemeManager.getColor(ThemeColor.ACCENT));
                    g2.fillRoundRect(x + 30, getHeight() / 2 - 3, Math.max(4, w), 6, 6, 6);
                    g2.dispose();
                }
            }
        };
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        int total = 0;
        for (int w : COL_WIDTHS) total += w;
        row.setPreferredSize(new Dimension(total, 30));
        row.setMaximumSize(new Dimension(4000, 30));
        int x = 0;
        for (int i = 0; i < cells.length; i++)
        {
            JLabel label = new ThemedLabel(cells[i], color);
            label.setFont(font);
            label.setBounds(x, 0, COL_WIDTHS[i] - 6, 30);
            row.add(label);
            x += COL_WIDTHS[i];
        }
        return row;
    }

    private static String nameOf(String gameId)
    {
        GameInfo info = GameManager.findCachedGame(gameId);
        return info != null && info.getName() != null ? info.getName() : gameId;
    }
}
