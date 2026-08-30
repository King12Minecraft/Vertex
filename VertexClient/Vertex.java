import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Vertex
 * ------
 * Application entry point. Launches the login screen first - MainMenu
 * only opens after a successful login (see AuthWindow, LoginPanel).
 *
 * Also installs a last-resort uncaught exception handler, so that if
 * something throws an exception that isn't already caught closer to
 * where it happened, it shows up as a real dialog instead of silently
 * doing nothing. Plain JOptionPane on purpose - it must work even if
 * something in our own theming code is what's broken.
 */
public class Vertex
{
    public static void main(String[] args)
    {
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
