package support;

/**
 * Check
 * -----
 * Formalizes the hand-rolled "a label, a boolean, a running total" pattern
 * every scratch test this project has ever written already reinvented from
 * scratch (see ROADMAP.md's Testing Infrastructure entry) - a few lines, not
 * a framework. No external test dependency (JUnit etc.) by design: this
 * project has zero external dependencies (see build.sh/test.sh, plain javac
 * + jar, no Maven/Gradle) and both VertexClient/VertexServer still open and
 * run directly in BlueJ - adding one would be a real philosophy break for a
 * small convenience this class already gives for free.
 *
 * Usage: one instance per test's main(), call check(label, condition) for
 * every assertion, then finish() as the last line - it prints the same
 * "N checks, M failures" summary every prior scratch test already printed
 * by hand, and calls System.exit(1) if anything failed so test.sh can tell
 * a passing run from a failing one by exit code.
 */
public class Check
{
    private int checks = 0;
    private int failures = 0;

    public void check(String label, boolean condition)
    {
        checks++;
        if (!condition)
        {
            failures++;
            System.out.println("FAIL: " + label);
        }
    }

    public int getChecks() { return checks; }
    public int getFailures() { return failures; }

    /** Call as the last line of main(). Prints the summary and exits nonzero on any failure. */
    public void finish()
    {
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0)
        {
            System.exit(1);
        }
    }
}
