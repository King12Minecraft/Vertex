package admin;

import support.Check;

import java.util.List;

/**
 * FeedbackManagerTest
 * --------------------
 * Graduated from a scratch test - the narrower sibling of the
 * GameSuggestionStore/AdminLog newline-forgery fix (security pass,
 * 2026-09-27): FeedbackManager's delimiter-block format allows free text to
 * contain ordinary newlines (that's the whole reason it uses a delimiter
 * instead of one-line-per-entry), but a submission containing a line
 * consisting of EXACTLY the 64-dash DELIMITER string would otherwise end
 * that entry's block early on reload, misparsing everything after it as a
 * second, differently-attributed entry. Runs in its own fresh temp working
 * directory via test.sh.
 */
public class FeedbackManagerTest
{
    public static void main(String[] args)
    {
        Check check = new Check();

        // Reconstructed rather than copy-pasted, so this test can't silently drift
        // out of sync with FeedbackManager's own private DELIMITER constant if that
        // ever changes length.
        StringBuilder delimiter = new StringBuilder();
        for (int i = 0; i < 64; i++) delimiter.append('-');

        FeedbackManager manager = new FeedbackManager();
        manager.submit("alice", "BUG", "A real bug", "The button does nothing when clicked.", null);
        manager.submit("mallory", "SUGGESTION", "Innocent-looking title",
            "Here's my suggestion.\n" + delimiter + "\nThis text after the fake delimiter should stay part of mallory's own entry, not become a separate one.",
            null);

        List<String> beforeReload = manager.getAllDescriptions();
        check.check("Two submissions produce exactly two entries before reload", beforeReload.size() == 2);

        FeedbackManager reloaded = new FeedbackManager();
        List<String> afterReload = reloaded.getAllDescriptions();
        check.check("Still exactly two entries after a real save-then-reload round trip", afterReload.size() == 2);

        boolean malloryEntryIntact = false;
        for (String entry : afterReload)
        {
            if (entry.contains("This text after the fake delimiter should stay part of mallory's own entry"))
            {
                malloryEntryIntact = true;
                check.check("Mallory's entry is still attributed to mallory, not split into a second entry",
                    entry.contains("mallory"));
            }
        }
        check.check("The text after the fake delimiter line survived intact in one entry", malloryEntryIntact);

        boolean aliceEntryIntact = false;
        for (String entry : afterReload)
        {
            if (entry.contains("The button does nothing when clicked."))
            {
                aliceEntryIntact = true;
            }
        }
        check.check("An ordinary bug report with no delimiter text still round-trips correctly", aliceEntryIntact);

        check.finish();
    }
}
