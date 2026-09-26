package economy;
import net.ClientHandler;

/**
 * EconomyKernel
 * -------------
 * The one class a game's server-side code should need to read to answer
 * "how do I pay this player?" - before this existed, that answer was
 * scattered across a dozen similarly-shaped but separately-named methods
 * on EconomyManager (awardSnakeScore, awardPuzzleQuestCompletion,
 * awardMinesweeperCompletion, awardRacingPlacement,
 * awardSpaceBattlePlacement, ...), several of which turned out to be
 * byte-identical duplicates of each other once compared side by side
 * (see EconomyManager.awardPlacement's javadoc, and
 * EconomyConfig.getPracticeReward's Snake entry) - a new game's author
 * had no way to tell, without reading all of them, whether a shape they
 * needed already existed under a different game's name.
 *
 * Every game's reward need is one of exactly four shapes. Pick the one
 * that matches instead of writing a new EconomyManager method:
 *
 *   1. {@link #awardCompletion} - an offline/practice game with a numeric
 *      score to scale the reward from. Add the formula to
 *      EconomyConfig.getPracticeReward and call this - nothing else
 *      needs an edit for a new game.
 *   2. {@link #awardFlatCompletion} - an offline/practice game with
 *      nothing meaningful to score (solved-or-not, like Sudoku or
 *      Minesweeper) - a fixed reward regardless of how it went.
 *   3. {@link #awardMatchWin} - the winner of a standard online match
 *      with exactly one winner (most 2-player games; also fine for a
 *      match with no winner at all, in which case just don't call it).
 *   4. {@link #awardPlacement} - a race/FFA-style game where several
 *      players finish in ranked order and only the top 3 are rewarded.
 *
 * A fifth, rarer shape - several players TIE for the win and split a
 * pool (Square Wars, Trivia Blitz) - has no kernel wrapper yet since only
 * two games need it and their split math is genuinely per-game; call
 * EconomyManager.awardMatchWinCoins directly for that one, with the
 * per-winner amount you've already computed.
 *
 * Deliberately a static utility, not an injected instance - EconomyManager
 * is still the one already threaded through every match/handler
 * constructor as a live dependency (that plumbing is unrelated to this;
 * see PROGRAM_STRUCTURE.md), and every kernel method here is a pure,
 * stateless one-line delegation to it. Same "static facade over an
 * existing manager" shape as PerformanceMode/EconomyConfig already use
 * elsewhere in this codebase.
 */
public final class EconomyKernel
{
    private EconomyKernel()
    {
        // Static utility class - never instantiated.
    }

    /** Shape 1: score-scaled reward for a completed offline/practice game. Add gameId's formula to EconomyConfig.getPracticeReward - this method itself never needs a per-game change. */
    public static void awardCompletion(EconomyManager economyManager, ClientHandler player, String gameId, int score)
    {
        economyManager.awardPracticeScore(player, gameId, score);
    }

    /** Shape 2: flat reward for a game with nothing meaningful to score - just "did they finish it." */
    public static void awardFlatCompletion(EconomyManager economyManager, ClientHandler player, int amount, String reason)
    {
        economyManager.awardCoins(player, amount, reason);
    }

    /** Shape 3: the winner of a standard online match with exactly one winner. */
    public static void awardMatchWin(EconomyManager economyManager, ClientHandler winner, String gameId)
    {
        economyManager.awardWin(winner, gameId);
    }

    /** Shape 4: 1st/2nd/3rd placement reward for a race/FFA-style game. Returns the coins actually awarded (0 outside the top 3) so the caller can report it in its own result message. */
    public static int awardPlacement(EconomyManager economyManager, ClientHandler player, String gameId, int place, String activityLabel)
    {
        return economyManager.awardPlacement(player, gameId, place, activityLabel);
    }
}
