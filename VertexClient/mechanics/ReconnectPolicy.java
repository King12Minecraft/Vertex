package mechanics;

import net.ClientHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * ReconnectPolicy
 * ----------------
 * The one place that says how long a disconnected player gets to come back, and which
 * games are exceptions. When a player's connection drops mid-match (a wifi hiccup, an
 * app restart), the match waits GRACE_SECONDS for them to log back in and continue
 * before the opponent is declared the winner - see ReconnectRegistry for the mechanism.
 *
 * Applies to every game that has adopted ReconnectRegistry, except the games listed in
 * EXCEPTIONS, where a drop still forfeits immediately (or, for group games, the match
 * simply carries on without that player). A match's disconnect handler asks
 * canReconnect(...) rather than checking anything itself, so moving a game in or out of
 * the exception list is a one-line change here. Guests never get a grace period - they
 * have no account to log back in as.
 */
public final class ReconnectPolicy
{
    /** How long a disconnected player has to come back. */
    public static final int GRACE_SECONDS = 30;
    public static final long GRACE_MS = GRACE_SECONDS * 1000L;

    /** gameId -> why a disconnect there is not given a grace period. Everything not listed uses it (where the game has adopted ReconnectRegistry). */
    private static final Map<String, String> EXCEPTIONS = new HashMap<String, String>();

    static
    {
        String group = "group game - there is no single opponent to forfeit to; the match carries on without the player who left";
        exception("racing", group);
        exception("space-battle", group);
        exception("square-wars", group);
        exception("zombie-survival", group);
        exception("among-us", group);
        exception("telephone", group);
        exception("caption-chaos", group);
        exception("trivia-blitz", group + " (their score stays locked in)");
        exception("fight-arena", group);
    }

    private ReconnectPolicy()
    {
        // Static utility class - never instantiated.
    }

    private static void exception(String gameId, String reason)
    {
        EXCEPTIONS.put(gameId, reason);
    }

    /** False for the exception games. */
    public static boolean isEnabled(String gameId)
    {
        return !EXCEPTIONS.containsKey(gameId);
    }

    /** Why a game is an exception, or null if it isn't one. */
    public static String exceptionReason(String gameId)
    {
        return EXCEPTIONS.get(gameId);
    }

    /** Whether this disconnecting player should be given the grace window: logged in (a guest can't log back in), actually dropped rather than choosing to leave, and the game isn't an exception. */
    public static boolean canReconnect(ClientHandler who, String gameId)
    {
        return who.getAccountId() != null && !who.isLeavingVoluntarily() && isEnabled(gameId);
    }

    /** What the remaining player is told while waiting. */
    public static String waitingNotice()
    {
        return "Opponent disconnected - waiting to reconnect (up to " + GRACE_SECONDS + "s)...";
    }
}
