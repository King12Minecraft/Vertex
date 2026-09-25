package games;

/**
 * EmbeddedGamePanel
 * ------------------
 * Implemented by a game panel that's embedded in MainMenu's game-host
 * slot (see MainMenu.showGame(...)/returnToGames()) rather than opened
 * as its own separate window. MainMenu calls requestLeave() before
 * navigating away from a hosted game (e.g. someone clicks a Sidebar
 * entry mid-match) - this is the embedded replacement for the old
 * per-window JFrame's windowClosing confirmation, since there's no
 * window-close event to hook once a game isn't its own window anymore.
 */
public interface EmbeddedGamePanel
{
    /**
     * Return true to allow navigating away immediately. Return false to
     * intercept and handle it yourself instead (e.g. show a confirm
     * dialog, and if the player confirms, call
     * MainMenu.getInstance().returnToGames() from there) - navigation
     * does not proceed on false, so a game with nothing at stake (no
     * active match) should just return true unconditionally.
     */
    boolean requestLeave();
}
