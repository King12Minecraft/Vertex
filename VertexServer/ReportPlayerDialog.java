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
 * ReportPlayerDialog
 * ------------------
 * Prompts for a username and a reason, then submits REPORT_SUBMIT_REQUEST.
 * The server logs it into ModerationManager's queue for a moderator to
 * review on the Moderation page - see ClientHandler.handleReportSubmit.
 * Same visual pattern as NewDirectMessageDialog, kept themed rather
 * than falling back to a plain JOptionPane.
 */
public class ReportPlayerDialog
{
    private ReportPlayerDialog()
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

        JLabel title = new JLabel("Report a Player");
        title.setFont(UITheme.FONT_HEADING.deriveFont(18f));
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(title);
        body.add(Box.createVerticalStrut(14));

        final ThemedTextField usernameField = new ThemedTextField("Username");
        usernameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        usernameField.setMaximumSize(new Dimension(2000, 42));
        body.add(usernameField);
        body.add(Box.createVerticalStrut(10));

        final ThemedTextField reasonField = new ThemedTextField("Reason");
        reasonField.setAlignmentX(Component.LEFT_ALIGNMENT);
        reasonField.setMaximumSize(new Dimension(2000, 42));
        body.add(reasonField);

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

        ThemedButton submit = new ThemedButton("Submit", true);
        submit.setPreferredSize(new Dimension(100, 38));
        submit.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                final String target = usernameField.getValue().trim();
                final String reason = reasonField.getValue().trim();
                if (target.length() < 3)
                {
                    errorLabel.setText("Enter a valid username.");
                    return;
                }

                dialog.dispose();
                submitReport(target, reason);
            }
        });

        buttonRow.add(cancel);
        buttonRow.add(submit);
        root.add(buttonRow, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static void submitReport(final String target, final String reason)
    {
        final Message request = new Message();
        request.setType(MessageType.REPORT_SUBMIT_REQUEST);
        request.setUsername(target);
        request.setReportReason(reason);

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message response = NetworkManager.send(request);
                if (response != null && response.isSuccess())
                {
                    NotificationCenter.add("Report Submitted", "Your report on " + target + " has been sent to the moderators.");
                }
            }
        });
        worker.start();
    }
}
