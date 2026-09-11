package economy;

import java.util.ArrayList;
import java.util.List;

/**
 * AchievementDefinitions
 * ----------------------
 * Client-side mirror of AchievementManager.ALL_DEFINITIONS on the
 * server - the server only ever sends which IDs are unlocked
 * (ACHIEVEMENTS_RESPONSE), not the full definitions, so the client
 * needs its own copy to render locked achievements too. Keep this in
 * sync with the server list if achievements are ever added/changed.
 */
public class AchievementDefinitions
{
    public static class Definition
    {
        public final String id;
        public final String name;
        public final String description;
        /** Null for achievements with no clean single number to track (e.g. "finish 1st in a Race" - a one-off condition, not a running count). Otherwise one of "wins:<gameId>", "coins", or "total-plays" - matches the keys ClientHandler.handleAchievementsRequest() computes progress for. */
        public final String metricKey;
        public final int target;

        public Definition(String id, String name, String description)
        {
            this(id, name, description, null, 0);
        }

        public Definition(String id, String name, String description, String metricKey, int target)
        {
            this.id = id;
            this.name = name;
            this.description = description;
            this.metricKey = metricKey;
            this.target = target;
        }
    }

    private static final List<Definition> ALL = new ArrayList<Definition>();
    static
    {
        ALL.add(new Definition("first-blood", "First Blood", "Win your first ranked match, in any game."));
        ALL.add(new Definition("chess-novice", "Chess Novice", "Win 5 games of Chess.", "wins:chess", 5));
        ALL.add(new Definition("chess-master", "Chess Master", "Win 25 games of Chess.", "wins:chess", 25));
        ALL.add(new Definition("battleship-admiral", "Battleship Admiral", "Win 10 games of Battleship.", "wins:battleship", 10));
        ALL.add(new Definition("rps-champion", "Rock Paper Scissors Champion", "Win 10 Rock Paper Scissors series.", "wins:rock-paper-scissors", 10));
        ALL.add(new Definition("tictactoe-ace", "Tic-Tac-Toe Ace", "Win 10 games of Tic-Tac-Toe Online.", "wins:tictactoe-online", 10));
        ALL.add(new Definition("fight-champion", "Fight Champion", "Win 10 Fight Arena matches.", "wins:fight-arena", 10));
        ALL.add(new Definition("racing-ace", "Racing Ace", "Finish 1st in an online Race."));
        ALL.add(new Definition("zombie-survivor", "Survivor", "Survive all 8 waves of an online Zombie Survival match."));
        ALL.add(new Definition("space-ace", "Space Ace", "Finish 1st in an online Space Battle."));
        ALL.add(new Definition("high-roller", "High Roller", "Hold 1000 coins at once.", "coins", 1000));
        ALL.add(new Definition("dedicated", "Dedicated", "Play 50 games, of any kind, total.", "total-plays", 50));
    }

    private AchievementDefinitions()
    {
        // Static constants holder - never instantiated.
    }

    public static List<Definition> getAll()
    {
        return ALL;
    }

    /** Null if no achievement with that id is defined - callers should fall back to showing the raw id in that case rather than crashing. */
    public static String findName(String id)
    {
        for (int i = 0; i < ALL.size(); i++)
        {
            if (ALL.get(i).id.equals(id))
            {
                return ALL.get(i).name;
            }
        }
        return null;
    }
}
