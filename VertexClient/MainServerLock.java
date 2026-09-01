import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * MainServerLock
 * ---------------
 * Guards who can start a server in main-server mode (as opposed to
 * satellite mode). First-ever setup is open: whoever starts a server
 * as main FIRST on a given installation is asked to set a password
 * right then, which gets hashed and saved to a local file. Every
 * subsequent attempt to start as main - even restarting the same
 * setup - requires that same password. This doesn't (and can't,
 * without real PKI infrastructure this project's scope doesn't
 * support) stop someone from copying the whole codebase elsewhere and
 * setting up their OWN separately-locked main server; what it does
 * stop is someone on THIS installation casually or accidentally
 * reconfiguring which server is "the" main one without knowing the
 * password the original admin set.
 */
public class MainServerLock
{
    private static final String LOCK_FILE = "gamehub_main_server_lock.dat";

    private MainServerLock()
    {
        // Static utility class - never instantiated.
    }

    public static boolean isEstablished()
    {
        return new java.io.File(LOCK_FILE).exists();
    }

    /** Only call this once isEstablished() has already returned false - sets the password for the first time. */
    public static void establish(String password)
    {
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash(password, salt);

        PrintWriter writer = null;
        try
        {
            writer = new PrintWriter(new FileWriter(LOCK_FILE));
            writer.println(salt);
            writer.println(hash);
        }
        catch (IOException e)
        {
            System.err.println("Could not save the main server lock: " + e.getMessage());
        }
        finally
        {
            if (writer != null) writer.close();
        }
    }

    /** Only call this once isEstablished() has already returned true - checks the given password against the one set during establish(). */
    public static boolean verify(String password)
    {
        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(LOCK_FILE));
            String salt = reader.readLine();
            String hash = reader.readLine();
            if (salt == null || hash == null)
            {
                return false;
            }
            return PasswordHasher.matches(password, salt, hash);
        }
        catch (IOException e)
        {
            return false;
        }
        finally
        {
            if (reader != null) { try { reader.close(); } catch (IOException ignored) { } }
        }
    }
}
