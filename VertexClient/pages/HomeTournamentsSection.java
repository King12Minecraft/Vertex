package pages;

import games.GameInfo;
import games.GameManager;
import net.Message;
import net.MessageType;
import net.NetworkManager;
import theme.ThemeColor;
import theme.UITheme;
import ui.ThemedButton;
import ui.ThemedLabel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * HomeTournamentsSection
 * ----------------------
 * Tournaments you can join or that are running right now (up to three; finished ones are left out), with a Join
 * button on those still taking players. An empty list points at the Tournaments page, where one can be started.
 *
 * Data: TOURNAMENT_LIST_RESPONSE, entries "id|gameId|status|playerCount|champion". The server sends it as a direct
 * reply and also broadcasts it on every roster change, which is why it is deliberately NOT in
 * NetworkManager.RESPONSE_TYPES: this section asks with a fire-and-forget request and listens as a PushListener -
 * a blocking send() here would wait out its 10-second timeout and stall every other request (see the note on
 * TournamentsPanel.refreshList). HomePanel holds a reference to each section, which keeps the (weak) listener alive.
 */
public class HomeTournamentsSection extends HomeSectionPanel implements NetworkManager.PushListener
{
    private static final int MAX_SHOWN = 3;

    private final JPanel list = new JPanel();

    public HomeTournamentsSection()
    {
        super("TOURNAMENTS", "");
        setHeaderAction("All tournaments >", new Runnable()
        {
            public void run() { goTo(Pages.TOURNAMENTS); }
        });
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        setBodyContent(list);
        showMessage("Looking for tournaments...");
        NetworkManager.addPushListener(this);
    }

    @Override
    public void refresh()
    {
        Message request = new Message();
        request.setType(MessageType.TOURNAMENT_LIST_REQUEST);
        NetworkManager.sendAsync(request);
    }

    @Override
    public void onPush(final Message message)
    {
        if (message.getType() == MessageType.TOURNAMENT_LIST_RESPONSE)
        {
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run() { render(message.getTournamentEntries()); }
            });
        }
    }

    private void render(List<String> entries)
    {
        // open-for-registration first, then the ones already running
        List<String[]> open = new ArrayList<String[]>();
        List<String[]> running = new ArrayList<String[]>();
        if (entries != null)
        {
            for (String entry : entries)
            {
                String[] parts = entry.split("\\|", -1);
                if (parts.length < 4) continue;
                if ("REGISTRATION".equals(parts[2])) open.add(parts);
                else if ("ROUND_1".equals(parts[2]) || "FINAL".equals(parts[2])) running.add(parts);
            }
        }
        List<String[]> shown = new ArrayList<String[]>(open);
        shown.addAll(running);

        list.removeAll();
        if (shown.isEmpty())
        {
            list.add(text("No tournaments are open right now. Start one and invite a few friends."));
            list.add(Box.createVerticalStrut(10));
            ThemedButton start = new ThemedButton("Start a tournament", true);
            start.setAlignmentX(Component.LEFT_ALIGNMENT);
            start.setPreferredSize(new Dimension(210, 34));
            start.setMaximumSize(new Dimension(210, 34));
            start.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { goTo(Pages.TOURNAMENTS); }
            });
            list.add(start);
        }
        else
        {
            for (int i = 0; i < shown.size() && i < MAX_SHOWN; i++)
            {
                list.add(row(shown.get(i)));
                list.add(Box.createVerticalStrut(8));
            }
            if (shown.size() > MAX_SHOWN)
            {
                JLabel more = new ThemedLabel("+ " + (shown.size() - MAX_SHOWN) + " more", ThemeColor.TEXT_MUTED);
                more.setFont(UITheme.FONT_SMALL);
                more.setAlignmentX(Component.LEFT_ALIGNMENT);
                list.add(more);
            }
        }
        list.revalidate();
        list.repaint();
    }

    private JPanel row(String[] parts)
    {
        final String id = parts[0];
        String gameId = parts[1];
        String status = parts[2];
        String playerCount = parts[3];

        GameInfo info = GameManager.findCachedGame(gameId);
        String gameName = info != null ? info.getName() : gameId;
        boolean registering = "REGISTRATION".equals(status);
        String statusText = registering ? playerCount + "/4 players registered"
            : "ROUND_1".equals(status) ? "Semifinals in progress" : "Final in progress";

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(4000, 44));

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        JLabel name = new ThemedLabel(gameName + " Tournament", ThemeColor.TEXT_PRIMARY);
        name.setFont(UITheme.FONT_BODY);
        JLabel statusLabel = new ThemedLabel(statusText, registering ? ThemeColor.SUCCESS : ThemeColor.TEXT_MUTED);
        statusLabel.setFont(UITheme.FONT_SMALL);
        textCol.add(name);
        textCol.add(statusLabel);
        row.add(textCol, BorderLayout.CENTER);

        if (registering)
        {
            ThemedButton join = new ThemedButton("Join", true);
            join.setPreferredSize(new Dimension(76, 30));
            join.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    Message request = new Message();
                    request.setType(MessageType.TOURNAMENT_JOIN_REQUEST);
                    request.setTournamentId(id);
                    NetworkManager.sendAsync(request);
                }
            });
            row.add(join, BorderLayout.EAST);
        }
        return row;
    }

    private JLabel text(String text)
    {
        JLabel label = new ThemedLabel("<html><table width='300' cellpadding='0' cellspacing='0'><tr><td>" + text + "</td></tr></table></html>",
            ThemeColor.TEXT_MUTED);
        label.setFont(UITheme.FONT_BODY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void showMessage(String message)
    {
        list.removeAll();
        list.add(text(message));
        list.revalidate();
        list.repaint();
    }
}
