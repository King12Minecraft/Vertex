import javax.swing.BorderFactory;
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * FriendChatDialog
 * ----------------
 * A single, focused 1:1 chat with one friend, opened directly from a
 * "Message" button on the Friends page - not routed through the Chat
 * page's channel picker. Reuses the exact same PRIVATE_MESSAGE
 * protocol ChatPanel's DM tab already uses, so this is fully
 * interoperable with it: a message sent from here shows up in Chat's
 * DM tab for the recipient, and vice versa. Same as every DM in
 * GameHub, this is live/session-only - no server-side history, so the
 * dialog always opens with an empty log. Non-modal, so the Friends
 * page stays usable while it's open, and more than one friend's chat
 * can be open at once.
 */
public class FriendChatDialog extends JDialog implements NetworkManager.PushListener
{
    private final String friendUsername;
    private JPanel messageList;
    private JScrollPane scrollPane;
    private ThemedTextField inputField;

    public FriendChatDialog(Component anchor, String friendUsername)
    {
        super((Frame) SwingUtilities.getWindowAncestor(anchor), "Chat with " + friendUsername, false);
        this.friendUsername = friendUsername;

        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setResizable(false);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        root.setPreferredSize(new Dimension(380, 460));
        getRootPane().setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JLabel title = new JLabel(friendUsername);
        title.setFont(UITheme.FONT_NAV_BOLD);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setBorder(new EmptyBorder(16, 16, 10, 16));
        root.add(title, BorderLayout.NORTH);

        messageList = new JPanel();
        messageList.setOpaque(false);
        messageList.setLayout(new BoxLayout(messageList, BoxLayout.Y_AXIS));
        messageList.setBorder(new EmptyBorder(0, 16, 0, 16));

        scrollPane = new JScrollPane(messageList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scrollPane);
        root.add(scrollPane, BorderLayout.CENTER);

        JPanel inputRow = new JPanel(new BorderLayout());
        inputRow.setOpaque(false);
        inputRow.setBorder(new EmptyBorder(10, 16, 16, 16));

        inputField = new ThemedTextField("Message " + friendUsername + "...");
        inputRow.add(inputField, BorderLayout.CENTER);

        final ThemedButton send = new ThemedButton("Send", true);
        send.setPreferredSize(new Dimension(80, 38));
        send.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { sendMessage(); }
        });
        JPanel sendWrap = new JPanel(new BorderLayout());
        sendWrap.setOpaque(false);
        sendWrap.setBorder(new EmptyBorder(0, 8, 0, 0));
        sendWrap.add(send, BorderLayout.CENTER);
        inputRow.add(sendWrap, BorderLayout.EAST);

        root.add(inputRow, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setLocationRelativeTo(anchor);

        NetworkManager.addPushListener(this);
        addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent e) { NetworkManager.removePushListener(FriendChatDialog.this); }
        });
    }

    private void sendMessage()
    {
        String text = inputField.getValue().trim();
        if (text.isEmpty())
        {
            return;
        }
        inputField.clear();

        Message request = new Message();
        request.setType(MessageType.PRIVATE_MESSAGE);
        request.setToUsername(friendUsername);
        request.setChatText(text);
        NetworkManager.sendAsync(request);
        // Not appended locally here - the server echoes every private message
        // back to its sender too (see ClientHandler.handlePrivateMessage), so
        // onPush below is the single source of truth for what actually sent.
    }

    @Override
    public void onPush(final Message message)
    {
        if (message.getType() != MessageType.PRIVATE_MESSAGE)
        {
            return;
        }
        String sender = message.getUsername();
        String toUsername = message.getToUsername();
        boolean relevant = friendUsername.equalsIgnoreCase(sender) || friendUsername.equalsIgnoreCase(toUsername);
        if (!relevant)
        {
            return;
        }

        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                boolean isMe = Session.isLoggedIn() && sender != null
                    && sender.equalsIgnoreCase(Session.getCurrentAccount().getUsername());
                appendMessage(isMe ? "You" : sender, message.getChatText());
            }
        });
    }

    private void appendMessage(String displaySender, String text)
    {
        JLabel line = new JLabel("<html><b>" + displaySender + ":</b> " + escapeHtml(text) + "</html>");
        line.setFont(UITheme.FONT_BODY);
        line.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        line.setBorder(new EmptyBorder(4, 0, 4, 0));
        messageList.add(line);
        messageList.revalidate();
        messageList.repaint();

        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum());
            }
        });
    }

    private String escapeHtml(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
