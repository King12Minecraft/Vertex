import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LeaderboardManager
 * ------------------
 * Per-game ELO ratings for 1v1 games (Chess, Battleship, Rock Paper
 * Scissors, Tic-Tac-Toe), a pairwise-ELO approximation for Fight
 * Arena's multiplayer matches, and best-score tracking for score-based
 * games (Racing, Snake, Tetris, etc.) - all through one shared manager
 * since a leaderboard query looks the same either way (a sorted list
 * of username + number).
 *
 * ELO is the standard formula (K=32, starting rating 1200). Fight
 * Arena doesn't get "real" multiplayer rating (that's what dedicated
 * systems like TrueSkill are for, a much bigger undertaking) -
 * instead, every winner is treated as having pairwise-beaten every
 * loser, applying a normal 1v1 ELO exchange for each pair. This is a
 * well-established, simple approximation, not mathematically
 * equivalent to true multiplayer skill rating, but good enough to
 * produce a genuinely useful ranked ladder.
 *
 * Among Us is deliberately NOT rated here - Crewmate/Impostor is an
 * asymmetric role split, not a symmetric skill contest, so ELO doesn't
 * map onto it cleanly. It could get a simple win-rate-by-role stat
 * later without needing this class's rating math.
 */
public class LeaderboardManager
{
    private static final String RATINGS_FILE = "gamehub_ratings.dat";
    private static final String SCORES_FILE = "gamehub_best_scores.dat";
    private static final int STARTING_RATING = 1200;
    private static final int K_FACTOR = 32;

    /** ratings.get(gameId).get(accountId) = current rating. */
    private final Map<String, Map<Integer, Integer>> ratings = new HashMap<String, Map<Integer, Integer>>();
    /** record.get(gameId).get(accountId) = {wins, losses, draws}. */
    private final Map<String, Map<Integer, int[]>> records = new HashMap<String, Map<Integer, int[]>>();
    /** bestScores.get(gameId).get(accountId) = highest score seen. */
    private final Map<String, Map<Integer, Integer>> bestScores = new HashMap<String, Map<Integer, Integer>>();

    private final ServerAccountStore accountStore;
    private AchievementManager achievementManager;
    private SyncService syncService;

    public LeaderboardManager(ServerAccountStore accountStore)
    {
        this.accountStore = accountStore;
        load();
    }

    /** Set once from GameServer after both managers exist - lets a rated result trigger achievement checks automatically, so the 5+ match classes calling recordRatedMatch/recordMultiplayerResult don't each need their own separate call into AchievementManager. */
    public void setAchievementManager(AchievementManager achievementManager)
    {
        this.achievementManager = achievementManager;
    }

    /** Set once from GameServer - lets a rating change trigger a background push to the main server automatically, same reasoning as setAchievementManager: individual match classes shouldn't each need their own sync-awareness. */
    public void setSyncService(SyncService syncService)
    {
        this.syncService = syncService;
    }

    // ==================== ELO (1v1 games) ====================

    /** outcomeForA: 1.0 = A won, 0.5 = draw, 0.0 = A lost. */
    public synchronized void recordRatedMatch(String gameId, int accountIdA, int accountIdB, double outcomeForA)
    {
        if (accountIdA <= 0 || accountIdB <= 0)
        {
            return;
        }

        int ratingA = getRating(gameId, accountIdA);
        int ratingB = getRating(gameId, accountIdB);

        double expectedA = 1.0 / (1.0 + Math.pow(10, (ratingB - ratingA) / 400.0));
        double expectedB = 1.0 - expectedA;
        double outcomeForB = 1.0 - outcomeForA;

        int newRatingA = (int) Math.round(ratingA + K_FACTOR * (outcomeForA - expectedA));
        int newRatingB = (int) Math.round(ratingB + K_FACTOR * (outcomeForB - expectedB));

        setRating(gameId, accountIdA, newRatingA);
        setRating(gameId, accountIdB, newRatingB);

        updateRecord(gameId, accountIdA, outcomeForA);
        updateRecord(gameId, accountIdB, outcomeForB);

        checkWinAchievement(gameId, accountIdA, outcomeForA);
        checkWinAchievement(gameId, accountIdB, outcomeForB);

        save();

        if (syncService != null)
        {
            syncService.syncAccountAsync(accountIdA);
            syncService.syncAccountAsync(accountIdB);
        }
    }

    private void checkWinAchievement(String gameId, int accountId, double outcome)
    {
        if (achievementManager != null && outcome == 1.0)
        {
            achievementManager.checkWinAchievements(accountId, gameId, getRecord(gameId, accountId)[0]);
        }
    }

    /**
     * Fight Arena (or any future multiplayer game) - winners is every
     * account on the winning side, losers is everyone else. Applies a
     * normal 1v1 ELO exchange between every winner/loser pair - see
     * this class's own javadoc for why this approximation was chosen
     * over a dedicated multiplayer rating system.
     */
    public synchronized void recordMultiplayerResult(String gameId, List<Integer> winners, List<Integer> losers)
    {
        for (int i = 0; i < winners.size(); i++)
        {
            for (int j = 0; j < losers.size(); j++)
            {
                recordRatedMatch(gameId, winners.get(i), losers.get(j), 1.0);
            }
        }
    }

