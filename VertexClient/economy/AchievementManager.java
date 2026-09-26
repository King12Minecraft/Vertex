package economy;
import net.MessageType;
import net.Message;
import net.ClientHandler;
import account.Account;
import social.ChatManager;
import account.ServerAccountStore;

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
 * Data-driven since 2026-09-26 (the "achievements kernel" requested
 * alongside EconomyKernel - see ROADMAP.md): every Definition below
 * carries its own trigger, either a threshold on a named metric
 * ("wins:chess" >= 5) or a one-shot event key ("racing:place1") -
 * {@link #checkThreshold} and {@link #checkEvent} are the two generic
 * entry points that unlock whatever Definitions match, so adding a new
 * achievement is one Definition, never a new `checkXxx` method or
 * another branch in a hand-maintained if-chain. Unlike EconomyKernel,
 * this generic engine lives directly on AchievementManager rather than
 * a separate facade class - the original 6 named check methods
 * (checkWinAchievements, checkRacingPlacement, ...) were already a
 * clean, sensible public API (not scattered/duplicated the way
 * EconomyManager's award* methods were), so they're kept as thin,
 * more-readable wrappers over the two generic methods rather than
 * removed - existing call sites needed zero changes. A future game
 * with a genuinely new trigger shape can call checkThreshold/checkEvent
 * directly instead of waiting for a new named wrapper.
 */
public class AchievementManager
{
    private static final String DATA_FILE = "gamehub_achievements.dat";

    public static class Definition
    {
        public final String id;
        public final String name;
        public final String description;
        /** Metric key this achievement's threshold applies to (e.g. "wins:chess", "coins") - null if this is an event-based achievement instead. */
        final String metric;
        /** The value {@link #metric} must reach - meaningless if metric is null. */
        final int threshold;
        /** One-shot trigger key (e.g. "racing:place1") - null if this is a threshold-based achievement instead. Exactly one of metric/eventKey is non-null. */
        final String eventKey;

        private Definition(String id, String name, String description, String metric, int threshold, String eventKey)
        {
            this.id = id;
            this.name = name;
            this.description = description;
            this.metric = metric;
            this.threshold = threshold;
            this.eventKey = eventKey;
        }

        /** A metric that grows over time (win count, coin balance, total plays) crossing a fixed value. */
        static Definition threshold(String id, String name, String description, String metric, int threshold)
        {
            return new Definition(id, name, description, metric, threshold, null);
        }

        /** A one-shot moment (finished 1st, survived to the end) rather than a growing counter. */
        static Definition event(String id, String name, String description, String eventKey)
        {
            return new Definition(id, name, description, null, 0, eventKey);
        }
    }

    private static final List<Definition> ALL_DEFINITIONS = new ArrayList<Definition>();
    static
    {
        ALL_DEFINITIONS.add(Definition.threshold("first-blood", "First Blood", "Win your first ranked match, in any game.", "any-win", 1));
        ALL_DEFINITIONS.add(Definition.threshold("chess-novice", "Chess Novice", "Win 5 games of Chess.", "wins:chess", 5));
        ALL_DEFINITIONS.add(Definition.threshold("chess-master", "Chess Master", "Win 25 games of Chess.", "wins:chess", 25));
        ALL_DEFINITIONS.add(Definition.threshold("battleship-admiral", "Battleship Admiral", "Win 10 games of Battleship.", "wins:battleship", 10));
        ALL_DEFINITIONS.add(Definition.threshold("rps-champion", "Rock Paper Scissors Champion", "Win 10 Rock Paper Scissors series.", "wins:rock-paper-scissors", 10));
        ALL_DEFINITIONS.add(Definition.threshold("tictactoe-ace", "Tic-Tac-Toe Ace", "Win 10 games of Tic-Tac-Toe Online.", "wins:tictactoe-online", 10));
        ALL_DEFINITIONS.add(Definition.threshold("fight-champion", "Fight Champion", "Win 10 Fight Arena matches.", "wins:fight-arena", 10));
        ALL_DEFINITIONS.add(Definition.event("racing-ace", "Racing Ace", "Finish 1st in an online Race.", "racing:place1"));
        ALL_DEFINITIONS.add(Definition.event("zombie-survivor", "Survivor", "Survive all 8 waves of an online Zombie Survival match.", "zombie-survival:won"));
        ALL_DEFINITIONS.add(Definition.event("space-ace", "Space Ace", "Finish 1st in an online Space Battle.", "space-battle:place1"));
        ALL_DEFINITIONS.add(Definition.threshold("high-roller", "High Roller", "Hold 1000 coins at once.", "coins", 1000));
        ALL_DEFINITIONS.add(Definition.threshold("dedicated", "Dedicated", "Play 50 games, of any kind, total.", "total-plays", 50));
    }

    private final Map<Integer, Set<String>> unlockedByAccount = new HashMap<Integer, Set<String>>();
    private ServerAccountStore accountStore;
    private ChatManager chatManager;

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

    public static List<Definition> getAllDefinitions()
    {
        return ALL_DEFINITIONS;
    }

    public synchronized Set<String> getUnlocked(int accountId)
    {
        Set<String> unlocked = unlockedByAccount.get(accountId);
        return unlocked == null ? new HashSet<String>() : new HashSet<String>(unlocked);
    }

    /** Call after any rated match's winner is decided (win count already includes this result) - checks the win-count achievement for that specific game plus "First Blood" (any game's first win). Thin wrapper over checkThreshold - kept as a named method since "a win just happened" reads better at the call site than the raw metric strings it maps to. */
    public void checkWinAchievements(int accountId, String gameId, int wins)
    {
        checkThreshold(accountId, "any-win", 1);
        checkThreshold(accountId, "wins:" + gameId, wins);
    }

    public void checkRacingPlacement(int accountId, int place)
    {
        if (place == 1) checkEvent(accountId, "racing:place1");
    }

    public void checkZombieSurvival(int accountId, boolean won, int waveReached)
    {
        if (won) checkEvent(accountId, "zombie-survival:won");
    }

    public void checkSpaceBattlePlacement(int accountId, int place)
    {
        if (place == 1) checkEvent(accountId, "space-battle:place1");
    }

    public void checkCoinBalance(int accountId, int currentBalance)
    {
        checkThreshold(accountId, "coins", currentBalance);
    }

    public void checkPlayCount(int accountId, int totalPlays)
    {
        checkThreshold(accountId, "total-plays", totalPlays);
    }

    /**
     * The generic engine for threshold-based achievements: unlocks every Definition
     * whose metric matches and whose threshold is already met. A future game with a
     * new growing-counter achievement can call this directly with its own metric key
     * instead of needing a new named wrapper method here.
     */
    public synchronized void checkThreshold(int accountId, String metric, int currentValue)
    {
        if (accountId <= 0)
        {
            return;
        }
        for (int i = 0; i < ALL_DEFINITIONS.size(); i++)
        {
            Definition def = ALL_DEFINITIONS.get(i);
            if (metric.equals(def.metric) && currentValue >= def.threshold)
            {
                unlock(accountId, def.id);
            }
        }
    }

    /**
     * The generic engine for event-based (one-shot) achievements: unlocks every
     * Definition whose eventKey matches. A future game with a new "this specific
     * thing just happened" achievement can call this directly instead of needing a
     * new named wrapper method here.
     */
    public synchronized void checkEvent(int accountId, String eventKey)
    {
        if (accountId <= 0)
        {
            return;
        }
        for (int i = 0; i < ALL_DEFINITIONS.size(); i++)
        {
            Definition def = ALL_DEFINITIONS.get(i);
            if (eventKey.equals(def.eventKey))
            {
                unlock(accountId, def.id);
            }
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
        }
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
