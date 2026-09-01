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
 * ChangeUsernameDialog
 * ---------------------
 * Lets the logged-in account change its username. Sends the request to
 * the real server via NetworkManager (Phase 5) - the server
 * re-verifies the current password before accepting a rename, since a
 * network request needs to prove it's really you (unlike the old local
 * file, which only your own logged-in process could ever touch). The
 * account ID never changes (Section 11) - only the display name.
 */
public class ChangeUsernameDialog
{
    private ChangeUsernameDialog()
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

        JLabel title = new JLabel("Change Username");
        title.setFont(UITheme.FONT_HEADING.deriveFont(18f));
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(title);

        JLabel note = new JLabel("Your account ID stays the same - only the name changes.");
        note.setFont(UITheme.FONT_SMALL);
        note.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        note.setBorder(new EmptyBorder(6, 0, 16, 0));
        body.add(note);

        body.add(smallLabel("New username"));
        final ThemedTextField usernameField = new ThemedTextField("");
        usernameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        usernameField.setMaximumSize(new Dimension(2000, 42));
        body.add(usernameField);
        body.add(Box.createVerticalStrut(14));

        body.add(smallLabel("Current password (to confirm it's you)"));
        final ThemedPasswordField passwordField = new ThemedPasswordField();
        passwordField.setAlignmentX(Component.LEFT_ALIGNMENT);
        passwordField.setMaximumSize(new Dimension(2000, 42));
        body.add(passwordField);

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
                final String newUsername = usernameField.getValue();
                final String currentPassword = passwordField.getValue();

                if (newUsername.length() < 3)
                {
                    errorLabel.setText("Username must be at least 3 characters.");
                    return;
                }
                if (currentPassword.isEmpty())
                {
                    errorLabel.setText("Enter your current password to confirm.");
                    return;
                }

                errorLabel.setText("Saving...");
                save.setEnabled(false);

                Thread worker = new Thread(new Runnable()
                {
                    public void run()
                    {
                        Message request = new Message();
                        request.setType(MessageType.CHANGE_USERNAME_REQUEST);
                        request.setUsername(Session.getCurrentAccount().getUsername());
                        request.setCurrentPassword(currentPassword);
                        request.setNewUsername(newUsername);

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
                                    Session.login(response.getAccount(), Session.getCurrentPassword());
                                    dialog.dispose();
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
