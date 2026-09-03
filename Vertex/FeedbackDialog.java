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
 * FeedbackDialog
 * ---------------
 * Report a bug or suggest something, right from within the app - not a
 * report about another player (see ReportPlayerDialog for that), a
 * report about Vertex itself. Submits FEEDBACK_SUBMIT_REQUEST; the
 * server keeps it in a real, readable txt file (see FeedbackManager),
 * viewable afterward via FeedbackListDialog - by an admin (every
 * submission) or by whoever sent it (just their own).
 */
public class FeedbackDialog
{
    private FeedbackDialog()
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
        root.enableTopAccent();
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(new LineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(20, 24, 4, 24));

        JLabel title = new JLabel("Report a Bug or Suggest Something");
        title.setFont(UITheme.FONT_HEADING.deriveFont(18f));
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(title);
        body.add(Box.createVerticalStrut(14));

        final ThemedButton bugButton = new ThemedButton("Bug", true);
        bugButton.setPreferredSize(new Dimension(110, 36));
        final ThemedButton suggestionButton = new ThemedButton("Suggestion", false);
        suggestionButton.setPreferredSize(new Dimension(130, 36));

        final boolean[] isBug = { true };
        bugButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                isBug[0] = true;
                bugButton.setPrimary(true);
                suggestionButton.setPrimary(false);
            }
        });
        suggestionButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                isBug[0] = false;
                bugButton.setPrimary(false);
                suggestionButton.setPrimary(true);
            }
        });

        JPanel typeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        typeRow.setOpaque(false);
        typeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        typeRow.add(bugButton);
        typeRow.add(suggestionButton);
        body.add(typeRow);
        body.add(Box.createVerticalStrut(14));

        final ThemedTextArea textArea = new ThemedTextArea(
            "What happened, or what would you like to see?", 6);
        textArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        textArea.setPreferredSize(new Dimension(380, 140));
        textArea.setMaximumSize(new Dimension(2000, 140));
        body.add(textArea);

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
                String text = textArea.getValue();
                if (text.length() < 3)
                {
                    errorLabel.setText("Say a little more first.");
                    return;
                }

                dialog.dispose();
                submitFeedback(isBug[0] ? "BUG" : "SUGGESTION", text);
            }
        });

        buttonRow.add(cancel);
        buttonRow.add(submit);
        root.add(buttonRow, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static void submitFeedback(final String type, final String text)
    {
        final Message request = new Message();
        request.setType(MessageType.FEEDBACK_SUBMIT_REQUEST);
        request.setFeedbackType(type);
        request.setFeedbackText(text);

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                final Message response = NetworkManager.send(request);
                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (response != null && response.isSuccess())
                        {
                            NotificationCenter.add("Thanks!", "Your "
                                + ("BUG".equals(type) ? "bug report" : "suggestion") + " has been sent.");
                        }
                        else
                        {
                            NotificationCenter.add("Feedback", response != null && response.getErrorText() != null
                                ? response.getErrorText() : "Could not send that right now.");
                        }
                    }
                });
            }
        });
        worker.start();
    }
}
