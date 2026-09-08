import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * DialogUtils
 * -----------
 * Shared helpers for the app's many small custom popups (all built the
 * same way: an undecorated JDialog with its own RoundedPanel content).
 * enableEscapeToClose() gives every one of them a free, expected piece
 * of desktop-app behavior - Escape dismisses the dialog - without each
 * one needing its own key binding boilerplate.
 */
public class DialogUtils
{
    private DialogUtils()
    {
        // Static utility class - never instantiated.
    }

    /** Binds Escape to dialog.dispose() - safe to call on any dialog, since every one of these popups treats dispose() as its normal "just close it" path (none of them need special cleanup on close beyond what a Cancel/Close button already does). */
    public static void enableEscapeToClose(final JDialog dialog)
    {
        JComponent root = dialog.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escapeCloseDialog");
        root.getActionMap().put("escapeCloseDialog", new AbstractAction()
        {
            public void actionPerformed(ActionEvent e) { dialog.dispose(); }
        });
    }
}
