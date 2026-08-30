import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Color;

/**
 * ConnectionIndicator
 * --------------------
 * Shows the current connection state as a colored dot + label. Defaults
 * to OFFLINE since no server exists yet - Phase 5 wires this to the
 * real NetworkManager via setState(...).
 */
public class ConnectionIndicator extends JPanel
{
    private ConnectionState state;
    private final StatusDot dot;
    private final JLabel label;

    public ConnectionIndicator(ConnectionState initialState)
    {
        this.state = initialState;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

        dot = new StatusDot(colorFor(initialState), 8);
        dot.setBorder(new EmptyBorder(0, 0, 0, 8));

        label = new JLabel(textFor(initialState));
        label.setFont(UITheme.FONT_SMALL);
        label.setForeground(colorFor(initialState));

        add(dot);
        add(label);

        ThemeManager.addListener(new Runnable()
        {
            public void run() { refresh(); }
        });
    }

    public void setState(ConnectionState state)
    {
        this.state = state;
        refresh();
    }

    /** Call after any sendAsync - refreshes the "N pending" text if the count changed. */
    public void refreshPendingCount()
    {
        refresh();
    }

    private void refresh()
    {
        dot.setColor(colorFor(state));
        label.setText(textFor(state));
        label.setForeground(colorFor(state));
    }

    private Color colorFor(ConnectionState s)
    {
        switch (s)
        {
            case ONLINE:       return ThemeManager.getColor(ThemeColor.SUCCESS);
            case CONNECTING:
            case RECONNECTING: return ThemeManager.getColor(ThemeColor.ACCENT);
            default:           return ThemeManager.getColor(ThemeColor.TEXT_MUTED);
        }
    }

    private String textFor(ConnectionState s)
    {
        switch (s)
        {
            case ONLINE:       return "Online";
            case CONNECTING:   return "Connecting...";
            case RECONNECTING: return "Reconnecting...";
            default:
                int pending = NetworkManager.getPendingSyncCount();
                return pending > 0 ? "Offline - " + pending + " pending" : "Offline";
        }
    }
}
