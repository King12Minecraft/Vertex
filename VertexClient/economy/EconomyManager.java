package economy;
import net.MessageType;
import net.Message;
import net.ClientHandler;
import account.Account;
import account.ServerAccountStore;

import java.time.LocalDate;
import java.util.List;

public class EconomyManager
{
    public enum PurchaseResult { SUCCESS, INSUFFICIENT_COINS, ALREADY_OWNED, ITEM_NOT_FOUND, NOT_LOGGED_IN }

    private final ServerAccountStore accountStore;
    private final ChallengeManager challengeManager = new ChallengeManager();
    private final TransactionManager transactionManager;
    private AchievementManager achievementManager;

    public EconomyManager(ServerAccountStore accountStore, TransactionManager transactionManager)
    {
        this.accountStore = accountStore;
        this.transactionManager = transactionManager;
    }

    /** Set once from GameServer - lets any coin award check the "High Roller" balance achievement without every award site needing its own separate call. */
    public void setAchievementManager(AchievementManager achievementManager)
    {
        this.achievementManager = achievementManager;
    }

    private void checkCoinAchievement(Account account)
    {
        if (achievementManager != null)
        {
            achievementManager.checkCoinBalance(account.getAccountId(), account.getCoins());
        }
    }

    public ChallengeManager getChallengeManager()
    {
        return challengeManager;
    }

    public List<ShopItemDefinition> getShopItems()
    {
        return EconomyConfig.getShopItems();
    }

    public List<String> getRecentTransactions(int accountId)
    {
        return transactionManager.getRecentDescriptions(accountId);
    }

    public void awardWin(ClientHandler winner, String gameId)
    {
        String username = winner.getLoggedInUsername();
        if (username == null) return;
        Account account = accountStore.findByUsername(username);
        if (account == null) return;

        int reward = EconomyConfig.getWinReward(gameId);
        if (reward > 0)
        {
            account.setCoins(account.getCoins() + reward);
            transactionManager.log(account.getAccountId(), reward, "Won a match");
        }

        recordOnlineWin(account, winner, gameId, reward);
    }

    /**
     * Shared tail of every "this player just won an online match" path - records
     * progress on the generic win-count challenges (whose reward, on completion, is
     * an *additional* coin grant on top of whatever the match itself paid) and sends
     * the resulting wallet/challenge updates. Split out of awardWin so the games that
     * don't route through awardWin (ties/multi-winner games via awardCoins, and
     * placement games where only 1st counts as a win) still make challenge progress
     * instead of that progress being silently unreachable for their players.
     */
    private void recordOnlineWin(Account account, ClientHandler winner, String gameId, int matchReward)
    {
        List<ChallengeProgressInfo> changedChallenges = challengeManager.recordWin(account, gameId);
        accountStore.updateAccount(account);
        checkCoinAchievement(account);

        if (matchReward > 0)
        {
            Message walletUpdate = new Message();
            walletUpdate.setType(MessageType.WALLET_UPDATE);
            walletUpdate.setCoins(account.getCoins());
            winner.sendMessage(walletUpdate);
        }

        if (!changedChallenges.isEmpty())
        {
            Message challengeUpdate = new Message();
            challengeUpdate.setType(MessageType.CHALLENGE_UPDATE);
            challengeUpdate.setChallenges(changedChallenges);
            winner.sendMessage(challengeUpdate);

            if (matchReward == 0 && account.getCoins() > 0)
            {
                Message walletUpdate = new Message();
                walletUpdate.setType(MessageType.WALLET_UPDATE);
                walletUpdate.setCoins(account.getCoins());
                winner.sendMessage(walletUpdate);
            }
        }
    }

    /**
     * For online games where several players can win at once (ties) and there's no
     * single EconomyConfig.getWinReward(gameId) lookup - the caller already computed
     * a per-winner split. Otherwise identical to awardWin: pays the coins, then
     * records the same generic win-count challenge progress.
     */
    public void awardMatchWinCoins(ClientHandler winner, String gameId, int amount, String reason)
    {
        String username = winner.getLoggedInUsername();
        if (username == null || amount <= 0) return;
        Account account = accountStore.findByUsername(username);
        if (account == null) return;

        account.setCoins(account.getCoins() + amount);
        transactionManager.log(account.getAccountId(), amount, reason);

        recordOnlineWin(account, winner, gameId, amount);
    }

