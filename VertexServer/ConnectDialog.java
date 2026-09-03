import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * ConnectDialog
 * -------------
 * Opt-in, not shown automatically - reachable from AuthWindow's "Change
 * Server" link if the default (localhost:7777, or whatever was last
 * used) isn't the server the person wants. Lets them type in "host:port"
 * for whichever server they want to play on instead (a friend's, their
 * own on a custom port, etc.). Sets NetworkConfig's host/port, then runs
 * the given callback either way - a blank entry falls back to whatever's
 * already set rather than blocking the person from continuing.
 */
public class ConnectDialog
{
    private ConnectDialog()
    {
        // Static utility class - never instantiated.
    }

    public static void show(final Runnable onConnect)
    {
        final JDialog dialog = new JDialog((JFrame) null, "Connect to Server", true);
        dialog.setUndecorated(true);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.enableTopAccent();
        root.setBorder(new EmptyBorder(24, 24, 20, 24));
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JLabel title = new JLabel("Connect to Server");
        title.setFont(UITheme.FONT_HEADING.deriveFont(18f));
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(title);

        JLabel hint = new JLabel("Enter the server's address, e.g. localhost:7777 or a friend's IP:port.");
        hint.setFont(UITheme.FONT_SMALL);
        hint.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setBorder(new EmptyBorder(6, 0, 16, 0));
        root.add(hint);

        final ThemedTextField addressField = new ThemedTextField("host:port");
        addressField.setValue(NetworkConfig.getServerHost() + ":" + NetworkConfig.getServerPort());
        addressField.setAlignmentX(Component.LEFT_ALIGNMENT);
        addressField.setMaximumSize(new Dimension(2000, 38));
        root.add(addressField);
        root.add(javax.swing.Box.createVerticalStrut(16));

        final JLabel errorLabel = new JLabel(" ");
        errorLabel.setFont(UITheme.FONT_SMALL);
        errorLabel.setForeground(new Color(230, 90, 90));
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        errorLabel.setBorder(new EmptyBorder(0, 0, 8, 0));
        root.add(errorLabel);

        ActionListener connectAction = new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                String input = addressField.getValue().trim();
                if (input.isEmpty())
                {
                    dialog.dispose();
                    onConnect.run();
                    return;
                }

                int colonIndex = input.lastIndexOf(':');
                if (colonIndex <= 0 || colonIndex == input.length() - 1)
                {
                    errorLabel.setText("Use the format host:port, e.g. localhost:7777.");
                    return;
                }

                String host = input.substring(0, colonIndex);
                String portText = input.substring(colonIndex + 1);
                int port;
                try
                {
                    port = Integer.parseInt(portText);
                }
                catch (NumberFormatException ex)
                {
                    errorLabel.setText("That's not a valid port number.");
                    return;
                }
                if (port < 1 || port > 65535)
                {
                    errorLabel.setText("Port must be between 1 and 65535.");
                    return;
                }

                NetworkConfig.setServerHost(host);
                NetworkConfig.setServerPort(port);
                dialog.dispose();
                onConnect.run();
            }
        };

        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        ThemedButton connectButton = new ThemedButton("Connect", true);
        connectButton.setPreferredSize(new Dimension(110, 38));
        connectButton.addActionListener(connectAction);
        row.add(connectButton);
        root.add(row);

        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }
}
