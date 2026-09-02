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
 * GamePickerDialog
 * ----------------
 * Reused for both Friends' per-row "Invite" button and Group Chat's
 * "Invite to Game" button - shows every online-capable game, picking
 * one sends a GAME_INVITE to each target username. This doesn't create
 * a private match; it's a nudge for the recipient(s) to go queue up
 * for the same game (Vertex's matchmaking is public-queue based).
 */
public class GamePickerDialog
{
    private GamePickerDialog()
    {
        // Static utility class - never instantiated.
    }

    public static void show(Component anchor, final List<String> targetUsernames)
    {
        Frame owner = (Frame) javax.swing.SwingUtilities.getWindowAncestor(anchor);
        final JDialog dialog = new JDialog(owner, true);
        dialog.setUndecorated(true);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        root.setPreferredSize(new Dimension(340, 380));
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JLabel title = new JLabel("Invite to Play");
        title.setFont(UITheme.FONT_NAV_BOLD);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setBorder(new EmptyBorder(16, 16, 10, 16));
        root.add(title, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(new EmptyBorder(0, 16, 16, 16));

        List<GameInfo> games = GameManager.getCachedGames();
        boolean any = false;
        for (int i = 0; i < games.size(); i++)
        {
            final GameInfo game = games.get(i);
            if (!game.isOnline() || game.isComingSoon())
            {
                continue;
            }
            any = true;

            RoundedPanel row = new RoundedPanel(ThemeColor.BG_APP, UITheme.RADIUS_BUTTON);
            row.setLayout(new BorderLayout());
            row.setBorder(new EmptyBorder(10, 14, 10, 14));
            row.setMaximumSize(new Dimension(2000, 44));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel nameLabel = new JLabel(game.getName());
            nameLabel.setFont(UITheme.FONT_BODY);
            nameLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
            row.add(nameLabel, BorderLayout.WEST);

            ThemedButton pick = new ThemedButton("Invite", true);
            pick.setPreferredSize(new Dimension(80, 30));
            pick.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    dialog.dispose();
                    sendInvites(targetUsernames, game.getGameId());
                }
            });
            row.add(pick, BorderLayout.EAST);

            list.add(row);
            list.add(Box.createVerticalStrut(6));
        }

        if (!any)
        {
            JLabel empty = new JLabel("No online games available right now.");
            empty.setFont(UITheme.FONT_SMALL);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            list.add(empty);
        }

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        root.add(scroll, BorderLayout.CENTER);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static void sendInvites(List<String> targetUsernames, String gameId)
    {
        for (int i = 0; i < targetUsernames.size(); i++)
        {
            Message request = new Message();
            request.setType(MessageType.GAME_INVITE);
            request.setToUsername(targetUsernames.get(i));
            request.setGameId(gameId);
            NetworkManager.sendAsync(request);
        }
        NotificationCenter.add("Invite Sent", "Invited " + targetUsernames.size()
            + (targetUsernames.size() == 1 ? " player" : " players") + " to play.");
    }
}
