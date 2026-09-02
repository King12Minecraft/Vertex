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
import java.util.List;

/**
 * SpectateDialog
 * --------------
 * Browses every currently-live match for a game and lets the person
 * join one as a read-only watcher. Chess-only for now, matching the
 * server side (see ClientHandler.handleSpectatableMatches) - other
 * turn-based games would extend the same way later.
 */
public class SpectateDialog implements NetworkManager.PushListener
{
    private final JDialog dialog;
    private final JPanel list;
    private final String gameId;

    private SpectateDialog(Component anchor, String gameId)
    {
        this.gameId = gameId;
        Frame owner = (Frame) javax.swing.SwingUtilities.getWindowAncestor(anchor);
        dialog = new JDialog(owner, true);
        dialog.setUndecorated(true);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        root.setPreferredSize(new Dimension(360, 400));
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JLabel title = new JLabel("Spectate Chess");
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

        NetworkManager.addPushListener(this);

        Message request = new Message();
        request.setType(MessageType.SPECTATABLE_MATCHES_REQUEST);
        request.setGameId(gameId);
        NetworkManager.sendAsync(request);

        JLabel loading = new JLabel("Looking for live matches...");
        loading.setFont(UITheme.FONT_SMALL);
        loading.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        list.add(loading);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
    }

    public static void show(Component anchor, String gameId)
    {
        new SpectateDialog(anchor, gameId).dialog.setVisible(true);
    }

    @Override
    public void onPush(final Message message)
    {
        if (message.getType() != MessageType.SPECTATABLE_MATCHES_RESPONSE || !gameId.equals(message.getGameId()))
        {
            return;
        }
        javax.swing.SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { render(message.getSpectatableMatches()); }
        });
    }

    private void render(List<String> matches)
    {
        NetworkManager.removePushListener(this);
        list.removeAll();

        if (matches == null || matches.isEmpty())
        {
            JLabel empty = new JLabel("No live matches right now - check back soon.");
            empty.setFont(UITheme.FONT_SMALL);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            list.add(empty);
        }
        else
        {
            for (int i = 0; i < matches.size(); i++)
            {
                String[] parts = matches.get(i).split("\\|", -1);
                if (parts.length < 3)
                {
                    continue;
                }
                final String matchId = parts[0];
                final String playerAName = parts[1];
                final String playerBName = parts[2];
                String label = playerAName + " vs " + playerBName;

                RoundedPanel row = new RoundedPanel(ThemeColor.BG_APP, UITheme.RADIUS_BUTTON);
                row.setLayout(new BorderLayout());
                row.setBorder(new EmptyBorder(10, 14, 10, 14));
                row.setMaximumSize(new Dimension(2000, 44));
                row.setAlignmentX(Component.LEFT_ALIGNMENT);

                JLabel nameLabel = new JLabel(label);
                nameLabel.setFont(UITheme.FONT_BODY);
                nameLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
                row.add(nameLabel, BorderLayout.WEST);

                ThemedButton watch = new ThemedButton("Watch", true);
                watch.setPreferredSize(new Dimension(80, 30));
                watch.addActionListener(new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        dialog.dispose();
                        if ("chess".equals(gameId))
                        {
                            ChessWindow window = new ChessWindow(matchId);
                            window.setVisible(true);
                        }
                        else if ("rock-paper-scissors".equals(gameId))
                        {
                            RockPaperScissorsWindow window = RockPaperScissorsWindow.forSpectating(matchId, playerAName, playerBName);
                            window.setVisible(true);
                        }
                        else if ("battleship".equals(gameId))
                        {
                            BattleshipWindow window = BattleshipWindow.forSpectating(matchId, playerAName, playerBName);
                            window.setVisible(true);
                        }
                    }
                });
                row.add(watch, BorderLayout.EAST);

                list.add(row);
                list.add(Box.createVerticalStrut(6));
            }
        }

        list.revalidate();
        list.repaint();
    }
}
