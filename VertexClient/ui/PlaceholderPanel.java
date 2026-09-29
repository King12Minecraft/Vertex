package ui;
import theme.ThemeColor;
import theme.UITheme;

import javax.swing.JComponent;
import javax.swing.JLabel;

/**
 * PlaceholderPanel
 * ----------------
 * The "clear this container and drop in a single muted label" shape
 * FriendsPanel, LeaderboardPanel, and ShopPanel each independently
 * hand-rolled for their own loading-failed/empty-list states - three real
 * call sites was enough repetition to justify a shared primitive instead
 * of a fourth hand-rolled copy the next time a panel needs the same thing
 * (see ROADMAP.md's own note on this). A static helper, not a component
 * every panel must be built from - GameWindowKernel's centered()/center()
 * already established that shape for exactly this kind of small, optional
 * Swing convenience in this codebase.
 *
 * Deliberately just "clear and show one label," not a stateful
 * loading/empty/error enum-driven widget - every existing call site
 * already decides its own message text (a real network reason, a "no
 * results" message, etc.), so there's nothing generic left to add beyond
 * the label itself. Callers that need to batch this alongside other
 * container changes (LeaderboardPanel's shop grids clear three sibling
 * containers together, for one) still own their own revalidate()/repaint()
 * timing - show() only ever touches the one container it's given.
 */
public class PlaceholderPanel
{
    private PlaceholderPanel()
    {
        // Static utility class - never instantiated.
    }

    /** Clears container and shows a single muted-text label in its place - the placeholder itself handles its own revalidate()/repaint(). */
    public static void show(JComponent container, String text)
    {
        container.removeAll();
        container.add(mutedLabel(text));
        container.revalidate();
        container.repaint();
    }

    /** Just the label, for a caller that's already mid-way through rebuilding a container's other children (e.g. an empty-state label sitting alongside real rows added right after it) and doesn't want show()'s own revalidate()/repaint() to run early. */
    public static JLabel mutedLabel(String text)
    {
        return new ThemedLabel(text, UITheme.FONT_SMALL, ThemeColor.TEXT_MUTED);
    }
}
