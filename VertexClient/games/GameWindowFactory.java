package games;

import javax.swing.JComponent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * GameWindowFactory
 * -----------------
 * The one place that maps a game id to "how to construct its embedded
 * window" - client-only (unlike `GameInfo`/`GameRegistry`, which are
 * byte-identical on both client and server and so can never reference
 * a Swing class), since every entry here is a reference to a
 * `*Window` constructor that only exists in `VertexClient`.
 *
 * Replaces what used to be a 49-branch `if/else` in
 * `GameLauncher.openGame` - every branch had the exact same shape by
 * the time the embedded-games conversion finished (`MainMenu
 * .getInstance().showGame(new XxxWindow())`, with zero exceptions),
 * which is exactly what a factory map is for. `GameLauncher` still
 * owns the actual `showGame(...)` call and the "not converted yet"
 * fallback for an id with no entry - this class only knows how to
 * build the window for an id, nothing about when or whether to.
 */
public final class GameWindowFactory
{
    private static final Map<String, Supplier<JComponent>> FACTORIES = new HashMap<String, Supplier<JComponent>>();

    static
    {
        FACTORIES.put("snake", new Supplier<JComponent>() { public JComponent get() { return new SnakeWindow(); } });
        FACTORIES.put("tictactoe-online", new Supplier<JComponent>() { public JComponent get() { return new TicTacToeWindow(); } });
        FACTORIES.put("racing", new Supplier<JComponent>() { public JComponent get() { return new RacingWindow(); } });
        FACTORIES.put("puzzle-quest", new Supplier<JComponent>() { public JComponent get() { return new PuzzleQuestWindow(); } });
        FACTORIES.put("rock-paper-scissors", new Supplier<JComponent>() { public JComponent get() { return new RockPaperScissorsWindow(); } });
        FACTORIES.put("pingpong", new Supplier<JComponent>() { public JComponent get() { return new PongWindow(); } });
        FACTORIES.put("2048", new Supplier<JComponent>() { public JComponent get() { return new Merge2048Window(); } });
        FACTORIES.put("dino-dash", new Supplier<JComponent>() { public JComponent get() { return new DinoWindow(); } });
        FACTORIES.put("tetris", new Supplier<JComponent>() { public JComponent get() { return new TetrisWindow(); } });
        FACTORIES.put("crossing-road", new Supplier<JComponent>() { public JComponent get() { return new CrossingRoadWindow(); } });
        FACTORIES.put("aim-trainer", new Supplier<JComponent>() { public JComponent get() { return new AimTrainerWindow(); } });
        FACTORIES.put("among-us", new Supplier<JComponent>() { public JComponent get() { return new AmongUsWindow(); } });
        FACTORIES.put("fight-arena", new Supplier<JComponent>() { public JComponent get() { return new FightArenaWindow(); } });
        FACTORIES.put("chess", new Supplier<JComponent>() { public JComponent get() { return new ChessWindow(); } });
        FACTORIES.put("battleship", new Supplier<JComponent>() { public JComponent get() { return new BattleshipWindow(); } });
        FACTORIES.put("checkers", new Supplier<JComponent>() { public JComponent get() { return new CheckersWindow(); } });
        FACTORIES.put("square-wars", new Supplier<JComponent>() { public JComponent get() { return new SquareWarsWindow(); } });
        FACTORIES.put("trivia-blitz", new Supplier<JComponent>() { public JComponent get() { return new TriviaWindow(); } });
        FACTORIES.put("minesweeper", new Supplier<JComponent>() { public JComponent get() { return new MinesweeperWindow(); } });
        FACTORIES.put("sudoku", new Supplier<JComponent>() { public JComponent get() { return new SudokuWindow(); } });
        FACTORIES.put("simon-says", new Supplier<JComponent>() { public JComponent get() { return new SimonWindow(); } });
        FACTORIES.put("whack-a-mole", new Supplier<JComponent>() { public JComponent get() { return new WhackAMoleWindow(); } });
        FACTORIES.put("match-three", new Supplier<JComponent>() { public JComponent get() { return new MatchThreeWindow(); } });
        FACTORIES.put("maze-chase", new Supplier<JComponent>() { public JComponent get() { return new MazeChaseWindow(); } });
        FACTORIES.put("brick-breaker", new Supplier<JComponent>() { public JComponent get() { return new BrickBreakerWindow(); } });
        FACTORIES.put("flappy-bird", new Supplier<JComponent>() { public JComponent get() { return new FlappyBirdWindow(); } });
        FACTORIES.put("galaxy-defender", new Supplier<JComponent>() { public JComponent get() { return new GalaxyDefenderWindow(); } });
        FACTORIES.put("word-guess", new Supplier<JComponent>() { public JComponent get() { return new WordGuessWindow(); } });
        FACTORIES.put("bubble-shooter", new Supplier<JComponent>() { public JComponent get() { return new BubbleShooterWindow(); } });
        FACTORIES.put("lights-out", new Supplier<JComponent>() { public JComponent get() { return new LightsOutWindow(); } });
        FACTORIES.put("peg-solitaire", new Supplier<JComponent>() { public JComponent get() { return new PegSolitaireWindow(); } });
        FACTORIES.put("klondike", new Supplier<JComponent>() { public JComponent get() { return new KlondikeWindow(); } });
        FACTORIES.put("yahtzee", new Supplier<JComponent>() { public JComponent get() { return new YahtzeeWindow(); } });
        FACTORIES.put("mancala", new Supplier<JComponent>() { public JComponent get() { return new MancalaWindow(); } });
        FACTORIES.put("dots-and-boxes", new Supplier<JComponent>() { public JComponent get() { return new DotsAndBoxesWindow(); } });
        FACTORIES.put("reversi", new Supplier<JComponent>() { public JComponent get() { return new ReversiWindow(); } });
        FACTORIES.put("memory-match", new Supplier<JComponent>() { public JComponent get() { return new MemoryMatchWindow(); } });
        FACTORIES.put("air-hockey", new Supplier<JComponent>() { public JComponent get() { return new AirHockeyWindow(); } });
        FACTORIES.put("word-duel", new Supplier<JComponent>() { public JComponent get() { return new WordDuelWindow(); } });
        FACTORIES.put("dice-duel", new Supplier<JComponent>() { public JComponent get() { return new DiceDuelWindow(); } });
        FACTORIES.put("snake-arena", new Supplier<JComponent>() { public JComponent get() { return new SnakeArenaWindow(); } });
        FACTORIES.put("tetris-duel", new Supplier<JComponent>() { public JComponent get() { return new TetrisDuelWindow(); } });
        FACTORIES.put("fusion-grid", new Supplier<JComponent>() { public JComponent get() { return new FusionGridWindow(); } });
        FACTORIES.put("typing-duel", new Supplier<JComponent>() { public JComponent get() { return new TypingDuelWindow(); } });
        FACTORIES.put("signal-grid", new Supplier<JComponent>() { public JComponent get() { return new SignalGridWindow(); } });
        FACTORIES.put("card-rush", new Supplier<JComponent>() { public JComponent get() { return new CardRushWindow(); } });
        FACTORIES.put("connect-four", new Supplier<JComponent>() { public JComponent get() { return new ConnectFourWindow(); } });
        FACTORIES.put("zombie-survival", new Supplier<JComponent>() { public JComponent get() { return new ZombieSurvivalWindow(); } });
        FACTORIES.put("space-battle", new Supplier<JComponent>() { public JComponent get() { return new SpaceBattleWindow(); } });
    }

    private GameWindowFactory()
    {
        // Static utility class - never instantiated.
    }

    /** The constructor for gameId's embedded window, or null if gameId isn't a converted game (GameLauncher falls back to the "not converted yet" notice in that case). */
    public static Supplier<JComponent> factoryFor(String gameId)
    {
        return FACTORIES.get(gameId);
    }

    /** Every game id this factory knows how to build - used by tests/tooling to confirm registry coverage without hand-maintaining a second list. */
    public static java.util.Set<String> knownGameIds()
    {
        return java.util.Collections.unmodifiableSet(FACTORIES.keySet());
    }
}
