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
 * RematchOfferDialog
 * -------------------
 * Shown to whoever receives a REMATCH_OFFERED push, regardless of what
 * window they currently have open (mirrors GameInviteDialog's role for
 * GAME_INVITE) - matches end and their window disposes, so nothing
 * game-specific is guaranteed to still be alive to catch this.
 * Accepting sends REMATCH_RESPONSE and opens a waiting ChessWindow
 * ready to receive the CHESS_MATCH_FOUND the server sends right after.
 */
public class RematchOfferDialog
{
    private RematchOfferDialog()
    {
        // Static utility class - never instantiated.
    }

    public static void show(Component anchor, final String requesterUsername, final String gameId)
    {
        Frame owner = anchor != null ? (Frame) javax.swing.SwingUtilities.getWindowAncestor(anchor) : null;
        final JDialog dialog = new JDialog(owner, "Rematch?", false);
        dialog.setUndecorated(true);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(20, 24, 8, 24));

        JLabel title = new JLabel("Rematch?");
        title.setFont(UITheme.FONT_HEADING.deriveFont(18f));
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(title);
        body.add(javax.swing.Box.createVerticalStrut(8));

        JLabel message = new JLabel(requesterUsername + " wants a rematch.");
        message.setFont(UITheme.FONT_BODY);
        message.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        message.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(message);

        root.add(body, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonRow.setOpaque(false);
        buttonRow.setBorder(new EmptyBorder(18, 24, 20, 24));

        ThemedButton decline = new ThemedButton("Decline", false);
        decline.setPreferredSize(new Dimension(100, 38));
        decline.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { dialog.dispose(); }
        });

        ThemedButton accept = new ThemedButton("Accept", true);
        accept.setPreferredSize(new Dimension(100, 38));
        accept.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                dialog.dispose();

                Message response = new Message();
                response.setType(MessageType.REMATCH_RESPONSE);
                response.setToUsername(requesterUsername);
                response.setGameId(gameId);
                response.setSuccess(true);
                NetworkManager.sendAsync(response);

                if ("chess".equals(gameId))
                {
                    ChessWindow window = ChessWindow.forRematchWait(requesterUsername);
                    window.setVisible(true);
                }
            }
        });

        buttonRow.add(decline);
        buttonRow.add(accept);
        root.add(buttonRow, BorderLayout.SOUTH);

        dialog.pack();
        if (owner != null)
        {
            dialog.setLocationRelativeTo(owner);
        }
        dialog.setVisible(true);
    }
}
