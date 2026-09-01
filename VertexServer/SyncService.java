import java.util.ArrayList;
import java.util.List;

/**
 * SyncService
 * -----------
 * The single place LeaderboardManager/AchievementManager/EconomyManager
 * call into whenever something worth syncing changes for an account
 * (a rating update, a new achievement, a coin change) - gathers that
 * account's full current state and pushes it to the main server on a
 * background thread, so gameplay never blocks on a network round trip.
 *
 * mainServerConnection is null until GameServer.setMainServer() is
 * called (or forever, if this server IS the main server) - every
 * method here is a safe no-op in that case, so managers can call
 * syncAccountAsync() unconditionally without needing to check "am I
 * on a satellite" themselves.
 */
public class SyncService
{
    private final ServerAccountStore accountStore;
    private final LeaderboardManager leaderboardManager;
    private final AchievementManager achievementManager;
    private MainServerConnection mainServerConnection;

    public SyncService(ServerAccountStore accountStore, LeaderboardManager leaderboardManager, AchievementManager achievementManager)
    {
        this.accountStore = accountStore;
        this.leaderboardManager = leaderboardManager;
        this.achievementManager = achievementManager;
    }

    public void setMainServerConnection(MainServerConnection connection)
    {
        this.mainServerConnection = connection;
    }

    /** Gathers this account's current Account/ratings/achievements and pushes them to main on a background thread. Safe to call from anywhere, anytime - a no-op if this server isn't a satellite (mainServerConnection null), and any push failure (main unreachable) is swallowed here rather than surfaced, since a satellite should keep running its own local games regardless of whether main happens to be reachable at this exact moment. */
    public void syncAccountAsync(final int accountId)
    {
        if (mainServerConnection == null)
        {
            return;
        }

        Thread pushThread = new Thread(new Runnable()
        {
            public void run()
            {
                Account account = accountStore.findById(accountId);
                if (account == null)
                {
                    return;
                }
                List<String> ratings = leaderboardManager.getAllRatingsForAccount(accountId);
                List<String> unlocked = new ArrayList<String>(achievementManager.getUnlocked(accountId));
                mainServerConnection.pushToMain(account, ratings, unlocked);
            }
        });
        pushThread.setDaemon(true);
        pushThread.start();
    }
}
