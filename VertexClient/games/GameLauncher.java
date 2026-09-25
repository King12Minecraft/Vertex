package games;
import pages.MainMenu;
import ui.GameHubDialog;

import javax.swing.JOptionPane;
import java.awt.Component;

/**
 * GameLauncher
 * ------------
 * Shared "launch this game" logic - originally embedded in
 * GamesPanel's Play button, extracted so QuickPlayDropdown (and any
 * future entry point) can launch a game the same way without
 * duplicating the switch-on-gameId logic or the error-visibility
 * safety net.
 *
 * launch(...) is the ONLY public entry point, and every existing call
 * site already goes through it - it now shows GameDetailDialog (art,
 * tags, difficulty, full rules/controls text, a Play button) rather
 * than opening the game's window immediately, so there's no separate
 * "skip the rules page" path to accidentally wire a future button
 * into. GameDetailDialog's own Play button calls openGame(...) below
 * once someone has actually seen that page - package-private on
 * purpose, since GameDetailDialog is the only caller meant to reach
 * it directly.
 */
public class GameLauncher
{
    private GameLauncher()
    {
        // Static utility class - never instantiated.
    }

    /** Shows the game's rules/controls page first - see GameDetailDialog, whose own Play button is what actually starts the game (openGame(...) below). Coming-soon games skip straight to the existing "not converted yet" notice, since there's nothing to preview yet. */
    public static void launch(Component anchor, GameInfo game)
    {
        if (game.isComingSoon())
        {
            GameHubDialog.show(anchor, game.getName(),
                "This game hasn't been converted yet - it arrives once it's brought "
                + "into Vertex, following the same process Snake and Tic-Tac-Toe went through.");
            return;
        }

        GameDetailDialog.show(anchor, game);
    }

    /** Actually opens the game's window - only meant to be called by GameDetailDialog's own Play button, once someone has already seen the rules page that launch(...) shows. */
    static void openGame(Component anchor, GameInfo game)
    {
        try
        {
            if ("snake".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new SnakeWindow());
            }
            else if ("tictactoe-online".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new TicTacToeWindow());
            }
            else if ("racing".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new RacingWindow());
            }
            else if ("puzzle-quest".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new PuzzleQuestWindow());
            }
            else if ("rock-paper-scissors".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new RockPaperScissorsWindow());
            }
            else if ("pingpong".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new PongWindow());
            }
            else if ("2048".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new Merge2048Window());
            }
            else if ("dino-dash".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new DinoWindow());
            }
            else if ("tetris".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new TetrisWindow());
            }
            else if ("crossing-road".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new CrossingRoadWindow());
            }
            else if ("aim-trainer".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new AimTrainerWindow());
            }
            else if ("among-us".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new AmongUsWindow());
            }
            else if ("fight-arena".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new FightArenaWindow());
            }
            else if ("chess".equals(game.getGameId()))
            {
                // The embedded-games proof-of-concept: Chess joins MainMenu's
                // game-host slot instead of opening its own JFrame. Every
                // other game here still opens its own window until it's
                // converted the same way.
                MainMenu.getInstance().showGame(new ChessWindow());
            }
            else if ("battleship".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new BattleshipWindow());
            }
            else if ("checkers".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new CheckersWindow());
            }
            else if ("square-wars".equals(game.getGameId()))
            {
                SquareWarsWindow window = new SquareWarsWindow();
                window.setVisible(true);
            }
            else if ("trivia-blitz".equals(game.getGameId()))
            {
                TriviaWindow window = new TriviaWindow();
                window.setVisible(true);
            }
            else if ("minesweeper".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new MinesweeperWindow());
            }
            else if ("sudoku".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new SudokuWindow());
            }
            else if ("simon-says".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new SimonWindow());
            }
            else if ("whack-a-mole".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new WhackAMoleWindow());
            }
            else if ("match-three".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new MatchThreeWindow());
            }
            else if ("maze-chase".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new MazeChaseWindow());
            }
            else if ("brick-breaker".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new BrickBreakerWindow());
            }
            else if ("flappy-bird".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new FlappyBirdWindow());
            }
            else if ("galaxy-defender".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new GalaxyDefenderWindow());
            }
            else if ("word-guess".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new WordGuessWindow());
            }
            else if ("bubble-shooter".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new BubbleShooterWindow());
            }
            else if ("lights-out".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new LightsOutWindow());
            }
            else if ("peg-solitaire".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new PegSolitaireWindow());
            }
            else if ("klondike".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new KlondikeWindow());
            }
            else if ("yahtzee".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new YahtzeeWindow());
            }
            else if ("mancala".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new MancalaWindow());
            }
            else if ("dots-and-boxes".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new DotsAndBoxesWindow());
            }
            else if ("reversi".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new ReversiWindow());
            }
            else if ("memory-match".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new MemoryMatchWindow());
            }
            else if ("air-hockey".equals(game.getGameId()))
            {
                AirHockeyWindow window = new AirHockeyWindow();
                window.setVisible(true);
            }
            else if ("word-duel".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new WordDuelWindow());
            }
            else if ("dice-duel".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new DiceDuelWindow());
            }
            else if ("snake-arena".equals(game.getGameId()))
            {
                SnakeArenaWindow window = new SnakeArenaWindow();
                window.setVisible(true);
            }
            else if ("tetris-duel".equals(game.getGameId()))
            {
                TetrisDuelWindow window = new TetrisDuelWindow();
                window.setVisible(true);
            }
            else if ("fusion-grid".equals(game.getGameId()))
            {
                FusionGridWindow window = new FusionGridWindow();
                window.setVisible(true);
            }
            else if ("typing-duel".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new TypingDuelWindow());
            }
            else if ("signal-grid".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new SignalGridWindow());
            }
            else if ("card-rush".equals(game.getGameId()))
            {
                CardRushWindow window = new CardRushWindow();
                window.setVisible(true);
            }
            else if ("connect-four".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new ConnectFourWindow());
            }
            else if ("zombie-survival".equals(game.getGameId()))
            {
                ZombieSurvivalWindow window = new ZombieSurvivalWindow();
                window.setVisible(true);
            }
            else if ("space-battle".equals(game.getGameId()))
            {
                SpaceBattleWindow window = new SpaceBattleWindow();
                window.setVisible(true);
            }
            else
            {
                GameHubDialog.show(anchor, game.getName(),
                    "This game hasn't been converted yet - it arrives once it's brought "
                    + "into Vertex, following the same process Snake and Tic-Tac-Toe went through.");
            }
        }
        catch (Exception ex)
        {
            // Surface the REAL error instead of failing silently - plain
            // JOptionPane on purpose, since it must work even if
            // something in our own theming is what broke.
            ex.printStackTrace();
            JOptionPane.showMessageDialog(anchor,
                "Could not launch " + game.getName() + ":\n\n" + ex,
                "Launch Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
