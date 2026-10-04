package economy;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * PracticeRewardLimiter
 * ---------------------
 * The offline games (Snake, Tetris, 2048, Minesweeper, ...) run entirely on the player's own
 * computer and report a score afterwards, so the server cannot check that a round happened - a
 * modified client can send "I scored the maximum" as often as it likes. The per-submission cap in
 * EconomyConfig.getPracticeReward limits one report but not how many are sent. This limits the
 * reports: a game can pay out at most once every MIN_INTERVAL_MS per account, and an account can
 * earn at most DAILY_CAP coins a day from offline games in total. Together they turn "unlimited
 * coins" into "about what an honest day of playing earns", without needing to trust anything the
 * client says about what it did. Online matches are unaffected - the server decides those itself.
 *
 * In memory only: a server restart resets it, which is fine for an abuse limit. The two numbers are
 * reversible defaults (see ROADMAP.md) - raise or lower them here.
 */
public class PracticeRewardLimiter
{
    /** Fast enough not to bother a real round (the shortest are well over this), slow enough to stop a script. */
    public static final long MIN_INTERVAL_MS = 15000;
    /** Roughly 10-15 good rounds of the highest-paying games per day. */
    public static final int DAILY_CAP = 300;

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private final LongSupplier clock;
    private final Map<String, Long> lastPayoutAt = new HashMap<String, Long>();
    /** accountId -> { dayNumber, coinsEarnedThatDay } */
    private final Map<Integer, long[]> daily = new HashMap<Integer, long[]>();

    public PracticeRewardLimiter()
    {
        this(new LongSupplier()
        {
            public long getAsLong() { return System.currentTimeMillis(); }
        });
    }

    /** Test seam: lets a test move time forward instead of waiting. */
    public PracticeRewardLimiter(LongSupplier clock)
    {
        this.clock = clock;
    }

    /**
     * How many of `reward` coins this account may be paid right now for this game: 0 if it was paid for
     * this game less than MIN_INTERVAL_MS ago or today's cap is used up, less than `reward` if the cap
     * is nearly reached, otherwise all of it. A non-zero answer is recorded as paid.
     */
    public synchronized int allow(int accountId, String gameId, int reward)
    {
        if (reward <= 0)
        {
            return 0;
        }
        long now = clock.getAsLong();

        String key = accountId + "|" + gameId;
        Long last = lastPayoutAt.get(key);
        if (last != null && now - last < MIN_INTERVAL_MS)
        {
            return 0;
        }

        long today = now / DAY_MS;
        long[] entry = daily.get(accountId);
        if (entry == null || entry[0] != today)
        {
            entry = new long[] { today, 0 };
            daily.put(accountId, entry);
        }
        int remaining = (int) Math.max(0, DAILY_CAP - entry[1]);
        int granted = Math.min(reward, remaining);
        if (granted <= 0)
        {
            return 0;
        }

        entry[1] += granted;
        lastPayoutAt.put(key, now);
        return granted;
    }
}
