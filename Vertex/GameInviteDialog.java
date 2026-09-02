import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * GameInviteDialog
 * ----------------
 * Shown to the recipient of a GAME_INVITE (sent from a Friends row or
 * a Group Chat header). No private matchmaking behind this - clicking
 * Join just opens that game the normal way (mode-select/queue), same
 * as clicking it from the Games page. It's a nudge to go queue up
 * together, not a guaranteed shared match, since Vertex's matchmaking
 * is public-queue based rather than room/lobby based.
 */
public class GameInviteDialog
{
    private GameInviteDialog()
    {
        // Static utility class - never instantiated.
    }

    public static void show(Component anchor, String inviterUsername, String gameId)
    {
        Frame owner = anchor != null ? (Frame) javax.swing.SwingUtilities.getWindowAncestor(anchor) : null;
        final JDialog dialog = new JDialog(owner, "Game Invite", false);
        dialog.setUndecorated(true);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(20, 24, 8, 24));

        JLabel title = new JLabel("Game Invite");
        title.setFont(UITheme.FONT_HEADING.deriveFont(18f));
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(title);
        body.add(javax.swing.Box.createVerticalStrut(8));

        String gameName = resolveGameName(gameId);
        JLabel message = new JLabel(inviterUsername + " invited you to play " + gameName + ".");
        message.setFont(UITheme.FONT_BODY);
        message.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        message.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(message);

        root.add(body, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonRow.setOpaque(false);
        buttonRow.setBorder(new EmptyBorder(18, 24, 20, 24));

        ThemedButton dismiss = new ThemedButton("Dismiss", false);
        dismiss.setPreferredSize(new Dimension(100, 38));
        dismiss.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { dialog.dispose(); }
        });

        ThemedButton join = new ThemedButton("Join", true);
        join.setPreferredSize(new Dimension(100, 38));
        join.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                dialog.dispose();
                launchGame(anchor, gameId);
            }
        });

        buttonRow.add(dismiss);
        buttonRow.add(join);
        root.add(buttonRow, BorderLayout.SOUTH);

        dialog.pack();
        if (owner != null)
        {
            dialog.setLocationRelativeTo(owner);
        }
        dialog.setVisible(true);
    }

    private static String resolveGameName(String gameId)
    {
        java.util.List<GameInfo> games = GameManager.getCachedGames();
        for (int i = 0; i < games.size(); i++)
        {
            if (games.get(i).getGameId().equals(gameId))
            {
                return games.get(i).getName();
            }
        }
        return gameId;
    }

    private static void launchGame(Component anchor, String gameId)
    {
        java.util.List<GameInfo> games = GameManager.getCachedGames();
        for (int i = 0; i < games.size(); i++)
        {
            if (games.get(i).getGameId().equals(gameId))
            {
                GameLauncher.launch(anchor, games.get(i));
                return;
            }
        }
    }
}
