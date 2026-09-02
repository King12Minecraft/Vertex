import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GameHistoryManager
{
    private static final String HISTORY_FILE = "gamehub_play_history.dat";
    private static final int RECENT_LIMIT = 4;
    private static final int TRENDING_LIMIT = 4;

    private static class PlayEvent
    {
        int accountId;
        String gameId;
        long timestamp;
    }

    private final List<PlayEvent> events = new ArrayList<PlayEvent>();
    private AchievementManager achievementManager;

    public GameHistoryManager()
    {
        load();
    }

    /** Set once from GameServer - lets every recordPlay() call (already made by every match manager) trigger the "Dedicated" play-count achievement check automatically. */
    public void setAchievementManager(AchievementManager achievementManager)
    {
        this.achievementManager = achievementManager;
    }

    private void load()
    {
        File file = new File(HISTORY_FILE);
        if (!file.exists()) return;

        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null)
            {
                String[] parts = line.split("\\|", -1);
                if (parts.length < 3) continue;
                PlayEvent event = new PlayEvent();
                event.accountId = Integer.parseInt(parts[0]);
                event.gameId = parts[1];
                event.timestamp = Long.parseLong(parts[2]);
                events.add(event);
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not load play history: " + e.getMessage());
        }
        finally
        {
            if (reader != null) { try { reader.close(); } catch (IOException ignored) { } }
        }
    }

    public synchronized void recordPlay(int accountId, String gameId)
    {
        if (gameId == null || gameId.isEmpty()) return;

        PlayEvent event = new PlayEvent();
        event.accountId = accountId;
        event.gameId = gameId;
        event.timestamp = System.currentTimeMillis();
        events.add(event);

        appendToFile(event);

        if (achievementManager != null && accountId > 0)
        {
            achievementManager.checkPlayCount(accountId, getTotalPlayCount(accountId));
        }
    }

    private void appendToFile(PlayEvent event)
    {
        PrintWriter writer = null;
        try
        {
            writer = new PrintWriter(new FileWriter(HISTORY_FILE, true));
            writer.println(event.accountId + "|" + event.gameId + "|" + event.timestamp);
        }
        catch (IOException e)
        {
            System.err.println("Could not save play history: " + e.getMessage());
        }
        finally
        {
            if (writer != null) writer.close();
        }
    }

    public synchronized List<String> getRecentGameIds(int accountId)
    {
        List<String> result = new ArrayList<String>();
        for (int i = events.size() - 1; i >= 0 && result.size() < RECENT_LIMIT; i--)
        {
            PlayEvent event = events.get(i);
            if (event.accountId == accountId && !result.contains(event.gameId))
            {
                result.add(event.gameId);
            }
        }
        return result;
    }

    /** Total plays across every game, ever - used by the achievements system's "games played" milestone. */
    public synchronized int getTotalPlayCount(int accountId)
    {
        int count = 0;
        for (int i = 0; i < events.size(); i++)
        {
            if (events.get(i).accountId == accountId)
            {
                count++;
            }
        }
        return count;
    }

    public synchronized List<String> getTrendingGameIds()
    {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < events.size(); i++)
        {
            String gameId = events.get(i).gameId;
            Integer current = counts.get(gameId);
            counts.put(gameId, current == null ? 1 : current + 1);
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(counts.entrySet());
        entries.sort(new java.util.Comparator<Map.Entry<String, Integer>>()
        {
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b)
            {
                return b.getValue() - a.getValue();
            }
        });

        List<String> result = new ArrayList<String>();
        for (int i = 0; i < entries.size() && i < TRENDING_LIMIT; i++)
        {
            result.add(entries.get(i).getKey());
        }
        return result;
    }
}
