package account;

import support.Check;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * AdminBootstrapTest
 * -------------------
 * First-run administrator setup: the admin is created from the server console (or the
 * environment), nobody becomes admin by signing up, existing admins are left alone, and bad
 * input is re-asked rather than accepted. Driven with a scripted prompter - no real console.
 */
public class AdminBootstrapTest
{
    /** Answers prompts from a fixed list, in order; null once exhausted (like a closed input). */
    static class Script implements AdminBootstrap.Prompter
    {
        final List<String> answers;
        int next = 0;
        Script(String... answers) { this.answers = new ArrayList<String>(Arrays.asList(answers)); }
        String take() { return next < answers.size() ? answers.get(next++) : null; }
        public String readLine(String prompt) { return take(); }
        public String readPassword(String prompt) { return take(); }
    }

    /** Each scenario starts from an empty accounts file - every ServerAccountStore in a JVM shares the same one. */
    private static ServerAccountStore freshStore()
    {
        new java.io.File("gamehub_server_accounts.dat").delete();
        return new ServerAccountStore();
    }

    private static PrintStream sink(ByteArrayOutputStream buffer) { return new PrintStream(buffer); }

    public static void main(String[] args)
    {
        Check check = new Check();

        // fresh server, scripted console
        {
            ServerAccountStore store = freshStore();
            check.check("a fresh store has no admin", !store.hasAdminAccount());
            ByteArrayOutputStream log = new ByteArrayOutputStream();
            AdminBootstrap.Result r = AdminBootstrap.ensureAdmin(store, new Script("boss", "secret123", "secret123"), null, null, sink(log));
            check.check("console setup creates the admin", r == AdminBootstrap.Result.CREATED && store.hasAdminAccount());
            check.check("the account has the ADMIN role and the chosen name",
                store.findByUsername("boss") != null && store.findByUsername("boss").getRole() == Role.ADMIN);
            check.check("the chosen password works, another doesn't",
                store.attemptLogin("boss", "secret123") == ServerAccountStore.LoginResult.SUCCESS
                && store.attemptLogin("boss", "wrong-pass") != ServerAccountStore.LoginResult.SUCCESS);
            check.check("the password is never echoed to the log", !log.toString().contains("secret123"));

            AdminBootstrap.Result again = AdminBootstrap.ensureAdmin(store, new Script("other", "secret123", "secret123"), null, null, sink(log));
            check.check("with an admin already present nothing is asked or created", again == AdminBootstrap.Result.ALREADY_EXISTS && store.findByUsername("other") == null);
        }

        // bad input is re-asked
        {
            ServerAccountStore store = freshStore();
            ByteArrayOutputStream log = new ByteArrayOutputStream();
            Script s = new Script(
                "bad name!", "secret123", "secret123",     // invalid username
                "okname", "short", "short",                // too-short password
                "okname", "secret123", "different1",       // mismatch
                "okname", "secret123", "secret123");       // fourth try - but only 3 attempts are allowed
            AdminBootstrap.Result r = AdminBootstrap.ensureAdmin(store, s, null, null, sink(log));
            check.check("three bad attempts in a row give up without creating anything", r == AdminBootstrap.Result.SKIPPED && !store.hasAdminAccount());

            ServerAccountStore store2 = freshStore();
            AdminBootstrap.Result r2 = AdminBootstrap.ensureAdmin(store2,
                new Script("bad name!", "secret123", "secret123", "okname", "secret123", "secret123"), null, null, sink(log));
            check.check("a bad attempt followed by a good one succeeds", r2 == AdminBootstrap.Result.CREATED && store2.findByUsername("okname") != null);
        }

        // closed input
        {
            ServerAccountStore store = freshStore();
            AdminBootstrap.Result r = AdminBootstrap.ensureAdmin(store, new Script(), null, null, sink(new ByteArrayOutputStream()));
            check.check("input that ends immediately creates nothing", r == AdminBootstrap.Result.SKIPPED && !store.hasAdminAccount());
        }

        // environment, and no console at all
        {
            ServerAccountStore store = freshStore();
            ByteArrayOutputStream log = new ByteArrayOutputStream();
            AdminBootstrap.Result r = AdminBootstrap.ensureAdmin(store, null, "envadmin", "envpass99", sink(log));
            check.check("environment variables create the admin with no console", r == AdminBootstrap.Result.CREATED
                && store.findByUsername("envadmin").getRole() == Role.ADMIN);
            check.check("the environment password isn't printed", !log.toString().contains("envpass99"));

            ServerAccountStore bare = freshStore();
            ByteArrayOutputStream log2 = new ByteArrayOutputStream();
            AdminBootstrap.Result none = AdminBootstrap.ensureAdmin(bare, null, null, null, sink(log2));
            check.check("no console and no environment: skipped, nobody made admin, with a warning",
                none == AdminBootstrap.Result.SKIPPED && !bare.hasAdminAccount() && log2.toString().contains("NO administrator"));

            ServerAccountStore badEnv = freshStore();
            AdminBootstrap.Result rb = AdminBootstrap.ensureAdmin(badEnv, null, "x", "pw", sink(new ByteArrayOutputStream()));
            check.check("an unusable environment pair is refused", rb == AdminBootstrap.Result.SKIPPED && !badEnv.hasAdminAccount());
        }

        // a plain account never turns an admin-less store into one with an admin
        {
            ServerAccountStore store = freshStore();
            store.createAccount("firstplayer", "password1", Role.PLAYER);
            check.check("an ordinary account on an admin-less store leaves it without an admin", !store.hasAdminAccount());
        }

        check.finish();
    }
}
