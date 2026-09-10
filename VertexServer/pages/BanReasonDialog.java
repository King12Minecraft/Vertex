package pages;
import net.Message;
import net.MessageType;
import net.NetworkManager;
import ui.DialogUtils;
import ui.RoundedPanel;
import ui.ThemedButton;
import ui.ThemedTextField;
import theme.UITheme;
import theme.ThemeColor;
import theme.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * BanReasonDialog
 * ----------------
 * The "ban hammer" - a small dialog asking for a reason before a ban
 * actually goes through, opened from the Ban button on a player's row
 * in the Admin Panel. The reason is kept on record (ModerationManager
 * persists it alongside who banned them and when) and shown to the
 * banned player themselves as part of their disconnect message, not
 * just logged silently for admins only.
 */
public class BanReasonDialog
{
    public static void show(Component anchor, final String username, final Runnable onDone)
    {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(anchor);
        final JDialog dialog = new JDialog(owner, true);
        dialog.setUndecorated(true);
        DialogUtils.enableEscapeToClose(dialog);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        root.setPreferredSize(new Dimension(360, 220));
        root.enableTopAccent();
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(20, 20, 8, 20));

        JLabel title = new JLabel("Ban " + username + "?");
        title.setFont(UITheme.FONT_NAV_BOLD);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(title);

        JLabel subtitle = new JLabel("This disconnects them immediately if they're online, and blocks any future login.");
        subtitle.setFont(UITheme.FONT_SMALL);
        subtitle.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(4, 0, 14, 0));
        body.add(subtitle);

        JLabel reasonLabel = new JLabel("Reason (shown to the banned player):");
        reasonLabel.setFont(UITheme.FONT_SMALL);
        reasonLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        reasonLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(reasonLabel);

        final ThemedTextField reasonField = new ThemedTextField("e.g. Cheating, harassment...");
        reasonField.setAlignmentX(Component.LEFT_ALIGNMENT);
        reasonField.setMaximumSize(new Dimension(2000, 42));
        body.add(reasonField);

        root.add(body, BorderLayout.CENTER);

        final JLabel statusLabel = new JLabel(" ");
        statusLabel.setFont(UITheme.FONT_SMALL);
        statusLabel.setForeground(new java.awt.Color(240, 100, 100));

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonRow.setOpaque(false);
        buttonRow.setBorder(new EmptyBorder(10, 20, 16, 20));

        ThemedButton cancel = new ThemedButton("Cancel", false);
        cancel.setPreferredSize(new Dimension(90, 36));
        cancel.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { dialog.dispose(); }
        });

        final ThemedButton confirm = new ThemedButton("Ban", true);
        confirm.setPreferredSize(new Dimension(90, 36));
        confirm.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                confirm.setEnabled(false);
                final String reason = reasonField.getValue().trim();

                Thread worker = new Thread(new Runnable()
                {
                    public void run()
                    {
                        Message request = new Message();
                        request.setType(MessageType.ADMIN_BAN_REQUEST);
                        request.setTargetUsername(username);
                        request.setChatText(reason);
                        final Message response = NetworkManager.send(request);

                        SwingUtilities.invokeLater(new Runnable()
                        {
                            public void run()
                            {
                                if (response != null && response.isSuccess())
                                {
                                    dialog.dispose();
                                    if (onDone != null) onDone.run();
                                }
                                else
                                {
                                    confirm.setEnabled(true);
                                    statusLabel.setText(response != null && response.getErrorText() != null
                                        ? response.getErrorText() : "Could not reach the server.");
                                }
                            }
                        });
                    }
                });
                worker.start();
            }
        });

        buttonRow.add(statusLabel);
        buttonRow.add(cancel);
        buttonRow.add(confirm);
        root.add(buttonRow, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(anchor);
        dialog.setVisible(true);
    }
}
