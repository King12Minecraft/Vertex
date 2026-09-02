import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * QuestRow
 * --------
 * One quest/challenge's progress, in two sizes: full detail (used on
 * the Quests page - title, description, reward, progress bar) and
 * compact (used in the Sidebar's always-visible in-progress mini-list -
 * title + slim bar only, no description). Both read from the same
 * ChallengeProgressInfo the server already sends; "quest" is just the
 * player-facing name for what the Challenges system already tracks.
 *
 * Color-coded by reset period so different quest types are visually
 * distinct at a glance: DAILY uses the theme accent, WEEKLY uses
 * success green, permanent (NONE) uses the gradient's second color -
 * all colors already come from the active theme, so this stays
 * consistent across all 10 palettes rather than hardcoding one look.
 */
public class QuestRow extends RoundedPanel
{
    public QuestRow(ChallengeProgressInfo quest, boolean compact)
    {
        super(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        if (!compact)
        {
            enableTopAccent();
        }
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(compact ? 8 : 14, compact ? 10 : 16, compact ? 8 : 14, compact ? 10 : 16));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setMaximumSize(new Dimension(2000, compact ? 52 : 92));

        Color periodColor = colorForResetPeriod(quest.getResetPeriod());

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel(quest.getTitle() + (quest.isCompleted() ? "  \u2713" : ""));
        title.setFont(compact ? UITheme.FONT_SMALL : UITheme.FONT_NAV_BOLD);
        title.setForeground(quest.isCompleted()
            ? ThemeManager.getColor(ThemeColor.SUCCESS)
            : ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        titleRow.add(title);

        if (!compact)
        {
            JLabel periodBadge = new JLabel(labelForResetPeriod(quest.getResetPeriod()));
            periodBadge.setFont(UITheme.FONT_SMALL);
            periodBadge.setForeground(periodColor);
            periodBadge.setBorder(new EmptyBorder(0, 8, 0, 0));
            titleRow.add(periodBadge);
        }

        textCol.add(titleRow);

        if (!compact)
        {
            JLabel desc = new JLabel(quest.getDescription() + "  (+" + quest.getRewardCoins() + " coins)");
            desc.setFont(UITheme.FONT_SMALL);
            desc.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            desc.setAlignmentX(Component.LEFT_ALIGNMENT);
            desc.setBorder(new EmptyBorder(2, 0, 8, 0));
            textCol.add(desc);
        }

        JProgressBar bar = new JProgressBar(0, Math.max(quest.getTarget(), 1));
        bar.setValue(quest.getProgress());
        bar.setStringPainted(!compact);
        if (!compact)
        {
            bar.setString(quest.getProgress() + " / " + quest.getTarget());
        }
        bar.setForeground(quest.isCompleted() ? ThemeManager.getColor(ThemeColor.SUCCESS) : periodColor);
        bar.setBackground(ThemeManager.getColor(ThemeColor.BG_SIDEBAR));
        bar.setBorderPainted(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(2000, compact ? 6 : 18));
        bar.setPreferredSize(new Dimension(2000, compact ? 6 : 18));
        if (compact)
        {
            bar.setBorder(new EmptyBorder(4, 0, 0, 0));
        }
        textCol.add(bar);

        add(textCol, BorderLayout.CENTER);
    }

    private Color colorForResetPeriod(String resetPeriod)
    {
        if ("WEEKLY".equals(resetPeriod))
        {
            return ThemeManager.getColor(ThemeColor.SUCCESS);
        }
        if ("NONE".equals(resetPeriod))
        {
            return ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_END);
        }
        return ThemeManager.getColor(ThemeColor.ACCENT);
    }

    private String labelForResetPeriod(String resetPeriod)
    {
        if ("WEEKLY".equals(resetPeriod))
        {
            return "WEEKLY";
        }
        if ("NONE".equals(resetPeriod))
        {
            return "ONE-TIME";
        }
        return "DAILY";
    }
}
