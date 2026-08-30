import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * GameHubDialog
 * -------------
 * Shared themed modal dialog. Used everywhere instead of JOptionPane so
 * every popup in the app matches the active theme instead of looking
 * like a default Swing dialog. Supports a simple title+message notice,
 * or the same plus a bullet list (used by Refresh).
 */
public class GameHubDialog
{
    private GameHubDialog()
    {
        // Static utility class - never instantiated.
    }

    public static void show(Component anchor, String title, String message)
    {
        show(anchor, title, message, null);
    }

    public static void show(Component anchor, String title, String message, List<String> bullets)
    {
        show(anchor, title, message, bullets, null, null);
    }

    /** Same dialog, with an extra action button (e.g. "Rematch") next to OK - onAction runs after the dialog closes, not instead of closing it. Existing show(...) callers are untouched; this is purely additive. */
    public static void showWithAction(Component anchor, String title, String message, String actionLabel, Runnable onAction)
    {
        show(anchor, title, message, null, actionLabel, onAction);
    }

    private static void show(Component anchor, String title, String message, List<String> bullets, String actionLabel, Runnable onAction)
    {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(anchor);
        final JDialog dialog = new JDialog(owner, true);
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        root.enableTopAccent();
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(new LineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        root.add(createTitleBar(dialog), BorderLayout.NORTH);
        root.add(createBody(title, message, bullets), BorderLayout.CENTER);
        root.add(createOkRow(dialog, actionLabel, onAction), BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static JPanel createTitleBar(final JDialog dialog)
    {
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        titleBar.setBorder(new EmptyBorder(16, 22, 4, 12));

        JLabel barLabel = new JLabel("VERTEX");
        barLabel.setFont(UITheme.FONT_NAV_BOLD);
        barLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        titleBar.add(barLabel, BorderLayout.WEST);
        titleBar.add(createCloseButton(dialog), BorderLayout.EAST);
        return titleBar;
    }

    private static JPanel createBody(String title, String message, List<String> bullets)
    {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(6, 24, 4, 24));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(UITheme.FONT_HEADING.deriveFont(18f));
        titleLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(titleLabel);

        JLabel messageLabel = new JLabel("<html><body style='width:280px'>" + message + "</body></html>");
        messageLabel.setFont(UITheme.FONT_BODY);
        messageLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        messageLabel.setBorder(new EmptyBorder(8, 0, 0, 0));
        body.add(messageLabel);

        if (bullets != null)
        {
            for (int i = 0; i < bullets.size(); i++)
            {
                JLabel bulletLabel = new JLabel("\u2022  " + bullets.get(i));
                bulletLabel.setFont(UITheme.FONT_BODY);
                bulletLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
                bulletLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                bulletLabel.setBorder(new EmptyBorder(8, 0, 0, 0));
                body.add(bulletLabel);
            }
        }

        return body;
    }

    private static javax.swing.JButton createCloseButton(final JDialog dialog)
    {
        javax.swing.JButton close = new javax.swing.JButton("\u00D7");
        close.setFont(UITheme.FONT_NAV_BOLD);
        close.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        close.setFocusPainted(false);
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setOpaque(false);
        close.setPreferredSize(new Dimension(28, 28));
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { dialog.dispose(); }
        });
        return close;
    }

    private static JPanel createOkRow(final JDialog dialog, String actionLabel, final Runnable onAction)
    {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(18, 24, 20, 24));

        if (actionLabel != null && onAction != null)
        {
            ThemedButton action = new ThemedButton(actionLabel, false);
            action.setPreferredSize(new Dimension(110, 38));
            action.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    dialog.dispose();
                    onAction.run();
                }
            });
            row.add(action);
        }

        ThemedButton ok = new ThemedButton("OK", true);
        ok.setPreferredSize(new Dimension(90, 38));
        ok.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { dialog.dispose(); }
        });

        row.add(ok);
        dialog.getRootPane().setDefaultButton(ok);
        return row;
    }
}
