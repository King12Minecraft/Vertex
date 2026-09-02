import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ReplayManager
 * -------------
 * Persists finished Chess matches as an ordered list of board
 * snapshots (one per move, including the starting position) rather
 * than move notation - the client just steps through pre-rendered
 * board states with Prev/Next, no need to duplicate chess move-
 * application logic client-side. Chess-only for now, same scoping as
 * spectator mode; other turn-based games would extend the same way.
 */
public class ReplayManager
{
    private static final String DATA_FILE = "gamehub_replays.dat";

    public static class Replay
    {
        public final String id;
        public final String gameId;
        public final String player1;
        public final String player2;
        public final String result;
        public final long timestamp;
        public final List<String> snapshots;

        Replay(String id, String gameId, String player1, String player2, String result, long timestamp, List<String> snapshots)
        {
            this.id = id;
            this.gameId = gameId;
            this.player1 = player1;
            this.player2 = player2;
            this.result = result;
            this.timestamp = timestamp;
            this.snapshots = snapshots;
        }
    }

    private final Map<String, Replay> replaysById = new HashMap<String, Replay>();
    private int nextReplayId = 1;

    public ReplayManager()
    {
        load();
    }

    public synchronized void save(String gameId, String player1, String player2, String result, List<String> snapshots)
    {
        String id = "replay-" + (nextReplayId++);
        Replay replay = new Replay(id, gameId, player1, player2, result, System.currentTimeMillis(), new ArrayList<String>(snapshots));
        replaysById.put(id, replay);
        appendToFile(replay);
    }

    /** Every replay involving this username, most recent first, formatted "id|opponent|result|timestamp". */
    public synchronized List<String> listForPlayer(String username)
    {
        List<Replay> matches = new ArrayList<Replay>();
        for (Replay replay : replaysById.values())
        {
            if (username.equalsIgnoreCase(replay.player1) || username.equalsIgnoreCase(replay.player2))
            {
                matches.add(replay);
            }
        }
        java.util.Collections.sort(matches, new java.util.Comparator<Replay>()
        {
            public int compare(Replay a, Replay b) { return Long.compare(b.timestamp, a.timestamp); }
        });

        List<String> result = new ArrayList<String>();
        for (int i = 0; i < matches.size(); i++)
        {
            Replay replay = matches.get(i);
            String opponent = username.equalsIgnoreCase(replay.player1) ? replay.player2 : replay.player1;
            result.add(replay.id + "|" + replay.gameId + "|" + opponent + "|" + replay.result + "|" + replay.timestamp);
        }
        return result;
    }

    public synchronized Replay get(String replayId)
    {
        return replaysById.get(replayId);
    }

    /** Appends one line per replay: id|gameId|player1|player2|result|timestamp|snapshot1;snapshot2;... - board snapshots never contain ';' or '|' themselves (only letters and '.'), so those are safe delimiters. */
    private void appendToFile(Replay replay)
    {
        PrintWriter writer = null;
        try
        {
            writer = new PrintWriter(new FileWriter(DATA_FILE, true));
            StringBuilder snapshotsJoined = new StringBuilder();
            for (int i = 0; i < replay.snapshots.size(); i++)
            {
                if (i > 0) snapshotsJoined.append(';');
                snapshotsJoined.append(replay.snapshots.get(i));
            }
            writer.println(replay.id + "|" + replay.gameId + "|" + replay.player1 + "|" + replay.player2
                + "|" + replay.result + "|" + replay.timestamp + "|" + snapshotsJoined);
        }
        catch (IOException e)
        {
            System.err.println("Could not save " + DATA_FILE + ": " + e.getMessage());
        }
        finally
        {
            if (writer != null) writer.close();
        }
    }

    private void load()
    {
        File file = new File(DATA_FILE);
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
                if (parts.length < 7)
                {
                    continue;
                }
                String id = parts[0];
                String gameId = parts[1];
                String player1 = parts[2];
                String player2 = parts[3];
                String result = parts[4];
                long timestamp = Long.parseLong(parts[5]);
                List<String> snapshots = new ArrayList<String>();
                if (!parts[6].isEmpty())
                {
                    String[] snapshotParts = parts[6].split(";", -1);
                    for (int i = 0; i < snapshotParts.length; i++)
                    {
                        snapshots.add(snapshotParts[i]);
                    }
                }

                replaysById.put(id, new Replay(id, gameId, player1, player2, result, timestamp, snapshots));

                int numericPart = parseTrailingNumber(id);
                if (numericPart >= nextReplayId)
                {
                    nextReplayId = numericPart + 1;
                }
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not load " + DATA_FILE + ": " + e.getMessage());
        }
        finally
        {
            if (reader != null)
            {
                try { reader.close(); } catch (IOException ignored) { }
            }
        }
    }

    private int parseTrailingNumber(String id)
    {
        int dash = id.lastIndexOf('-');
        if (dash == -1) return 0;
        try
        {
            return Integer.parseInt(id.substring(dash + 1));
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }
}
