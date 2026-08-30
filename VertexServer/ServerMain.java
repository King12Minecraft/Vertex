import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class ServerMain
{
    public static void main(String[] args)
    {
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
}
