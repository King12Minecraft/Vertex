import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RacingMatch
 * -----------
 * One online race, 3-6 players. Deliberately NOT live position sync -
 * that would mean broadcasting every racer's lane/obstacle state many
 * times a second, a fundamentally bigger real-time protocol than
 * anything else in Vertex (including Tic-Tac-Toe's turn-based one).
 * Instead: every racer gets the SAME seed (see RacingGame's seeded
 * constructor), so they all face the identical obstacle sequence and
 * the identical finish line (RacingGame.FINISH_FRAMES). Each races
 * independently and locally, then reports whether they finished (and
 * how fast) or crashed (and how far they got) once their run ends.
 * Ranking: everyone who finished ranks above everyone who crashed,
 * finishers sorted fastest-first, crashers sorted by who survived
 * longest. Only 1st/2nd/3rd get a coin reward.
 */
public class RacingMatch
{
    private final String matchId;
    private final List<ClientHandler> racers;
    private final long seed;
    private final RacingMatchManager matchManager;
    private final EconomyManager economyManager;
    private final AchievementManager achievementManager;

    private final Map<ClientHandler, Boolean> finishedMap = new HashMap<ClientHandler, Boolean>();
    private final Map<ClientHandler, Integer> frameCountMap = new HashMap<ClientHandler, Integer>();
    private boolean over = false;

    public RacingMatch(String matchId, List<ClientHandler> racers, long seed,
                        RacingMatchManager matchManager, EconomyManager economyManager, AchievementManager achievementManager)
    {
        this.matchId = matchId;
        this.racers = racers;
        this.seed = seed;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.achievementManager = achievementManager;
    }

    public void start()
    {
        List<String> roster = rosterUsernames();
        for (int i = 0; i < racers.size(); i++)
        {
            Message msg = new Message();
            msg.setType(MessageType.RACE_MATCH_FOUND);
            msg.setMatchId(matchId);
            msg.setRaceSeed(seed);
            msg.setRaceRosterUsernames(roster);
            racers.get(i).sendMessage(msg);
        }
    }

    private List<String> rosterUsernames()
    {
        List<String> roster = new ArrayList<String>();
        for (int i = 0; i < racers.size(); i++)
        {
            roster.add(racers.get(i).getLoggedInUsername());
        }
        return roster;
    }

    public synchronized void reportFinished(ClientHandler who, boolean finished, int frameCount)
    {
        if (over || !racers.contains(who) || finishedMap.containsKey(who))
        {
            return;
        }

        finishedMap.put(who, finished);
        frameCountMap.put(who, frameCount);

        if (finishedMap.size() == racers.size())
        {
            finish();
        }
    }

    private void finish()
    {
        over = true;
        matchManager.endMatch(matchId);

        List<ClientHandler> ranked = new ArrayList<ClientHandler>(racers);
        ranked.sort(new Comparator<ClientHandler>()
        {
            public int compare(ClientHandler a, ClientHandler b)
            {
                boolean aFinished = finishedMap.get(a);
                boolean bFinished = finishedMap.get(b);
                if (aFinished != bFinished)
                {
                    return aFinished ? -1 : 1;
                }
                int aFrames = frameCountMap.get(a);
                int bFrames = frameCountMap.get(b);
                return aFinished ? Integer.compare(aFrames, bFrames) : Integer.compare(bFrames, aFrames);
            }
        });

        List<String> roster = rosterUsernames();

        for (int i = 0; i < ranked.size(); i++)
        {
            int place = i + 1;
            ClientHandler racer = ranked.get(i);
            int reward = economyManager.awardRacingPlacement(racer, place);

            if (achievementManager != null && racer.getAccountId() != null)
            {
                achievementManager.checkRacingPlacement(racer.getAccountId(), place);
            }

            Message msg = new Message();
            msg.setType(MessageType.RACE_RESULT);
            msg.setMatchId(matchId);
            msg.setRacePlace(place);
            msg.setRaceReward(reward);
            msg.setRaceRosterUsernames(roster);
            racer.sendMessage(msg);
        }
    }

    /** A disconnect counts as the worst possible crash (0 frames survived) if they hadn't already reported - the race still finishes for everyone else once all remaining racers report in. */
    public synchronized void handleDisconnect(ClientHandler who)
    {
        if (over || !racers.contains(who) || finishedMap.containsKey(who))
        {
            return;
        }
        reportFinished(who, false, 0);
    }
}
