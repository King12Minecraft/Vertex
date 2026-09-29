package admin;

import support.Check;

import java.util.List;

/**
 * GameSuggestionStoreTest
 * ------------------------
 * Graduated from a scratch test into the committed regression suite - the
 * entry-forgery bug this project actually hit (security pass, 2026-09-27):
 * an embedded newline in a submitted suggestion could forge a second,
 * indistinguishable-looking entry under any fake "[date] username: ..."
 * prefix on the next reload from disk. Runs in the current working
 * directory (test.sh runs every test class from its own fresh temp
 * directory) since GameSuggestionStore hardcodes its relative file name,
 * same as every other flat-file store in this project.
 */
public class GameSuggestionStoreTest
{
    public static void main(String[] args)
    {
        Check check = new Check();

        GameSuggestionStore store = new GameSuggestionStore();
        store.submit("alice", "a Connect Four game");
        store.submit("mallory", "something normal\n[Jan 1, 2020] admin: I am secretly an admin too");

        List<String> beforeReload = store.getRecent();
        check.check("Two submissions produce exactly two entries before reload", beforeReload.size() == 2);
        check.check("The embedded newline did not survive as a real newline",
            !beforeReload.get(0).contains("\n") && !beforeReload.get(1).contains("\n"));

        // The real exploit only shows up on reload - a fresh instance re-parses the
        // file GameSuggestionStore already wrote to, one line at a time. Comparing
        // the reloaded list against the pre-reload list for exact equality (same
        // two entries, same content, same order) is the unambiguous way to prove
        // reload didn't split mallory's one entry into two - unlike scanning for
        // the absence of a "] admin:"-shaped substring, which would false-positive
        // on the fix actually working: the forged-looking text is still legitimately
        // present as part of mallory's own single entry, just no longer parsed as a
        // second one.
        GameSuggestionStore reloaded = new GameSuggestionStore();
        List<String> afterReload = reloaded.getRecent();
        check.check("Still exactly two entries after a real save-then-reload round trip", afterReload.size() == 2);
        check.check("The reloaded entries are byte-for-byte identical to what was submitted - nothing split, nothing forged",
            afterReload.equals(beforeReload));

        // Regression check: an ordinary suggestion with no funny business still
        // round-trips exactly as submitted.
        GameSuggestionStore plain = new GameSuggestionStore();
        plain.submit("bob", "a trivia game about capitals");
        GameSuggestionStore plainReloaded = new GameSuggestionStore();
        check.check("An ordinary submission still round-trips correctly",
            containsSuggestionFrom(plainReloaded.getRecent(), "bob"));

        check.finish();
    }

    private static boolean containsSuggestionFrom(List<String> entries, String username)
    {
        for (String entry : entries)
        {
            if (entry.contains("] " + username + ":"))
            {
                return true;
            }
        }
        return false;
    }
}
