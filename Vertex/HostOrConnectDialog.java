import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * HostOrConnectDialog
 * --------------------
 * The very first screen after the splash - choose whether this
 * instance hosts its own local server (starting a real GameServer
 * in-process, same engine ServerMain uses, just launched from the
 * ordinary client entry point instead) or connects out to an
 * existing one via ConnectDialog. Hosting skips ConnectDialog
 * entirely - once you've started your own server, there's nothing
 * to ask, you're obviously connecting to yourself.
 */
public class HostOrConnectDialog
{
    private HostOrConnectDialog()
    {
        // Static utility class - never instantiated.
    }

    public static void show(final Runnable onReady)
    {
        final JDialog dialog = new JDialog((JFrame) null, "Vertex", true);
        dialog.setUndecorated(true);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.enableTopAccent();
        root.setBorder(new EmptyBorder(24, 24, 20, 24));
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JLabel title = new JLabel("Host or Connect?");
        title.setFont(UITheme.FONT_HEADING.deriveFont(18f));
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(title);

        JLabel hint = new JLabel("<html><body style='width:280px'>Host a server here for others to join, or connect to a server someone else is already running.</body></html>");
        hint.setFont(UITheme.FONT_SMALL);
        hint.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setBorder(new EmptyBorder(6, 0, 18, 0));
        root.add(hint);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttonRow.setOpaque(false);
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        ThemedButton hostButton = new ThemedButton("Host a Server", true);
        hostButton.setPreferredSize(new Dimension(140, 40));
        hostButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                dialog.dispose();
                startHosting(onReady);
            }
        });
        buttonRow.add(hostButton);

        ThemedButton connectButton = new ThemedButton("Connect to a Server", false);
        connectButton.setPreferredSize(new Dimension(160, 40));
        connectButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                dialog.dispose();
                ConnectDialog.show(onReady);
            }
        });
        buttonRow.add(connectButton);

        root.add(buttonRow);

        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    /** Asks which port to host on (same pattern ServerMain uses standalone), starts a real GameServer in-process, points NetworkConfig at "localhost" + that port, then proceeds - no ConnectDialog needed, since hosting means connecting to yourself. */
    private static void startHosting(Runnable onReady)
    {
        GameServer server = new GameServer();

        String mainAddress = JOptionPane.showInputDialog(null,
            "Is this the MAIN server, or a satellite of another one?\n"
            + "Leave blank if this IS the main server.\n"
            + "Otherwise, enter the main server's address (host:port) to sync with.",
            "Vertex - Main Server", JOptionPane.QUESTION_MESSAGE);

        if (mainAddress != null && !mainAddress.trim().isEmpty())
        {
            String[] parsedMain = parseAddress(mainAddress.trim());
            if (parsedMain == null)
            {
                JOptionPane.showMessageDialog(null, "Use the format host:port for the main server, e.g. 192.168.1.10:7777.",
                    "Vertex - Invalid Address", JOptionPane.ERROR_MESSAGE);
                show(onReady);
                return;
            }
            server.setMainServer(parsedMain[0], Integer.parseInt(parsedMain[1]));
        }
        else if (!authorizeAsMainServer())
        {
            // Cancelled, or the password was wrong - don't silently fall back to
            // hosting as main anyway. Just stop; the person can try again.
            return;
        }

        boolean started = server.start();
        if (!started)
        {
            JOptionPane.showMessageDialog(null,
                "Could not start a server on that port - is something else already using it?",
                "Vertex - Failed to Host", JOptionPane.ERROR_MESSAGE);
            show(onReady);
            return;
        }

        NetworkConfig.setServerHost("localhost");
        onReady.run();
    }

    /** Gates who can start THIS installation as the main server. First time (no lock file yet) is open - sets a password right then. Every time after that requires the same password. Returns false (and shows nothing further) if the person cancels or gets it wrong, so the caller can just stop rather than silently falling back to hosting as main anyway. */
    private static boolean authorizeAsMainServer()
    {
        if (!MainServerLock.isEstablished())
        {
            JLabel message = new JLabel("<html><body style='width:260px'>No main server has been set up on this installation yet. Set a password now to protect it - you'll need this password to start as the main server again later.</body></html>");
            JPasswordField newPasswordField = new JPasswordField();
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.add(message);
            panel.add(javax.swing.Box.createVerticalStrut(10));
            panel.add(newPasswordField);

            int choice = JOptionPane.showConfirmDialog(null, panel, "Set Main Server Password", JOptionPane.OK_CANCEL_OPTION);
            if (choice != JOptionPane.OK_OPTION)
            {
                return false;
            }
            String newPassword = new String(newPasswordField.getPassword());
            if (newPassword.length() < 6)
            {
                JOptionPane.showMessageDialog(null, "Password must be at least 6 characters.",
                    "Vertex - Invalid Password", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            MainServerLock.establish(newPassword);
            return true;
        }

        JPasswordField passwordField = new JPasswordField();
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel("This installation already has a main server password set."));
        panel.add(javax.swing.Box.createVerticalStrut(10));
        panel.add(passwordField);

        int choice = JOptionPane.showConfirmDialog(null, panel, "Main Server Password", JOptionPane.OK_CANCEL_OPTION);
        if (choice != JOptionPane.OK_OPTION)
        {
            return false;
        }
        String password = new String(passwordField.getPassword());
        if (!MainServerLock.verify(password))
        {
            JOptionPane.showMessageDialog(null, "Incorrect main server password.",
                "Vertex - Access Denied", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
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
