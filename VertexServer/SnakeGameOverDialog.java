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
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(anchor);
        final JDialog dialog = new JDialog(owner, true);
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(new LineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(24, 24, 4, 24));

        JLabel title = new JLabel("Game Over");
        title.setFont(UITheme.FONT_HEADING.deriveFont(18f));
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(title);

        JLabel scoreLabel = new JLabel("Score: " + score);
        scoreLabel.setFont(UITheme.FONT_BODY);
        scoreLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        scoreLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        scoreLabel.setBorder(new EmptyBorder(8, 0, 0, 0));
        body.add(scoreLabel);

        root.add(body, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonRow.setOpaque(false);
        buttonRow.setBorder(new EmptyBorder(18, 24, 20, 24));

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
