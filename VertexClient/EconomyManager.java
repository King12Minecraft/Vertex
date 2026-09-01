import java.time.LocalDate;
import java.util.List;

public class EconomyManager
{
    public enum PurchaseResult { SUCCESS, INSUFFICIENT_COINS, ALREADY_OWNED, ITEM_NOT_FOUND, NOT_LOGGED_IN }

    private final ServerAccountStore accountStore;
    private final ChallengeManager challengeManager = new ChallengeManager();
    private final TransactionManager transactionManager;
    private AchievementManager achievementManager;
    private SyncService syncService;

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

    /** Set once from GameServer - lets every coin-changing method (award*, purchase, applyDailyLoginReward) trigger a background sync automatically via the shared checkCoinAchievement() hook below, same pattern as LeaderboardManager/AchievementManager's own setSyncService(). */
    public void setSyncService(SyncService syncService)
    {
        this.syncService = syncService;
    }

    private void checkCoinAchievement(Account account)
    {
        if (achievementManager != null)
        {
            achievementManager.checkCoinBalance(account.getAccountId(), account.getCoins());
        }
        if (syncService != null)
        {
            syncService.syncAccountAsync(account.getAccountId());
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

        List<ChallengeProgressInfo> changedChallenges = challengeManager.recordWin(account, gameId);
        accountStore.updateAccount(account);
        checkCoinAchievement(account);

        if (reward > 0)
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

            if (reward == 0 && account.getCoins() > 0)
            {
                Message walletUpdate = new Message();
                walletUpdate.setType(MessageType.WALLET_UPDATE);
                walletUpdate.setCoins(account.getCoins());
                winner.sendMessage(walletUpdate);
            }
        }
    }

    /**
     * Racing placement reward - 1st/2nd/3rd only. Returns the coins
     * awarded (0 if outside the top 3) so RacingMatch can include it
     * directly in the RACE_RESULT sent to that player, without a
     * second round-trip to find out what happened.
     */
    public int awardRacingPlacement(ClientHandler racer, int place)
    {
        String username = racer.getLoggedInUsername();
        if (username == null) return 0;
        Account account = accountStore.findByUsername(username);
        if (account == null) return 0;

        int reward = EconomyConfig.getRacingPlacementReward(place);
        if (reward <= 0)
        {
            return 0;
        }

        account.setCoins(account.getCoins() + reward);
        transactionManager.log(account.getAccountId(), reward, "Finished " + placeOrdinal(place) + " in a race");
        accountStore.updateAccount(account);
        checkCoinAchievement(account);

        Message walletUpdate = new Message();
        walletUpdate.setType(MessageType.WALLET_UPDATE);
        walletUpdate.setCoins(account.getCoins());
        racer.sendMessage(walletUpdate);

        return reward;
    }

    private String placeOrdinal(int place)
    {
        if (place == 1) return "1st";
        if (place == 2) return "2nd";
        if (place == 3) return "3rd";
        return place + "th";
    }

    public void awardSnakeScore(ClientHandler player, int score)
    {
        String username = player.getLoggedInUsername();
        if (username == null) return;
        Account account = accountStore.findByUsername(username);
        if (account == null) return;

        int reward = EconomyConfig.getSnakeReward(score);
        if (reward <= 0)
        {
            return;
        }

        account.setCoins(account.getCoins() + reward);
        transactionManager.log(account.getAccountId(), reward, "Snake score reward");
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
        if (syncService != null)
        {
            syncService.syncAccountAsync(account.getAccountId());
        }

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
