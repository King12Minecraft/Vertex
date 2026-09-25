package games;
import pages.MainMenu;
import ui.GameHubDialog;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import java.awt.Component;
import java.util.function.Supplier;

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
 *
 * openGame itself used to be a 49-branch if/else, one per converted
 * game - by the time the embedded-games conversion finished, every
 * single branch had the exact same shape
 * (MainMenu.getInstance().showGame(new XxxWindow())), which is what
 * GameWindowFactory's id-to-constructor map now replaces it with. A
 * game with no factory entry (not yet converted, or a typo'd id)
 * falls through to the same "not converted yet" notice launch(...)
 * already shows for comingSoon games.
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
            Supplier<JComponent> factory = GameWindowFactory.factoryFor(game.getGameId());
            if (factory != null)
            {
                MainMenu.getInstance().showGame(factory.get());
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
