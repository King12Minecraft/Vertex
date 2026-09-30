package pages;

import account.Session;
import net.Message;
import net.MessageType;
import net.NetworkManager;

import javax.swing.SwingUtilities;

/**
 * SessionRestorer
 * ---------------
 * The client half of "a dropped connection shouldn't cost you your match". NetworkManager
 * already re-opens the socket by itself after a drop, but the server only knows who a
 * connection belongs to from a login - a fresh socket is anonymous, and the match that is
 * being held for the player (mechanics.ReconnectRegistry) is only handed back when they log
 * in. So when the connection comes back, this logs in again with the credentials cached for
 * this run (Session keeps them in memory only, never on disk), and if the server answers with
 * a pending match, MatchResume puts the player straight back into it. Guests, and anyone
 * who was logged out, are skipped; a failed re-login (say the password changed) is left
 * alone rather than logging the player out from under themselves.
 */
public final class SessionRestorer
{
    private static boolean installed = false;

    private SessionRestorer() { }

    /** Safe to call more than once; MainMenu calls it when it comes up (the only time there can be anything to restore). */
    public static synchronized void install()
    {
        if (installed)
        {
            return;
        }
        installed = true;
        NetworkManager.setReconnectedHook(new Runnable()
        {
            public void run() { restore(); }
        });
    }

    private static void restore()
    {
        if (!Session.isLoggedIn())
        {
            return;
        }
        String password = Session.getCurrentPassword();
        if (password == null || Session.getCurrentAccount() == null)
        {
            return;
        }

        Message request = new Message();
        request.setType(MessageType.LOGIN_REQUEST);
        request.setUsername(Session.getCurrentAccount().getUsername());
        request.setPassword(password);
        final Message response = NetworkManager.send(request);
        if (response == null || !response.isSuccess())
        {
            return;
        }

        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                Session.login(response.getAccount(), Session.getCurrentPassword());
                MatchResume.resumeIfPending(response);
            }
        });
    }
}
