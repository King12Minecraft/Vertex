import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Vertex
 * ------
 * Application entry point. Launches the login screen first - MainMenu
 * only opens after a successful login (see AuthWindow, LoginPanel).
 *
 * Used to show a "Host or Connect?" prompt (HostOrConnectDialog) before
 * this, on every single launch, before you could even log in. That's
 * gone now - straight from the splash to the login screen, same as
 * ServerMain (the combined server+client entry point) already did.
 * AuthWindow connects to whatever server NetworkConfig already points at
 * (localhost:7777 by default) in the background and shows a live
 * ConnectionIndicator either way, with "Play Offline" if nothing answers
 * - so there's no dead end if nothing's listening yet. Hosting is still
 * available, just moved to Settings -> Hosting Server, reachable once
 * you're actually in (see HostServerDialog) instead of blocking the door.
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
