package ai;

/**
 * BotStrategy
 * -----------
 * The single interface every reusable bot/opponent AI in Vertex
 * implements, whatever game it's for. `S` is the (game-specific) state
 * type the strategy needs to see to decide, and `A` is the
 * (game-specific) action/move type it returns.
 *
 * Deliberately minimal for this first slice of the shared AI layer:
 * one method, no assumptions about turn order, board shape, or
 * scoring - those all live in the state type a particular game
 * chooses. A strategy MUST NOT mutate the state it's given; it only
 * reads it and returns a proposed move. The caller's own,
 * already-validated game logic is what actually applies that move -
 * this keeps every game (and, for multiplayer games, the server)
 * authoritative over its own rules, with the AI layer purely advisory.
 *
 * A strategy is also allowed to throw - AiKernel.chooseMove() is the
 * safety net that guarantees a match is never left stuck because of
 * it; implementations don't need to defensively catch their own bugs.
 */
public interface BotStrategy<S, A>
{
    A chooseMove(S state);
}
