import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * AchievementToast
 * ----------------
 * A small, non-modal, auto-dismissing notification for a live
 * achievement unlock - deliberately not a GameHubDialog (which needs
 * an explicit OK click), since a celebratory popup shouldn't demand
 * an action to go away. Floats independently as a JWindow rather than
 * a JDialog, so it isn't tied to whatever window happens to be active
 * when the unlock arrives, and slides into the bottom-right corner.
 */
public class AchievementToast
{
    private static final int DISPLAY_MS = 4500;
    private static final int WIDTH = 300;

    private AchievementToast()
    {
        // Static utility class - never instantiated.
    }

    public static void show(String achievementName, String description)
    {
        final JWindow window = new JWindow();
        window.setAlwaysOnTop(true);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 14);
        root.setLayout(new BorderLayout());
        root.enableTopAccent();
        window.setContentPane(root);
        root.setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.ACCENT), 1));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel header = new JLabel("\u2605 Achievement Unlocked");
        header.setFont(UITheme.FONT_SMALL);
        header.setForeground(ThemeManager.getColor(ThemeColor.ACCENT));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(header);

        JLabel nameLabel = new JLabel(achievementName);
        nameLabel.setFont(UITheme.FONT_NAV_BOLD);
        nameLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameLabel.setBorder(new EmptyBorder(4, 0, 4, 0));
        body.add(nameLabel);

        JLabel descLabel = new JLabel(description);
        descLabel.setFont(UITheme.FONT_SMALL);
        descLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(descLabel);

        root.add(body, BorderLayout.CENTER);

        window.pack();
        window.setSize(new Dimension(WIDTH, window.getHeight()));

        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        window.setLocation(screen.x + screen.width - WIDTH - 24, screen.y + screen.height - window.getHeight() - 24);

        window.setVisible(true);

        Timer dismissTimer = new Timer(DISPLAY_MS, new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { window.dispose(); }
        });
        dismissTimer.setRepeats(false);
        dismissTimer.start();
    }
}
