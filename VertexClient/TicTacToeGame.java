/**
 * TicTacToeGame
 * -------------
 * Implements Game for consistency with the rest of the catalog, but
 * unlike Snake (fully offline), a networked match's real state lives on
 * the server and is echoed to the client via NetworkManager pushes -
 * there's no meaningful local state to save/load here. This is a known
 * simplification; a fuller design would separate "offline-capable
 * local state" from "live networked session," which matters more once
 * spectating or reconnecting mid-match is needed.
 */
public class TicTacToeGame implements Game
{
    public GameInfo getInfo()
    {
        return new GameInfo("tictactoe-online", "Tic-Tac-Toe (Online)", "Multiplayer", "Online", true, false, "1.0");
    }

    public void start()
    {
        // No-op - TicTacToeWindow drives the actual match lifecycle.
    }

    public void pause()
    {
        // No-op - nothing meaningful to pause in a live networked match.
    }

    public String saveState()
    {
        return "";
    }

    public void loadState(String savedState)
    {
        // Not applicable - see class comment.
    }
}
