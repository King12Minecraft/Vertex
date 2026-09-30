package economy;

import support.Check;

/**
 * PracticeRewardLimiterTest
 * --------------------------
 * The rate limit on coins from offline games (whose scores the server cannot verify): a game pays at
 * most once per interval per account, an account earns at most the daily cap in total, the cap is
 * per account and resets the next day, and a zero reward is never "paid". Time is driven by a fake
 * clock, so nothing waits.
 */
public class PracticeRewardLimiterTest
{
    public static void main(String[] args)
    {
        Check check = new Check();
        final long[] now = { 1_000_000_000_000L };
        PracticeRewardLimiter limiter = new PracticeRewardLimiter(new java.util.function.LongSupplier()
        {
            public long getAsLong() { return now[0]; }
        });

        check.check("the first payout is allowed in full", limiter.allow(1, "snake", 25) == 25);
        check.check("the same game again straight away is refused", limiter.allow(1, "snake", 25) == 0);
        now[0] += PracticeRewardLimiter.MIN_INTERVAL_MS - 1;
        check.check("...and still refused just inside the interval", limiter.allow(1, "snake", 25) == 0);
        check.check("a different game isn't held up by that one", limiter.allow(1, "tetris", 30) == 30);
        check.check("another account isn't held up either", limiter.allow(2, "snake", 25) == 25);
        now[0] += 2;
        check.check("after the interval the same game pays again", limiter.allow(1, "snake", 25) == 25);
        check.check("a zero reward is never paid or recorded", limiter.allow(1, "sudoku", 0) == 0 && limiter.allow(1, "sudoku", 30) == 30);

        // run the cap down: keep paying 30 from rotating games, each past its own interval
        PracticeRewardLimiter capped = new PracticeRewardLimiter(new java.util.function.LongSupplier()
        {
            public long getAsLong() { return now[0]; }
        });
        int total = 0;
        int rounds = 0;
        while (rounds < 100)
        {
            now[0] += PracticeRewardLimiter.MIN_INTERVAL_MS + 1;
            int got = capped.allow(7, "klondike", 40);
            if (got == 0) break;
            total += got;
            rounds++;
        }
        check.check("total payouts stop exactly at the daily cap (" + total + ")", total == PracticeRewardLimiter.DAILY_CAP);
        check.check("the last payout was trimmed to fit rather than refused (300 = 7 x 40 + 20)", rounds == 8);
        now[0] += PracticeRewardLimiter.MIN_INTERVAL_MS + 1;
        check.check("once the cap is used up nothing more is paid that day", capped.allow(7, "tetris", 30) == 0);
        check.check("another account has its own cap", capped.allow(8, "klondike", 40) == 40);

        now[0] += 24L * 60 * 60 * 1000;
        check.check("the next day the cap resets", capped.allow(7, "klondike", 40) == 40);

        check.finish();
    }
}
