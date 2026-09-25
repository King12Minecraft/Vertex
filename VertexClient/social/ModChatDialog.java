package social;
import net.NetworkManager;
import net.MessageType;
import net.Message;
import account.Session;
import economy.PlayerColorRegistry;
import ui.ThemedButton;
import ui.ThemedTextField;
import ui.ThemedLabel;
import ui.ThemedScrollBarUI;
import ui.RoundedPanel;
import ui.DialogUtils;
import theme.ThemeManager;
import theme.UITheme;
import theme.ThemeColor;

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
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * ModChatDialog
 * -------------
 * A staff-only chat channel, reachable from ModeratorPanel. Every
 * message (MOD_CHAT_MESSAGE) is gated server-side by
 * ClientHandler.isModeratorOrAdmin() - the actual security boundary -
 * both for sending and for who it gets broadcast to; this dialog being
 * reachable at all is just a convenience gate on top of that (see
 * Sidebar's existing MODERATION nav entry for the same pattern).
 *
 * No message history: this only shows messages sent while the dialog
 * is open, the same limitation general group chats would have without
 * a dedicated history store - acceptable for a first pass at a staff
 * channel, revisit if it turns out mods need to catch up on what they
 * missed.
 */
public class ModChatDialog extends JDialog implements NetworkManager.PushListener
{
    private JPanel messageList;
    private JScrollPane scroll;
    private ThemedTextField inputField;

    public ModChatDialog(Component anchor)
    {
        super((Frame) SwingUtilities.getWindowAncestor(anchor), "Mod Chat", false);
        setUndecorated(true);
        DialogUtils.enableEscapeToClose(this);

        NetworkManager.addPushListener(this);
        addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent e) { NetworkManager.removePushListener(ModChatDialog.this); }
        });

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        root.enableTopAccent();
        root.setPreferredSize(new Dimension(420, 520));
        setContentPane(root);
        getRootPane().setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        root.add(createTitleBar(), BorderLayout.NORTH);

        messageList = new JPanel();
        messageList.setOpaque(false);
        messageList.setLayout(new BoxLayout(messageList, BoxLayout.Y_AXIS));
        messageList.setBorder(new EmptyBorder(4, 12, 4, 12));

        scroll = new JScrollPane(messageList);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        ThemedScrollBarUI.apply(scroll);
        root.add(scroll, BorderLayout.CENTER);

        root.add(createInputRow(), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(anchor);
    }

    private JPanel createTitleBar()
    {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(16, 20, 8, 12));

        JLabel title = new JLabel("Mod Chat");
        title.setFont(UITheme.FONT_HEADING.deriveFont(18f));
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        bar.add(title, BorderLayout.WEST);

        javax.swing.JButton close = new javax.swing.JButton("×");
        close.setFont(UITheme.FONT_NAV_BOLD);
        close.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        close.setFocusPainted(false);
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setOpaque(false);
        close.setPreferredSize(new Dimension(28, 28));
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { dispose(); }
        });
        bar.add(close, BorderLayout.EAST);

        return bar;
    }

    private JPanel createInputRow()
    {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(10, 16, 16, 16));

        inputField = new ThemedTextField("Message staff...");
        row.add(inputField, BorderLayout.CENTER);

        ThemedButton send = new ThemedButton("Send", true);
        send.setPreferredSize(new Dimension(80, 36));
        row.add(send, BorderLayout.EAST);

        ActionListener sendAction = new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { sendMessage(); }
        };
        inputField.addActionListener(sendAction);
        send.addActionListener(sendAction);

        return row;
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
        request.setType(MessageType.MOD_CHAT_MESSAGE);
        request.setChatText(text);
        NetworkManager.sendAsync(request);
    }

    public void onPush(final Message message)
    {
        if (message.getType() != MessageType.MOD_CHAT_MESSAGE)
        {
            return;
        }
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { appendMessage(message); }
        });
    }

    private void appendMessage(Message message)
    {
        RoundedPanel row = new RoundedPanel(ThemeColor.BG_SIDEBAR, 8);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(6, 10, 6, 10));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        Color roleColor = "ADMIN".equals(message.getSenderRole()) ? new Color(230, 90, 90)
            : "MODERATOR".equals(message.getSenderRole()) ? new Color(90, 170, 230) : null;
        Color nameColor = roleColor != null ? roleColor : PlayerColorRegistry.resolve(message.getSenderColorId());
        if (nameColor == null)
        {
            nameColor = ThemeManager.getColor(ThemeColor.TEXT_PRIMARY);
        }

        boolean isMe = message.getUsername() != null && Session.isLoggedIn()
            && message.getUsername().equals(Session.getCurrentAccount().getUsername());

        JLabel nameLabel = new JLabel(isMe ? "You" : message.getUsername());
        nameLabel.setFont(UITheme.FONT_SMALL.deriveFont(11f));
        nameLabel.setForeground(nameColor);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(nameLabel);

        JLabel textLabel = new ThemedLabel(
            "<html><body style='width:280px'>" + escapeHtml(message.getChatText()) + "</body></html>",
            ThemeColor.TEXT_PRIMARY);
        textLabel.setFont(UITheme.FONT_BODY);
        textLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(textLabel);

        row.add(content, BorderLayout.CENTER);
        // BoxLayout idiom: without a capped maximum height, a Y_AXIS box
        // stretches every child to fill unused vertical space rather than
        // letting each row hug its own content - cap it to what this row
        // actually needs, now that its content is fully built.
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));

        messageList.add(row);
        messageList.add(Box.createVerticalStrut(6));
        messageList.revalidate();

        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                javax.swing.JScrollBar bar = scroll.getVerticalScrollBar();
                bar.setValue(bar.getMaximum());
            }
        });
    }

    private static String escapeHtml(String text)
    {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
