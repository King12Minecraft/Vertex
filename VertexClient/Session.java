import java.util.ArrayList;
import java.util.List;

/**
 * Session
 * -------
 * Holds who is currently logged in on this client. Components that
 * display account info (TopBar, ProfilePanel) register a listener here
 * so they refresh automatically when the account changes (login,
 * logout, username change) - same pattern as ThemeManager.
 */
public class Session
{
    private static Account currentAccount = null;
    private static final List<Runnable> listeners = new ArrayList<Runnable>();

    private Session()
    {
        // Static utility class - never instantiated.
    }

    public static void login(Account account)
    {
        currentAccount = account;
        notifyListeners();
    }

    public static void logout()
    {
        currentAccount = null;
        notifyListeners();
    }

    public static Account getCurrentAccount()
    {
        return currentAccount;
    }

    public static boolean isLoggedIn()
    {
        return currentAccount != null;
    }

    /** Call after mutating the current account (e.g. a username change) to refresh listeners. */
    public static void notifyListeners()
    {
        for (int i = 0; i < listeners.size(); i++)
        {
            listeners.get(i).run();
        }
    }

    public static void addListener(Runnable listener)
    {
        listeners.add(listener);
    }
}
