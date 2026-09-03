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

        JLabel hint = new JLabel("<html><body style='width:280px'>Starts a real Vertex server on this computer for others to join. Leave the address below blank to host as the MAIN server, or point it at an existing main server's address to host a satellite that stays synced with it.</body></html>");
        hint.setFont(UITheme.FONT_SMALL);
        hint.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setBorder(new EmptyBorder(6, 0, 18, 0));
        root.add(hint);

        JLabel addressLabel = new JLabel("Main server address (optional)");
        addressLabel.setFont(UITheme.FONT_SMALL);
        addressLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        addressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        addressLabel.setBorder(new EmptyBorder(0, 0, 6, 0));
        root.add(addressLabel);

        final ThemedTextField addressField = new ThemedTextField("host:port, e.g. 192.168.1.10:7777");
        addressField.setAlignmentX(Component.LEFT_ALIGNMENT);
        addressField.setMaximumSize(new Dimension(2000, 38));
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
}
