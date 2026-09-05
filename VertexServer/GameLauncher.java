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
 */
public class GameLauncher
{
    private GameLauncher()
    {
        // Static utility class - never instantiated.
    }

    public static void launch(Component anchor, GameInfo game)
    {
        if (game.isComingSoon())
        {
            GameHubDialog.show(anchor, game.getName(),
                "This game hasn't been converted yet - it arrives once it's brought "
                + "into Vertex, following the same process Snake and Tic-Tac-Toe went through.");
            return;
        }

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
                ChessWindow window = new ChessWindow();
                window.setVisible(true);
            }
            else if ("battleship".equals(game.getGameId()))
            {
                BattleshipWindow window = new BattleshipWindow();
                window.setVisible(true);
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
