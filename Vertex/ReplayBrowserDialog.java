import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * ReplayBrowserDialog
 * --------------------
 * Lists every past match the person has played (Chess only, matching
 * ReplayManager's current scope) and opens a ReplayWindow to step
 * through whichever one they pick.
 *
 * Fetches with a blocking NetworkManager.send() on a background thread
 * rather than sendAsync()+PushListener - REPLAY_LIST_RESPONSE and
 * REPLAY_RESPONSE are only ever direct replies, never pushed
 * unprompted, so there's nothing to listen for. (sendAsync()+onPush
 * here used to be able to steal a response meant for someone else's
 * concurrent blocking send() call, since NetworkManager's response
 * queue has no per-request correlation - see AchievementsPanel's note
 * for the full explanation.)
 */
public class ReplayBrowserDialog
{
    private final JDialog dialog;
    private final JPanel list;

    private ReplayBrowserDialog(Component anchor)
    {
        Frame owner = (Frame) javax.swing.SwingUtilities.getWindowAncestor(anchor);
        dialog = new JDialog(owner, true);
        dialog.setUndecorated(true);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        root.setPreferredSize(new Dimension(380, 420));
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JLabel title = new JLabel("My Replays");
        title.setFont(UITheme.FONT_NAV_BOLD);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setBorder(new EmptyBorder(16, 16, 10, 16));
        root.add(title, BorderLayout.NORTH);

        list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(new EmptyBorder(0, 16, 16, 16));

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        root.add(scroll, BorderLayout.CENTER);

        JLabel loading = new JLabel("Loading your matches...");
        loading.setFont(UITheme.FONT_SMALL);
        loading.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        list.add(loading);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);

        fetchListInBackground();
    }

    public static void show(Component anchor)
    {
        new ReplayBrowserDialog(anchor).dialog.setVisible(true);
    }

    private void fetchListInBackground()
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.REPLAY_LIST_REQUEST);
                final Message response = NetworkManager.send(request);

                javax.swing.SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        renderList(response == null || !response.isSuccess() ? null : response.getReplayEntries());
                    }
                });
            }
        });
        worker.start();
    }

    private void renderList(List<String> entries)
    {
        list.removeAll();

        if (entries == null || entries.isEmpty())
        {
            JLabel empty = new JLabel("No finished matches yet.");
            empty.setFont(UITheme.FONT_SMALL);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            list.add(empty);
        }
        else
        {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, h:mm a");
            for (int i = 0; i < entries.size(); i++)
            {
                String[] parts = entries.get(i).split("\\|", -1);
                if (parts.length < 5)
                {
                    continue;
                }
                final String replayId = parts[0];
                final String gameId = parts[1];
                String opponent = parts[2];
                String result = parts[3];
                String date = dateFormat.format(new Date(Long.parseLong(parts[4])));

                RoundedPanel row = new RoundedPanel(ThemeColor.BG_APP, UITheme.RADIUS_BUTTON);
                row.setLayout(new BorderLayout());
                row.setBorder(new EmptyBorder(10, 14, 10, 14));
                row.setMaximumSize(new Dimension(2000, 50));
                row.setAlignmentX(Component.LEFT_ALIGNMENT);

                JPanel textCol = new JPanel();
                textCol.setOpaque(false);
                textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
                JLabel nameLabel = new JLabel(gameName(gameId) + " vs " + opponent + " (" + resultLabel(result) + ")");
                nameLabel.setFont(UITheme.FONT_BODY);
                nameLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
                JLabel dateLabel = new JLabel(date);
                dateLabel.setFont(UITheme.FONT_SMALL);
                dateLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
                textCol.add(nameLabel);
                textCol.add(dateLabel);
                row.add(textCol, BorderLayout.WEST);

                ThemedButton watch = new ThemedButton("View", true);
                watch.setPreferredSize(new Dimension(70, 30));
                watch.addActionListener(new ActionListener()
                {
                    public void actionPerformed(ActionEvent e) { requestReplay(replayId, gameId); }
                });
                row.add(watch, BorderLayout.EAST);

                list.add(row);
                list.add(Box.createVerticalStrut(6));
            }
        }

        list.revalidate();
        list.repaint();
    }

    private String gameName(String gameId)
    {
        if ("chess".equals(gameId)) return "Chess";
        if ("rock-paper-scissors".equals(gameId)) return "Rock Paper Scissors";
        if ("battleship".equals(gameId)) return "Battleship";
        return gameId;
    }

    private String resultLabel(String result)
    {
        if ("DRAW".equals(result)) return "Draw";
        if ("WHITE".equals(result)) return "White won";
        if ("BLACK".equals(result)) return "Black won";
        if ("PLAYER_A".equals(result)) return "Player 1 won";
        if ("PLAYER_B".equals(result)) return "Player 2 won";
        return result;
    }

    private String pendingGameId;

    private void requestReplay(final String replayId, String gameId)
    {
        pendingGameId = gameId;

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.REPLAY_REQUEST);
                request.setReplayId(replayId);
                final Message response = NetworkManager.send(request);

                javax.swing.SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        openReplay(response == null || !response.isSuccess() ? null : response.getReplaySnapshots());
                    }
                });
            }
        });
        worker.start();
    }

    /** Player names aren't re-fetched with the snapshot/round data itself, so viewers use generic labels (White/Black, Player 1/Player 2) rather than adding another round trip. */
    private void openReplay(List<String> snapshots)
    {
        dialog.dispose();
        if (snapshots == null || snapshots.isEmpty())
        {
            return;
        }
        if ("rock-paper-scissors".equals(pendingGameId))
        {
            RPSReplayWindow window = new RPSReplayWindow(snapshots);
            window.setVisible(true);
        }
        else if ("battleship".equals(pendingGameId))
        {
            BattleshipReplayWindow window = new BattleshipReplayWindow(snapshots);
            window.setVisible(true);
        }
        else
        {
            ReplayWindow window = new ReplayWindow(snapshots, "White", "Black");
            window.setVisible(true);
        }
    }
}
