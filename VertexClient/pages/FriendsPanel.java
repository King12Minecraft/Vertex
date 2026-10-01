package pages;
import games.GamePickerDialog;
import account.PlayerProfileDialog;
import ui.InitialBadge;
import economy.PinnedFriendsStore;
import theme.ThemeManager;
import ui.GameHubDialog;
import net.MessageType;
import ui.ThemedButton;
import theme.UITheme;
import ui.ThemedScrollBarUI;
import net.NetworkManager;
import ui.PageHeader;
import theme.ThemeColor;
import net.Message;
import ui.ThemedTextField;
import ui.ThemedLabel;
import ui.RoundedPanel;
import ui.PlaceholderPanel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
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
public class FriendsPanel extends PageScaffold implements NetworkManager.PushListener
{
    private JPanel requestsList;
    private SectionCard requestsCard;
    private JPanel friendsList;
    private SectionCard friendsCard;
    private ThemedTextField friendSearchField;
    private List<String> cachedFriends;
    private List<String> cachedOnlineFriends;
    private ThemedTextField addFriendField;

    public FriendsPanel()
    {
        super("FRIENDS", "Add friends, answer requests, and see who is online.");

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        SectionCard addCard = createAddFriendCard();
        requestsCard = createRequestsCard();
        left.add(addCard);
        left.add(Box.createVerticalStrut(16));
        left.add(requestsCard);
        left.add(Box.createVerticalGlue());
        friendsCard = createFriendsCard();

        row(PageScaffold.split(left, friendsCard, 1, 1));

        NetworkManager.addPushListener(this);
        loadFriendData();
    }

    private SectionCard createAddFriendCard()
    {
        JPanel box = new JPanel(new BorderLayout(10, 0));
        box.setOpaque(false);

        addFriendField = new ThemedTextField("Username to add");
        box.add(addFriendField, BorderLayout.CENTER);

        ThemedButton send = new ThemedButton("Add", true);
        send.setPreferredSize(new Dimension(80, 34));
        send.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { sendFriendRequest(); }
        });
        box.add(send, BorderLayout.EAST);

        SectionCard card = new SectionCard("ADD A FRIEND").content(box);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
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
                        else
                        {
                            String connectionIssue = NetworkManager.describeIfNotReady();
                            GameHubDialog.show(FriendsPanel.this, "Add Friend", connectionIssue != null
                                ? connectionIssue : "Couldn't reach the server - try again.");
                        }
                    }
                });
            }
        });
        worker.start();
    }

    private SectionCard createRequestsCard()
    {
        requestsList = new JPanel();
        requestsList.setOpaque(false);
        requestsList.setLayout(new BoxLayout(requestsList, BoxLayout.Y_AXIS));
        SectionCard card = new SectionCard("FRIEND REQUESTS").content(requestsList);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        return card;
    }

    private SectionCard createFriendsCard()
    {
        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));

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

        return new SectionCard("YOUR FRIENDS").content(wrap);
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
                        else if (response == null)
                        {
                            String connectionIssue = NetworkManager.describeIfNotReady();
                            PlaceholderPanel.show(requestsList, connectionIssue != null ? connectionIssue
                                : "Couldn't load friends - try again.");
                            friendsList.removeAll();
                            friendsList.revalidate();
                            friendsList.repaint();
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
        requestsCard.setTitle("FRIEND REQUESTS" + (count > 0 ? " (" + count + ")" : ""));

        requestsList.removeAll();
        if (pending == null || pending.isEmpty())
        {
            requestsList.add(PlaceholderPanel.mutedLabel("No pending requests."));
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
        RoundedPanel row = new RoundedPanel(ThemeColor.BG_APP, UITheme.RADIUS_BUTTON);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(8, 12, 8, 12));
        row.setMaximumSize(new Dimension(2000, 54));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel who = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        who.setOpaque(false);
        who.add(new InitialBadge(username, 32));
        JLabel nameLabel = new ThemedLabel(username, ThemeColor.TEXT_PRIMARY);
        nameLabel.setFont(UITheme.FONT_BODY);
        who.add(nameLabel);
        row.add(who, BorderLayout.WEST);

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
        friendsCard.setTitle("YOUR FRIENDS" + (count > 0 ? " (" + count + ")" : ""));

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
            friendsList.add(PlaceholderPanel.mutedLabel("No friends yet - add one above."));
        }
        else if (visible.isEmpty())
        {
            friendsList.add(PlaceholderPanel.mutedLabel("No friends match \"" + friendSearchField.getValue() + "\"."));
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
        RoundedPanel row = new RoundedPanel(ThemeColor.BG_APP, UITheme.RADIUS_BUTTON);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(8, 12, 8, 12));
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

        left.add(new InitialBadge(username, 32).presence(isOnline));

        JLabel nameLabel = new ThemedLabel(username, ThemeColor.TEXT_PRIMARY);
        nameLabel.setFont(UITheme.FONT_BODY);
        nameLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        nameLabel.addMouseListener(new MouseAdapter()
        {
            public void mouseClicked(MouseEvent e) { PlayerProfileDialog.show(nameLabel, username); }
        });
        left.add(nameLabel);


        row.add(left, BorderLayout.WEST);

        ThemedButton message = new ThemedButton("Message", false);
        message.setPreferredSize(new Dimension(110, 32));
        message.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                MainMenu.openDirectMessage(username);
            }
        });

        ThemedButton invite = new ThemedButton("Invite", false);
        invite.setPreferredSize(new Dimension(90, 32));
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

        row.add(rightWrap, BorderLayout.EAST);

        return row;
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
