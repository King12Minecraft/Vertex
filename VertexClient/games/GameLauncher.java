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
                SnakeWindow window = new SnakeWindow();
                window.setVisible(true);
            }
            else if ("tictactoe-online".equals(game.getGameId()))
            {
                TicTacToeWindow window = new TicTacToeWindow();
                window.setVisible(true);
            }
            else if ("racing".equals(game.getGameId()))
            {
                RacingWindow window = new RacingWindow();
                window.setVisible(true);
            }
            else if ("puzzle-quest".equals(game.getGameId()))
            {
                PuzzleQuestWindow window = new PuzzleQuestWindow();
                window.setVisible(true);
            }
            else if ("rock-paper-scissors".equals(game.getGameId()))
            {
                RockPaperScissorsWindow window = new RockPaperScissorsWindow();
                window.setVisible(true);
            }
            else if ("pingpong".equals(game.getGameId()))
            {
                PongWindow window = new PongWindow();
                window.setVisible(true);
            }
            else if ("2048".equals(game.getGameId()))
            {
                Merge2048Window window = new Merge2048Window();
                window.setVisible(true);
            }
            else if ("dino-dash".equals(game.getGameId()))
            {
                DinoWindow window = new DinoWindow();
                window.setVisible(true);
            }
            else if ("tetris".equals(game.getGameId()))
            {
                TetrisWindow window = new TetrisWindow();
                window.setVisible(true);
            }
            else if ("crossing-road".equals(game.getGameId()))
            {
                CrossingRoadWindow window = new CrossingRoadWindow();
                window.setVisible(true);
            }
            else if ("aim-trainer".equals(game.getGameId()))
            {
                AimTrainerWindow window = new AimTrainerWindow();
                window.setVisible(true);
            }
            else if ("among-us".equals(game.getGameId()))
            {
                AmongUsWindow window = new AmongUsWindow();
                window.setVisible(true);
            }
            else if ("fight-arena".equals(game.getGameId()))
            {
                FightArenaWindow window = new FightArenaWindow();
                window.setVisible(true);
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
                BattleshipWindow window = new BattleshipWindow();
                window.setVisible(true);
            }
            else if ("checkers".equals(game.getGameId()))
            {
                CheckersWindow window = new CheckersWindow();
                window.setVisible(true);
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
                MinesweeperWindow window = new MinesweeperWindow();
                window.setVisible(true);
            }
            else if ("sudoku".equals(game.getGameId()))
            {
                SudokuWindow window = new SudokuWindow();
                window.setVisible(true);
            }
            else if ("simon-says".equals(game.getGameId()))
            {
                SimonWindow window = new SimonWindow();
                window.setVisible(true);
            }
            else if ("whack-a-mole".equals(game.getGameId()))
            {
                WhackAMoleWindow window = new WhackAMoleWindow();
                window.setVisible(true);
            }
            else if ("match-three".equals(game.getGameId()))
            {
                MatchThreeWindow window = new MatchThreeWindow();
                window.setVisible(true);
            }
            else if ("maze-chase".equals(game.getGameId()))
            {
                MazeChaseWindow window = new MazeChaseWindow();
                window.setVisible(true);
            }
            else if ("brick-breaker".equals(game.getGameId()))
            {
                BrickBreakerWindow window = new BrickBreakerWindow();
                window.setVisible(true);
            }
            else if ("flappy-bird".equals(game.getGameId()))
            {
                FlappyBirdWindow window = new FlappyBirdWindow();
                window.setVisible(true);
            }
            else if ("galaxy-defender".equals(game.getGameId()))
            {
                GalaxyDefenderWindow window = new GalaxyDefenderWindow();
                window.setVisible(true);
            }
            else if ("word-guess".equals(game.getGameId()))
            {
                WordGuessWindow window = new WordGuessWindow();
                window.setVisible(true);
            }
            else if ("bubble-shooter".equals(game.getGameId()))
            {
                BubbleShooterWindow window = new BubbleShooterWindow();
                window.setVisible(true);
            }
            else if ("lights-out".equals(game.getGameId()))
            {
                LightsOutWindow window = new LightsOutWindow();
                window.setVisible(true);
            }
            else if ("peg-solitaire".equals(game.getGameId()))
            {
                PegSolitaireWindow window = new PegSolitaireWindow();
                window.setVisible(true);
            }
            else if ("klondike".equals(game.getGameId()))
            {
                KlondikeWindow window = new KlondikeWindow();
                window.setVisible(true);
            }
            else if ("yahtzee".equals(game.getGameId()))
            {
                YahtzeeWindow window = new YahtzeeWindow();
                window.setVisible(true);
            }
            else if ("mancala".equals(game.getGameId()))
            {
                MancalaWindow window = new MancalaWindow();
                window.setVisible(true);
            }
            else if ("dots-and-boxes".equals(game.getGameId()))
            {
                DotsAndBoxesWindow window = new DotsAndBoxesWindow();
                window.setVisible(true);
            }
            else if ("reversi".equals(game.getGameId()))
            {
                MainMenu.getInstance().showGame(new ReversiWindow());
            }
            else if ("memory-match".equals(game.getGameId()))
            {
                MemoryMatchWindow window = new MemoryMatchWindow();
                window.setVisible(true);
            }
            else if ("air-hockey".equals(game.getGameId()))
            {
                AirHockeyWindow window = new AirHockeyWindow();
                window.setVisible(true);
            }
            else if ("word-duel".equals(game.getGameId()))
            {
                WordDuelWindow window = new WordDuelWindow();
                window.setVisible(true);
            }
            else if ("dice-duel".equals(game.getGameId()))
            {
                DiceDuelWindow window = new DiceDuelWindow();
                window.setVisible(true);
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
                TypingDuelWindow window = new TypingDuelWindow();
                window.setVisible(true);
            }
            else if ("signal-grid".equals(game.getGameId()))
            {
                SignalGridWindow window = new SignalGridWindow();
                window.setVisible(true);
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