    public synchronized int getRating(String gameId, int accountId)
    {
        Map<Integer, Integer> gameRatings = ratings.get(gameId);
        if (gameRatings == null)
        {
            return STARTING_RATING;
        }
        Integer rating = gameRatings.get(accountId);
        return rating == null ? STARTING_RATING : rating;
    }

    /** Every game this account has an on-record rating in, "gameId:rating" per entry - used for main-server sync (MainServerConnection), where a whole account's rated history needs to travel as one payload rather than querying game-by-game. Games the account has never played (still sitting at STARTING_RATING with no real record) are skipped rather than padding the sync payload with defaults. */
    public synchronized java.util.List<String> getAllRatingsForAccount(int accountId)
    {
        java.util.List<String> result = new java.util.ArrayList<String>();
        for (Map.Entry<String, Map<Integer, Integer>> gameEntry : ratings.entrySet())
        {
            Integer rating = gameEntry.getValue().get(accountId);
            if (rating != null)
            {
                result.add(gameEntry.getKey() + ":" + rating);
            }
        }
        return result;
    }

    /** Applies a set of "gameId:rating" entries (from getAllRatingsForAccount's format) directly - used when a satellite server receives synced ratings from the main server and needs to adopt them locally, bypassing the normal ELO-exchange calculation since these are already-final values, not a match outcome to compute from. */
    public synchronized void applySyncedRatings(int accountId, java.util.List<String> syncedRatings)
    {
        if (syncedRatings == null)
        {
            return;
        }
        for (String entry : syncedRatings)
        {
            String[] parts = entry.split(":", -1);
            if (parts.length == 2)
            {
                try
                {
                    setRating(parts[0], accountId, Integer.parseInt(parts[1]));
                }
                catch (NumberFormatException e)
                {
                    // Malformed entry from a mismatched protocol version - skip rather than crash the whole sync.
                }
            }
        }
        save();
    }

    private void setRating(String gameId, int accountId, int newRating)
    {
        Map<Integer, Integer> gameRatings = ratings.get(gameId);
        if (gameRatings == null)
        {
            gameRatings = new HashMap<Integer, Integer>();
            ratings.put(gameId, gameRatings);
        }
        gameRatings.put(accountId, newRating);
    }

    private void updateRecord(String gameId, int accountId, double outcome)
    {
        Map<Integer, int[]> gameRecords = records.get(gameId);
        if (gameRecords == null)
        {
            gameRecords = new HashMap<Integer, int[]>();
            records.put(gameId, gameRecords);
        }
        int[] record = gameRecords.get(accountId);
        if (record == null)
        {
            record = new int[3];
            gameRecords.put(accountId, record);
        }
        if (outcome == 1.0) record[0]++;
        else if (outcome == 0.0) record[1]++;
        else record[2]++;
    }

    public synchronized int[] getRecord(String gameId, int accountId)
    {
        Map<Integer, int[]> gameRecords = records.get(gameId);
        if (gameRecords == null)
        {
            return new int[] { 0, 0, 0 };
        }
        int[] record = gameRecords.get(accountId);
        return record == null ? new int[] { 0, 0, 0 } : record.clone();
    }

    // ==================== Best scores (score-based games) ====================

    public synchronized void recordScore(String gameId, int accountId, int score)
    {
        if (accountId <= 0)
        {
            return;
        }
        Map<Integer, Integer> gameScores = bestScores.get(gameId);
        if (gameScores == null)
        {
            gameScores = new HashMap<Integer, Integer>();
            bestScores.put(gameId, gameScores);
        }
        Integer current = gameScores.get(accountId);
        if (current == null || score > current)
        {
            gameScores.put(accountId, score);
            save();
        }
    }

    public synchronized int getBestScore(String gameId, int accountId)
    {
        Map<Integer, Integer> gameScores = bestScores.get(gameId);
        if (gameScores == null)
        {
            return 0;
        }
        Integer score = gameScores.get(accountId);
        return score == null ? 0 : score;
    }

    // ==================== Leaderboard queries ====================

    /** Top entries for a rated (ELO) game, formatted "rank|username|rating|wins|losses|draws". */
    public synchronized List<String> getRatingLeaderboard(String gameId, int limit)
    {
        Map<Integer, Integer> gameRatings = ratings.get(gameId);
        if (gameRatings == null || gameRatings.isEmpty())
        {
            return new ArrayList<String>();
        }

        List<Map.Entry<Integer, Integer>> sorted = new ArrayList<Map.Entry<Integer, Integer>>(gameRatings.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<Integer, Integer>>()
        {
            public int compare(Map.Entry<Integer, Integer> a, Map.Entry<Integer, Integer> b)
            {
                return b.getValue() - a.getValue();
            }
        });

        List<String> result = new ArrayList<String>();
        int rank = 1;
        for (int i = 0; i < sorted.size() && rank <= limit; i++)
        {
            int accountId = sorted.get(i).getKey();
            Account account = accountStore.findById(accountId);
            if (account == null)
            {
                continue;
            }
            int[] record = getRecord(gameId, accountId);
            result.add(rank + "|" + account.getUsername() + "|" + sorted.get(i).getValue()
                + "|" + record[0] + "|" + record[1] + "|" + record[2]);
            rank++;
        }
        return result;
    }

