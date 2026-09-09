package games;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GameMetadata
 * ------------
 * Extra descriptive info per game that GameInfo itself doesn't carry -
 * tags (2D, Multiplayer, Online, ELO-Rated, etc.) and a rough
 * difficulty rating. Purely client-side presentation data for
 * GameDetailDialog; none of this affects matchmaking or gameplay.
 */
public class GameMetadata
{
    public static final String EASY = "Easy";
    public static final String MEDIUM = "Medium";
    public static final String HARD = "Hard";

    private static final Map<String, String[]> TAGS = new HashMap<String, String[]>();
    private static final Map<String, String> DIFFICULTY = new HashMap<String, String>();

    static
    {
        tag("snake", EASY, "2D", "Single Player", "Arcade");
        tag("tictactoe-online", EASY, "2D", "Multiplayer", "Online", "Turn-Based");
        tag("square-wars", MEDIUM, "2D", "Multiplayer", "Online", "Real-Time");
        tag("racing", MEDIUM, "2D", "Multiplayer", "Online", "Real-Time");
        tag("puzzle-quest", MEDIUM, "2D", "Single Player", "Puzzle");
        tag("rock-paper-scissors", EASY, "2D", "Single/Multiplayer", "Online", "Turn-Based");
        tag("pingpong", EASY, "2D", "Single Player", "Arcade");
        tag("2048", MEDIUM, "2D", "Single Player", "Puzzle");
        tag("dino-dash", EASY, "2D", "Single Player", "Arcade");
        tag("tetris", MEDIUM, "2D", "Single Player", "Puzzle");
        tag("crossing-road", EASY, "2D", "Single Player", "Arcade");
        tag("aim-trainer", EASY, "2D", "Single Player", "Arcade");
        tag("among-us", MEDIUM, "2D", "Multiplayer", "Online", "Social");
        tag("zombie-survival", HARD, "2D", "Multiplayer", "Online", "Real-Time", "Co-op");
        tag("fight-arena", HARD, "2D", "Multiplayer", "Online", "Real-Time");
        tag("chess", HARD, "2D", "Multiplayer", "Online", "Turn-Based", "ELO-Rated");
        tag("battleship", MEDIUM, "2D", "Single/Multiplayer", "Online", "Turn-Based", "ELO-Rated");
        tag("connect-four", EASY, "2D", "Multiplayer", "Online", "Turn-Based", "ELO-Rated");
        tag("checkers", MEDIUM, "2D", "Multiplayer", "Online", "Turn-Based", "ELO-Rated");
        tag("space-battle", HARD, "2D", "Multiplayer", "Online", "Real-Time");
        tag("trivia-blitz", EASY, "2D", "Multiplayer", "Online", "Quiz");
    }

    private static void tag(String gameId, String difficulty, String... tags)
    {
        DIFFICULTY.put(gameId, difficulty);
        TAGS.put(gameId, tags);
    }

    private GameMetadata()
    {
        // Static utility class - never instantiated.
    }

    /** Never null - falls back to "Medium" for any game without an explicit rating, since that's a reasonable middle-ground default rather than crashing or leaving a blank UI element. */
    public static String getDifficulty(String gameId)
    {
        String difficulty = DIFFICULTY.get(gameId);
        return difficulty != null ? difficulty : MEDIUM;
    }

    /** Never null - an empty list for any game without specific tags defined. */
    public static List<String> getTags(String gameId)
    {
        String[] tags = TAGS.get(gameId);
        if (tags == null)
        {
            return new ArrayList<String>();
        }
        List<String> list = new ArrayList<String>();
        for (String t : tags) list.add(t);
        return list;
    }
}
