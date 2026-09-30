package mechanics;

import net.ClientHandler;
import net.Message;
import net.MessageType;

/**
 * PairReconnect
 * -------------
 * The reconnect-grace logic every two-player match needs, written once. A match creates one of
 * these (passing itself as the lock and a small {@link Host} adapter) and then:
 *
 *   - its handleDisconnect(who) becomes {@code reconnect.handleDisconnect(who)}  (NOT synchronized -
 *     this class takes the match lock itself and registers with the shared registry only after
 *     releasing it, per the lock-ordering rule in {@link ReconnectRegistry});
 *   - each player action starts with {@code if (reconnect.isPaused()) return;} (call it under the
 *     match lock, which the action methods already hold).
 *
 * What this handles: a drop from a logged-in player pauses the match and tells the other player;
 * a second drop while paused, a guest, a deliberate Leave, or the window running out all end the
 * match through {@link Host#forfeit}; a login inside the window swaps the new handler into the
 * dropped slot and resumes. What stays per-game (the Host): what a forfeit looks like (result
 * message, coins) and how to describe the match state to a returning player.
 *
 * Slots are 0 and 1 (the match's playerA / playerB).
 */
public class PairReconnect
{
    /** The per-game bits. Every method except isOver() is called while holding the match lock. */
    public interface Host
    {
        String matchId();

        boolean isOver();

        ClientHandler player(int slot);

        void setPlayer(int slot, ClientHandler handler);

        /** State string put on the waiting notice, so the remaining player's board stays correct while paused. */
        String stateString();

        /** The match ends now because of a drop: mark it over, end it with the manager, and if {@code remaining} is not null tell them (OPPONENT_LEFT) and pay the win. null means both players are gone. */
        void forfeit(ClientHandler remaining);

        /** A player is back in {@code slot} (already swapped in via setPlayer): send {@code opponent} a fresh update (clears their waiting notice) and describe the match for the returning player. */
        ReconnectRegistry.ReconnectResult resume(int slot, ClientHandler opponent);

        /** Point the new handler's currentXxxMatch field at this match, so its later moves and disconnect reach it. */
        void attach(ClientHandler handler);
    }

    private final Object lock;
    private final String gameId;
    private final Host host;
    private final long resumeDelayMs;
    private int droppedSlot = -1;
    private long heldUntil = 0;

    public PairReconnect(Object matchLock, String gameId, Host host)
    {
        this(matchLock, gameId, host, 0);
    }

    /**
     * resumeDelayMs is for real-time games: after the returning player is back, the game stays
     * frozen this much longer, so their window (rebuilt from the login response) is on screen
     * before the puck / snake / pieces move again. Turn-based games pass 0 (the default).
     */
    public PairReconnect(Object matchLock, String gameId, Host host, long resumeDelayMs)
    {
        this.lock = matchLock;
        this.gameId = gameId;
        this.host = host;
        this.resumeDelayMs = resumeDelayMs;
    }

    /** True while a player is inside the grace window. Call under the match lock. */
    public boolean isPaused()
    {
        return droppedSlot >= 0;
    }

    /** For real-time games' tick and input handlers: true while paused, and for the resume delay after a return. Call under the match lock. */
    public boolean isHeld()
    {
        return droppedSlot >= 0 || System.currentTimeMillis() < heldUntil;
    }

    /**
     * Re-sends the waiting notice (with the current state) to the player who is waiting. For a
     * match whose state can still change during the pause - a timer that clears a mismatch, say -
     * this keeps their view current without an ordinary update, which would make their window
     * think play had resumed. Call under the match lock; does nothing unless paused.
     */
    public void refreshNotice()
    {
        if (droppedSlot < 0)
        {
            return;
        }
        Message notice = new Message();
        notice.setType(MessageType.OPPONENT_DISCONNECTED_NOTICE);
        notice.setMatchId(host.matchId());
        notice.setBoardState(host.stateString());
        notice.setErrorText(ReconnectPolicy.waitingNotice());
        host.player(1 - droppedSlot).sendMessage(notice);
    }

    public void handleDisconnect(ClientHandler who)
    {
        Integer accountId;
        synchronized (lock)
        {
            if (host.isOver())
            {
                return;
            }
            int slot = (who == host.player(0)) ? 0 : 1;
            if (droppedSlot >= 0)
            {
                // The other player was already in a grace period and now this one is gone too.
                droppedSlot = -1;
                host.forfeit(null);
                return;
            }
            if (!ReconnectPolicy.canReconnect(who, gameId))
            {
                host.forfeit(host.player(1 - slot));
                return;
            }

            droppedSlot = slot;
            accountId = who.getAccountId();
            refreshNotice();
        }

        // Lock ordering (see ReconnectRegistry): register only after the match lock is released.
        ReconnectRegistry.shared().beginGracePeriod(accountId, new ReconnectRegistry.ReconnectableMatch()
        {
            public void onReconnectTimeout()
            {
                PairReconnect.this.onTimeout();
            }

            public ReconnectRegistry.ReconnectResult onReconnect(ClientHandler newHandler)
            {
                return PairReconnect.this.onReconnect(newHandler);
            }

            public void attachToHandler(ClientHandler handler)
            {
                host.attach(handler);
            }
        });
    }

    private void onTimeout()
    {
        synchronized (lock)
        {
            if (host.isOver() || droppedSlot < 0)
            {
                return;
            }
            int slot = droppedSlot;
            droppedSlot = -1;
            host.forfeit(host.player(1 - slot));
        }
    }

    /** Called by ReconnectRegistry.tryReconnect() while it holds the registry's lock - must not call back into the registry. */
    private ReconnectRegistry.ReconnectResult onReconnect(ClientHandler newHandler)
    {
        synchronized (lock)
        {
            if (host.isOver() || droppedSlot < 0)
            {
                return null;
            }
            int slot = droppedSlot;
            droppedSlot = -1;
            heldUntil = System.currentTimeMillis() + resumeDelayMs;
            host.setPlayer(slot, newHandler);
            return host.resume(slot, host.player(1 - slot));
        }
    }
}
