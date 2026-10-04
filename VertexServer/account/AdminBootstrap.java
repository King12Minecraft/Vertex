package account;

import java.io.PrintStream;

/**
 * AdminBootstrap
 * --------------
 * First-run administrator setup, done from the server's own console. Replaces the old rule
 * "the first account anyone creates on a fresh server becomes ADMIN" - which meant whoever
 * reached a newly started, publicly reachable server first owned it. Now the operator picks
 * the admin name and password at the server console (or, for an unattended start, in the
 * VERTEX_ADMIN_USER / VERTEX_ADMIN_PASSWORD environment variables); they never travel
 * through the game client or a chat. If neither is available the server still starts, warns
 * loudly, and simply has no administrator until it is restarted from a console - it never
 * hands admin to a stranger.
 *
 * Takes its input through a tiny interface so it can be tested without a real console.
 */
public final class AdminBootstrap
{
    private AdminBootstrap() { }

    /** Where prompts come from: the real console, or a scripted source in tests. */
    public interface Prompter
    {
        String readLine(String prompt);
        /** Same, but the answer shouldn't be echoed where the source can avoid it. */
        String readPassword(String prompt);
    }

    public enum Result { ALREADY_EXISTS, CREATED, SKIPPED }

    private static final int MAX_ATTEMPTS = 3;

    /** prompter may be null (no console); envUser/envPassword may be null or empty. */
    public static Result ensureAdmin(ServerAccountStore store, Prompter prompter,
                                     String envUser, String envPassword, PrintStream out)
    {
        if (store.hasAdminAccount())
        {
            return Result.ALREADY_EXISTS;
        }

        if (envUser != null && !envUser.isEmpty() && envPassword != null && !envPassword.isEmpty())
        {
            String problem = problemWith(store, envUser, envPassword);
            if (problem != null)
            {
                out.println("VERTEX_ADMIN_USER / VERTEX_ADMIN_PASSWORD were set but not usable: " + problem);
            }
            else
            {
                store.createAccount(envUser, envPassword, Role.ADMIN);
                out.println("Created the administrator account \"" + envUser + "\" from the environment.");
                return Result.CREATED;
            }
        }

        if (prompter == null)
        {
            out.println("=====================================================================");
            out.println(" This server has NO administrator account yet, and no console to set");
            out.println(" one up on. It is running without one (nobody can moderate or use");
            out.println(" admin tools). To create it, start the server from a terminal, or set");
            out.println(" VERTEX_ADMIN_USER and VERTEX_ADMIN_PASSWORD and restart.");
            out.println("=====================================================================");
            return Result.SKIPPED;
        }

        out.println("First-run setup: this server has no administrator account yet.");
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++)
        {
            String username = prompter.readLine("Administrator username: ");
            String password = prompter.readPassword("Administrator password (min 6 characters): ");
            String confirm = prompter.readPassword("Repeat the password: ");
            if (username == null || password == null || confirm == null)
            {
                break;   // input ended
            }
            username = username.trim();
            if (!password.equals(confirm))
            {
                out.println("The two passwords didn't match - try again.");
                continue;
            }
            String problem = problemWith(store, username, password);
            if (problem != null)
            {
                out.println(problem);
                continue;
            }
            store.createAccount(username, password, Role.ADMIN);
            out.println("Administrator account \"" + username + "\" created.");
            return Result.CREATED;
        }

        out.println("No administrator was created. The server will run without one; restart it to try again.");
        return Result.SKIPPED;
    }

    /** null if the pair is acceptable, otherwise a sentence saying why not - same rules as creating an account from the client. */
    private static String problemWith(ServerAccountStore store, String username, String password)
    {
        if (!ServerAccountStore.isValidUsernameFormat(username))
        {
            return "Username can only contain letters, numbers, underscores and hyphens (3-20 characters).";
        }
        if (store.usernameExists(username))
        {
            return "That username is already taken.";
        }
        if (password == null || password.length() < 6)
        {
            return "Password must be at least 6 characters.";
        }
        return null;
    }
}
