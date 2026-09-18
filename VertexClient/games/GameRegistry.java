package games;

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
        games.add(new GameInfo("zombie-survival", "Zombie Survival", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("fight-arena", "Fight Arena", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("chess", "Chess", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("battleship", "Battleship", "Single/Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("connect-four", "Connect Four", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("checkers", "Checkers", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("trivia-blitz", "Trivia Blitz", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("minesweeper", "Minesweeper", "Single Player", "Practice Mode", false, false, "1.0"));
        games.add(new GameInfo("sudoku", "Sudoku", "Single Player", "Practice Mode", false, false, "1.0"));
        games.add(new GameInfo("simon-says", "Simon Says", "Single Player", "Practice Mode", false, false, "1.0"));
        games.add(new GameInfo("whack-a-mole", "Whack-a-Mole", "Single Player", "Practice Mode", false, false, "1.0"));
        games.add(new GameInfo("match-three", "Gem Match", "Single Player", "Practice Mode", false, false, "1.0"));
        games.add(new GameInfo("maze-chase", "Maze Chase", "Single Player", "Practice Mode", false, false, "1.0"));
        games.add(new GameInfo("brick-breaker", "Brick Breaker", "Single Player", "Practice Mode", false, false, "1.0"));
        games.add(new GameInfo("flappy-bird", "Flappy Bird", "Single Player", "Practice Mode", false, false, "1.0"));
        games.add(new GameInfo("galaxy-defender", "Galaxy Defender", "Single Player", "Practice Mode", false, false, "1.0"));
        games.add(new GameInfo("word-guess", "Word Guess", "Single Player", "Practice Mode", false, false, "1.0"));
        games.add(new GameInfo("bubble-shooter", "Bubble Shooter", "Single Player", "Practice Mode", false, false, "1.0"));
        games.add(new GameInfo("lights-out", "Lights Out", "Single Player", "Practice Mode", false, false, "1.0"));
        games.add(new GameInfo("dots-and-boxes", "Dots and Boxes", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("reversi", "Reversi", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("memory-match", "Memory Match", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("air-hockey", "Air Hockey", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("word-duel", "Word Duel", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("dice-duel", "Dice Duel", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("snake-arena", "Snake Arena", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("tetris-duel", "Competitive Tetris", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("fusion-grid", "Fusion Grid", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("typing-duel", "Typing Duel", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("signal-grid", "Signal Grid", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("card-rush", "Card Rush", "Multiplayer", "Online", true, false, "1.0"));
        games.add(new GameInfo("space-battle", "Space Battle", "Multiplayer", "Online", true, false, "1.0"));
    }

    public synchronized List<GameInfo> getAllGames() { return new ArrayList<GameInfo>(games); }
}
