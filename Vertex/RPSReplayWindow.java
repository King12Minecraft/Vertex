import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;

/**
 * RPSReplayWindow
 * ----------------
 * Every round of a finished Rock Paper Scissors match, shown all at
 * once rather than stepped through - unlike Chess's board (where only
 * one position makes sense at a time), a best-of-5 RPS match is just
 * 3-5 short rounds, so a scrollable list is simpler and more useful
 * than a Prev/Next stepper. Round log format is "moveA:moveB:scoreA:
 * scoreB" per round; player identity isn't preserved as "you" vs
 * opponent (the log doesn't carry that distinction), so rounds are
 * labeled neutrally as Player 1 / Player 2 by position.
 */
public class RPSReplayWindow extends JFrame
{
    public RPSReplayWindow(List<String> roundLog)
    {
        super("Vertex - Replay: Rock Paper Scissors");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_APP, 0);
        root.setLayout(new BorderLayout());
        root.setBorder(new EmptyBorder(20, 24, 20, 24));
        root.setPreferredSize(new Dimension(360, 420));

        JLabel title = new JLabel("Round-by-Round");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setBorder(new EmptyBorder(0, 0, 16, 0));
        root.add(title, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

        for (int i = 0; i < roundLog.size(); i++)
        {
            String[] parts = roundLog.get(i).split(":", -1);
            if (parts.length < 4)
            {
                continue;
            }
            String pickA = parts[0];
            String pickB = parts[1];
            String scoreA = parts[2];
            String scoreB = parts[3];

            RoundedPanel row = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_BUTTON);
            row.setLayout(new BorderLayout());
            row.setBorder(new EmptyBorder(10, 14, 10, 14));
            row.setMaximumSize(new Dimension(2000, 50));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel roundLabel = new JLabel("Round " + (i + 1) + ":  " + pickA + " vs " + pickB);
            roundLabel.setFont(UITheme.FONT_BODY);
            roundLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
            row.add(roundLabel, BorderLayout.WEST);

            JLabel scoreLabel = new JLabel(scoreA + " - " + scoreB);
            scoreLabel.setFont(UITheme.FONT_SMALL);
            scoreLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            row.add(scoreLabel, BorderLayout.EAST);

            list.add(row);
            list.add(javax.swing.Box.createVerticalStrut(6));
        }

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        root.add(scroll, BorderLayout.CENTER);

        setContentPane(root);
        pack();
        setLocationRelativeTo(null);
        SignatureOverlay.attach(this);
        GlitchEffectOverlay.attach(this);
    }
}
