package ai;

import java.util.HashMap;
import java.util.Map;

/**
 * AiKernel
 * --------
 * The single shared entry point every game goes through to ask its
 * bot/opponent AI for a move - this is the "safety net" BotStrategy's
 * own Javadoc refers to. A game registers a primary strategy (the
 * real, possibly-imperfect "smart" logic) together with a fallback
 * strategy (something deliberately trivial and bulletproof - e.g.
 * "pick any legal move at random") under its own gameId. chooseMove()
 * always tries the primary strategy first; if it throws for any
 * reason, the kernel silently falls back to the fallback strategy
 * instead of letting the exception propagate and stall a live match.
 *
 * Deliberately minimal for this first slice of the shared AI layer:
 * no game-state introspection, no descriptor/knowledge/procgen
 * machinery yet - just the one seam (a shared registry + guaranteed-
 * safe move selection) proven end-to-end with a single real game
 * (Tic-Tac-Toe Practice Mode) before anything else is built on top of
 * it. Every game keeps applying whatever move comes back through its
 * own already-validated logic - the kernel only ever returns a
 * proposed move, it never touches game state itself. That split is
 * what keeps every game (and, for multiplayer games, the server)
 * authoritative over its own rules, with this layer purely advisory.
 *
 * Registering with an unregistered gameId is treated as a programmer
 * error (fails loudly, immediately, with IllegalStateException) since
 * it means a game forgot to wire itself up - very different from a
 * strategy failing AT RUNTIME while making a real decision, which is
 * exactly what the primary/fallback safety net exists to absorb
 * silently so a live match never gets stuck because of a bot bug.
 */
public final class AiKernel
{
    private AiKernel()
    {
        // Static utility class - never instantiated.
    }

    private static final Map<String, Registration<?, ?>> REGISTRY = new HashMap<String, Registration<?, ?>>();

    private static final class Registration<S, A>
    {
        final BotStrategy<S, A> primary;
        final BotStrategy<S, A> fallback;

        Registration(BotStrategy<S, A> primary, BotStrategy<S, A> fallback)
        {
            this.primary = primary;
            this.fallback = fallback;
        }
    }

    /**
     * Registers (or replaces) the primary/fallback strategy pair for a
     * game. Safe to call more than once for the same gameId - the
     * latest registration simply replaces the previous one, so a
     * game's class can register unconditionally from a static
     * initializer without needing an "already registered" guard.
     */
    public static synchronized <S, A> void register(String gameId, BotStrategy<S, A> primary, BotStrategy<S, A> fallback)
    {
        if (gameId == null || gameId.isEmpty())
        {
            throw new IllegalArgumentException("gameId must be a non-empty string");
        }
        if (primary == null || fallback == null)
        {
            throw new IllegalArgumentException("Both a primary and a fallback strategy are required for game: " + gameId);
        }
        REGISTRY.put(gameId, new Registration<S, A>(primary, fallback));
    }

    public static synchronized boolean isRegistered(String gameId)
    {
        return REGISTRY.containsKey(gameId);
    }

    /** Test/debug hook - removes a registration so a test doesn't leak state into others. Never used by real game code. */
    static synchronized void unregisterForTesting(String gameId)
    {
        REGISTRY.remove(gameId);
    }

    /**
     * Asks the strategy registered for gameId to choose a move given
     * the current state. Tries the primary strategy first; if it
     * throws any RuntimeException, falls back to the registered
     * fallback strategy instead of propagating the failure - a buggy
     * "smart" strategy can never stall or crash a live match. If the
     * fallback ALSO throws (it shouldn't, by contract - see
     * BotStrategy's Javadoc - but this guards against it anyway), an
     * AiDecisionException wrapping the ORIGINAL primary failure is
     * thrown, since that's the actionable bug to go fix.
     */
    @SuppressWarnings("unchecked")
    public static synchronized <S, A> A chooseMove(String gameId, S state)
    {
        Registration<?, ?> raw = REGISTRY.get(gameId);
        if (raw == null)
        {
            throw new IllegalStateException("No AI strategy registered for game: " + gameId
                + " - call AiKernel.register(...) before asking it for a move.");
        }
        Registration<S, A> registration = (Registration<S, A>) raw;

        try
        {
            return registration.primary.chooseMove(state);
        }
        catch (RuntimeException primaryFailure)
        {
            try
            {
                return registration.fallback.chooseMove(state);
            }
            catch (RuntimeException fallbackFailure)
            {
                throw new AiDecisionException(
                    "Both the primary and fallback AI strategies failed for game: " + gameId, primaryFailure);
            }
        }
    }
}
