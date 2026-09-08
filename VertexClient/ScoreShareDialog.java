import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * ScoreShareDialog
 * ----------------
 * Two ways to share a result (a high score, a win, a placement -
 * whatever text the caller builds): copy it to the clipboard, or send
 * it straight to a friend as a private message. Same friend-fetching
 * approach as FriendPickerDialog (blocking send() on a background
 * thread, not sendAsync()+PushListener - see that class's own javadoc
 * for why that distinction matters here), just generalized to send a
 * plain chat message instead of a party invite.
 */
public class ScoreShareDialog
{
    private final JDialog dialog;
    private final JPanel friendList;
    private final String shareText;

    private ScoreShareDialog(Component anchor, String shareText)
    {
        this.shareText = shareText;
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(anchor);
        dialog = new JDialog(owner, true);
        dialog.setUndecorated(true);
        DialogUtils.enableEscapeToClose(dialog);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        root.setPreferredSize(new Dimension(360, 440));
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(new EmptyBorder(18, 18, 12, 18));

        JLabel title = new JLabel("Share Result");
        title.setFont(UITheme.FONT_NAV_BOLD);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(title);

        RoundedPanel preview = new RoundedPanel(ThemeColor.BG_APP, UITheme.RADIUS_BUTTON);
        preview.setLayout(new BorderLayout());
        preview.setBorder(new EmptyBorder(10, 12, 10, 12));
        preview.setAlignmentX(Component.LEFT_ALIGNMENT);
        preview.setMaximumSize(new Dimension(2000, 60));
        JLabel previewLabel = new JLabel("<html><body style='width:290px'>" + escapeHtml(shareText) + "</body></html>");
        previewLabel.setFont(UITheme.FONT_SMALL);
        previewLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        preview.add(previewLabel, BorderLayout.CENTER);
        top.add(Box.createVerticalStrut(10));
        top.add(preview);
        top.add(Box.createVerticalStrut(10));

        final ThemedButton copyButton = new ThemedButton("Copy to Clipboard", false);
        copyButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        copyButton.setMaximumSize(new Dimension(2000, 36));
        copyButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(ScoreShareDialog.this.shareText), null);
                copyButton.setText("Copied!");
            }
        });
        top.add(copyButton);
        top.add(Box.createVerticalStrut(14));

        JLabel sendLabel = new JLabel("Or send to a friend:");
        sendLabel.setFont(UITheme.FONT_SMALL);
        sendLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        sendLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(sendLabel);

        root.add(top, BorderLayout.NORTH);

        friendList = new JPanel();
        friendList.setOpaque(false);
        friendList.setLayout(new BoxLayout(friendList, BoxLayout.Y_AXIS));
        friendList.setBorder(new EmptyBorder(0, 18, 18, 18));

        JScrollPane scroll = new JScrollPane(friendList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        root.add(scroll, BorderLayout.CENTER);

        JLabel loading = new JLabel("Loading friends...");
        loading.setFont(UITheme.FONT_SMALL);
        loading.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        friendList.add(loading);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);

        fetchInBackground();
    }

    /** shareText is whatever the caller wants shared verbatim - this dialog doesn't build or format it, just offers ways to send it. */
    public static void show(Component anchor, String shareText)
    {
        new ScoreShareDialog(anchor, shareText).dialog.setVisible(true);
    }

    private void fetchInBackground()
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.FRIEND_LIST_REQUEST);
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        render(response == null || !response.isSuccess() ? null : response.getFriendUsernames());
                    }
                });
            }
        });
        worker.start();
    }

    private void render(List<String> friends)
    {
        friendList.removeAll();

        if (!Session.isLoggedIn())
        {
            JLabel note = new JLabel("Log in to send this to a friend.");
            note.setFont(UITheme.FONT_SMALL);
            note.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            friendList.add(note);
        }
        else if (friends == null || friends.isEmpty())
        {
            JLabel empty = new JLabel("You don't have any friends added yet.");
            empty.setFont(UITheme.FONT_SMALL);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            friendList.add(empty);
        }
        else
        {
            for (int i = 0; i < friends.size(); i++)
            {
                final String friend = friends.get(i);
                RoundedPanel row = new RoundedPanel(ThemeColor.BG_APP, UITheme.RADIUS_BUTTON);
                row.setLayout(new BorderLayout());
                row.setBorder(new EmptyBorder(10, 14, 10, 14));
                row.setMaximumSize(new Dimension(2000, 44));
                row.setAlignmentX(Component.LEFT_ALIGNMENT);

                JLabel nameLabel = new JLabel(friend);
                nameLabel.setFont(UITheme.FONT_BODY);
                nameLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
                row.add(nameLabel, BorderLayout.WEST);

                final ThemedButton send = new ThemedButton("Send", true);
                send.setPreferredSize(new Dimension(80, 30));
                send.addActionListener(new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        Message request = new Message();
                        request.setType(MessageType.PRIVATE_MESSAGE);
                        request.setToUsername(friend);
                        request.setChatText(shareText);
                        NetworkManager.sendAsync(request);
                        send.setText("Sent!");
                        send.setEnabled(false);
                    }
                });
                row.add(send, BorderLayout.EAST);

                friendList.add(row);
                friendList.add(Box.createVerticalStrut(6));
            }
        }

        friendList.revalidate();
        friendList.repaint();
    }

    private String escapeHtml(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
