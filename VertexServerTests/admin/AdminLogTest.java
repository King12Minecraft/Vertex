package admin;

import support.Check;

import java.util.List;

/**
 * AdminLogTest
 * ------------
 * Graduated from a scratch test - same one-line-per-entry newline-forgery
 * fix as GameSuggestionStoreTest, applied to an admin-typed action reason
 * instead of a public suggestion (lower severity since only admins/mods
 * ever reach AdminLog.log() at all, but the fix and the regression risk
 * are identical). Runs in its own fresh temp working directory via
 * test.sh, same as every other flat-file-store test here.
 */
public class AdminLogTest
{
    public static void main(String[] args)
    {
        Check check = new Check();

        AdminLog log = new AdminLog();
        log.log("moderator1", "banned a player for spam");
        log.log("moderator2", "muted someone\n[Jan 1, 2020 1:00 AM] system: nothing happened here");

        List<String> beforeReload = log.getRecent();
        check.check("Two log() calls produce exactly two entries before reload", beforeReload.size() == 2);
        check.check("The embedded newline did not survive as a real newline",
            !beforeReload.get(0).contains("\n") && !beforeReload.get(1).contains("\n"));

        // Comparing the reloaded list against the pre-reload list for exact
        // equality is the unambiguous way to prove reload didn't split
        // moderator2's one entry into two - scanning for the absence of a
        // "] system:"-shaped substring would false-positive on the fix actually
        // working, since that text is still legitimately present as part of
        // moderator2's own single entry (see GameSuggestionStoreTest's identical
        // note - the same mistake was made and caught there first).
        AdminLog reloaded = new AdminLog();
        List<String> afterReload = reloaded.getRecent();
        check.check("Still exactly two entries after a real save-then-reload round trip", afterReload.size() == 2);
        check.check("The reloaded entries are byte-for-byte identical to what was logged - nothing split, nothing forged",
            afterReload.equals(beforeReload));
        check.check("Both real actors are still present after reload",
            containsEntryFrom(afterReload, "moderator1") && containsEntryFrom(afterReload, "moderator2"));

        check.finish();
    }

    private static boolean containsEntryFrom(List<String> entries, String actorUsername)
    {
        for (String entry : entries)
        {
            if (entry.contains("] " + actorUsername + ":"))
            {
                return true;
            }
        }
        return false;
    }
}
