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
import java.awt.Dimension;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * SatelliteServersPanel
 * -----------------------
 * Admin-only (see Sidebar/PermissionManager.isAdmin) - lists every
 * satellite server that has ever registered itself with this server
 * (meaning THIS server is acting as the main/canonical one - see
 * GameServer.isSatellite()/SatelliteRegistry). Each satellite
 * self-reports once on startup (GameServer.start() ->
 * MainServerConnection.registerAsSatellite()), so this list reflects
 * "servers that have EVER synced here", not necessarily "servers
 * currently online" - there's no live up/down status, just last-seen.
 */
public class SatelliteServersPanel extends RoundedPanel
{
    private final JPanel list;

    public SatelliteServersPanel()
    {
        // Every other full-page panel (GamesPanel, QuestsPanel, FriendsPanel,
        // etc.) extends RoundedPanel so it paints its own themed background
        // across its full bounds; this one was a plain JPanel with no
        // background of its own, so it relied entirely on whatever
        // happened to be painted behind it (MainMenu's contentPanel,
        // previously an unthemed white JPanel - see MainMenu's own fix).
        // That made the whole page look unstyled/broken instead of just
        // an admin list.
        super(ThemeColor.BG_APP, 0);
        setLayout(new BorderLayout());

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(24, 28, 24, 28));

        JLabel title = new JLabel("Satellite Servers");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(title);

        JLabel hint = new JLabel("Every server that has ever synced with this one - not necessarily online right now, just last seen.");
        hint.setFont(UITheme.FONT_SMALL);
        hint.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setBorder(new EmptyBorder(4, 0, 20, 0));
        content.add(hint);

        list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(list);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        add(scroll, BorderLayout.CENTER);

        refresh();
    }

    private void refresh()
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.SATELLITE_LIST_REQUEST);
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run() { render(response); }
                });
            }
        });
        worker.start();
    }

    private void render(Message response)
    {
        list.removeAll();

        if (response == null || !response.isSuccess())
        {
            JLabel error = new JLabel(response != null && response.getErrorText() != null
                ? response.getErrorText() : "Could not load the satellite list.");
            error.setFont(UITheme.FONT_SMALL);
            error.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            list.add(error);
            list.revalidate();
            list.repaint();
            return;
        }

        List<String> entries = response.getSatelliteList();
        if (entries == null || entries.isEmpty())
        {
            JLabel empty = new JLabel("No satellite servers have registered here yet.");
            empty.setFont(UITheme.FONT_SMALL);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            list.add(empty);
        }
        else
        {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, h:mm a");
            for (int i = 0; i < entries.size(); i++)
            {
                String[] parts = entries.get(i).split("\\|", -1);
                if (parts.length < 2)
                {
                    continue;
                }
                String address = parts[0];
                long lastSeenMillis;
                try { lastSeenMillis = Long.parseLong(parts[1]); }
                catch (NumberFormatException e) { continue; }

                list.add(buildRow(address, dateFormat.format(new Date(lastSeenMillis))));
                list.add(Box.createVerticalStrut(8));
            }
        }

        list.revalidate();
        list.repaint();
    }

    private RoundedPanel buildRow(String address, String lastSeenText)
    {
        RoundedPanel row = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(14, 18, 14, 18));
        row.setMaximumSize(new Dimension(2000, 54));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel addressLabel = new JLabel(address);
        addressLabel.setFont(UITheme.FONT_NAV_BOLD);
        addressLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        row.add(addressLabel, BorderLayout.WEST);

        JLabel lastSeenLabel = new JLabel("Last seen: " + lastSeenText);
        lastSeenLabel.setFont(UITheme.FONT_SMALL);
        lastSeenLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        row.add(lastSeenLabel, BorderLayout.EAST);

        return row;
    }
}
