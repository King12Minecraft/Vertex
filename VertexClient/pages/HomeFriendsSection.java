package pages;

import account.Session;
import net.Message;
import net.MessageType;
import net.NetworkManager;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.ThemedButton;
import ui.ThemedLabel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * HomeFriendsSection
 * ------------------
 * Which of your friends are online right now (up to five), each with a Message button that opens the DM. The count
 * ("3 of 12 online") sits under the title; an account with no friends is nudged to the Friends page, and one whose
 * friends are all offline is told so rather than shown a blank card.
 *
 * Data: FRIEND_LIST_REQUEST (friend names plus which are online - the same call the Friends page makes). It is a
 * normal blocking request, so it runs on a background thread in refresh(). Live changes arrive as FRIEND_STATUS_UPDATE
 * and friend-request pushes: this section is a PushListener and simply refreshes when one comes in. (HomePanel keeps
 * a reference to every section, which is what keeps the listener alive - NetworkManager only holds weak references.)
 */
public class HomeFriendsSection extends HomeSectionPanel implements NetworkManager.PushListener
{
    private static final int MAX_SHOWN = 5;

    private final JPanel list = new JPanel();
    private final JLabel countLabel = new ThemedLabel(" ", ThemeColor.TEXT_MUTED);

    public HomeFriendsSection()
    {
        super("FRIENDS ONLINE", "");
        setHeaderAction("All friends >", new Runnable()
        {
            public void run() { goTo(Pages.FRIENDS); }
        });

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        countLabel.setFont(UITheme.FONT_SMALL);
        countLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        countLabel.setBorder(new EmptyBorder(0, 0, 8, 0));
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(countLabel);
        content.add(list);
        setBodyContent(content);

        showMessage("Loading your friends...");
        NetworkManager.addPushListener(this);
    }

    @Override
    public void refresh()
    {
        if (!Session.isLoggedIn())
        {
            countLabel.setText(" ");
            showMessage("Log in to see which friends are online.");
            return;
        }
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.FRIEND_LIST_REQUEST);
                final Message response = NetworkManager.send(request);
                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run() { render(response); }
                });
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void onPush(Message message)
    {
        MessageType type = message.getType();
        if (type == MessageType.FRIEND_STATUS_UPDATE || type == MessageType.FRIEND_ACCEPTED_NOTICE)
        {
            refresh();
        }
    }

    private void render(Message response)
    {
        if (response == null || !response.isSuccess())
        {
            countLabel.setText(" ");
            showMessage("Couldn't load your friends right now.");
            return;
        }
        List<String> friends = response.getFriendUsernames();
        List<String> online = response.getOnlineFriendUsernames();
        int total = friends == null ? 0 : friends.size();
        int onlineCount = online == null ? 0 : online.size();

        list.removeAll();
        if (total == 0)
        {
            countLabel.setText(" ");
            list.add(message("No friends yet - add a few and you'll see who's around here."));
            list.add(Box.createVerticalStrut(10));
            list.add(button("Find friends", Pages.FRIENDS));
        }
        else if (onlineCount == 0)
        {
            countLabel.setText("0 of " + total + " online");
            list.add(message("Nobody is online right now. They'll show up here when they are."));
        }
        else
        {
            countLabel.setText(onlineCount + " of " + total + " online");
            for (int i = 0; i < onlineCount && i < MAX_SHOWN; i++)
            {
                list.add(friendRow(online.get(i)));
                list.add(Box.createVerticalStrut(6));
            }
            if (onlineCount > MAX_SHOWN)
            {
                JLabel more = new ThemedLabel("+ " + (onlineCount - MAX_SHOWN) + " more online", ThemeColor.TEXT_MUTED);
                more.setFont(UITheme.FONT_SMALL);
                more.setAlignmentX(Component.LEFT_ALIGNMENT);
                list.add(more);
            }
        }
        list.revalidate();
        list.repaint();
    }

    private JPanel friendRow(final String username)
    {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(4000, 32));

        JComponent dot = new JComponent()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.applyAntialiasing(g2);
                g2.setColor(ThemeManager.getColor(ThemeColor.SUCCESS));
                g2.fillOval(0, getHeight() / 2 - 4, 9, 9);
                g2.dispose();
            }
        };
        dot.setPreferredSize(new Dimension(12, 32));
        row.add(dot, BorderLayout.WEST);

        JLabel name = new ThemedLabel(username, ThemeColor.TEXT_PRIMARY);
        name.setFont(UITheme.FONT_BODY);
        row.add(name, BorderLayout.CENTER);

        ThemedButton message = new ThemedButton("Message", false);
        message.setPreferredSize(new Dimension(116, 30));
        message.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { MainMenu.openDirectMessage(username); }
        });
        row.add(message, BorderLayout.EAST);
        return row;
    }

    private JLabel message(String text)
    {
        JLabel label = new ThemedLabel("<html><table width='300' cellpadding='0' cellspacing='0'><tr><td>" + text + "</td></tr></table></html>",
            ThemeColor.TEXT_MUTED);
        label.setFont(UITheme.FONT_BODY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private ThemedButton button(String text, final String pageKey)
    {
        ThemedButton b = new ThemedButton(text, true);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setPreferredSize(new Dimension(140, 34));
        b.setMaximumSize(new Dimension(140, 34));
        b.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { goTo(pageKey); }
        });
        return b;
    }

    private void showMessage(String text)
    {
        list.removeAll();
        list.add(message(text));
        list.revalidate();
        list.repaint();
    }
}
