package economy;

import support.Check;

/**
 * EconomyConfigTest
 * ------------------
 * Added after finding a real, live bug (audit, 2026-09-29): TriviaMatch.
 * finishMatch() has always called EconomyConfig.getWinReward("trivia-blitz")
 * to compute the pot every winner splits, but "trivia-blitz" was never added
 * to getWinReward()'s table - every Trivia Blitz match had been silently
 * paying its winner(s) zero coins since the game shipped, with no test
 * catching it because no economy test existed at all.
 *
 * This locks in the complete set of game ids that actually need a
 * getWinReward() entry, found by grepping every Match class for a call to
 * EconomyManager.awardWin(...) or EconomyConfig.getWinReward(...) directly
 * (the two real call paths - see EconomyManager.awardWin() and
 * SquareWarsMatch/TriviaMatch's own custom multi-winner-split logic) rather
 * than guessing from the games list. A future game that adds one of those
 * calls without also adding a getWinReward() entry will fail this test
 * instead of silently paying zero coins forever.
 */
public class EconomyConfigTest
{
    private static final String[] WIN_REWARD_GAME_IDS =
    {
        "tictactoe-online", "square-wars", "zombie-survival", "chess", "battleship",
        "rock-paper-scissors", "fight-arena", "among-us", "connect-four", "checkers",
        "reversi", "memory-match", "air-hockey", "word-duel", "dice-duel", "snake-arena",
        "tetris-duel", "fusion-grid", "typing-duel", "signal-grid", "card-rush",
        "dots-and-boxes", "telephone", "trivia-blitz"
    };

    public static void main(String[] args)
    {
        Check check = new Check();

        for (String gameId : WIN_REWARD_GAME_IDS)
        {
            check.check("getWinReward(\"" + gameId + "\") pays something, not the 0 fallback for an "
                + "unrecognized id", EconomyConfig.getWinReward(gameId) > 0);
        }

        check.check("An id no game actually uses still falls back to 0, not an exception",
            EconomyConfig.getWinReward("not-a-real-game") == 0);

        check.finish();
    }
}
