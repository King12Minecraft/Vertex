import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * FriendsPanel
 * ------------
 * Phase 10 - friend requests (incoming, with accept/decline), the
 * friend list with live online/offline presence, and a simple
 * add-by-username flow. Fully server-driven: FRIEND_LIST_REQUEST for
 * the initial fetch, then FRIEND_REQUEST_RECEIVED / FRIEND_ACCEPTED_NOTICE
 * / FRIEND_STATUS_UPDATE pushes keep it live without polling. On any of
 * those pushes this just refetches the whole list rather than patching
 * individual rows in place - simpler and plenty fast at this scale.
 */
public class FriendsPanel extends RoundedPanel implements NetworkManager.PushListener
{
    private JPanel requestsList;
    private JLabel requestsCountLabel;
    private JPanel friendsList;
    private JLabel friendsCountLabel;
    private ThemedTextField friendSearchField;
    private List<String> cachedFriends;
    private List<String> cachedOnlineFriends;
    private ThemedTextField addFriendField;

    public FriendsPanel()
    {
        super(ThemeColor.BG_APP, 0);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 32, 24, 32));

        add(new PageHeader("FRIENDS"), BorderLayout.NORTH);
        add(createContent(), BorderLayout.CENTER);

        NetworkManager.addPushListener(this);
        loadFriendData();
    }

    private JScrollPane createContent()
    {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(createAddFriendRow());
        content.add(Box.createVerticalStrut(20));
        content.add(createRequestsSection());
        content.add(Box.createVerticalStrut(20));
        content.add(createFriendsSection());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        return scroll;
    }

    private JPanel createAddFriendRow()
    {
        RoundedPanel card = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.setMaximumSize(new Dimension(2000, 66));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.enableTopAccent();

        addFriendField = new ThemedTextField("Username to add");
        card.add(addFriendField, BorderLayout.CENTER);

        ThemedButton send = new ThemedButton("Add Friend", true);
        send.setPreferredSize(new Dimension(130, 34));
        send.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { sendFriendRequest(); }
        });

        JPanel sendWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        sendWrap.setOpaque(false);
        sendWrap.setBorder(new EmptyBorder(0, 12, 0, 0));
        sendWrap.add(send);
        card.add(sendWrap, BorderLayout.EAST);

        return card;
    }

    private void sendFriendRequest()
    {
        String target = addFriendField.getValue().trim();
        if (target.isEmpty())
        {
            return;
        }

        final Message request = new Message();
        request.setType(MessageType.FRIEND_REQUEST_SEND);
        request.setToUsername(target);

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
                            addFriendField.clear();
                            loadFriendData();
                        }
                        else if (response != null)
                        {
                            GameHubDialog.show(FriendsPanel.this, "Add Friend", response.getErrorText());
                        }
                    }
                });
            }
        });
        worker.start();
    }

    private JPanel createRequestsSection()
    {
        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);

        requestsCountLabel = sectionLabel("FRIEND REQUESTS");
        wrap.add(requestsCountLabel);

        requestsList = new JPanel();
        requestsList.setOpaque(false);
        requestsList.setLayout(new BoxLayout(requestsList, BoxLayout.Y_AXIS));
        requestsList.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.add(requestsList);

        return wrap;
    }

    private JPanel createFriendsSection()
    {
        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);

        friendsCountLabel = sectionLabel("YOUR FRIENDS");
        wrap.add(friendsCountLabel);

        friendSearchField = new ThemedTextField("Search friends...");
        friendSearchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        friendSearchField.setMaximumSize(new Dimension(2000, 38));
        friendSearchField.addChangeListener(new Runnable()
        {
            public void run() { renderFriends(cachedFriends, cachedOnlineFriends); }
        });
        wrap.add(friendSearchField);
        wrap.add(Box.createVerticalStrut(10));

        friendsList = new JPanel();
        friendsList.setOpaque(false);
        friendsList.setLayout(new BoxLayout(friendsList, BoxLayout.Y_AXIS));
        friendsList.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.add(friendsList);

        return wrap;
    }

    private JLabel sectionLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_NAV_BOLD);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 12, 0));
        return label;
    }

    private void loadFriendData()
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
                        if (response != null && response.isSuccess())
                        {
                            renderRequests(response.getPendingIncomingUsernames());
                            renderFriends(response.getFriendUsernames(), response.getOnlineFriendUsernames());
                        }
                    }
                });
            }
        });
        worker.start();
    }

    private void renderRequests(List<String> pending)
    {
        int count = pending == null ? 0 : pending.size();
        requestsCountLabel.setText("FRIEND REQUESTS" + (count > 0 ? " (" + count + ")" : ""));

        requestsList.removeAll();
        if (pending == null || pending.isEmpty())
        {
            requestsList.add(mutedLabel("No pending requests."));
        }
        else
        {
            for (int i = 0; i < pending.size(); i++)
            {
                requestsList.add(buildRequestRow(pending.get(i)));
                requestsList.add(Box.createVerticalStrut(8));
            }
        }
        requestsList.revalidate();
        requestsList.repaint();
    }

    private JPanel buildRequestRow(final String username)
    {
        RoundedPanel row = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(12, 16, 12, 16));
        row.setMaximumSize(new Dimension(2000, 54));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nameLabel = new JLabel(username);
        nameLabel.setFont(UITheme.FONT_BODY);
        nameLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        row.add(nameLabel, BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);

        ThemedButton accept = new ThemedButton("Accept", true);
        accept.setPreferredSize(new Dimension(90, 32));
        accept.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { respondToRequest(username, true); }
        });

        ThemedButton decline = new ThemedButton("Decline", false);
        decline.setPreferredSize(new Dimension(90, 32));
        decline.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { respondToRequest(username, false); }
        });

        buttons.add(accept);
        buttons.add(decline);
        row.add(buttons, BorderLayout.EAST);

        return row;
    }

    private void respondToRequest(String requesterUsername, boolean accept)
    {
        Message request = new Message();
        request.setType(accept ? MessageType.FRIEND_ACCEPT_REQUEST : MessageType.FRIEND_DECLINE_REQUEST);
        request.setUsername(requesterUsername);
        NetworkManager.sendAsync(request);
        loadFriendData();
    }

    private void renderFriends(List<String> friends, List<String> onlineFriends)
    {
        cachedFriends = friends;
        cachedOnlineFriends = onlineFriends;

        int count = friends == null ? 0 : friends.size();
        friendsCountLabel.setText("YOUR FRIENDS" + (count > 0 ? " (" + count + ")" : ""));

        String query = friendSearchField != null ? friendSearchField.getValue().toLowerCase() : "";
        List<String> visible = new java.util.ArrayList<String>();
        if (friends != null)
        {
            for (int i = 0; i < friends.size(); i++)
            {
                if (query.isEmpty() || friends.get(i).toLowerCase().contains(query))
                {
                    visible.add(friends.get(i));
                }
            }
        }
        // Stable sort: pinned friends float to the top, everyone else keeps their existing relative order.
        java.util.Collections.sort(visible, new java.util.Comparator<String>()
        {
            public int compare(String a, String b)
            {
                boolean pinnedA = PinnedFriendsStore.isPinned(a);
                boolean pinnedB = PinnedFriendsStore.isPinned(b);
                if (pinnedA == pinnedB) return 0;
                return pinnedA ? -1 : 1;
            }
        });

        friendsList.removeAll();
        if (friends == null || friends.isEmpty())
        {
            friendsList.add(mutedLabel("No friends yet - add one above."));
        }
        else if (visible.isEmpty())
        {
            friendsList.add(mutedLabel("No friends match \"" + friendSearchField.getValue() + "\"."));
        }
        else
        {
            for (int i = 0; i < visible.size(); i++)
            {
                String name = visible.get(i);
                boolean isOnline = onlineFriends != null && onlineFriends.contains(name);
                friendsList.add(buildFriendRow(name, isOnline));
                friendsList.add(Box.createVerticalStrut(8));
            }
        }
        friendsList.revalidate();
        friendsList.repaint();
    }

    private JPanel buildFriendRow(final String username, boolean isOnline)
    {
        RoundedPanel row = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(12, 16, 12, 16));
        row.setMaximumSize(new Dimension(2000, 54));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        final JLabel pinLabel = new JLabel(PinnedFriendsStore.isPinned(username) ? "\u2605" : "\u2606");
        pinLabel.setFont(UITheme.FONT_BODY);
        pinLabel.setForeground(ThemeManager.getColor(PinnedFriendsStore.isPinned(username) ? ThemeColor.ACCENT : ThemeColor.TEXT_MUTED));
        pinLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        pinLabel.setToolTipText(PinnedFriendsStore.isPinned(username) ? "Unpin" : "Pin to top");
        pinLabel.addMouseListener(new MouseAdapter()
        {
            public void mouseClicked(MouseEvent e)
            {
                PinnedFriendsStore.toggle(username);
                renderFriends(cachedFriends, cachedOnlineFriends);
            }
        });
        left.add(pinLabel);

        StatusDot dot = new StatusDot(ThemeManager.getColor(isOnline ? ThemeColor.SUCCESS : ThemeColor.TEXT_MUTED), 9);
        left.add(dot);

        JLabel nameLabel = new JLabel(username);
        nameLabel.setFont(UITheme.FONT_BODY);
        nameLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        left.add(nameLabel);

        JLabel statusLabel = new JLabel(isOnline ? "Online" : "Offline");
        statusLabel.setFont(UITheme.FONT_SMALL);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        left.add(statusLabel);

        row.add(left, BorderLayout.WEST);

        ThemedButton message = new ThemedButton("Message", false);
        message.setPreferredSize(new Dimension(96, 32));
        message.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                new FriendChatDialog(FriendsPanel.this, username).setVisible(true);
            }
        });

        ThemedButton invite = new ThemedButton("Invite", false);
        invite.setPreferredSize(new Dimension(80, 32));
        invite.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                GamePickerDialog.show(FriendsPanel.this, java.util.Collections.singletonList(username));
            }
        });

        JPanel rightWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightWrap.setOpaque(false);
        rightWrap.add(message);
        rightWrap.add(invite);

        if (!isOnline)
        {
            ThemedButton join = new ThemedButton("Join", false);
            join.setPreferredSize(new Dimension(70, 32));
            join.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { attemptJoinFriend(username); }
            });
            rightWrap.add(join);
        }

        row.add(rightWrap, BorderLayout.EAST);

        return row;
    }

    /** Looks up which server (if any) this friend is currently online at, via the main-server presence system (see PresenceRegistry/FRIEND_LOCATION_REQUEST) - main knows the full cross-server picture even if the friend is on a totally different satellite than this one. Offers to switch there if found, using the same seamless re-auth ServerBrowserDialog's switcher uses. */
    private void attemptJoinFriend(final String username)
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.FRIEND_LOCATION_REQUEST);
                request.setUsername(username);
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run() { handleJoinLocation(username, response); }
                });
            }
        });
        worker.start();
    }

    private void handleJoinLocation(String username, Message response)
    {
        String address = response != null ? response.getPresenceAddress() : null;
        if (address == null)
        {
            GameHubDialog.show(this, "Join Friend", username + " doesn't appear to be online right now.");
            return;
        }

        int colonIndex = address.lastIndexOf(':');
        if (colonIndex <= 0)
        {
            return;
        }
        final String host = address.substring(0, colonIndex);
        final int port = Integer.parseInt(address.substring(colonIndex + 1));

        int choice = JOptionPane.showConfirmDialog(this,
            username + " is online at " + address + ". Switch there now?",
            "Join Friend", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION)
        {
            return;
        }

        ServerBrowserDialog.switchTo(this, host, port);
    }

    private JLabel mutedLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_SMALL);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        return label;
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        if (type == MessageType.FRIEND_REQUEST_RECEIVED)
        {
            NotificationCenter.add("Friend Request", message.getUsername() + " wants to be friends.");
            loadFriendData();
        }
        else if (type == MessageType.FRIEND_ACCEPTED_NOTICE)
        {
            NotificationCenter.add("Friend Request Accepted", message.getUsername() + " accepted your friend request.");
            loadFriendData();
        }
        else if (type == MessageType.FRIEND_STATUS_UPDATE)
        {
            loadFriendData();
        }
    }
}
