import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * GameRulesDialog
 * ---------------
 * A simple "how to play" popup, shown from the Rules button on every
 * game card. Text comes from GameRules.
 */
public class GameRulesDialog
{
    public static void show(Component anchor, String gameName, String gameId)
    {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(anchor);
        final JDialog dialog = new JDialog(owner, true);
        dialog.setUndecorated(true);
        DialogUtils.enableEscapeToClose(dialog);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        root.setPreferredSize(new Dimension(360, 260));
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(22, 22, 8, 22));

        JLabel title = new JLabel(gameName + " - Rules");
        title.setFont(UITheme.FONT_NAV_BOLD);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(title);

        JLabel rulesLabel = new JLabel("<html><body style='width:300px'>" + escapeHtml(GameRules.get(gameId)) + "</body></html>");
        rulesLabel.setFont(UITheme.FONT_BODY);
        rulesLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        rulesLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        rulesLabel.setBorder(new EmptyBorder(12, 0, 0, 0));
        body.add(rulesLabel);

        root.add(body, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 10, 0));
        buttonRow.setOpaque(false);
        buttonRow.setBorder(new EmptyBorder(10, 22, 18, 22));

        ThemedButton close = new ThemedButton("Got it", true);
        close.setPreferredSize(new Dimension(90, 36));
        close.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { dialog.dispose(); }
        });
        buttonRow.add(close);
        root.add(buttonRow, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(anchor);
        dialog.setVisible(true);
    }

    private static String escapeHtml(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
