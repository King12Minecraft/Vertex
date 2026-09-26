package games;
import ui.ThemedButton;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * GameWindowKernel
 * ----------------
 * The two pieces of Swing boilerplate every one of the ~51 embedded game
 * windows in this codebase has hand-rolled at least once (many several
 * times, once per screen) since the embedded-games conversion:
 *
 *   1. The "GridBagLayout centering wrapper" - a bare board/content panel
 *      added directly to a CardLayout deck or BorderLayout.CENTER
 *      stretches to fill the whole available area instead of centering
 *      at its own preferred size. Every single game conversion this
 *      session hit this exact bug in a slightly different disguise
 *      (a board, a mode-select column, a stacked group of controls, an
 *      HTML label inflating its container's reported width) and fixed
 *      it the same way each time: wrap the content in a small
 *      `new JPanel(new GridBagLayout())` with the content added via a
 *      bare `new GridBagConstraints()`. That fix is now one line
 *      instead of four.
 *   2. The "Leave" button - same label, same size, same construction,
 *      every time; only the actual leave action varies per game
 *      (most call `MainMenu.getInstance().returnToGames()`, some route
 *      through a custom `setReturnAction`-style callback instead - see
 *      SnakeWindow - so that part stays the caller's job, passed in as
 *      a plain Runnable).
 *
 * Deliberately NOT a base class every game window must extend - the ~51
 * existing windows have too much individual shape (mode-select/searching/
 * waiting/board screens in different combinations, bare canvases vs.
 * GridLayout boards vs. scroll panes) to retrofit onto one shared
 * superclass safely in one pass, and forcing new games through an
 * inheritance hierarchy for two lines of layout code would be the wrong
 * trade. A static helper any window can call piecemeal, exactly like
 * PerformanceMode/EconomyKernel are static utilities rather than base
 * classes, is the safer fit - existing windows can adopt it opportunistically
 * (see ReversiWindow/BrickBreakerWindow for the first two), and every
 * future game gets the one-line version from day one.
 */
public final class GameWindowKernel
{
    private GameWindowKernel()
    {
        // Static utility class - never instantiated.
    }

    /**
     * Wraps content in a non-opaque GridBagLayout panel so it renders at its own
     * preferred size, centered, instead of being stretched to fill whatever
     * container it's placed in (CardLayout, BorderLayout.CENTER, ...). Replaces the
     * repeated:
     * <pre>
     *   JPanel wrapper = new JPanel(new GridBagLayout());
     *   wrapper.setOpaque(false);
     *   wrapper.add(content, new GridBagConstraints());
     * </pre>
     */
    public static JPanel centered(JComponent content)
    {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);
        wrapper.add(content, new GridBagConstraints());
        return wrapper;
    }

    /**
     * Same centering fix as {@link #centered}, for the other common shape: a screen's
     * own outer panel (often a RoundedPanel already serving as that screen's
     * background) should do the centering itself rather than being wrapped by a new
     * one - sets its layout to GridBagLayout and adds content at the default
     * (centered) constraints. Replaces:
     * <pre>
     *   wrapper.setLayout(new GridBagLayout());
     *   wrapper.add(content, new GridBagConstraints());
     * </pre>
     */
    public static void center(Container ownScreenPanel, JComponent content)
    {
        ownScreenPanel.setLayout(new GridBagLayout());
        ownScreenPanel.add(content, new GridBagConstraints());
    }

    /**
     * A themed "Leave" button matching the size/style every game already uses,
     * running onLeaveConfirmed when clicked - the caller decides what leaving
     * actually means (most: {@code if (requestLeave()) MainMenu.getInstance()
     * .returnToGames();} - some, like Snake, route through their own returnAction
     * callback instead).
     */
    public static ThemedButton leaveButton(final Runnable onLeaveConfirmed)
    {
        ThemedButton button = new ThemedButton("Leave", false);
        button.setPreferredSize(new Dimension(90, 34));
        button.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { onLeaveConfirmed.run(); }
        });
        return button;
    }
}
