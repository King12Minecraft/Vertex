import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ZombieSurvivalMatch
 * --------------------
 * One online Zombie Survival run, 2-4 players. Same "shared seed,
 * independent local simulation" approach as RacingMatch - every
 * player fights the identical spawn sequence in their own window,
 * then reports whether they survived every wave or died first (and
 * on which wave), once their run ends. Unlike Racing there's no
 * placement ranking here - it's co-op in spirit even though each run
 * is simulated locally, so every player who survives all waves gets
 * the full reward independently, not just the fastest one.
 */
public class ZombieSurvivalMatch
{
    private static final String GAME_ID = "zombie-survival";

    private final String matchId;
    private final List<ClientHandler> players;
    private final long seed;
    private final ZombieSurvivalMatchManager matchManager;
    private final EconomyManager economyManager;
    private final AchievementManager achievementManager;
    private final LeaderboardManager leaderboardManager;

    private final Map<ClientHandler, Boolean> wonMap = new HashMap<ClientHandler, Boolean>();
    private final Map<ClientHandler, Integer> waveMap = new HashMap<ClientHandler, Integer>();
    private final Map<ClientHandler, Integer> killsMap = new HashMap<ClientHandler, Integer>();
    private boolean over = false;

    public ZombieSurvivalMatch(String matchId, List<ClientHandler> players, long seed,
                                ZombieSurvivalMatchManager matchManager, EconomyManager economyManager,
                                AchievementManager achievementManager, LeaderboardManager leaderboardManager)
    {
        this.matchId = matchId;
        this.players = players;
        this.seed = seed;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.achievementManager = achievementManager;
        this.leaderboardManager = leaderboardManager;
    }

    public void start()
    {
        List<String> roster = rosterUsernames();
        for (int i = 0; i < players.size(); i++)
        {
            Message msg = new Message();
            msg.setType(MessageType.ZOMBIE_MATCH_FOUND);
            msg.setMatchId(matchId);
            msg.setZombieSeed(seed);
            msg.setZombieRosterUsernames(roster);
            players.get(i).sendMessage(msg);
        }
    }

    private List<String> rosterUsernames()
    {
        List<String> roster = new ArrayList<String>();
        for (int i = 0; i < players.size(); i++)
        {
            roster.add(players.get(i).getLoggedInUsername());
        }
        return roster;
    }

    public synchronized void reportFinished(ClientHandler who, boolean won, int waveReached, int zombiesKilled)
    {
        if (over || !players.contains(who) || wonMap.containsKey(who))
        {
            return;
        }

        wonMap.put(who, won);
        waveMap.put(who, waveReached);
        killsMap.put(who, zombiesKilled);

        if (wonMap.size() == players.size())
        {
            finish();
        }
    }

    private void finish()
    {
        over = true;
        matchManager.endMatch(matchId);

        for (int i = 0; i < players.size(); i++)
        {
            ClientHandler player = players.get(i);
            boolean won = wonMap.get(player);
            int waveReached = waveMap.get(player);
            int kills = killsMap.get(player);

            int reward = 0;
            if (won)
            {
                economyManager.awardWin(player, GAME_ID);
                reward = EconomyConfig.getWinReward(GAME_ID);
            }

            if (player.getAccountId() != null)
            {
                leaderboardManager.recordScore(GAME_ID, player.getAccountId(), kills);
                if (achievementManager != null)
                {
                    achievementManager.checkZombieSurvival(player.getAccountId(), won, waveReached);
                }
            }

            Message msg = new Message();
            msg.setType(MessageType.ZOMBIE_RESULT);
            msg.setMatchId(matchId);
            msg.setZombieWon(won);
            msg.setZombieWaveReached(waveReached);
            msg.setZombieReward(reward);
            msg.setScore(kills);
            player.sendMessage(msg);
        }
    }

    /** A disconnect counts as dying on whatever wave they were last known to be on if they hadn't already reported - the run still finishes for everyone else once all remaining players report in. */
    public synchronized void handleDisconnect(ClientHandler who)
    {
        if (over || !players.contains(who) || wonMap.containsKey(who))
        {
            return;
        }
        reportFinished(who, false, 0, 0);
    }
}
