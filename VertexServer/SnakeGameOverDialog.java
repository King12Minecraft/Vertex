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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * SnakeGameOverDialog
 * --------------------
 * Themed "Game Over" popup with a final score and Play Again / Close
 * buttons. Same visual pattern as GameHubDialog, just with two actions
 * instead of one - reusable for other games' game-over screens later.
 */
public class SnakeGameOverDialog
{
    public interface Choice
    {
        void onPlayAgain();
        void onClose();
    }

    private SnakeGameOverDialog()
    {
        // Static utility class - never instantiated.
    }

    public static void show(Component anchor, int score, final Choice choice)
    {
        show(anchor, score, null, null, choice);
    }

    /** Same dialog, but with a custom headline/summary line instead of the generic "Game Over" + "Score: N" - used for a win screen (e.g. Zombie Survival clearing all waves) where "Game Over" reads wrong but a restart option still makes sense. customMessage may be null to fall back to the original plain score display. */
    public static void show(Component anchor, int score, String customMessage, final Choice choice)
    {
        show(anchor, score, customMessage, null, choice);
    }

    /** shareText, when non-null, adds a Share button that opens ScoreShareDialog with that exact text - callers build their own wording ("I scored 42 in Snake!", "I won a Checkers match on Vertex!") since only they know what's actually worth bragging about for that game. Null skips the Share button entirely, same as the two simpler overloads above. */
    public static void show(Component anchor, int score, String customMessage, final String shareText, final Choice choice)
    {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(anchor);
        final JDialog dialog = new JDialog(owner, true);
        dialog.setUndecorated(true);
        DialogUtils.enableEscapeToClose(dialog);
        dialog.setBackground(new Color(0, 0, 0, 0));

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(new LineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(24, 24, 4, 24));

        JLabel title = new JLabel(customMessage != null ? "Round Complete!" : "Game Over");
        title.setFont(UITheme.FONT_HEADING.deriveFont(18f));
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(title);

        JLabel scoreLabel = new JLabel(customMessage != null ? customMessage : "Score: " + score);
        scoreLabel.setFont(UITheme.FONT_BODY);
        scoreLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        scoreLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        scoreLabel.setBorder(new EmptyBorder(8, 0, 0, 0));
        body.add(scoreLabel);

        root.add(body, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonRow.setOpaque(false);
        buttonRow.setBorder(new EmptyBorder(18, 24, 20, 24));

        if (shareText != null)
        {
            ThemedButton shareButton = new ThemedButton("Share", false);
            shareButton.setPreferredSize(new Dimension(80, 38));
            shareButton.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { ScoreShareDialog.show(dialog.getOwner(), shareText); }
            });
            buttonRow.add(shareButton);
        }

        ThemedButton closeButton = new ThemedButton("Close", false);
        closeButton.setPreferredSize(new Dimension(90, 38));
        closeButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                dialog.dispose();
                choice.onClose();
            }
        });

        ThemedButton playAgain = new ThemedButton("Play Again", true);
        playAgain.setPreferredSize(new Dimension(120, 38));
        playAgain.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                dialog.dispose();
                choice.onPlayAgain();
            }
        });

        buttonRow.add(closeButton);
        buttonRow.add(playAgain);
        root.add(buttonRow, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
}
