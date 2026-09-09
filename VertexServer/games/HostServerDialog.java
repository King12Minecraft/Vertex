package games;
import net.GameServer;
import ui.ThemedButton;
import ui.ThemedPasswordField;
import net.MainServerLock;
import ui.ThemedTextField;
import theme.UITheme;
import net.NetworkConfig;
import theme.ThemeManager;
import theme.ThemeColor;
import ui.RoundedPanel;
import ui.DialogUtils;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * HostServerDialog
 * -----------------
 * Themed replacement for the old JOptionPane-based hosting prompts that
 * used to live in HostOrConnectDialog (raw JOptionPane.showInputDialog /
 * a bare JPanel+JPasswordField in a plain confirm dialog - the one part
 * of the app that didn't match the rest of the UI at all). Reachable
 * from Settings -> Hosting Server, after logging in, instead of being
 * the very first thing shown before you can even log in.
 *
 * Starts a real GameServer in-process (same engine ServerMain uses),
 * optionally pointed at a main server to sync with, then hands off to
 * ServerBrowserDialog.switchTo() to reconnect this client to its own
 * freshly-started server and re-authenticate with the already-logged-in
 * account's cached password - no separate login step.
 */
public class HostServerDialog
{
    private HostServerDialog()
    {
        // Static utility class - never instantiated.
    }

    public static void show(final Component anchor)
    {
        final JDialog dialog = new JDialog((JFrame) SwingUtilities.getWindowAncestor(anchor), "Host a Server", true);
        dialog.setUndecorated(true);
        DialogUtils.enableEscapeToClose(dialog);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.enableTopAccent();
        root.setBorder(new EmptyBorder(24, 24, 20, 24));
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JLabel title = new JLabel("Hosting Server");
        title.setFont(UITheme.FONT_HEADING.deriveFont(18f));
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(title);

        JLabel hint = new JLabel(NetworkConfig.SATELLITE_SERVERS_ENABLED
            ? "<html><body style='width:280px'>Starts a real Vertex server on this computer for others to join. Leave the address below blank to host as the MAIN server, or point it at an existing main server's address to host a satellite that stays synced with it.</body></html>"
            : "<html><body style='width:280px'>Starts a real Vertex server on this computer for others to join, as the MAIN server. Satellite hosting (syncing to an existing main server) is temporarily disabled.</body></html>");
        hint.setFont(UITheme.FONT_SMALL);
        hint.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setBorder(new EmptyBorder(6, 0, 18, 0));
        root.add(hint);

        JLabel addressLabel = new JLabel(NetworkConfig.SATELLITE_SERVERS_ENABLED
            ? "Main server address (optional)"
            : "Main server address (satellite hosting disabled)");
        addressLabel.setFont(UITheme.FONT_SMALL);
        addressLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        addressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        addressLabel.setBorder(new EmptyBorder(0, 0, 6, 0));
        root.add(addressLabel);

        final ThemedTextField addressField = new ThemedTextField("host:port, e.g. 192.168.1.10:7777");
        addressField.setAlignmentX(Component.LEFT_ALIGNMENT);
        addressField.setMaximumSize(new Dimension(2000, 38));
        addressField.setEnabled(NetworkConfig.SATELLITE_SERVERS_ENABLED);
        root.add(addressField);
        root.add(javax.swing.Box.createVerticalStrut(16));

        final boolean firstSetup = !MainServerLock.isEstablished();
        JLabel passwordLabel = new JLabel(firstSetup
            ? "Set a main server password (only asked once on this computer)"
            : "Main server password");
        passwordLabel.setFont(UITheme.FONT_SMALL);
        passwordLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        passwordLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        passwordLabel.setBorder(new EmptyBorder(0, 0, 6, 0));
        root.add(passwordLabel);

        final ThemedPasswordField passwordField = new ThemedPasswordField();
        passwordField.setAlignmentX(Component.LEFT_ALIGNMENT);
        passwordField.setMaximumSize(new Dimension(2000, 42));
        root.add(passwordField);
        root.add(javax.swing.Box.createVerticalStrut(6));

        JLabel passwordHint = new JLabel(firstSetup
            ? "This only applies when hosting as MAIN (address left blank)."
            : "Only needed when hosting as MAIN (address left blank).");
        passwordHint.setFont(UITheme.FONT_SMALL.deriveFont(11f));
        passwordHint.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        passwordHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        passwordHint.setBorder(new EmptyBorder(0, 0, 14, 0));
        root.add(passwordHint);

        final JLabel errorLabel = new JLabel(" ");
        errorLabel.setFont(UITheme.FONT_SMALL);
        errorLabel.setForeground(new Color(230, 90, 90));
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        errorLabel.setBorder(new EmptyBorder(0, 0, 8, 0));
        root.add(errorLabel);

        final ThemedButton startButton = new ThemedButton("Start Hosting", true);
        startButton.setPreferredSize(new Dimension(150, 40));

        ActionListener startAction = new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                errorLabel.setText(" ");

                String addressInput = addressField.getValue().trim();
                final String[] mainAddress;
                if (addressInput.isEmpty())
                {
                    mainAddress = null;
                }
                else
                {
                    mainAddress = parseAddress(addressInput);
                    if (mainAddress == null)
                    {
                        errorLabel.setText("Use the format host:port for the main server, e.g. 192.168.1.10:7777.");
                        return;
                    }
                }

                String password = passwordField.getValue();
                if (mainAddress == null)
                {
                    if (firstSetup)
                    {
                        if (password.length() < 6)
                        {
                            errorLabel.setText("Password must be at least 6 characters.");
                            return;
                        }
                        MainServerLock.establish(password);
                    }
                    else if (!MainServerLock.verify(password))
                    {
                        errorLabel.setText("Incorrect main server password.");
                        return;
                    }
                }

                startButton.setEnabled(false);
                startButton.setText("Starting...");

                Thread worker = new Thread(new Runnable()
                {
                    public void run()
                    {
                        GameServer server = new GameServer();
                        if (mainAddress != null)
                        {
                            server.setMainServer(mainAddress[0], Integer.parseInt(mainAddress[1]));
                        }
                        final boolean started = server.start();
                        final int port = NetworkConfig.getServerPort();

                        SwingUtilities.invokeLater(new Runnable()
                        {
                            public void run()
                            {
                                if (!started)
                                {
                                    startButton.setEnabled(true);
                                    startButton.setText("Start Hosting");
                                    errorLabel.setText("Could not start a server on port " + port + " - is something else already using it?");
                                    return;
                                }
                                dialog.dispose();
                                showHostingStartedDialog(anchor, port);
                                ServerBrowserDialog.switchTo(anchor, "localhost", port);
                            }
                        });
                    }
                });
                worker.start();
            }
        };
        startButton.addActionListener(startAction);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(startButton);
        root.add(row);

        dialog.pack();
        dialog.setLocationRelativeTo(anchor);
        dialog.setVisible(true);
    }

    private static String[] parseAddress(String input)
    {
        int colonIndex = input.lastIndexOf(':');
        if (colonIndex <= 0 || colonIndex == input.length() - 1)
        {
            return null;
        }
        String host = input.substring(0, colonIndex);
        String portText = input.substring(colonIndex + 1);
        try
        {
            int port = Integer.parseInt(portText);
            if (port < 1 || port > 65535)
            {
                return null;
            }
            return new String[] { host, String.valueOf(port) };
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /** Shows the address a friend on the same network would actually connect to, with a Copy button - "localhost" (what ServerBrowserDialog switches to right after this, for the host's own connection) only ever works for the host's own machine, so it's useless to hand to someone else. Uses InetAddress.getLocalHost() for a best-effort LAN IP; on an odd network setup this can still come back as a loopback address, so the label is worded as a starting point to check, not a guarantee. Auto-closes after a few seconds so it doesn't block the normal flow into the server browser. */
    private static void showHostingStartedDialog(Component anchor, int port)
    {
        String address;
        try
        {
            address = java.net.InetAddress.getLocalHost().getHostAddress() + ":" + port;
        }
        catch (java.net.UnknownHostException e)
        {
            address = "localhost:" + port;
        }
        final String shareAddress = address;

        Frame owner = anchor != null ? (Frame) SwingUtilities.getWindowAncestor(anchor) : null;
        final JDialog dialog = new JDialog(owner, false);
        dialog.setUndecorated(true);
        DialogUtils.enableEscapeToClose(dialog);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(20, 22, 20, 22));
        root.enableTopAccent();
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JLabel title = new JLabel("Server started!");
        title.setFont(UITheme.FONT_NAV_BOLD);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(title);

        JLabel subtitle = new JLabel("<html><body style='width:260px'>Share this address with friends on the "
            + "same network so they can connect:</body></html>");
        subtitle.setFont(UITheme.FONT_SMALL);
        subtitle.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(6, 0, 12, 0));
        root.add(subtitle);

        RoundedPanel addressBox = new RoundedPanel(ThemeColor.BG_APP, UITheme.RADIUS_BUTTON);
        addressBox.setLayout(new java.awt.BorderLayout());
        addressBox.setBorder(new EmptyBorder(8, 12, 8, 12));
        addressBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        addressBox.setMaximumSize(new Dimension(2000, 40));
        JLabel addressLabel = new JLabel(shareAddress);
        addressLabel.setFont(UITheme.FONT_NAV_BOLD);
        addressLabel.setForeground(ThemeManager.getColor(ThemeColor.ACCENT));
        addressBox.add(addressLabel, java.awt.BorderLayout.CENTER);
        root.add(addressBox);

        final ThemedButton copyButton = new ThemedButton("Copy Address", true);
        copyButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        copyButton.setMaximumSize(new Dimension(2000, 36));
        copyButton.setBorder(new EmptyBorder(12, 0, 0, 0));
        copyButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(shareAddress), null);
                copyButton.setText("Copied!");
            }
        });
        root.add(copyButton);

        dialog.pack();
        if (anchor != null)
        {
            dialog.setLocationRelativeTo(anchor);
        }
        else
        {
            dialog.setLocationRelativeTo(null);
        }
        dialog.setVisible(true);

        javax.swing.Timer autoClose = new javax.swing.Timer(8000, new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { dialog.dispose(); }
        });
        autoClose.setRepeats(false);
        autoClose.start();
    }
}
