import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * FriendPickerDialog
 * ------------------
 * A small friend-list picker used for party invites - fetches the
 * current friend list the same way FriendsPanel does (FRIEND_LIST_REQUEST),
 * shows each friend with an Invite button, sends PARTY_INVITE_REQUEST
 * on selection. Kept separate from FriendsPanel itself since this is a
 * transient picker, not a persistent page.
 */
public class FriendPickerDialog implements NetworkManager.PushListener
{
    private final JDialog dialog;
    private final JPanel list;

    private FriendPickerDialog(Component anchor)
    {
        Frame owner = (Frame) javax.swing.SwingUtilities.getWindowAncestor(anchor);
        dialog = new JDialog(owner, true);
        dialog.setUndecorated(true);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        root.setPreferredSize(new Dimension(320, 380));
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JLabel title = new JLabel("Invite to Party");
        title.setFont(UITheme.FONT_NAV_BOLD);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setBorder(new EmptyBorder(16, 16, 10, 16));
        root.add(title, BorderLayout.NORTH);

        list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(new EmptyBorder(0, 16, 16, 16));

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        root.add(scroll, BorderLayout.CENTER);

        NetworkManager.addPushListener(this);

        Message request = new Message();
        request.setType(MessageType.FRIEND_LIST_REQUEST);
        NetworkManager.sendAsync(request);

        JLabel loading = new JLabel("Loading friends...");
        loading.setFont(UITheme.FONT_SMALL);
        loading.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        list.add(loading);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
    }

    public static void showForParty(Component anchor)
    {
        new FriendPickerDialog(anchor).dialog.setVisible(true);
    }

    @Override
    public void onPush(final Message message)
    {
        if (message.getType() != MessageType.FRIEND_LIST_RESPONSE)
        {
            return;
        }
        javax.swing.SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { render(message.getFriendUsernames()); }
        });
    }

    private void render(List<String> friends)
    {
        NetworkManager.removePushListener(this);
        list.removeAll();

        if (friends == null || friends.isEmpty())
        {
            JLabel empty = new JLabel("You don't have any friends added yet.");
            empty.setFont(UITheme.FONT_SMALL);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            list.add(empty);
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

                ThemedButton invite = new ThemedButton("Invite", true);
                invite.setPreferredSize(new Dimension(80, 30));
                invite.addActionListener(new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        Message request = new Message();
                        request.setType(MessageType.PARTY_INVITE_REQUEST);
                        request.setToUsername(friend);
                        NetworkManager.sendAsync(request);
                        dialog.dispose();
                    }
                });
                row.add(invite, BorderLayout.EAST);

                list.add(row);
                list.add(Box.createVerticalStrut(6));
            }
        }

        list.revalidate();
        list.repaint();
    }
}
