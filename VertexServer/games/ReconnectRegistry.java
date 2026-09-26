package games;
import net.ClientHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * ReconnectRegistry
 * -----------------
 * Generic "give a disconnected player a short grace window to log back in
 * before their match is forfeited" mechanism, keyed by accountId (a brand
 * new socket/ClientHandler exists on reconnect, with no continuity except
 * the same account logging back in - so accountId, not the ClientHandler
 * reference, is the only stable identity to track this by). Any match type
 * can use this by implementing ReconnectableMatch; wired up for
 * TicTacToeMatch first (see its handleDisconnect/onReconnect/
 * onReconnectTimeout) as the proof this generalizes, matching the
 * "prove it on one game first" approach ai/search and engine both took
 * before scaling to more games. Guests (no account) never get a grace
 * period - there's no stable identity to reconnect against, so a guest
 * disconnect still finalizes immediately, exactly like every match type
 * that hasn't adopted this registry yet.
 *
 * Threading note - lock ordering: this class's own lock is only ever
 * acquired either alone (beginGracePeriod/cancel with no match lock held)
 * or immediately before a ReconnectableMatch callback (tryReconnect, and
 * the grace-timer's own callback both take this registry's lock first,
 * then the match's). A ReconnectableMatch must never call back into this
 * registry while holding its own lock (the reverse order) - that would be
 * a classic lock-ordering deadlock between a thread disconnecting a match
 * and a thread reconnecting one. TicTacToeMatch.handleDisconnect is
 * carefully structured to call beginGracePeriod() only after its own
 * synchronized block has already exited, for exactly this reason.
 */
public class ReconnectRegistry
{
    private static final long DEFAULT_GRACE_MS = 45_000;

    private final long graceMs;

    public ReconnectRegistry()
    {
        this(DEFAULT_GRACE_MS);
    }

    /** Package-visible seam for tests - lets a test use a short grace window (e.g. a few hundred ms) instead of waiting out the real 45s default. */
    ReconnectRegistry(long graceMs)
    {
        this.graceMs = graceMs;
    }

    /** What a match hands back once a reconnect actually happens - everything the login response needs to resend the player their match. */
    public static class ReconnectResult
    {
        public final String matchId;
        public final String gameId;
        public final String mySymbol;
        public final String opponentUsername;
        public final String boardState;
        public final String turnSymbol;

        public ReconnectResult(String matchId, String gameId, String mySymbol,
                                String opponentUsername, String boardState, String turnSymbol)
        {
            this.matchId = matchId;
            this.gameId = gameId;
            this.mySymbol = mySymbol;
            this.opponentUsername = opponentUsername;
            this.boardState = boardState;
            this.turnSymbol = turnSymbol;
        }
    }

    /** Implemented by a match class that wants grace-period reconnect support. */
    public interface ReconnectableMatch
    {
        /** The grace window expired with no reconnect - finalize exactly as an immediate disconnect always did (declare the remaining player the winner, clean up). Never called if a reconnect already happened first. */
        void onReconnectTimeout();

        /** The disconnected account logged back in within the window - swap the stale ClientHandler reference for newHandler and resume. Returns the info the login response needs, or null if the match is no longer in a state where reconnecting makes sense (already ended some other way). */
        ReconnectResult onReconnect(ClientHandler newHandler);

        /** Called right after a successful onReconnect() - must call the matching handler.setCurrentXxxMatch(this) so newHandler's own future disconnect is routed back to this match. Without this, a session that reconnects and later disconnects again would go unnoticed (its ClientHandler.currentXxxMatch field would still be null from before it ever joined this match), leaving the other player waiting forever with no forfeit ever triggered. */
        void attachToHandler(ClientHandler handler);
    }

    private static class Entry
    {
        final ReconnectableMatch match;
        final Timer timer;
        Entry(ReconnectableMatch match, Timer timer) { this.match = match; this.timer = timer; }
    }

    private final Map<Integer, Entry> pending = new HashMap<Integer, Entry>();

    /**
     * Starts a grace period for accountId, tied to the given match. Call this only
     * AFTER releasing the match's own lock (see the class-level threading note) -
     * this method itself never calls back into the match, but the timer it schedules
     * eventually will.
     */
    public synchronized void beginGracePeriod(int accountId, final ReconnectableMatch match)
    {
        // Only one pending reconnect per account at a time. This can't happen in
        // practice today (a player is never in two matches of the same reconnect-
        // aware type simultaneously), but if it ever did, cancel the earlier grace
        // period first so its timer can't fire a stale timeout later.
        Entry existing = pending.remove(accountId);
        if (existing != null)
        {
            existing.timer.cancel();
        }

        Timer timer = new Timer(true);
        timer.schedule(new TimerTask()
        {
            public void run()
            {
                boolean stillPending;
                synchronized (ReconnectRegistry.this)
                {
                    stillPending = pending.remove(accountId) != null;
                }
                // Only fire the timeout if this entry hadn't already been claimed by a
                // reconnect (or superseded) - the remove() above and tryReconnect()'s own
                // remove() share the same lock, so exactly one of the two ever wins.
                if (stillPending)
                {
                    match.onReconnectTimeout();
                }
            }
        }, graceMs);

        pending.put(accountId, new Entry(match, timer));
    }

    /**
     * Called on a successful login. If accountId has a match waiting, cancels its
     * grace timer, asks the match to reconnect newHandler into it, and returns the
     * result to populate the login response with - or null if no match was pending.
     */
    public synchronized ReconnectResult tryReconnect(int accountId, ClientHandler newHandler)
    {
        Entry entry = pending.remove(accountId);
        if (entry == null)
        {
            return null;
        }
        entry.timer.cancel();
        ReconnectResult result = entry.match.onReconnect(newHandler);
        if (result != null)
        {
            entry.match.attachToHandler(newHandler);
        }
        return result;
    }

    /** Cancels a pending grace period with no reconnect and no timeout callback - used when there's no one left to wait for (e.g. the other player also disconnects mid-grace-period). */
    public synchronized void cancel(int accountId)
    {
        Entry entry = pending.remove(accountId);
        if (entry != null)
        {
            entry.timer.cancel();
        }
    }
}
