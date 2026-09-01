import java.util.ArrayList;
import java.util.List;

public class GameRegistry
{
    private final List<GameInfo> games = new ArrayList<GameInfo>();

    public GameRegistry() { seed(); }

    private void seed()
    {
        games.add(new GameInfo("snake", "Snake", "Single Player", "Practice Mode", false, false, "1.0"));
        games.add(new GameInfo("tictactoe-online", "Tic-Tac-Toe (Online)", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("square-wars", "Square Wars", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("racing", "Racing", "Single/Multiplayer", "Online", true, false, "1.1"));
        games.add(new GameInfo("puzzle-quest", "Puzzle Quest", "Single Player", "Practice Mode", false, false, "1.0"));
        games.add(new GameInfo("rock-paper-scissors", "Rock Paper Scissors", "Single/Multiplayer", "Online", true, false, "1.1"));
        games.add(new GameInfo("pingpong", "Ping Pong", "Single Player", "Practice Mode", false, false, "1.0"));
        games.add(new GameInfo("2048", "2048", "Single Player", "Practice Mode", false, false, "1.0"));
        games.add(new GameInfo("dino-dash", "Dino Dash", "Single Player", "Practice Mode", false, false, "1.0"));
        games.add(new GameInfo("tetris", "Tetris", "Single Player", "Practice Mode", false, false, "1.0"));
        games.add(new GameInfo("crossing-road", "Crossing Road", "Single Player", "Practice Mode", false, false, "1.0"));
        games.add(new GameInfo("aim-trainer", "Aim Trainer", "Single Player", "Practice Mode", false, false, "1.0"));
        games.add(new GameInfo("among-us", "Among Us", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("fight-arena", "Fight Arena", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("chess", "Chess", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("battleship", "Battleship", "Single/Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("zombie-survival", "Zombie Survival", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("space-battle", "Space Battle", "Multiplayer", "Coming Soon", false, true, "0.1"));
    }

    public synchronized List<GameInfo> getAllGames() { return new ArrayList<GameInfo>(games); }
}