    /**
     * Placement reward for a race/FFA-style game with no single 2-player "winner" -
     * 1st/2nd/3rd only. Returns the coins awarded (0 if outside the top 3) so the
     * caller (RacingMatch/SpaceBattleMatch) can include it directly in its own result
     * message, without a second round-trip to find out what happened. activityLabel
     * is the human-readable phrase for the transaction log ("a race", "a Space
     * Battle") - everything else about the two games' placement rewards was
     * byte-identical duplication (awardRacingPlacement/awardSpaceBattlePlacement),
     * so this replaces both with one method instead of two copies that happen to
     * agree.
     */
    public int awardPlacement(ClientHandler player, String gameId, int place, String activityLabel)
    {
        String username = player.getLoggedInUsername();
        if (username == null) return 0;
        Account account = accountStore.findByUsername(username);
        if (account == null) return 0;

        int reward = EconomyConfig.getPlacementReward(place);
        if (reward <= 0)
        {
            return 0;
        }

        account.setCoins(account.getCoins() + reward);
        transactionManager.log(account.getAccountId(), reward, "Finished " + placeOrdinal(place) + " in " + activityLabel);

        if (place == 1)
        {
            // 1st place is this match's "win" for the generic win-count challenges -
            // a placement game has no single ClientHandler "winner" the way a
            // 2-player match does, so it's recorded here instead of through awardWin.
            recordOnlineWin(account, player, gameId, reward);
        }
        else
        {
            accountStore.updateAccount(account);
            checkCoinAchievement(account);

            Message walletUpdate = new Message();
            walletUpdate.setType(MessageType.WALLET_UPDATE);
            walletUpdate.setCoins(account.getCoins());
            player.sendMessage(walletUpdate);
        }

        return reward;
    }

    private String placeOrdinal(int place)
    {
        if (place == 1) return "1st";
        if (place == 2) return "2nd";
        if (place == 3) return "3rd";
        return place + "th";
    }

    /** Generic coin grant with an arbitrary amount and a plain-text reason for the transaction log - for games like Square Wars where the reward is a computed split (total prize / number of tied winners) rather than one of the fixed per-game formulas the other award* methods use. */
    public void awardCoins(ClientHandler player, int amount, String reason)
    {
        String username = player.getLoggedInUsername();
        if (username == null || amount <= 0) return;
        Account account = accountStore.findByUsername(username);
        if (account == null) return;

        account.setCoins(account.getCoins() + amount);
        transactionManager.log(account.getAccountId(), amount, reason);
        accountStore.updateAccount(account);
        checkCoinAchievement(account);

        Message walletUpdate = new Message();
        walletUpdate.setType(MessageType.WALLET_UPDATE);
        walletUpdate.setCoins(account.getCoins());
        player.sendMessage(walletUpdate);
    }

    /** Generic score-based reward for any offline/practice game, driven entirely by EconomyConfig.getPracticeReward's per-game formula table (Snake included, folded in there - see EconomyKernel.awardCompletion, the one-line entry point every future practice-mode game should call). */
    public void awardPracticeScore(ClientHandler player, String gameId, int score)
    {
        String username = player.getLoggedInUsername();
        if (username == null) return;
        Account account = accountStore.findByUsername(username);
        if (account == null) return;

        int reward = EconomyConfig.getPracticeReward(gameId, score);
        if (reward <= 0)
        {
            return;
        }

        account.setCoins(account.getCoins() + reward);
        transactionManager.log(account.getAccountId(), reward, "Practice reward");
        accountStore.updateAccount(account);
        checkCoinAchievement(account);

        Message walletUpdate = new Message();
        walletUpdate.setType(MessageType.WALLET_UPDATE);
        walletUpdate.setCoins(account.getCoins());
        player.sendMessage(walletUpdate);
    }


    public synchronized PurchaseResult purchase(ClientHandler buyer, String itemId, int[] outNewBalance)
    {
        String username = buyer.getLoggedInUsername();
        if (username == null) return PurchaseResult.NOT_LOGGED_IN;
        Account account = accountStore.findByUsername(username);
        if (account == null) return PurchaseResult.NOT_LOGGED_IN;

        ShopItemDefinition item = findItem(itemId);
        if (item == null) return PurchaseResult.ITEM_NOT_FOUND;
        if (account.getOwnedItemIds().contains(itemId)) return PurchaseResult.ALREADY_OWNED;
        if (account.getCoins() < item.priceCoins) return PurchaseResult.INSUFFICIENT_COINS;

        account.setCoins(account.getCoins() - item.priceCoins);
        account.getOwnedItemIds().add(itemId);
        transactionManager.log(account.getAccountId(), -item.priceCoins, "Purchased " + item.name);
        accountStore.updateAccount(account);

        outNewBalance[0] = account.getCoins();
        return PurchaseResult.SUCCESS;
    }

    public synchronized int applyDailyLoginReward(Account account)
    {
        LocalDate today = LocalDate.now();
        String todayString = today.toString();

        if (todayString.equals(account.getLastLoginDate()))
        {
            return 0;
        }

        LocalDate yesterday = today.minusDays(1);
        boolean consecutive = yesterday.toString().equals(account.getLastLoginDate());
        int newStreak = consecutive ? account.getLoginStreak() + 1 : 1;

        int reward = EconomyConfig.getDailyLoginReward(newStreak);

        account.setLastLoginDate(todayString);
        account.setLoginStreak(newStreak);
        account.setCoins(account.getCoins() + reward);
        transactionManager.log(account.getAccountId(), reward, "Daily login reward (Day " + newStreak + " streak)");
        accountStore.updateAccount(account);
        checkCoinAchievement(account);

        return reward;
    }

    private ShopItemDefinition findItem(String itemId)
    {
        List<ShopItemDefinition> items = EconomyConfig.getShopItems();
        for (int i = 0; i < items.size(); i++)
        {
            if (items.get(i).id.equals(itemId)) return items.get(i);
        }
        return null;
    }
}