    /** Top entries for a score-based game, formatted "rank|username|score". */
    public synchronized List<String> getScoreLeaderboard(String gameId, int limit)
    {
        Map<Integer, Integer> gameScores = bestScores.get(gameId);
        if (gameScores == null || gameScores.isEmpty())
        {
            return new ArrayList<String>();
        }

        List<Map.Entry<Integer, Integer>> sorted = new ArrayList<Map.Entry<Integer, Integer>>(gameScores.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<Integer, Integer>>()
        {
            public int compare(Map.Entry<Integer, Integer> a, Map.Entry<Integer, Integer> b)
            {
                return b.getValue() - a.getValue();
            }
        });

        List<String> result = new ArrayList<String>();
        int rank = 1;
        for (int i = 0; i < sorted.size() && rank <= limit; i++)
        {
            int accountId = sorted.get(i).getKey();
            Account account = accountStore.findById(accountId);
            if (account == null)
            {
                continue;
            }
            result.add(rank + "|" + account.getUsername() + "|" + sorted.get(i).getValue());
            rank++;
        }
        return result;
    }

    // ==================== Persistence ====================

    private void save()
    {
        PrintWriter writer = null;
        try
        {
            writer = new PrintWriter(new FileWriter(RATINGS_FILE));
            for (Map.Entry<String, Map<Integer, Integer>> gameEntry : ratings.entrySet())
            {
                String gameId = gameEntry.getKey();
                for (Map.Entry<Integer, Integer> accountEntry : gameEntry.getValue().entrySet())
                {
                    int accountId = accountEntry.getKey();
                    int rating = accountEntry.getValue();
                    int[] record = getRecord(gameId, accountId);
                    writer.println(gameId + "|" + accountId + "|" + rating + "|" + record[0] + "|" + record[1] + "|" + record[2]);
                }
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not save " + RATINGS_FILE + ": " + e.getMessage());
        }
        finally
        {
            if (writer != null) writer.close();
        }

        PrintWriter scoreWriter = null;
        try
        {
            scoreWriter = new PrintWriter(new FileWriter(SCORES_FILE));
            for (Map.Entry<String, Map<Integer, Integer>> gameEntry : bestScores.entrySet())
            {
                String gameId = gameEntry.getKey();
                for (Map.Entry<Integer, Integer> accountEntry : gameEntry.getValue().entrySet())
                {
                    scoreWriter.println(gameId + "|" + accountEntry.getKey() + "|" + accountEntry.getValue());
                }
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not save " + SCORES_FILE + ": " + e.getMessage());
        }
        finally
        {
            if (scoreWriter != null) scoreWriter.close();
        }
    }

    private void load()
    {
        loadRatings();
        loadScores();
    }

    private void loadRatings()
    {
        File file = new File(RATINGS_FILE);
        if (!file.exists())
        {
            return;
        }

        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null)
            {
                String[] parts = line.split("\\|", -1);
                if (parts.length < 6)
                {
                    continue;
                }
                String gameId = parts[0];
                int accountId = Integer.parseInt(parts[1]);
                int rating = Integer.parseInt(parts[2]);
                int wins = Integer.parseInt(parts[3]);
                int losses = Integer.parseInt(parts[4]);
                int draws = Integer.parseInt(parts[5]);

                setRating(gameId, accountId, rating);

                Map<Integer, int[]> gameRecords = records.get(gameId);
                if (gameRecords == null)
                {
                    gameRecords = new HashMap<Integer, int[]>();
                    records.put(gameId, gameRecords);
                }
                gameRecords.put(accountId, new int[] { wins, losses, draws });
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not load " + RATINGS_FILE + ": " + e.getMessage());
        }
        finally
        {
            if (reader != null)
            {
                try { reader.close(); } catch (IOException ignored) { }
            }
        }
    }

    private void loadScores()
    {
        File file = new File(SCORES_FILE);
        if (!file.exists())
        {
            return;
        }

        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null)
            {
                String[] parts = line.split("\\|", -1);
                if (parts.length < 3)
                {
                    continue;
                }
                String gameId = parts[0];
                int accountId = Integer.parseInt(parts[1]);
                int score = Integer.parseInt(parts[2]);

                Map<Integer, Integer> gameScores = bestScores.get(gameId);
                if (gameScores == null)
                {
                    gameScores = new HashMap<Integer, Integer>();
                    bestScores.put(gameId, gameScores);
                }
                gameScores.put(accountId, score);
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not load " + SCORES_FILE + ": " + e.getMessage());
        }
        finally
        {
            if (reader != null)
            {
                try { reader.close(); } catch (IOException ignored) { }
            }
        }
    }
}
