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
 * NewDirectMessageDialog
 * -----------------------
 * Prompts for a username and opens (or reopens) a DM conversation with
 * them in ChatPanel. Doesn't check the target actually exists or is
 * online - the message just won't be seen by them if they're offline
 * (no persistence yet, per Section 43's backlog note).
 */
public class NewDirectMessageDialog
{
    private NewDirectMessageDialog()
    {
        // Static utility class - never instantiated.
    }

    public static void show(Component anchor, final ChatPanel chatPanel)
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

        JLabel title = new JLabel("New Direct Message");
        title.setFont(UITheme.FONT_HEADING.deriveFont(18f));
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(title);
        body.add(Box.createVerticalStrut(14));

        final ThemedTextField usernameField = new ThemedTextField("Username");
        usernameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        usernameField.setMaximumSize(new Dimension(2000, 42));
        body.add(usernameField);

        final JLabel errorLabel = new JLabel(" ");
        errorLabel.setFont(UITheme.FONT_SMALL);
        errorLabel.setForeground(new Color(240, 100, 100));
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        errorLabel.setBorder(new EmptyBorder(8, 0, 0, 0));
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

        ThemedButton start = new ThemedButton("Start", true);
        start.setPreferredSize(new Dimension(100, 38));
        start.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                String username = usernameField.getValue();
                if (username.length() < 3)
                {
                    errorLabel.setText("Enter a valid username.");
                    return;
                }
                if (Session.isLoggedIn() && username.equalsIgnoreCase(Session.getCurrentAccount().getUsername()))
                {
                    errorLabel.setText("You can't message yourself.");
                    return;
                }
                dialog.dispose();
                chatPanel.openDirectMessage(username);
            }
        });

        buttonRow.add(cancel);
        buttonRow.add(start);
        root.add(buttonRow, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
}
