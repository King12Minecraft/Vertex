import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
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
import java.util.List;

/**
 * FeedbackListDialog
 * -------------------
 * Shows submitted bug reports/suggestions - the "viewable" half of the
 * feedback feature (see FeedbackDialog for submitting, FeedbackManager
 * for the underlying txt file). An admin opening this sees every
 * submission; anyone else sees only their own, since the server itself
 * already scopes FEEDBACK_LIST_RESPONSE that way (ClientHandler.
 * handleFeedbackList) - this dialog doesn't need its own admin check,
 * it just renders whatever the server decided to send back.
 */
public class FeedbackListDialog
{
    private FeedbackListDialog()
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
        root.setPreferredSize(new Dimension(520, 460));
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(new LineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(20, 24, 10, 24));

        JLabel title = new JLabel(PermissionManager.isAdmin(Session.getCurrentAccount())
            ? "All Feedback" : "Your Feedback");
        title.setFont(UITheme.FONT_HEADING.deriveFont(18f));
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        header.add(title, BorderLayout.WEST);
        root.add(header, BorderLayout.NORTH);

        final JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(new EmptyBorder(0, 24, 0, 20));

        JLabel loading = new JLabel("Loading...");
        loading.setFont(UITheme.FONT_SMALL);
        loading.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        list.add(loading);

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        root.add(scroll, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonRow.setOpaque(false);
        buttonRow.setBorder(new EmptyBorder(14, 24, 20, 24));

        ThemedButton close = new ThemedButton("Close", true);
        close.setPreferredSize(new Dimension(90, 38));
        close.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { dialog.dispose(); }
        });
        buttonRow.add(close);
        root.add(buttonRow, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);

        loadInto(list);
        dialog.setVisible(true);
    }

    private static void loadInto(final JPanel list)
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.FEEDBACK_LIST_REQUEST);
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run() { render(list, response); }
                });
            }
        });
        worker.start();
    }

    private static void render(JPanel list, Message response)
    {
        list.removeAll();

        if (response == null || !response.isSuccess())
        {
            list.add(mutedLabel(response != null && response.getErrorText() != null
                ? response.getErrorText() : "Could not load feedback."));
            list.revalidate();
            list.repaint();
            return;
        }

        List<String> entries = response.getFeedbackEntries();
        if (entries == null || entries.isEmpty())
        {
            list.add(mutedLabel("Nothing here yet."));
        }
        else
        {
            for (int i = 0; i < entries.size(); i++)
            {
                list.add(buildRow(entries.get(i)));
                list.add(Box.createVerticalStrut(8));
            }
        }

        list.revalidate();
        list.repaint();
    }

    private static RoundedPanel buildRow(String description)
    {
        RoundedPanel row = new RoundedPanel(ThemeColor.BG_SIDEBAR, UITheme.RADIUS_PANEL);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(12, 16, 12, 16));
        row.setMaximumSize(new Dimension(2000, 2000));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel textLabel = new JLabel("<html><body style='width:370px'>" + escape(description) + "</body></html>");
        textLabel.setFont(UITheme.FONT_SMALL);
        textLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        row.add(textLabel, BorderLayout.CENTER);

        return row;
    }

    private static String escape(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    private static JLabel mutedLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_SMALL);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        return label;
    }
}
