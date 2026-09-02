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
    private static String currentPassword = null;
    private static final List<Runnable> listeners = new ArrayList<Runnable>();

    private Session()
    {
        // Static utility class - never instantiated.
    }

    public static void login(Account account)
    {
        login(account, null);
    }

    /** Caches the password alongside the account for the lifetime of this process only (never written to disk) - lets the in-app server switcher re-authenticate against a different server without asking the person to retype their credentials. This is no meaningful new exposure: the project already sends this same password in plaintext over the login socket in the first place. */
    public static void login(Account account, String password)
    {
        currentAccount = account;
        currentPassword = password;
        notifyListeners();
    }

    public static void logout()
    {
        currentAccount = null;
        currentPassword = null;
        notifyListeners();
    }

    public static Account getCurrentAccount()
    {
        return currentAccount;
    }

    /** Null if the current session was never given a password to cache (e.g. restored some other way) - callers needing to re-authenticate should treat null as "can't do this silently, fall back to asking." */
    public static String getCurrentPassword()
    {
        return currentPassword;
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
