import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * ChangePasswordDialog
 * ---------------------
 * Lets the logged-in account change its password. Sends the request to
 * the real server via NetworkManager (Phase 5) - the server re-verifies
 * the current password itself (Section 46) rather than trusting the
 * client. New password is hashed server-side the same way as at
 * account creation (Section 33) - the client never hashes anything
 * itself anymore.
 */
public class ChangePasswordDialog
{
    private ChangePasswordDialog()
    {
        // Static utility class - never instantiated.
    }

    public static void show(Component anchor)
    {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(anchor);
        final JDialog dialog = new JDialog(owner, true);
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(new LineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(20, 24, 4, 24));

        JLabel title = new JLabel("Change Password");
        title.setFont(UITheme.FONT_HEADING.deriveFont(18f));
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(title);
        body.add(Box.createVerticalStrut(16));

        body.add(smallLabel("Current password"));
        final ThemedPasswordField current = new ThemedPasswordField();
        current.setAlignmentX(Component.LEFT_ALIGNMENT);
        current.setMaximumSize(new Dimension(2000, 42));
        body.add(current);
        body.add(Box.createVerticalStrut(14));

        body.add(smallLabel("New password"));
        final ThemedPasswordField next = new ThemedPasswordField();
        next.setAlignmentX(Component.LEFT_ALIGNMENT);
        next.setMaximumSize(new Dimension(2000, 42));
        body.add(next);
        body.add(Box.createVerticalStrut(14));

        body.add(smallLabel("Confirm new password"));
        final ThemedPasswordField confirm = new ThemedPasswordField();
        confirm.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirm.setMaximumSize(new Dimension(2000, 42));
        body.add(confirm);

        final JLabel errorLabel = new JLabel(" ");
        errorLabel.setFont(UITheme.FONT_SMALL);
        errorLabel.setForeground(new Color(240, 100, 100));
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        errorLabel.setBorder(new EmptyBorder(10, 0, 0, 0));
        body.add(errorLabel);

        root.add(body, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonRow.setOpaque(false);
        buttonRow.setBorder(new EmptyBorder(18, 24, 20, 24));

        ThemedButton cancel = new ThemedButton("Cancel", false);
        cancel.setPreferredSize(new Dimension(90, 38));
        cancel.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { dialog.dispose(); }
        });

        final ThemedButton save = new ThemedButton("Save", true);
        save.setPreferredSize(new Dimension(90, 38));
        save.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                final String currentPassword = current.getValue();
                final String newPassword = next.getValue();
                String confirmPassword = confirm.getValue();

                if (newPassword.length() < 6)
                {
                    errorLabel.setText("New password must be at least 6 characters.");
                    return;
                }
                if (!newPassword.equals(confirmPassword))
                {
                    errorLabel.setText("New passwords don't match.");
                    return;
                }

                errorLabel.setText("Saving...");
                save.setEnabled(false);

                Thread worker = new Thread(new Runnable()
                {
                    public void run()
                    {
                        Message request = new Message();
                        request.setType(MessageType.CHANGE_PASSWORD_REQUEST);
                        request.setUsername(Session.getCurrentAccount().getUsername());
                        request.setCurrentPassword(currentPassword);
                        request.setNewPassword(newPassword);

                        final Message response = NetworkManager.send(request);

                        SwingUtilities.invokeLater(new Runnable()
                        {
                            public void run()
                            {
                                save.setEnabled(true);
                                if (response == null)
                                {
                                    errorLabel.setText("Can't reach the server - is it running?");
                                }
                                else if (response.isSuccess())
                                {
                                    dialog.dispose();
                                    GameHubDialog.show(anchor, "Password Changed", "Your password has been updated.");
                                }
                                else
                                {
                                    errorLabel.setText(response.getErrorText());
                                }
                            }
                        });
                    }
                });
                worker.start();
            }
        });

        buttonRow.add(cancel);
        buttonRow.add(save);
        root.add(buttonRow, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static JLabel smallLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_SMALL);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 6, 0));
        return label;
    }
}
