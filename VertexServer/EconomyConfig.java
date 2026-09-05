import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class EconomyConfig
{
    private EconomyConfig()
    {
        // Static utility class - never instantiated.
    }

    public static int getWinReward(String gameId)
    {
        if ("tictactoe-online".equals(gameId)) return 15;
        if ("square-wars".equals(gameId))      return 25;
        if ("zombie-survival".equals(gameId))  return 30;
        if ("chess".equals(gameId))            return 20;
        if ("battleship".equals(gameId))       return 20;
        if ("rock-paper-scissors".equals(gameId)) return 15;
        if ("fight-arena".equals(gameId))      return 25;
        if ("among-us".equals(gameId))         return 20;
        return 0;
    }

    /** Flat reward for solving a Puzzle Quest puzzle - this game has no score concept (just move count), so there's nothing to scale a reward against. */
    public static final int PUZZLE_QUEST_REWARD = 10;

    /**
     * Score-based rewards for the remaining practice-mode games that
     * previously paid nothing at all (see SNAKE's own divisor/cap
     * above for the same idea) - divisors/caps are tuned per game to
     * roughly match each one's typical score scale, so a good round
     * anywhere is worth a broadly similar number of coins.
     */
    public static int getPracticeReward(String gameId, int score)
    {
        if (score <= 0)
        {
            return 0;
        }
        if ("pingpong".equals(gameId))       return Math.min(20, score * 2);
        if ("2048".equals(gameId))           return Math.min(30, score / 150);
        if ("dino-dash".equals(gameId))      return Math.min(25, score / 8);
        if ("tetris".equals(gameId))         return Math.min(35, score / 300);
        if ("crossing-road".equals(gameId))  return Math.min(25, score / 3);
        if ("aim-trainer".equals(gameId))    return Math.min(20, score);
        return 0;
    }

    /** Racing's placement rewards - 1st/2nd/3rd only, matching "first second and third get coins". Everyone else in the race gets nothing (they still had a real race, just no reward). */
    public static int getRacingPlacementReward(int place)
    {
        if (place == 1) return 50;
        if (place == 2) return 30;
        if (place == 3) return 15;
        return 0;
    }

    /** Space Battle's placement rewards - same 1st/2nd/3rd-only structure as Racing. */
    public static int getSpaceBattlePlacementReward(int place)
    {
        if (place == 1) return 50;
        if (place == 2) return 30;
        if (place == 3) return 15;
        return 0;
    }

    private static final int SNAKE_COINS_PER_POINTS = 5;
    private static final int SNAKE_MAX_REWARD = 25;

    public static int getSnakeReward(int score)
    {
        if (score <= 0)
        {
            return 0;
        }
        int reward = score / SNAKE_COINS_PER_POINTS;
        return Math.min(reward, SNAKE_MAX_REWARD);
    }

    /** Day 1-7 of a weekly cycle - wraps back to day 1's reward on day 8, 15, etc. */
    private static final int[] DAILY_LOGIN_REWARDS = { 10, 15, 20, 25, 30, 40, 50 };

    public static int getDailyLoginReward(int streakDay)
    {
        int index = (streakDay - 1) % DAILY_LOGIN_REWARDS.length;
        if (index < 0)
        {
            index = 0;
        }
        return DAILY_LOGIN_REWARDS[index];
    }

    public static List<ChallengeDefinition> getChallengeDefinitions()
    {
        List<ChallengeDefinition> list = new ArrayList<ChallengeDefinition>();

        list.add(new ChallengeDefinition("daily-win-1", "Win a Match",
            "Win any 1 online match.", 1, 10, ResetPeriod.DAILY, null));

        list.add(new ChallengeDefinition("daily-win-3", "Triple Threat",
            "Win 3 online matches.", 3, 30, ResetPeriod.DAILY, null));

        list.add(new ChallengeDefinition("weekly-win-10", "Champion",
            "Win 10 online matches this week.", 10, 100, ResetPeriod.WEEKLY, null));

        list.add(new ChallengeDefinition("tictactoe-win-5", "Tic-Tac-Toe Ace",
            "Win 5 games of Tic-Tac-Toe Online.", 5, 40, ResetPeriod.NONE, "tictactoe-online"));

        return list;
    }

    public static List<ShopItemDefinition> getShopItems()
    {
        List<ShopItemDefinition> list = new ArrayList<ShopItemDefinition>();

        list.add(new ShopItemDefinition("color-cyan", "Cyan", 500, "#38BDF8", "COLOR"));
        list.add(new ShopItemDefinition("color-rose", "Rose", 500, "#F26B8F", "COLOR"));
        list.add(new ShopItemDefinition("color-gold", "Gold", 750, "#FFCF53", "COLOR"));
        list.add(new ShopItemDefinition("color-violet", "Violet", 1000, "#A78BFA", "COLOR"));

        list.add(new ShopItemDefinition("badge-star", "Star", 300, "\u2605", "BADGE"));
        list.add(new ShopItemDefinition("badge-crown", "Crown", 800, "\u265B", "BADGE"));
        list.add(new ShopItemDefinition("badge-fire", "Fire", 600, "\uD83D\uDD25", "BADGE"));
        list.add(new ShopItemDefinition("badge-diamond", "Diamond", 900, "\u2666", "BADGE"));
        list.add(new ShopItemDefinition("badge-skull", "Skull", 700, "\uD83D\uDC80", "BADGE"));

        return list;
    }
}

class ChallengeDefinition
{
    final String id;
    final String title;
    final String description;
    final int target;
    final int rewardCoins;
    final ResetPeriod resetPeriod;
    final String gameIdFilter;

    ChallengeDefinition(String id, String title, String description, int target,
                         int rewardCoins, ResetPeriod resetPeriod, String gameIdFilter)
    {
        this.id = id;
        this.title = title;
        this.description = description;
        this.target = target;
        this.rewardCoins = rewardCoins;
        this.resetPeriod = resetPeriod;
        this.gameIdFilter = gameIdFilter;
    }

    boolean appliesToWin(String gameId)
    {
        return gameIdFilter == null || gameIdFilter.equals(gameId);
    }
}

enum ResetPeriod
{
    DAILY, WEEKLY, NONE;

    LocalDate periodStartFor(LocalDate today)
    {
        if (this == DAILY)
        {
            return today;
        }
        if (this == WEEKLY)
        {
            return today.minusDays(today.getDayOfWeek().getValue() - 1);
        }
        return LocalDate.of(2000, 1, 1);
    }
}

class ShopItemDefinition
{
    final String id;
    final String name;
    final int priceCoins;
    final String colorHex;
    /** "COLOR" or "BADGE" - which cosmetic slot this item occupies. */
    final String type;

    ShopItemDefinition(String id, String name, int priceCoins, String colorHex, String type)
    {
        this.id = id;
        this.name = name;
        this.priceCoins = priceCoins;
        this.colorHex = colorHex;
        this.type = type;
    }
}
