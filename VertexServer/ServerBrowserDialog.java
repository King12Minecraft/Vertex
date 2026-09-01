import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * ServerBrowserDialog
 * ---------------------
 * In-app server switcher, opened from Settings - shows which server
 * you're currently on, a short list of previously-used servers for
 * one-click reconnecting, and a field to add a new address. Switching
 * servers is explicitly NOT data-preserving: a different server means
 * a different account database entirely (coins, ELO, friends - none
 * of it carries over), so this confirms before switching and always
 * ends by logging the person out and sending them to a fresh login,
 * the same teardown SettingsPanel's own Log Out button already uses.
 */
public class ServerBrowserDialog
{
    private ServerBrowserDialog()
    {
        // Static utility class - never instantiated.
    }

    public static void show(Component anchor)
    {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(anchor);
        final JDialog dialog = new JDialog(owner, "Switch Server", true);
        dialog.setUndecorated(true);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.enableTopAccent();
        root.setBorder(new EmptyBorder(24, 24, 20, 24));
        root.setPreferredSize(new Dimension(360, 420));
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JLabel title = new JLabel("Switch Server");
        title.setFont(UITheme.FONT_HEADING.deriveFont(18f));
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(title);

        JLabel current = new JLabel("Currently on: " + NetworkConfig.getServerHost() + ":" + NetworkConfig.getServerPort());
        current.setFont(UITheme.FONT_SMALL);
        current.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        current.setAlignmentX(Component.LEFT_ALIGNMENT);
        current.setBorder(new EmptyBorder(6, 0, 4, 0));
        root.add(current);

        JLabel warning = new JLabel("<html><body style='width:280px'>Switching servers logs you out - coins, ELO, and friends don't carry over between different servers.</body></html>");
        warning.setFont(UITheme.FONT_SMALL);
        warning.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        warning.setAlignmentX(Component.LEFT_ALIGNMENT);
        warning.setBorder(new EmptyBorder(0, 0, 16, 0));
        root.add(warning);

        JLabel savedLabel = new JLabel("SAVED SERVERS");
        savedLabel.setFont(UITheme.FONT_NAV_BOLD);
        savedLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        savedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        savedLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
        root.add(savedLabel);

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setAlignmentX(Component.LEFT_ALIGNMENT);

        List<String> saved = SavedServersStore.getSaved();
        String currentAddress = NetworkConfig.getServerHost() + ":" + NetworkConfig.getServerPort();
        boolean anyShown = false;
        for (int i = 0; i < saved.size(); i++)
        {
            final String address = saved.get(i);
            if (address.equals(currentAddress))
            {
                continue;
            }
            list.add(buildServerRow(dialog, address));
            list.add(javax.swing.Box.createVerticalStrut(6));
            anyShown = true;
        }
        if (!anyShown)
        {
            JLabel empty = new JLabel("No other servers saved yet.");
            empty.setFont(UITheme.FONT_SMALL);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            list.add(empty);
        }
        root.add(list);
        root.add(javax.swing.Box.createVerticalStrut(16));

        JLabel addLabel = new JLabel("ADD A SERVER");
        addLabel.setFont(UITheme.FONT_NAV_BOLD);
        addLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        addLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        addLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
        root.add(addLabel);

        final ThemedTextField addressField = new ThemedTextField("host:port");
        addressField.setAlignmentX(Component.LEFT_ALIGNMENT);
        addressField.setMaximumSize(new Dimension(2000, 38));
        root.add(addressField);
        root.add(javax.swing.Box.createVerticalStrut(10));

        final JLabel errorLabel = new JLabel(" ");
        errorLabel.setFont(UITheme.FONT_SMALL);
        errorLabel.setForeground(new java.awt.Color(230, 90, 90));
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(errorLabel);

        JPanel bottomRow = new JPanel(new BorderLayout());
        bottomRow.setOpaque(false);
        bottomRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        ThemedButton closeButton = new ThemedButton("Close", false);
        closeButton.setPreferredSize(new Dimension(90, 38));
        closeButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { dialog.dispose(); }
        });
        bottomRow.add(closeButton, BorderLayout.WEST);

        ThemedButton connectButton = new ThemedButton("Connect", true);
        connectButton.setPreferredSize(new Dimension(110, 38));
        connectButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                String[] parsed = parseAddress(addressField.getValue(), errorLabel);
                if (parsed == null)
                {
                    return;
                }
                SavedServersStore.add(parsed[0] + ":" + parsed[1]);
                attemptSwitch(dialog, parsed[0], Integer.parseInt(parsed[1]));
            }
        });
        bottomRow.add(connectButton, BorderLayout.EAST);

        root.add(bottomRow);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static JPanel buildServerRow(final JDialog dialog, final String address)
    {
        RoundedPanel row = new RoundedPanel(ThemeColor.BG_APP, UITheme.RADIUS_BUTTON);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(8, 12, 8, 12));
        row.setMaximumSize(new Dimension(2000, 40));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel addressLabel = new JLabel(address);
        addressLabel.setFont(UITheme.FONT_BODY);
        addressLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        row.add(addressLabel, BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);

        ThemedButton removeButton = new ThemedButton("Remove", false);
        removeButton.setPreferredSize(new Dimension(80, 28));
        removeButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                SavedServersStore.remove(address);
                dialog.dispose();
            }
        });
        buttons.add(removeButton);

        ThemedButton connectButton = new ThemedButton("Connect", true);
        connectButton.setPreferredSize(new Dimension(80, 28));
        connectButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                int colonIndex = address.lastIndexOf(':');
                String host = address.substring(0, colonIndex);
                int port = Integer.parseInt(address.substring(colonIndex + 1));
                attemptSwitch(dialog, host, port);
            }
        });
        buttons.add(connectButton);

        row.add(buttons, BorderLayout.EAST);
        return row;
    }

    private static String[] parseAddress(String input, JLabel errorLabel)
    {
        input = input.trim();
        if (input.isEmpty())
        {
            errorLabel.setText("Enter a server address first.");
            return null;
        }
        int colonIndex = input.lastIndexOf(':');
        if (colonIndex <= 0 || colonIndex == input.length() - 1)
        {
            errorLabel.setText("Use the format host:port, e.g. localhost:7777.");
            return null;
        }
        String host = input.substring(0, colonIndex);
        String portText = input.substring(colonIndex + 1);
        try
        {
            int port = Integer.parseInt(portText);
            if (port < 1 || port > 65535)
            {
                errorLabel.setText("Port must be between 1 and 65535.");
                return null;
            }
            return new String[] { host, String.valueOf(port) };
        }
        catch (NumberFormatException e)
        {
            errorLabel.setText("That's not a valid port number.");
            return null;
        }
    }

    private static void attemptSwitch(JDialog dialog, String host, int port)
    {
        int choice = JOptionPane.showConfirmDialog(dialog,
            "Switch to " + host + ":" + port + "? You'll be logged out of your current session.",
            "Switch Server", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION)
        {
            return;
        }

        boolean connected = NetworkManager.switchServer(host, port);
        if (!connected)
        {
            JOptionPane.showMessageDialog(dialog, "Could not reach " + host + ":" + port + ".",
                "Switch Server - Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Session.logout();
        dialog.dispose();
        Frame owner = (Frame) dialog.getOwner();
        if (owner != null)
        {
            owner.dispose();
        }
        AuthWindow window = new AuthWindow();
        window.setVisible(true);
    }
}
