package mechanics;

import net.ClientHandler;
import support.Check;
import support.FakeClientHandler;

/**
 * ReconnectRegistryTest
 * ----------------------
 * The reconnect grace-period mechanism itself (previously exercised only through
 * individual games): a timeout forfeits exactly once, a reconnect inside the window
 * cancels it, and nothing fires twice. Uses the package-visible short-grace
 * constructor so the tests don't wait out the real 30 seconds. Also pins ReconnectPolicy:
 * the 30-second window and the exception list.
 */
public class ReconnectRegistryTest
{
    /** A ReconnectableMatch that records which callbacks ran. */
    static class RecordingMatch implements ReconnectRegistry.ReconnectableMatch
    {
        volatile int timeouts = 0;
        volatile int reconnects = 0;
        volatile int attaches = 0;
        final boolean acceptReconnect;

        RecordingMatch(boolean acceptReconnect) { this.acceptReconnect = acceptReconnect; }

        public void onReconnectTimeout() { timeouts++; }

        public ReconnectRegistry.ReconnectResult onReconnect(ClientHandler newHandler)
        {
            reconnects++;
            return acceptReconnect ? new ReconnectRegistry.ReconnectResult("m-1", "chess", "WHITE", "opp", "board", "WHITE") : null;
        }

        public void attachToHandler(ClientHandler handler) { attaches++; }
    }

    private static void sleep(long ms)
    {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public static void main(String[] args)
    {
        Check check = new Check();

        testTimeoutFiresOnce(check);
        testReconnectCancelsTimeout(check);
        testNothingPending(check);
        testCancel(check);
        testSecondGracePeriodReplacesFirst(check);
        testRefusedReconnect(check);
        testPolicy(check);

        check.finish();
    }

    private static void testTimeoutFiresOnce(Check check)
    {
        ReconnectRegistry registry = new ReconnectRegistry(120);
        RecordingMatch match = new RecordingMatch(true);
        registry.beginGracePeriod(1, match);
        check.check("Nothing happens before the grace window ends", match.timeouts == 0);
        sleep(500);
        check.check("When the window passes with no reconnect, the match is told exactly once", match.timeouts == 1);
        check.check("...and a late reconnect finds nothing to resume", registry.tryReconnect(1, new FakeClientHandler("a", 1)) == null && match.reconnects == 0);
    }

    private static void testReconnectCancelsTimeout(Check check)
    {
        ReconnectRegistry registry = new ReconnectRegistry(250);
        RecordingMatch match = new RecordingMatch(true);
        registry.beginGracePeriod(2, match);
        ReconnectRegistry.ReconnectResult result = registry.tryReconnect(2, new FakeClientHandler("b", 2));
        check.check("Reconnecting inside the window resumes the match and returns what the login needs",
            result != null && "m-1".equals(result.matchId) && match.reconnects == 1);
        check.check("...and re-attaches the new connection to the match (so a second drop is noticed)", match.attaches == 1);
        sleep(600);
        check.check("...and the forfeit timeout never fires afterwards", match.timeouts == 0);
        check.check("A second reconnect for the same drop finds nothing", registry.tryReconnect(2, new FakeClientHandler("b", 2)) == null && match.reconnects == 1);
    }

    private static void testNothingPending(Check check)
    {
        ReconnectRegistry registry = new ReconnectRegistry(100);
        check.check("Logging in with no match waiting resumes nothing", registry.tryReconnect(99, new FakeClientHandler("c", 99)) == null);
    }

    private static void testCancel(Check check)
    {
        ReconnectRegistry registry = new ReconnectRegistry(100);
        RecordingMatch match = new RecordingMatch(true);
        registry.beginGracePeriod(3, match);
        registry.cancel(3);
        sleep(400);
        check.check("cancel() stops the timeout with no callback (used when there is no one left to wait for)",
            match.timeouts == 0 && match.reconnects == 0);
    }

    private static void testSecondGracePeriodReplacesFirst(Check check)
    {
        ReconnectRegistry registry = new ReconnectRegistry(150);
        RecordingMatch first = new RecordingMatch(true);
        RecordingMatch second = new RecordingMatch(true);
        registry.beginGracePeriod(4, first);
        registry.beginGracePeriod(4, second);
        sleep(600);
        check.check("Only one pending reconnect per account: the newer replaces the older, whose timer is cancelled",
            first.timeouts == 0 && second.timeouts == 1);
    }

    private static void testRefusedReconnect(Check check)
    {
        ReconnectRegistry registry = new ReconnectRegistry(300);
        RecordingMatch match = new RecordingMatch(false);
        registry.beginGracePeriod(5, match);
        ReconnectRegistry.ReconnectResult result = registry.tryReconnect(5, new FakeClientHandler("e", 5));
        check.check("A match that can no longer be resumed returns null and is not re-attached",
            result == null && match.reconnects == 1 && match.attaches == 0);
    }

    private static void testPolicy(Check check)
    {
        check.check("The grace window is 30 seconds", ReconnectPolicy.GRACE_SECONDS == 30 && ReconnectPolicy.GRACE_MS == 30000L);
        check.check("The waiting notice states the real window", ReconnectPolicy.waitingNotice().contains("30s"));

        FakeClientHandler player = new FakeClientHandler("p", 7);
        FakeClientHandler guest = new FakeClientHandler("guest", null);
        check.check("Chess is not an exception", ReconnectPolicy.isEnabled("chess") && ReconnectPolicy.canReconnect(player, "chess"));
        check.check("A logged-in player in a normal game gets the window", ReconnectPolicy.canReconnect(player, "connect-four"));
        check.check("A guest never does (no account to log back in as)", !ReconnectPolicy.canReconnect(guest, "chess"));
        check.check("Group games are exceptions, each with a stated reason",
            !ReconnectPolicy.canReconnect(player, "racing") && !ReconnectPolicy.canReconnect(player, "trivia-blitz")
                && !ReconnectPolicy.canReconnect(player, "among-us") && ReconnectPolicy.exceptionReason("telephone") != null);
        check.check("Games not listed have no exception reason", ReconnectPolicy.exceptionReason("chess") == null);
    }
}
