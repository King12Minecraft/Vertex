import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SpaceBattleMatch
 * ----------------
 * One online Space Battle run, 3-6 pilots - same shape as RacingMatch.
 * Every pilot gets the identical seed and fights the identical
 * asteroid/enemy spawn sequence over SpaceBattleGame.MATCH_FRAMES,
 * independently and locally, then reports their final score once
 * their run ends (either the clock ran out, or their hull was
 * destroyed early). Ranked purely by score, highest first; only
 * 1st/2nd/3rd get a coin reward, same placement structure as Racing.
 */
public class SpaceBattleMatch
{
    private static final String GAME_ID = "space-battle";

    private final String matchId;
    private final List<ClientHandler> pilots;
    private final long seed;
    private final SpaceBattleMatchManager matchManager;
    private final EconomyManager economyManager;
    private final AchievementManager achievementManager;
    private final LeaderboardManager leaderboardManager;

    private final Map<ClientHandler, Integer> scoreMap = new HashMap<ClientHandler, Integer>();
    private boolean over = false;

    public SpaceBattleMatch(String matchId, List<ClientHandler> pilots, long seed,
                             SpaceBattleMatchManager matchManager, EconomyManager economyManager,
                             AchievementManager achievementManager, LeaderboardManager leaderboardManager)
    {
        this.matchId = matchId;
        this.pilots = pilots;
        this.seed = seed;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.achievementManager = achievementManager;
        this.leaderboardManager = leaderboardManager;
    }

    public void start()
    {
        List<String> roster = rosterUsernames();
        for (int i = 0; i < pilots.size(); i++)
        {
            Message msg = new Message();
            msg.setType(MessageType.SPACE_MATCH_FOUND);
            msg.setMatchId(matchId);
            msg.setSpaceSeed(seed);
            msg.setSpaceRosterUsernames(roster);
            pilots.get(i).sendMessage(msg);
        }
    }

    private List<String> rosterUsernames()
    {
        List<String> roster = new ArrayList<String>();
        for (int i = 0; i < pilots.size(); i++)
        {
            roster.add(pilots.get(i).getLoggedInUsername());
        }
        return roster;
    }

    public synchronized void reportFinished(ClientHandler who, int score)
    {
        if (over || !pilots.contains(who) || scoreMap.containsKey(who))
        {
            return;
        }

        scoreMap.put(who, score);

        if (scoreMap.size() == pilots.size())
        {
            finish();
        }
    }

    private void finish()
    {
        over = true;
        matchManager.endMatch(matchId);

        List<ClientHandler> ranked = new ArrayList<ClientHandler>(pilots);
        ranked.sort(new Comparator<ClientHandler>()
        {
            public int compare(ClientHandler a, ClientHandler b)
            {
                return Integer.compare(scoreMap.get(b), scoreMap.get(a));
            }
        });

        List<String> roster = rosterUsernames();

        for (int i = 0; i < ranked.size(); i++)
        {
            int place = i + 1;
            ClientHandler pilot = ranked.get(i);
            int score = scoreMap.get(pilot);
            int reward = economyManager.awardSpaceBattlePlacement(pilot, place);

            if (pilot.getAccountId() != null)
            {
                leaderboardManager.recordScore(GAME_ID, pilot.getAccountId(), score);
                if (achievementManager != null)
                {
                    achievementManager.checkSpaceBattlePlacement(pilot.getAccountId(), place);
                }
            }

            Message msg = new Message();
            msg.setType(MessageType.SPACE_RESULT);
            msg.setMatchId(matchId);
            msg.setSpacePlace(place);
            msg.setSpaceReward(reward);
            msg.setSpaceRosterUsernames(roster);
            msg.setScore(score);
            pilot.sendMessage(msg);
        }
    }

    /** A disconnect counts as reporting whatever score they had (0 if they hadn't reported at all) - the match still finishes for everyone else once all remaining pilots report in. */
    public synchronized void handleDisconnect(ClientHandler who)
    {
        if (over || !pilots.contains(who) || scoreMap.containsKey(who))
        {
            return;
        }
        reportFinished(who, 0);
    }
}
