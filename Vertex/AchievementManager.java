import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AchievementManager
 * ------------------
 * Permanent milestones, separate from the rotating daily/weekly
 * challenges - once unlocked, stays unlocked forever. Works entirely
 * by accountId rather than ClientHandler, and unlocks are silent (no
 * live push notification) - visible whenever the player opens the
 * Achievements page. This was a deliberate scope simplification: the
 * alternative (a live "Achievement Unlocked!" popup) would need every
 * one of the 5+ match classes to accept and call this manager
 * directly. Working by accountId instead lets LeaderboardManager and
 * GameHistoryManager - which every match already calls - trigger the
 * checks themselves, needing only one integration point each instead
 * of five-plus.
 *
 * Scoped to what existing tracked data already supports: win counts
 * per game (LeaderboardManager), total games played
 * (GameHistoryManager), and current coin balance (a proxy for "earned
 * a lot of coins," not literal lifetime-earned tracking).
 */
public class AchievementManager
{
    private static final String DATA_FILE = "gamehub_achievements.dat";

    public static class Definition
    {
        public final String id;
        public final String name;
        public final String description;

        Definition(String id, String name, String description)
        {
            this.id = id;
            this.name = name;
            this.description = description;
        }
    }

    private static final List<Definition> ALL_DEFINITIONS = new ArrayList<Definition>();
    static
    {
        ALL_DEFINITIONS.add(new Definition("first-blood", "First Blood", "Win your first ranked match, in any game."));
        ALL_DEFINITIONS.add(new Definition("chess-novice", "Chess Novice", "Win 5 games of Chess."));
        ALL_DEFINITIONS.add(new Definition("chess-master", "Chess Master", "Win 25 games of Chess."));
        ALL_DEFINITIONS.add(new Definition("battleship-admiral", "Battleship Admiral", "Win 10 games of Battleship."));
        ALL_DEFINITIONS.add(new Definition("rps-champion", "Rock Paper Scissors Champion", "Win 10 Rock Paper Scissors series."));
        ALL_DEFINITIONS.add(new Definition("tictactoe-ace", "Tic-Tac-Toe Ace", "Win 10 games of Tic-Tac-Toe Online."));
        ALL_DEFINITIONS.add(new Definition("fight-champion", "Fight Champion", "Win 10 Fight Arena matches."));
        ALL_DEFINITIONS.add(new Definition("racing-ace", "Racing Ace", "Finish 1st in an online Race."));
        ALL_DEFINITIONS.add(new Definition("zombie-survivor", "Survivor", "Survive all 8 waves of an online Zombie Survival match."));
        ALL_DEFINITIONS.add(new Definition("space-ace", "Space Ace", "Finish 1st in an online Space Battle."));
        ALL_DEFINITIONS.add(new Definition("high-roller", "High Roller", "Hold 1000 coins at once."));
        ALL_DEFINITIONS.add(new Definition("dedicated", "Dedicated", "Play 50 games, of any kind, total."));
    }

    private final Map<Integer, Set<String>> unlockedByAccount = new HashMap<Integer, Set<String>>();
    private ServerAccountStore accountStore;
    private ChatManager chatManager;
    private SyncService syncService;

    public AchievementManager()
    {
        load();
    }

    /** Set once from GameServer - lets a fresh unlock notify the player live, if they're currently online. Optional: if never set, unlocks stay silent (visible only when the Achievements page is opened), matching the original scope. */
    public void setNotificationTargets(ServerAccountStore accountStore, ChatManager chatManager)
    {
        this.accountStore = accountStore;
        this.chatManager = chatManager;
    }

    /** Set once from GameServer - lets a genuinely new unlock trigger a background push to the main server, same pattern as LeaderboardManager.setSyncService(). Not called from applySyncedUnlocks(), which is for adopting data that came FROM a sync, not for generating new sync-worthy events - that would push right back what was just received. */
    public void setSyncService(SyncService syncService)
    {
        this.syncService = syncService;
    }

    public static List<Definition> getAllDefinitions()
    {
        return ALL_DEFINITIONS;
    }

    public synchronized Set<String> getUnlocked(int accountId)
    {
        Set<String> unlocked = unlockedByAccount.get(accountId);
        return unlocked == null ? new HashSet<String>() : new HashSet<String>(unlocked);
    }

    /** Call after any rated match's winner is decided (win count already includes this result) - checks the win-count achievements for that specific game plus "First Blood". */
    public synchronized void checkWinAchievements(int accountId, String gameId, int wins)
    {
        if (accountId <= 0)
        {
            return;
        }

        unlock(accountId, "first-blood");

        if ("chess".equals(gameId))
        {
            if (wins >= 5) unlock(accountId, "chess-novice");
            if (wins >= 25) unlock(accountId, "chess-master");
        }
        else if ("battleship".equals(gameId))
        {
            if (wins >= 10) unlock(accountId, "battleship-admiral");
        }
        else if ("rock-paper-scissors".equals(gameId))
        {
            if (wins >= 10) unlock(accountId, "rps-champion");
        }
        else if ("tictactoe-online".equals(gameId))
        {
            if (wins >= 10) unlock(accountId, "tictactoe-ace");
        }
        else if ("fight-arena".equals(gameId))
        {
            if (wins >= 10) unlock(accountId, "fight-champion");
        }
    }

    public synchronized void checkRacingPlacement(int accountId, int place)
    {
        if (place == 1 && accountId > 0)
        {
            unlock(accountId, "racing-ace");
        }
    }

    public synchronized void checkZombieSurvival(int accountId, boolean won, int waveReached)
    {
        if (won && accountId > 0)
        {
            unlock(accountId, "zombie-survivor");
        }
    }

    public synchronized void checkSpaceBattlePlacement(int accountId, int place)
    {
        if (place == 1 && accountId > 0)
        {
            unlock(accountId, "space-ace");
        }
    }

    public synchronized void checkCoinBalance(int accountId, int currentBalance)
    {
        if (currentBalance >= 1000 && accountId > 0)
        {
            unlock(accountId, "high-roller");
        }
    }

    public synchronized void checkPlayCount(int accountId, int totalPlays)
    {
        if (totalPlays >= 50 && accountId > 0)
        {
            unlock(accountId, "dedicated");
        }
    }

    private void unlock(int accountId, String achievementId)
    {
        Set<String> unlocked = unlockedByAccount.get(accountId);
        if (unlocked == null)
        {
            unlocked = new HashSet<String>();
            unlockedByAccount.put(accountId, unlocked);
        }
        if (unlocked.add(achievementId))
        {
            save();
            notifyIfOnline(accountId, achievementId);
            if (syncService != null)
            {
                syncService.syncAccountAsync(accountId);
            }
        }
    }

    /** Adopts a set of achievement ids from a main-server sync directly - deliberately doesn't call unlock() (and its notifyIfOnline), since these are already-known unlocks from elsewhere, not a genuinely new moment worth a live toast. */
    public synchronized void applySyncedUnlocks(int accountId, java.util.List<String> ids)
    {
        if (ids == null)
        {
            return;
        }
        Set<String> unlocked = unlockedByAccount.get(accountId);
        if (unlocked == null)
        {
            unlocked = new HashSet<String>();
            unlockedByAccount.put(accountId, unlocked);
        }
        unlocked.addAll(ids);
        save();
    }

    private void notifyIfOnline(int accountId, String achievementId)
    {
        if (accountStore == null || chatManager == null)
        {
            return;
        }
        Account account = accountStore.findById(accountId);
        if (account == null)
        {
            return;
        }
        ClientHandler handler = chatManager.findByUsername(account.getUsername());
        if (handler == null)
        {
            return;
        }
        Definition definition = findDefinition(achievementId);
        if (definition == null)
        {
            return;
        }

        Message notice = new Message();
        notice.setType(MessageType.ACHIEVEMENT_UNLOCKED);
        notice.setAchievementId(definition.id);
        notice.setUsername(definition.name);
        notice.setErrorText(definition.description);
        handler.sendMessage(notice);
    }

    private Definition findDefinition(String id)
    {
        for (int i = 0; i < ALL_DEFINITIONS.size(); i++)
        {
            if (ALL_DEFINITIONS.get(i).id.equals(id))
            {
                return ALL_DEFINITIONS.get(i);
            }
        }
        return null;
    }

    private void save()
    {
        PrintWriter writer = null;
        try
        {
            writer = new PrintWriter(new FileWriter(DATA_FILE));
            for (Map.Entry<Integer, Set<String>> entry : unlockedByAccount.entrySet())
            {
                for (String achievementId : entry.getValue())
                {
                    writer.println(entry.getKey() + "|" + achievementId);
                }
            }
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
                if (parts.length < 2)
                {
                    continue;
                }
                int accountId = Integer.parseInt(parts[0]);
                String achievementId = parts[1];

                Set<String> unlocked = unlockedByAccount.get(accountId);
                if (unlocked == null)
                {
                    unlocked = new HashSet<String>();
                    unlockedByAccount.put(accountId, unlocked);
                }
                unlocked.add(achievementId);
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
}
