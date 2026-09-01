import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class ServerMain
{
    public static void main(String[] args)
    {
        if (!promptForPort())
        {
            return;
        }

        GameServer server = new GameServer();
        boolean started = server.start();

        if (!started)
        {
            JOptionPane.showMessageDialog(null,
                "Could not start the server - is the port already in use by another running server?",
                "Vertex Server - Failed to Start", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler()
        {
            public void uncaughtException(Thread t, Throwable e)
            {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null,
                    "An unexpected error occurred:\n\n" + e,
                    "Vertex - Unexpected Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                SplashScreen.showThenRun(new Runnable()
                {
                    public void run()
                    {
                        AuthWindow window = new AuthWindow();
                        window.setVisible(true);
                    }
                });
            }
        });
    }

    /** Asks which port to host on before anything else starts - leaving it blank keeps the default (7777). Returns false if the person cancels, in which case main() should just exit quietly rather than start a server nobody asked for. */
    private static boolean promptForPort()
    {
        String input = JOptionPane.showInputDialog(null,
            "Which port should this server listen on?\n(Leave blank for the default, " + NetworkConfig.getServerPort() + ")",
            "Vertex Server - Choose a Port", JOptionPane.QUESTION_MESSAGE);

        if (input == null)
        {
            return false;
        }
        input = input.trim();
        if (input.isEmpty())
        {
            return true;
        }

        try
        {
            int port = Integer.parseInt(input);
            if (port < 1 || port > 65535)
            {
                JOptionPane.showMessageDialog(null, "Port must be between 1 and 65535.",
                    "Vertex Server - Invalid Port", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            NetworkConfig.setServerPort(port);
            return true;
        }
        catch (NumberFormatException e)
        {
            JOptionPane.showMessageDialog(null, "That's not a valid port number.",
                "Vertex Server - Invalid Port", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
}
