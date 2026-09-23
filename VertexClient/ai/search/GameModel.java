package ai.search;

import java.util.List;

/**
 * GameModel
 * ---------
 * The one thing a game implements to get a real search-based AI
 * opponent "for free" from the shared ai.search engine - describes
 * that game's rules only. The search algorithm (Minimax), the bot
 * wrapper (GenericBotStrategy/RandomMoveStrategy), and the offline
 * match bookkeeping (PracticeMatch) are all fully generic and shared
 * by every game that implements this interface, so adding AI to a new
 * game never means writing new search/bot-plumbing code again - only
 * a GameModel describing that one game's rules.
 *
 * `S` is the game's state type (e.g. a char[] board), `M` the move
 * type (e.g. an Integer column/cell index). Players are numbered
 * 0, 1, ... rather than a boolean, so this isn't limited to strictly
 * 2-player games if a future game needs more.
 *
 * Contract every implementation must honor:
 *   - legalMoves(state, playerIndex) MUST return at least one move
 *     whenever isTerminal(state) is false. A game with a forced-pass
 *     rule (e.g. Reversi, when a side has no real placement available)
 *     represents that as a single synthetic "pass" move in the
 *     returned list, applied by applyMove as a no-op - this keeps
 *     "passing" a per-game rule detail the shared engine never needs
 *     to know about.
 *   - applyMove/legalMoves/evaluate must never mutate the state object
 *     passed in - return a new one (or the same reference for a true
 *     no-op like a pass) instead, since Minimax explores many
 *     hypothetical futures from the same real state.
 *   - winner(state) is only ever called when isTerminal(state) is
 *     true, and returns null for a draw.
 */
public interface GameModel<S, M>
{
    /** Every legal move for playerIndex from state. Never empty unless isTerminal(state) is true. */
    List<M> legalMoves(S state, int playerIndex);

    /** Returns the resulting state after playerIndex plays move - state itself is left untouched. */
    S applyMove(S state, M move, int playerIndex);

    /** True once the game is over (a win, a draw, or however this game's rules define "no more play"). */
    boolean isTerminal(S state);

    /** Which player moves next, given playerJustMoved just played (from a state already updated by applyMove). Usually alternates, but a game like Dots and Boxes can return the same player again after a qualifying move. */
    int nextPlayer(S state, int playerJustMoved);

    /** A heuristic score for state from forPlayerIndex's perspective - higher is better for that player. Only needs to be meaningful at/near the search depth limit; terminal states are typically scored as large wins/losses. */
    int evaluate(S state, int forPlayerIndex);

    /** The winning player, or null for a draw. Only ever called when isTerminal(state) is true. */
    Integer winner(S state);
}
