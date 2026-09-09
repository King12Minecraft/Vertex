package pages;
import theme.UITheme;
import games.GameLauncher;
import theme.ThemeManager;
import theme.ThemeColor;
import games.GameManager;
import games.GameInfo;
import net.NetworkManager;
import net.MessageType;
import net.Message;
import net.NavigationListener;
import ui.ThemedTextField;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * GlobalSearchField
 * -----------------
 * A search box in the top bar for jumping straight to a game or a
 * friend without navigating through pages first. Games launch
 * directly (GameLauncher.launch, same as clicking its card anywhere
 * else); friends navigate to the Messages page - opening the exact
 * DM from here would need routing into whichever ChatPanel instance
 * MainMenu currently owns, which isn't exposed to TopBar, so this
 * stops at "take me to Messages" rather than deep-linking the
 * specific conversation.
 */
public class GlobalSearchField extends JPanel
{
    private final ThemedTextField field;
    private final NavigationListener navigationListener;
    private List<String> cachedFriends;
    private JPopupMenu resultsPopup;

    public GlobalSearchField(NavigationListener navigationListener)
    {
        this.navigationListener = navigationListener;
        setOpaque(false);
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(200, 38));

        field = new ThemedTextField("Search games, friends...");
        field.addChangeListener(new Runnable()
        {
            public void run() { onSearchTextChanged(); }
        });
        add(field, BorderLayout.CENTER);

        fetchFriendsOnce();
    }

    /** Called from MainMenu's Ctrl+K binding - hands focus straight to the search text field. */
    public void focusSearchField()
    {
        field.requestFocusInWindow();
    }

    private void fetchFriendsOnce()
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
                        cachedFriends = (response != null && response.isSuccess()) ? response.getFriendUsernames() : new ArrayList<String>();
                    }
                });
            }
        });
        worker.start();
    }

    private void onSearchTextChanged()
    {
        String query = field.getValue().trim().toLowerCase();
        if (query.isEmpty())
        {
            if (resultsPopup != null) resultsPopup.setVisible(false);
            return;
        }
        showResults(query);
    }

    private void showResults(String query)
    {
        List<GameInfo> matchingGames = new ArrayList<GameInfo>();
        for (GameInfo game : GameManager.getCachedGames())
        {
            if (game.getName().toLowerCase().contains(query) && matchingGames.size() < 5)
            {
                matchingGames.add(game);
            }
        }

        List<String> matchingFriends = new ArrayList<String>();
        if (cachedFriends != null)
        {
            for (String friend : cachedFriends)
            {
                if (friend.toLowerCase().contains(query) && matchingFriends.size() < 5)
                {
                    matchingFriends.add(friend);
                }
            }
        }

        if (matchingGames.isEmpty() && matchingFriends.isEmpty())
        {
            if (resultsPopup != null) resultsPopup.setVisible(false);
            return;
        }

        resultsPopup = new JPopupMenu();
        resultsPopup.setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ThemeManager.getColor(ThemeColor.BG_PANEL));
        content.setBorder(new EmptyBorder(6, 0, 6, 0));

        if (!matchingGames.isEmpty())
        {
            content.add(sectionLabel("GAMES"));
            for (final GameInfo game : matchingGames)
            {
                content.add(resultRow(game.getName(), "Game", new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        resultsPopup.setVisible(false);
                        field.clear();
                        GameLauncher.launch(GlobalSearchField.this, game);
                    }
                }));
            }
        }

        if (!matchingFriends.isEmpty())
        {
            content.add(sectionLabel("FRIENDS"));
            for (final String friend : matchingFriends)
            {
                content.add(resultRow(friend, "Friend", new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        resultsPopup.setVisible(false);
                        field.clear();
                        navigationListener.onNavigate(Pages.CHAT);
                    }
                }));
            }
        }

        resultsPopup.add(content);
        resultsPopup.show(field, 0, field.getHeight() + 4);
    }

    private JLabel sectionLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_SMALL);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        label.setBorder(new EmptyBorder(6, 14, 4, 14));
        return label;
    }

    private JPanel resultRow(String primaryText, String tag, ActionListener onClick)
    {
        final JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(true);
        row.setBackground(ThemeManager.getColor(ThemeColor.BG_PANEL));
        row.setBorder(new EmptyBorder(8, 14, 8, 14));
        row.setPreferredSize(new Dimension(240, 34));
        row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

        JLabel nameLabel = new JLabel(primaryText);
        nameLabel.setFont(UITheme.FONT_BODY);
        nameLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        row.add(nameLabel, BorderLayout.WEST);

        JLabel tagLabel = new JLabel(tag);
        tagLabel.setFont(UITheme.FONT_SMALL);
        tagLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        row.add(tagLabel, BorderLayout.EAST);

        row.addMouseListener(new MouseAdapter()
        {
            public void mouseEntered(MouseEvent e) { row.setBackground(ThemeManager.getColor(ThemeColor.BG_SIDEBAR)); }
            public void mouseExited(MouseEvent e)  { row.setBackground(ThemeManager.getColor(ThemeColor.BG_PANEL)); }
            public void mouseClicked(MouseEvent e) { onClick.actionPerformed(null); }
        });

        return row;
    }
}
